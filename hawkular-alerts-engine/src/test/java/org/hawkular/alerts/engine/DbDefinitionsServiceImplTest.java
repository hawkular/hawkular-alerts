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

import static org.junit.Assert.assertTrue;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.hawkular.alerts.engine.impl.DbDefinitionsServiceImpl;
import org.junit.Before;
import org.junit.Test;

/**
 * Basic test for DbDefinitionsServiceImpl
 *
 * @author Lucas Ponce
 */
public class DbDefinitionsServiceImplTest {

    DataSource ds;

    @Before
    public void setup() throws Exception {
        JdbcDataSource jdbcDataSource = new JdbcDataSource();
        jdbcDataSource.setURL("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
        jdbcDataSource.setUser("sa");
        jdbcDataSource.setPassword("sa");
        ds = jdbcDataSource;

        String testFolder = getClass().getResource("/").getPath();
        System.setProperty("jboss.server.data.dir", testFolder);
    }

    @Test
    public void checkInitTest() throws Exception {

        DbDefinitionsServiceImpl db = new DbDefinitionsServiceImpl(ds);
        db.init();

        assertTrue(db.getAllTriggers().size() > 0);
        assertTrue(db.getAllConditions().size() > 0);
        assertTrue(db.getAllDampenings().size() > 0);
        assertTrue(db.getAllNotifiers().size() > 0);
    }

}
