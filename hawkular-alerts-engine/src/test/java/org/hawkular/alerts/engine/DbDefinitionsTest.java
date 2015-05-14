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

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.hawkular.alerts.engine.impl.DbAlertsServiceImpl;
import org.hawkular.alerts.engine.impl.DbDefinitionsServiceImpl;
import org.hawkular.alerts.engine.impl.DroolsRulesEngineImpl;
import org.hawkular.alerts.engine.impl.MemActionsServiceImpl;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;

/**
 * Basic test for DbDefinitionsServiceImpl
 *
 * @author Lucas Ponce
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class DbDefinitionsTest extends DefinitionsTest {

    static MemActionsServiceImpl actions = null;
    static DroolsRulesEngineImpl rules = null;
    static DbDefinitionsServiceImpl dbDefinitions;
    static DbAlertsServiceImpl dbAlerts;
    static DataSource ds;

    @BeforeClass
    public static void initSessionAndResetTestSchema() throws Exception {
        String testFolder = CassDefinitionsTest.class.getResource("/").getPath();
        System.setProperty("jboss.server.data.dir", testFolder);

        JdbcDataSource jdbcDataSource = new JdbcDataSource();
        jdbcDataSource.setURL("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
        jdbcDataSource.setUser("sa");
        jdbcDataSource.setPassword("sa");
        ds = jdbcDataSource;

        actions = new MemActionsServiceImpl();
        rules = new DroolsRulesEngineImpl();
        dbDefinitions = new DbDefinitionsServiceImpl();
        dbAlerts = new DbAlertsServiceImpl();

        dbDefinitions.setDatasource(ds);
        dbDefinitions.setAlertsService(dbAlerts);
        dbAlerts.setDatasource(ds);
        dbAlerts.setDefinitions(dbDefinitions);
        dbAlerts.setActions(actions);
        dbAlerts.setRules(rules);

        dbDefinitions.init();
        dbAlerts.initServices();

        definitionsService = dbDefinitions;
        alertsService = dbAlerts;
    }


}
