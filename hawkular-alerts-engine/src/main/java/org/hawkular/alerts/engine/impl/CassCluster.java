/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.jar.Manifest;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.AccessTimeout;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.enterprise.inject.Produces;

import org.cassalog.core.Cassalog;
import org.cassalog.core.CassalogBuilder;
import org.hawkular.alerts.engine.util.TokenReplacingReader;
import org.infinispan.Cache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.jboss.logging.Logger;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.QueryOptions;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SocketOptions;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;

/**
 * Cassandra cluster representation and session factory.
 *
 * @author Lucas Ponce
 */
@Startup
@Singleton
public class CassCluster {
    private static final Logger log = Logger.getLogger(CassCluster.class);

    /*
        PORT used by the Cassandra cluster
     */
    private static final String ALERTS_CASSANDRA_PORT = "hawkular-alerts.cassandra-cql-port";
    private static final String ALERTS_CASSANDRA_PORT_ENV = "CASSANDRA_CQL_PORT";

    /*
        List of nodes defined on the Cassandra cluster
     */
    private static final String ALERTS_CASSANDRA_NODES = "hawkular-alerts.cassandra-nodes";
    private static final String ALERTS_CASSANDRA_NODES_ENV = "CASSANDRA_NODES";

    /*
        Hawkular Alerts keyspace name used on Cassandra cluster
     */
    private static final String ALERTS_CASSANDRA_KEYSPACE = "hawkular-alerts.cassandra-keyspace";

    /*
        Number of attempts when Hawkular Alerts cannot connect with Cassandra cluster to retry
     */
    private static final String ALERTS_CASSANDRA_RETRY_ATTEMPTS = "hawkular-alerts.cassandra-retry-attempts";

    /*
        ALERTS_CASSANDRA_RETRY_TIMEOUT defined in milliseconds
     */
    private static final String ALERTS_CASSANDRA_RETRY_TIMEOUT = "hawkular-alerts.cassandra-retry-timeout";

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

    private String keyspace;
    private int attempts;
    private int timeout;
    private String cqlPort;
    private String nodes;
    private int connTimeout;
    private int readTimeout;
    private boolean overwrite = false;

    private Cluster cluster = null;

    private Session session = null;

    private boolean initialized = false;

    private boolean distributed = false;

    /**
     * Access to the manager of the caches used for the partition services.
     */
    @Resource(lookup = "java:jboss/infinispan/container/hawkular-alerts")
    private EmbeddedCacheManager cacheManager;

    private static final String SCHEMA = "schema";

    /**
     * This cache will be used to coordinate schema creation across a cluster of nodes.
     */
    @Resource(lookup = "java:jboss/infinispan/cache/hawkular-alerts/schema")
    private Cache schemaCache;

    private void readProperties() {
        attempts = Integer.parseInt(AlertProperties.getProperty(ALERTS_CASSANDRA_RETRY_ATTEMPTS, "5"));
        timeout = Integer.parseInt(AlertProperties.getProperty(ALERTS_CASSANDRA_RETRY_TIMEOUT, "2000"));
        cqlPort = AlertProperties.getProperty(ALERTS_CASSANDRA_PORT, ALERTS_CASSANDRA_PORT_ENV, "9042");
        nodes = AlertProperties.getProperty(ALERTS_CASSANDRA_NODES, ALERTS_CASSANDRA_NODES_ENV, "127.0.0.1");
        connTimeout = Integer.parseInt(AlertProperties.getProperty(ALERTS_CASSANDRA_CONNECT_TIMEOUT,
                ALERTS_CASSANDRA_CONNECT_TIMEOUT_ENV, String.valueOf(SocketOptions.DEFAULT_CONNECT_TIMEOUT_MILLIS)));
        readTimeout = Integer.parseInt(AlertProperties.getProperty(ALERTS_CASSANDRA_READ_TIMEOUT,
                ALERTS_CASSANDRA_READ_TIMEOUT_ENV, String.valueOf(SocketOptions.DEFAULT_READ_TIMEOUT_MILLIS)));
        overwrite = Boolean.parseBoolean(AlertProperties.getProperty(ALERTS_CASSANDRA_OVERWRITE,
                ALERTS_CASSANDRA_OVERWRITE_ENV, "false"));
        keyspace = AlertProperties.getProperty(ALERTS_CASSANDRA_KEYSPACE, "hawkular_alerts");
    }

    @PostConstruct
    public void initCassCluster() {
        readProperties();

        if (cacheManager != null) {
            distributed = cacheManager.getTransport() != null;
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
                .withProtocolVersion(ProtocolVersion.V3)
                .withQueryOptions(new QueryOptions().setRefreshSchemaIntervalMillis(0));

        if (socketOptions != null) {
            clusterBuilder.withSocketOptions(socketOptions);
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

    private void initSchemeDistributed() throws IOException {
        schemaCache.getAdvancedCache().lock(SCHEMA);
        initScheme();
    }

    private void initScheme() throws IOException {

        log.infof("Checking Schema existence for keyspace: %s", keyspace);

        KeyspaceMetadata keyspaceMetadata = cluster.getMetadata().getKeyspace(keyspace);
        if (keyspaceMetadata != null) {
            // If overwrite flag is true it should not check if all tables are created
            if (!overwrite) {
                waitForSchemaCheck();
                if (!checkSchema()) {
                    log.errorf("Keyspace %s detected, but failed on check phase.", keyspace);
                    initialized = false;
                } else {
                    log.infof("Schema already exist. Skipping schema creation.");
                    initialized = true;
                }
            }
        } else {
            log.infof("Creating Schema for keyspace %s", keyspace);
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
                "session", session
        );
        // List of versions of alerting
        URI script = getCassalogScript();
        cassalog.execute(script, vars);

        session.execute("INSERT INTO " + keyspace + ".sys_config (config_id, name, value) VALUES " +
                "('org.hawkular.alerts', 'version', '" + getNewHawkularAlertingVersion() + "')");
    }

    @Produces
    @CassClusterSession
    /*
        This timeout value should be adjusted to the worst case on a Cassandra scheme initialization.
        Normally it takes an order of < 1 minute a full schema generation.
        Taking into consideration that there are CI systems very slow we will increase this threshold before to throw
        an exception.
     */
    @AccessTimeout(value = 300, unit = TimeUnit.SECONDS)
    public Session getSession() {
        return session;
    }

    @PreDestroy
    public void shutdown() {
        log.info("Closing Cassandra cluster session");
        if (session != null && !session.isClosed()) {
            session.close();
        }
    }
}
