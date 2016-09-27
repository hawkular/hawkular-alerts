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
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.AccessTimeout;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.enterprise.inject.Produces;

import org.hawkular.alerts.engine.util.TokenReplacingReader;
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
    private static final Logger log = Logger.getLogger(CassDefinitionsServiceImpl.class);
    private static final String ALERTS_CASSANDRA_PORT = "hawkular-alerts.cassandra-cql-port";
    private static final String ALERTS_CASSANDRA_PORT_ENV = "CASSANDRA_CQL_PORT";
    private static final String ALERTS_CASSANDRA_NODES = "hawkular-alerts.cassandra-nodes";
    private static final String ALERTS_CASSANDRA_NODES_ENV = "CASSANDRA_NODES";
    private static final String ALERTS_CASSANDRA_KEYSPACE = "hawkular-alerts.cassandra-keyspace";
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
    }

    @PostConstruct
    public void initCassCluster() {
        readProperties();
        if (cluster == null && session == null) {

            int currentAttempts = attempts;
            /*
                It might happen that alerts component is faster than embedded cassandra deployed in hawkular.
                We will provide a simple attempt/retry loop to avoid issues at initialization.
             */
            while(session == null && !Thread.currentThread().isInterrupted() && currentAttempts >= 0) {
                try {
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
            if (session != null && !initialized) {
                String keyspace = AlertProperties.getProperty(ALERTS_CASSANDRA_KEYSPACE, "hawkular_alerts");
                try {
                    initScheme(session, keyspace, overwrite);
                } catch (IOException e) {
                    log.error("Error on initialization of Alerts scheme", e);
                }
            }
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (session != null && !initialized) {
            throw new RuntimeException("Cassandra alerts keyspace is not initialized");
        }
    }

    private void initScheme(Session session, String keyspace, boolean overwrite) throws IOException {

        if (log.isDebugEnabled()) {
            log.debug("Checking Schema existence for keyspace: " + keyspace);
        }

        KeyspaceMetadata keyspaceMetadata = cluster.getMetadata().getKeyspace(keyspace);
        if (keyspaceMetadata != null) {
            if (overwrite) {
                session.execute("DROP KEYSPACE " + keyspace);
            } else {
                int currentAttempts = attempts;
                while(!checkSchema(keyspace) && !Thread.currentThread().isInterrupted() && currentAttempts >= 0) {
                    log.warn("[" + currentAttempts + "] Keyspace detected but schema not fully created. " +
                            "Retrying in [" + timeout + "]ms...");
                    currentAttempts--;
                    try {
                        Thread.sleep(timeout);
                    } catch(InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                if (!checkSchema(keyspace)) {
                    log.errorf("Keyspace detected, but failed on check phase.", keyspace);
                    return;
                }
                log.debug("Schema already exist. Skipping schema creation.");
                initialized = true;
                return;
            }
        }

        log.infof("Creating Schema for keyspace %s", keyspace);

        ImmutableMap<String, String> schemaVars = ImmutableMap.of("keyspace", keyspace);

        String updatedCQL = null;
        try (InputStream isSchema = CassCluster.class.getResourceAsStream("/hawkular-alerts-schema.cql");
             InputStreamReader readerSchema = new InputStreamReader(isSchema);) {
            String content = CharStreams.toString(readerSchema);
            for (String cql : content.split("(?m)^-- #.*$")) {
                if (!cql.startsWith("--")) {
                    updatedCQL = substituteVars(cql.trim(), schemaVars);
                    if (log.isDebugEnabled()) {
                        log.debug("Executing CQL:\n" + updatedCQL + "\n");
                    }
                    session.execute(updatedCQL);
                }
            }
        } catch (Exception e) {
            log.errorf("Failed schema creation: %s\nEXECUTING CQL:\n%s", e, updatedCQL);
        }
        initialized = true;

        log.infof("Done creating Schema for keyspace: " + keyspace);
    }

    private boolean checkSchema(String keyspace) {
        ImmutableMap<String, String> schemaVars = ImmutableMap.of("keyspace", keyspace);

        String updatedCQL = null;
        try (InputStream isChecker = CassCluster.class.getResourceAsStream("/hawkular-alerts-checker.cql");
             InputStreamReader readerChecker = new InputStreamReader(isChecker);) {
            String content = CharStreams.toString(readerChecker);
            for (String cql : content.split("(?m)^-- #.*$")) {
                if (!cql.startsWith("--")) {
                    updatedCQL = substituteVars(cql.trim(), schemaVars);
                    if (log.isDebugEnabled()) {
                        log.debug("Checking CQL:\n" + updatedCQL + "\n");
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
