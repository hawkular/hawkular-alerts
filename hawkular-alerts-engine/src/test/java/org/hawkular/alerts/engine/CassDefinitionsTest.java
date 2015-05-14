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

import org.hawkular.alerts.engine.impl.AlertProperties;
import org.hawkular.alerts.engine.impl.CassCluster;
import org.junit.AfterClass;
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

    private final String TEST_TENANT = "jdoe";

    static Session session;
    static String keyspace;

    @BeforeClass
    public static void initSessionAndResetTestSchema() throws Exception {

        String testFolder = CassDefinitionsTest.class.getResource("/").getPath();
        System.setProperty("jboss.server.data.dir", testFolder);

        session = CassCluster.getSession();
        keyspace = AlertProperties.getProperty("hawkular-alerts.cassandra-keyspace", "hawkular_alerts_test");

        definitionsService = StandaloneAlerts.getDefinitionsService();
        alertsService = StandaloneAlerts.getAlertsService();
    }

    @AfterClass
    public static void cleanTestSchema() throws Exception {
        session.execute("DROP KEYSPACE " + keyspace);
    }

}
