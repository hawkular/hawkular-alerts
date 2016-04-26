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
package org.hawkular.alerts.engine;

import static org.hawkular.commons.cassandra.EmbeddedConstants.CASSANDRA_YAML;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;

import org.apache.cassandra.service.EmbeddedCassandraService;
import org.hawkular.alerts.api.services.ActionsCriteria;
import org.hawkular.alerts.api.services.AlertsCriteria;
import org.hawkular.alerts.api.services.EventsCriteria;
import org.hawkular.alerts.engine.impl.CassCluster;
import org.hawkular.commons.cassandra.CassandraYaml;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.Session;

/**
 * Basic tests for CassDefinitionsServiceImpl
 *
 * @author Lucas Ponce
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CassPersistenceTest extends PersistenceTest {

    private static final Logger logger = LoggerFactory.getLogger(CassPersistenceTest.class);

    static EmbeddedCassandraService embeddedCassandra;
    public static final String keyspace = "hawkular_alerts_test";

    @BeforeClass
    public static void initSessionAndResetTestSchema() throws Exception {

        File baseDir = Files.createTempDirectory(CassPersistenceTest.class.getName()).toFile();
        File cassandraYaml = new File(baseDir, "cassandra.yaml");

        URL defaultCassandraYamlUrl = CassandraYaml.class.getResource("/" + CASSANDRA_YAML);
        CassandraYaml.builder()
                .load(defaultCassandraYamlUrl)//
                .baseDir(baseDir)//
                .clusterName("hawkular-alerts")//
                .store(cassandraYaml)//
                .mkdirs()//
                .setCassandraConfigProp()//
                .setTriggersDirProp();

        embeddedCassandra = new EmbeddedCassandraService();
        embeddedCassandra.start();

        System.setProperty("hawkular-alerts.cassandra-keyspace", keyspace);

        CassCluster.getSession(true);

        definitionsService = StandaloneAlerts.getDefinitionsService();
        alertsService = StandaloneAlerts.getAlertsService();
        actionsService = StandaloneAlerts.getActionsService();
    }

    @AfterClass
    public static void cleanTestSchema() throws Exception {

        // try an clean up
        try {
            Session session = CassCluster.getSession();
            session.execute("DROP KEYSPACE " + keyspace);
        } catch (Throwable t) {
            // never mind, don't prevent further cleanup
        }
    }

    @Before
    public void cleanAlerts() throws Exception {
        AlertsCriteria criteria = new AlertsCriteria();
        logger.info("Deleted " + alertsService.deleteAlerts(TENANT, criteria) + " Alerts before test.\n");
    }

    @Before
    public void cleanEvents() throws Exception {
        EventsCriteria criteria = new EventsCriteria();
        logger.info("Deleted " + alertsService.deleteEvents(TENANT, criteria) + " Events before test.\n");
    }

    @Before
    public void cleanActions() throws Exception {
        ActionsCriteria criteria = new ActionsCriteria();
        logger.info("Deleted " + actionsService.deleteActions(TENANT, criteria) + " Actions before test.\n");
    }

}
