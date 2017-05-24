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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.cassandra.service.EmbeddedCassandraService;
import org.hawkular.alerts.api.model.export.Definitions;
import org.hawkular.alerts.api.model.export.ImportType;
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
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Basic tests for CassDefinitionsServiceImpl
 *
 * @author Lucas Ponce
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CassPersistenceTest extends PersistenceTest {

    private static final Logger logger = LoggerFactory.getLogger(CassPersistenceTest.class);
    private static ObjectMapper objectMapper;

    static EmbeddedCassandraService embeddedCassandra;
    public static final String keyspace = "hawkular_alerts_test";
    static CassCluster cluster;

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
        System.setProperty("hawkular-alerts.cassandra-overwrite", "true");

        definitionsService = StandaloneAlerts.getDefinitionsService();
        alertsService = StandaloneAlerts.getAlertsService();
        actionsService = StandaloneAlerts.getActionsService();

        mockPluginsDeployments();

        objectMapper = new ObjectMapper();
        Definitions definitions = objectMapper.readValue(
                CassPersistenceTest.class.getResourceAsStream("/hawkular-alerts/alerts-data.json"), Definitions.class);
        definitionsService.importDefinitions(TENANT, definitions, ImportType.DELETE);
    }

    private static void mockPluginsDeployments() throws Exception {
        Set<String> propsSnmp = new HashSet<>(Arrays.asList("host", "port", "oid", "description"));
        definitionsService.addActionPlugin("snmp", propsSnmp);

        Set<String> propsEmail = new HashSet<>(Arrays.asList("to", "cc", "description"));
        definitionsService.addActionPlugin("email", propsEmail);

        Set<String> propsSms = new HashSet<>(Arrays.asList("phone", "description"));
        definitionsService.addActionPlugin("sms", propsSms);

        Set<String> propsAerogear = new HashSet<>(Arrays.asList("alias", "description"));
        definitionsService.addActionPlugin("aerogear", propsAerogear);

        Set<String> propsFile = new HashSet<>(Arrays.asList("description"));
        definitionsService.addActionPlugin("file", propsFile);
    }

    @AfterClass
    public static void cleanTestSchema() throws Exception {

        // try an clean up
        try {
            Session session = cluster.getSession();
            session.execute("DROP KEYSPACE " + keyspace);
            session.close();
            cluster.shutdown();
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
