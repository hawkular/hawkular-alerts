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

import org.hawkular.alerts.api.services.ActionsCriteria;
import org.hawkular.alerts.api.services.AlertsCriteria;
import org.hawkular.alerts.api.services.EventsCriteria;
import org.hawkular.alerts.engine.cassandra.EmbeddedCassandra;
import org.hawkular.alerts.engine.impl.AlertProperties;
import org.hawkular.alerts.engine.impl.CassCluster;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;

import com.datastax.driver.core.Session;

/**
 * Basic tests for CassDefinitionsServiceImpl
 *
 * @author Lucas Ponce
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CassPersistenceTest extends PersistenceTest {

    private static final String JBOSS_DATA_DIR = "jboss.server.data.dir";
    private static final String EXTERNAL_CASSANDRA = "external_cassandra";

    static boolean externalCassandra;
    static String keyspace;

    @BeforeClass
    public static void initSessionAndResetTestSchema() throws Exception {

        String testFolder = CassPersistenceTest.class.getResource("/").getPath();
        System.setProperty(JBOSS_DATA_DIR, testFolder);

        externalCassandra = (null != System.getProperty(EXTERNAL_CASSANDRA));

        if (!externalCassandra) {
            System.out.print("Starting embedded Cassandra for unit testing...");
            EmbeddedCassandra.start();
        } else {
            System.out.print("Using External Cassandra for unit testing...");
        }

        keyspace = AlertProperties.getProperty("hawkular-alerts.cassandra-keyspace", "hawkular_alerts_test");

        Session session = CassCluster.getSession();

        // try and clean up if this was somehow left around by a prior run
        try {
            session.execute("DROP KEYSPACE " + keyspace);
        } catch (Throwable t) {
            // never mind, it may not be there
        }

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
            if (!externalCassandra) {
                System.out.print("Stopping embedded Cassandra for unit testing...");
                CassCluster.shutdown();
                EmbeddedCassandra.stop();
            }
        } catch (Throwable t) {
            // never mind, don't prevent further cleanup
        }
    }

    @Before
    public void cleanAlerts() throws Exception {
        AlertsCriteria criteria = new AlertsCriteria();
        System.out.printf("Deleted [%s] Alerts before test.\n", alertsService.deleteAlerts(TENANT, criteria));
    }

    @Before
    public void cleanEvents() throws Exception {
        EventsCriteria criteria = new EventsCriteria();
        System.out.printf("Deleted [%s] Events before test.\n", alertsService.deleteEvents(TENANT, criteria));
    }

    @Before
    public void cleanActions() throws Exception {
        ActionsCriteria criteria = new ActionsCriteria();
        System.out.printf("Deleted [%s] Actions before test.\n", actionsService.deleteActions(TENANT, criteria));
    }

}
