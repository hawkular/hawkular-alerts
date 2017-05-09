/*
 * Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.alerts.engine.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.Map;
import java.util.jar.Manifest;

import javax.net.ssl.SSLContext;

import org.cassalog.core.Cassalog;
import org.cassalog.core.CassalogBuilder;
import org.hawkular.alerts.cache.IspnCacheManager;
import org.hawkular.alerts.engine.util.TokenReplacingReader;
import org.hawkular.alerts.properties.AlertProperties;
import org.infinispan.Cache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.jboss.logging.Logger;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.JdkSSLOptions;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.QueryOptions;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.SSLOptions;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SocketOptions;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;

/**
 * Cassandra cluster representation and session factory.
 *
 * @author Lucas Ponce
 */
public class CassCluster {
    private static final Logger log = Logger.getLogger(CassCluster.class);

    /*
        PORT used by the Cassandra cluster
     */
    private static final String ALERTS_CASSANDRA_PORT = "hawkular-alerts.cassandra-cql-port";
    private static final String ALERTS_CASSANDRA_PORT_ENV = "CASSANDRA_CQL_PORT";
    private static final String ALERTS_CASSANDRA_PORT_ENV_DEFAULT = "9042";

    /*
        List of nodes defined on the Cassandra cluster
     */
    private static final String ALERTS_CASSANDRA_NODES = "hawkular-alerts.cassandra-nodes";
    private static final String ALERTS_CASSANDRA_NODES_ENV = "CASSANDRA_NODES";
    private static final String ALERTS_CASSANDRA_NODES_ENV_DEFAULT = "127.0.0.1";

    /*
        Hawkular Alerts keyspace name used on Cassandra cluster
     */
    private static final String ALERTS_CASSANDRA_KEYSPACE = "hawkular-alerts.cassandra-keyspace";
    private static final String ALERTS_CASSANDRA_KEYSPACE_DEFAULT = "hawkular_alerts";

    /*
        Number of attempts when Hawkular Alerts cannot connect with Cassandra cluster to retry
     */
    private static final String ALERTS_CASSANDRA_RETRY_ATTEMPTS = "hawkular-alerts.cassandra-retry-attempts";
    private static final String ALERTS_CASSANDRA_RETRY_ATTEMPTS_DEFAULT = "5";

    /*
        ALERTS_CASSANDRA_RETRY_TIMEOUT defined in milliseconds
     */
    private static final String ALERTS_CASSANDRA_RETRY_TIMEOUT = "hawkular-alerts.cassandra-retry-timeout";
    private static final String ALERTS_CASSANDRA_RETRY_TIMEOUT_DEFAULT = "2000";

    /*
        ALERTS_CASSANDRA_CONNECT_TIMEOUT and ALERTS_CASSANDRA_CONNECT_TIMEOUT_ENV defined in milliseconds
     */
    private static final String ALERTS_CASSANDRA_CONNECT_TIMEOUT = "hawkular-alerts.cassandra-connect-timeout";
    private static final String ALERTS_CASSANDRA_CONNECT_TIMEOUT_ENV = "CASSANDRA_CONNECT_TIMEOUT";

    /*
        ALERTS_CASSANDRA_READ_TIMEOUT and ALERTS_CASSANDRA_READ_TIMEOUT_ENV defined in milliseconds
     */
    private static final String ALERTS_CASSANDRA_READ_TIMEOUT = "hawkular-alerts.cassandra-read-timeout";
    private static final String ALERTS_CASSANDRA_READ_TIMEOUT_ENV = "CASSANDRA_READ_TIMEOUT";

    /*
        GLOBAL OVERWRITE true/false flag to recreate an existing schema
     */
    private static final String ALERTS_CASSANDRA_OVERWRITE = "hawkular-alerts.cassandra-overwrite";
    private static final String ALERTS_CASSANDRA_OVERWRITE_ENV = "CASSANDRA_OVERWRITE";
    private static final String ALERTS_CASSANDRA_OVERWRITE_ENV_DEFAULT = "false";

    /*
       True/false flag to use SSL communication with Cassandra cluster
     */
    private static final String ALERTS_CASSANDRA_USESSL = "hawkular-alerts.cassandra-use-ssl";
    private static final String ALERTS_CASSANDRA_USESSL_ENV = "CASSANDRA_USESSL";
    private static final String ALERTS_CASSANDRA_USESSL_ENV_DEFAULT = "false";

    private static final String ALERTS_CASSANDRA_MAX_QUEUE = "hawkular-alerts.cassandra-max-queue";
    private static final String ALERTS_CASSANDRA_MAX_QUEUE_ENV = "CASSANDRA_MAX_QUEUE";
    private static final String ALERTS_CASSANDRA_MAX_QUEUE_ENV_DEFAULT = "9182";

    private int attempts;
    private int timeout;
    private String cqlPort;
    private String nodes;
    private int connTimeout;
    private int readTimeout;
    private boolean overwrite = false;
    private String keyspace;
    private boolean cassandraUseSSL;
    private int maxQueue;

    private Cluster cluster = null;

    private Session session = null;

    private boolean initialized = false;

    private boolean distributed = false;

    private static final String SCHEMA = "schema";

    /**
     * This cache will be used to coordinate schema creation across a cluster of nodes.
     */
    private Cache schemaCache;

    private void readProperties() {
        attempts = Integer.parseInt(AlertProperties.getProperty(ALERTS_CASSANDRA_RETRY_ATTEMPTS,
                ALERTS_CASSANDRA_RETRY_ATTEMPTS_DEFAULT));
        timeout = Integer.parseInt(AlertProperties.getProperty(ALERTS_CASSANDRA_RETRY_TIMEOUT,
                ALERTS_CASSANDRA_RETRY_TIMEOUT_DEFAULT));
        cqlPort = AlertProperties.getProperty(ALERTS_CASSANDRA_PORT, ALERTS_CASSANDRA_PORT_ENV,
                ALERTS_CASSANDRA_PORT_ENV_DEFAULT);
        nodes = AlertProperties.getProperty(ALERTS_CASSANDRA_NODES, ALERTS_CASSANDRA_NODES_ENV,
                ALERTS_CASSANDRA_NODES_ENV_DEFAULT);
        connTimeout = Integer.parseInt(AlertProperties.getProperty(ALERTS_CASSANDRA_CONNECT_TIMEOUT,
                ALERTS_CASSANDRA_CONNECT_TIMEOUT_ENV, String.valueOf(SocketOptions.DEFAULT_CONNECT_TIMEOUT_MILLIS)));
        readTimeout = Integer.parseInt(AlertProperties.getProperty(ALERTS_CASSANDRA_READ_TIMEOUT,
                ALERTS_CASSANDRA_READ_TIMEOUT_ENV, String.valueOf(SocketOptions.DEFAULT_READ_TIMEOUT_MILLIS)));
        overwrite = Boolean.parseBoolean(AlertProperties.getProperty(ALERTS_CASSANDRA_OVERWRITE,
                ALERTS_CASSANDRA_OVERWRITE_ENV, ALERTS_CASSANDRA_OVERWRITE_ENV_DEFAULT));
        keyspace = AlertProperties.getProperty(ALERTS_CASSANDRA_KEYSPACE, ALERTS_CASSANDRA_KEYSPACE_DEFAULT);
        cassandraUseSSL = Boolean.parseBoolean(AlertProperties.getProperty(ALERTS_CASSANDRA_USESSL,
                ALERTS_CASSANDRA_USESSL_ENV, ALERTS_CASSANDRA_USESSL_ENV_DEFAULT));
        maxQueue = Integer.parseInt(AlertProperties.getProperty(ALERTS_CASSANDRA_MAX_QUEUE,
                ALERTS_CASSANDRA_MAX_QUEUE_ENV, ALERTS_CASSANDRA_MAX_QUEUE_ENV_DEFAULT));

        distributed = IspnCacheManager.isDistributed();
    }

    public void initCassCluster() {
        readProperties();

        if (distributed) {
            schemaCache = IspnCacheManager.getCacheManager().getCache(SCHEMA);
        }

        int currentAttempts = attempts;

        SocketOptions socketOptions = null;
        if (connTimeout != SocketOptions.DEFAULT_CONNECT_TIMEOUT_MILLIS ||
                readTimeout != SocketOptions.DEFAULT_READ_TIMEOUT_MILLIS) {
            socketOptions = new SocketOptions();
            if (connTimeout != SocketOptions.DEFAULT_CONNECT_TIMEOUT_MILLIS) {
                socketOptions.setConnectTimeoutMillis(connTimeout);
            }
            if (readTimeout != SocketOptions.DEFAULT_READ_TIMEOUT_MILLIS) {
                socketOptions.setReadTimeoutMillis(readTimeout);
            }
        }

        Cluster.Builder clusterBuilder = new Cluster.Builder()
                .addContactPoints(nodes.split(","))
                .withPort(new Integer(cqlPort))
                .withPoolingOptions(new PoolingOptions().setMaxQueueSize(maxQueue))
                .withProtocolVersion(ProtocolVersion.V3)
                .withQueryOptions(new QueryOptions().setRefreshSchemaIntervalMillis(0));

        if (socketOptions != null) {
            clusterBuilder.withSocketOptions(socketOptions);
        }

        if (cassandraUseSSL) {
            SSLOptions sslOptions = null;
            try {
                String[] defaultCipherSuites = { "TLS_RSA_WITH_AES_128_CBC_SHA", "TLS_RSA_WITH_AES_256_CBC_SHA" };
                sslOptions = JdkSSLOptions.builder().withSSLContext(SSLContext.getDefault())
                        .withCipherSuites(defaultCipherSuites).build();
                clusterBuilder.withSSL(sslOptions);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("SSL support is required but is not available in the JVM.", e);
            }
        }

        /*
            It might happen that alerts component is faster than embedded cassandra deployed in hawkular.
            We will provide a simple attempt/retry loop to avoid issues at initialization.
         */
        while(session == null && !Thread.currentThread().isInterrupted() && currentAttempts >= 0) {
            try {
                cluster = clusterBuilder.build();
                session = cluster.connect();
            } catch (Exception e) {
                log.warn("Could not connect to Cassandra cluster - assuming is not up yet. Cause: " +
                        ((e.getCause() == null) ? e : e.getCause()));
                if (attempts == 0) {
                    throw e;
                }
            }
            if (session == null) {
                log.warn("[" + currentAttempts + "] Retrying connecting to Cassandra cluster " +
                        "in [" + timeout + "]ms...");
                currentAttempts--;
                try {
                    Thread.sleep(timeout);
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        if (session != null) {
            try {
                waitForAllNodesToBeUp();
                if (distributed) {
                    initSchemeDistributed();
                } else {
                    initScheme();
                }
            } catch (IOException e) {
                log.error("Error on initialization of Alerts scheme", e);
            }
        }

        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (session != null && !initialized) {
            throw new RuntimeException("Cassandra alerts keyspace is not initialized");
        }
    }

    private void waitForAllNodesToBeUp() {
        boolean isReady = false;
        int attempts = this.attempts;

        while (!isReady && !Thread.currentThread().isInterrupted() && attempts-- >= 0) {
            isReady = true;
            for (Host host : cluster.getMetadata().getAllHosts()) {
                if (!host.isUp()) {
                    isReady = false;
                    log.warnf("Cassandra node %s may not be up yet. Waiting %s ms for node to come up", host, timeout);
                    try {
                        Thread.sleep(timeout);
                    } catch(InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    break;
                }
            }
        }
        if (!isReady) {
            throw new RuntimeException("It appears that not all nodes in the Cassandra cluster are up after " +
                    this.attempts + " checks. Schema updates cannot proceed without all nodes being up.");
        }
    }

    private void initSchemeDistributed() throws IOException {
        try {
            schemaCache.getAdvancedCache().getTransactionManager().begin();
            schemaCache.getAdvancedCache().lock(SCHEMA);
            initScheme();
            schemaCache.getAdvancedCache().getTransactionManager().rollback();
        } catch (Exception e) {
            log.error(e);
        }
    }

    private void initScheme() throws IOException {
        log.infof("Checking Schema existence for keyspace: %s", keyspace);
        createSchema(session, keyspace, overwrite);
        waitForSchemaCheck();
        if (!checkSchema()) {
            log.errorf("Schema %s not created correctly", keyspace);
            initialized = false;
        } else {
            initialized = true;
            log.infof("Done creating Schema for keyspace: %s", keyspace);
        }
    }

    private void waitForSchemaCheck() {
        int currentAttempts = attempts;
        while(!checkSchema() && !Thread.currentThread().isInterrupted() && currentAttempts >= 0) {
            log.warnf("[%s] Keyspace detected but schema not fully created. " +
                    "Retrying in [%s] ms...", currentAttempts, timeout);
            currentAttempts--;
            try {
                Thread.sleep(timeout);
            } catch(InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private boolean checkSchema() {
        ImmutableMap<String, String> schemaVars = ImmutableMap.of("keyspace", keyspace);

        String updatedCQL = null;
        try (InputStream isChecker = CassCluster.class.getResourceAsStream("/org/hawkular/alerts/schema/checker.cql");
             InputStreamReader readerChecker = new InputStreamReader(isChecker);) {
            String content = CharStreams.toString(readerChecker);
            for (String cql : content.split("(?m)^-- #.*$")) {
                if (!cql.startsWith("--")) {
                    updatedCQL = substituteVars(cql.trim(), schemaVars);
                    if (log.isDebugEnabled()) {
                        log.debugf("Checking CQL:\n %s \n",updatedCQL);
                    }
                    ResultSet rs = session.execute(updatedCQL);
                    if (rs.isExhausted()) {
                        log.warnf("Table not created.\nEXECUTING CQL: \n%s", updatedCQL);
                        return false;
                    }
                }
            }
            return true;
        } catch (Exception e) {
            log.errorf("Failed schema check: %s\nEXECUTING CQL:\n%s", e, updatedCQL);
            return false;
        }
    }

    private String substituteVars(String cql, Map<String, String> vars) {
        try (TokenReplacingReader reader = new TokenReplacingReader(cql, vars);
             StringWriter writer = new StringWriter()) {
            char[] buffer = new char[32768];
            int cnt;
            while ((cnt = reader.read(buffer)) != -1) {
                writer.write(buffer, 0, cnt);
            }
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to perform variable substition on CQL", e);
        }
    }

    private URI getCassalogScript() {
        try {
            return getClass().getResource("/org/hawkular/alerts/schema/cassalog.groovy").toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to load schema change script", e);
        }
    }

    private String getNewHawkularAlertingVersion() {
        try {
            Enumeration<URL> resources = getClass().getClassLoader().getResources("META-INF/MANIFEST.MF");
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                Manifest manifest = new Manifest(resource.openStream());
                String vendorId = manifest.getMainAttributes().getValue("Implementation-Vendor-Id");
                if (vendorId != null && vendorId.equals("org.hawkular.alerts")) {
                    return manifest.getMainAttributes().getValue("Implementation-Version");
                }
            }
            throw new RuntimeException("Unable to determine implementation version for Hawkular Alerting");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void createSchema(Session session, String keyspace, boolean resetDB) {

        CassalogBuilder builder = new CassalogBuilder();
        Cassalog cassalog = builder.withKeyspace(keyspace).withSession(session).build();
        Map<String, ?> vars  = ImmutableMap.of(
                "keyspace", keyspace,
                "reset", resetDB,
                "session", session,
                "logger", log
        );
        // List of versions of alerting
        URI script = getCassalogScript();
        cassalog.execute(script, vars);

        session.execute("INSERT INTO " + keyspace + ".sys_config (config_id, name, value) VALUES " +
                "('org.hawkular.alerts', 'version', '" + getNewHawkularAlertingVersion() + "')");
    }

    /*
        This timeout value should be adjusted to the worst case on a Cassandra scheme initialization.
        Normally it takes an order of < 1 minute a full schema generation.
        Taking into consideration that there are CI systems very slow we will increase this threshold before to throw
        an exception.
     */
    public Session getSession() {
        if (!isInitialized()) {
            int currentAttempts = attempts;
            while(!initialized && !Thread.currentThread().isInterrupted() && currentAttempts >= 0) {
                log.warnf("[%s] Session is not yet initialized. Retrying in [%s] ms...",
                        currentAttempts, timeout);
                currentAttempts--;
                try {
                    Thread.sleep(timeout);
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return session;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void shutdown() {
        log.info("Closing Cassandra cluster session");
        if (session != null && !session.isClosed()) {
            session.close();
        }
        if (!cluster.isClosed()) {
            cluster.close();
        }
    }
}
