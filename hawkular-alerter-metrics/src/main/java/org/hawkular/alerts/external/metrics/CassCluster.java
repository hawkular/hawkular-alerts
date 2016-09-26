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
package org.hawkular.alerts.external.metrics;

import org.jboss.logging.Logger;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.Session;

/**
 * Cassandra cluster representation and session factory.
 *
 * @author Lucas Ponce
 */
public class CassCluster {
    private static final Logger log = Logger.getLogger(CassCluster.class);
    private static final String ALERTS_CASSANDRA_PORT = "hawkular-alerts-metrics.cassandra-cql-port";
    private static final String ALERTS_CASSANDRA_NODES = "hawkular-alerts-metrics.cassandra-nodes";
    private static final String ALERTS_CASSANDRA_RETRY_ATTEMPTS = "hawkular-alerts-metrics.cassandra-retry-attempts";
    private static final String ALERTS_CASSANDRA_RETRY_TIMEOUT = "hawkular-alerts-metrics.cassandra-retry-timeout";

    private static Cluster cluster = null;

    private static Session session = null;

    private CassCluster() { }

    public static Session getSession() throws Exception {
        if (cluster == null && session == null) {
            String cqlPort = System.getProperty(ALERTS_CASSANDRA_PORT, "9042");
            String nodes = System.getProperty(ALERTS_CASSANDRA_NODES, "127.0.0.1");
            int attempts = Integer.parseInt(System.getProperty(ALERTS_CASSANDRA_RETRY_ATTEMPTS, "5"));
            int timeout = Integer.parseInt(System.getProperty(ALERTS_CASSANDRA_RETRY_TIMEOUT, "2000"));
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
        }
        return session;
    }

    public static void shutdown() {
        if (session != null && !session.isClosed()) {
            session.close();
        }
    }
}
