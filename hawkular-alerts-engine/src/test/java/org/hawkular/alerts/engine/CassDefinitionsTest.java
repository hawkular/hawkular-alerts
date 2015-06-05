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
package org.hawkular.alerts.engine;

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
public class CassDefinitionsTest extends DefinitionsTest {

    private static final String JBOSS_DATA_DIR = "jboss.server.data.dir";
    private static final String EXTERNAL_CASSANDRA = "external_cassandra";

    static Session session;
    static String keyspace;
    static String externalCass;

    @BeforeClass
    public static void initSessionAndResetTestSchema() throws Exception {

        String testFolder = CassDefinitionsTest.class.getResource("/").getPath();
        System.setProperty(JBOSS_DATA_DIR, testFolder);

        externalCass = System.getProperty(EXTERNAL_CASSANDRA);

        if (externalCass == null) {
            System.out.print("Starting embedded Cassandra for unit testing...");
            EmbeddedCassandra.start();

        }

        session = CassCluster.getSession();
        keyspace = AlertProperties.getProperty("hawkular-alerts.cassandra-keyspace", "hawkular_alerts_test");

        definitionsService = StandaloneAlerts.getDefinitionsService();
        alertsService = StandaloneAlerts.getAlertsService();
    }

    @AfterClass
    public static void cleanTestSchema() throws Exception {
        session.execute("DROP KEYSPACE " + keyspace);

        CassCluster.shutdown();

        if (externalCass == null) {
            System.out.print("Stopping embedded Cassandra for unit testing...");
            EmbeddedCassandra.stop();
        }
    }

    @Before
    public void cleanAlerts() throws Exception {
        /*
            We don't have a "purge" public method for Alerts as this info should theoretically remain in the database.
            But we are going to clean alerts data for a clean scenario between tests.
         */
        session.execute("TRUNCATE " + keyspace + ".alerts");
        session.execute("TRUNCATE " + keyspace + ".alerts_triggers");
        session.execute("TRUNCATE " + keyspace + ".alerts_ctimes");
        session.execute("TRUNCATE " + keyspace + ".alerts_statuses");
        session.execute("TRUNCATE " + keyspace + ".alerts_severities");
    }


}
