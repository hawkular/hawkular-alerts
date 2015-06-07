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
package org.hawkular.alerts.engine.cassandra;

import org.apache.cassandra.service.CassandraDaemon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * App initialization.
 * Embedded Cassandra prepared for unit testing
 *
 * @author Stefan Negrea
 * @author Lucas Ponce
 */
public class EmbeddedCassandra {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddedCassandra.class);

    private static CassandraDaemon cassandraDaemon;

    public EmbeddedCassandra() {
        logger.info("======== Hawkular Alerts - Embedded Cassandra ========");
    }

    public static synchronized void start() {
            if (cassandraDaemon == null) {
                try {
                    ConfigEditor editor = new ConfigEditor();
                    editor.initEmbeddedConfiguration();

                    cassandraDaemon = new CassandraDaemon();
                    cassandraDaemon.activate();
                } catch (Exception e) {
                    logger.error("Error initializing embbeded Cassandra server", e);
                }
            }
    }

    public static synchronized void stop() {
            if (cassandraDaemon != null) {
                cassandraDaemon.deactivate();
            }
    }
}
