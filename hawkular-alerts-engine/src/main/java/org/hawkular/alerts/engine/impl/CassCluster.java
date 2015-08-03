/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
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

import org.hawkular.alerts.engine.util.TokenReplacingReader;
import org.jboss.logging.Logger;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;

/**
 * Cassandra cluster representation and session factory.
 *
 * @author Lucas Ponce
 */
public class CassCluster {
    private static final Logger log = Logger.getLogger(CassDefinitionsServiceImpl.class);
    private static final String ALERTS_CASSANDRA_PORT = "hawkular-alerts.cassandra-cql-port";
    private static final String ALERTS_CASSANDRA_PORT_ENV = "CASSANDRA_CQL_PORT";
    private static final String ALERTS_CASSANDRA_NODES = "hawkular-alerts.cassandra-nodes";
    private static final String ALERTS_CASSANDRA_NODES_ENV = "CASSANDRA_NODES";
    private static final String ALERTS_CASSANDRA_KEYSPACE = "hawkular-alerts.cassandra-keyspace";
    private static final String ALERTS_CASSANDRA_RETRY_ATTEMPTS = "hawkular-alerts.cassandra-retry-attempts";
    private static final String ALERTS_CASSANDRA_RETRY_TIMEOUT = "hawkular-alerts.cassandra-retry-timeout";

    private static Cluster cluster = null;

    private static Session session = null;

    private static boolean initialized = false;

    private static CassCluster instance = new CassCluster();

    private CassCluster() { }

    private void initScheme(Session session, String keyspace) throws IOException {

        if (keyspace == null) {
            keyspace = AlertProperties.getProperty(ALERTS_CASSANDRA_KEYSPACE, "hawkular_alerts");
        }

        log.debugf("Creating Schema for keyspace " + keyspace);

        ResultSet resultSet = session.execute("SELECT * FROM system.schema_keyspaces WHERE keyspace_name = '" +
                keyspace + "'");
        if (!resultSet.isExhausted()) {
            log.debugf("Schema already exist. Skipping schema creation.");
            return;
        }

        ImmutableMap<String, String> schemaVars = ImmutableMap.of("keyspace", keyspace);

        try (InputStream inputStream = CassCluster.class.getResourceAsStream("/hawkular-alerts-schema.cql");
             InputStreamReader reader = new InputStreamReader(inputStream)) {
            String content = CharStreams.toString(reader);

            for (String cql : content.split("(?m)^-- #.*$")) {
                if (!cql.startsWith("--")) {
                    String updatedCQL = substituteVars(cql.trim(), schemaVars);
                    log.debugf("Executing CQL:\n" + updatedCQL + "\n");
                    session.execute(updatedCQL);
                }
            }
        }
        initialized = true;
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

    public static Session getSession() throws Exception {
        if (cluster == null && session == null) {
            String cqlPort = AlertProperties.getProperty(ALERTS_CASSANDRA_PORT, ALERTS_CASSANDRA_PORT_ENV, "9042");
            String nodes = AlertProperties.getProperty(ALERTS_CASSANDRA_NODES, ALERTS_CASSANDRA_NODES_ENV, "127.0.0.1");
            int attempts = Integer.parseInt(AlertProperties.getProperty(ALERTS_CASSANDRA_RETRY_ATTEMPTS, "5"));
            int timeout = Integer.parseInt(AlertProperties.getProperty(ALERTS_CASSANDRA_RETRY_TIMEOUT, "2000"));
            /*
                It might happen that alerts component is faster than embedded cassandra deployed in hawkular.
                We will provide a simple attempt/retry loop to avoid issues at initialization.
             */
            while(session == null && !Thread.currentThread().isInterrupted() && attempts >= 0) {
                try {
                    cluster = new Cluster.Builder()
                            .addContactPoints(nodes.split(","))
                            .withPort(new Integer(cqlPort))
                            .withProtocolVersion(ProtocolVersion.V3)
                            .build();
                    session = cluster.connect();
                } catch (Exception e) {
                    log.warn("Could not connect to Cassandra cluster - assuming is not up yet. Cause: " +
                            ((e.getCause() == null) ? e : e.getCause()));
                    if (attempts == 0) {
                        throw e;
                    }
                }
                if (session == null) {
                    log.warn("[" + attempts + "] Retrying connecting to Cassandra cluster in [" + timeout + "]ms...");
                    attempts--;
                    try {
                        Thread.sleep(timeout);
                    } catch(InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            if (session != null && !initialized) {
                String keyspace = AlertProperties.getProperty(ALERTS_CASSANDRA_KEYSPACE, "hawkular_alerts");
                instance.initScheme(session, keyspace);
            }
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        return session;
    }

    public static void shutdown() {
        if (session != null && !session.isClosed()) {
            session.close();
        }
    }
}
