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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.hawkular.alerts.api.model.condition.Alert;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.services.AlertsCriteria;
import org.hawkular.alerts.api.services.AlertsService;
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

        DbDefinitionsServiceImpl db = new DbDefinitionsServiceImpl(new TestAlertsService(), ds);
        db.init();

        assertTrue(db.getAllTriggers().size() > 0);
        assertTrue(db.getAllConditions().size() > 0);
        assertTrue(db.getAllDampenings().size() > 0);
        assertTrue(db.getAllActions().size() > 0);
    }

    @Test
    public void copyTriggerTest() throws Exception {

        DbDefinitionsServiceImpl db = new DbDefinitionsServiceImpl(new TestAlertsService(), ds);
        db.init();

        Trigger t = db.getTrigger("trigger-1");
        assert t != null;

        Collection<Condition> cs = db.getTriggerConditions(t.getId(), null);
        assert cs.size() == 1 : cs;
        Condition c = cs.iterator().next();

        Collection<Dampening> ds = db.getTriggerDampenings(t.getId(), null);
        assert ds.size() == 1 : cs;
        Dampening d = ds.iterator().next();

        Map<String, String> dataIdMap = new HashMap<>(1);
        dataIdMap.put(c.getDataId(), "NewDataId");

        Trigger nt = db.copyTrigger(t.getId(), dataIdMap);
        assert nt != null;
        assert !nt.getId().equals(t.getId()) : nt;
        assert nt.getName().equals(t.getName()) : nt;
        assert nt.getDescription().equals(t.getDescription()) : nt;
        assert nt.getFiringMatch().equals(t.getFiringMatch()) : nt;
        assert nt.getSafetyMatch().equals(t.getSafetyMatch()) : nt;

        Collection<Condition> ncs = db.getTriggerConditions(nt.getId(), null);
        assert ncs.size() == 1 : ncs;
        Condition nc = ncs.iterator().next();
        assert nc.getClass().equals(c.getClass()) : nc;
        assert nc.getTriggerId().equals(nt.getId()) : nc;
        assert nc.getTriggerMode().equals(c.getTriggerMode()) : nc;
        assert nc.getDataId().equals("NewDataId") : nc;
        assert nc.getConditionSetIndex() == c.getConditionSetIndex() : nc;
        assert nc.getConditionSetSize() == c.getConditionSetSize() : nc;

        Collection<Dampening> nds = db.getTriggerDampenings(nt.getId(), null);
        assert nds.size() == 1 : nds;
        Dampening nd = nds.iterator().next();
        assert nd.getTriggerId().equals(nt.getId()) : nd;
        assert nd.getTriggerMode().equals(d.getTriggerMode()) : nd;
        assert nd.getEvalTrueSetting() == d.getEvalTrueSetting() : nd;
        assert nd.getEvalTotalSetting() == d.getEvalTotalSetting() : nd;
        assert nd.getEvalTimeSetting() == d.getEvalTimeSetting() : nd;
    }

    private static class TestAlertsService implements AlertsService {

        @Override
        public void sendData(Data data) {
            // TODO Auto-generated method stub

        }

        @Override
        public void sendData(Collection<Data> data) {
            // TODO Auto-generated method stub

        }

        @Override
        public List<Alert> getAlerts(AlertsCriteria criteria) {
            return Collections.EMPTY_LIST;
        }

        @Override
        public void clear() {
            // TODO Auto-generated method stub

        }

        @Override
        public void reload() {
            // TODO Auto-generated method stub

        }

        @Override
        public void reloadTrigger(String triggerId) {
            // TODO Auto-generated method stub

        }

        @Override
        public void addAlerts(Collection<Alert> alerts) throws Exception {
            // TODO Auto-generated method stub

        }

    }
}
