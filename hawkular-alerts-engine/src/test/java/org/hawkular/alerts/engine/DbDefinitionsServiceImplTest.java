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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
import org.hawkular.alerts.api.model.trigger.Tag;
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
        assertTrue(t != null);

        Collection<Condition> cs = db.getTriggerConditions(t.getId(), null);
        assertTrue(cs.toString(), cs.size() == 1);
        Condition c = cs.iterator().next();

        Collection<Dampening> ds = db.getTriggerDampenings(t.getId(), null);
        assertTrue(cs.toString(), ds.size() == 1);
        Dampening d = ds.iterator().next();

        Map<String, String> dataIdMap = new HashMap<>(1);
        dataIdMap.put(c.getDataId(), "NewDataId");

        Trigger nt = db.copyTrigger(t.getId(), dataIdMap);
        assertNotNull(nt);
        assertTrue(nt.toString(), !nt.getId().equals(t.getId()));
        assertTrue(nt.toString(), nt.getName().equals(t.getName()));
        assertTrue(nt.toString(), nt.getDescription().equals(t.getDescription()));
        assertTrue(nt.toString(), nt.getFiringMatch().equals(t.getFiringMatch()));
        assertTrue(nt.toString(), nt.getSafetyMatch().equals(t.getSafetyMatch()));

        Collection<Condition> ncs = db.getTriggerConditions(nt.getId(), null);
        assertTrue(ncs.toString(), ncs.size() == 1);
        Condition nc = ncs.iterator().next();
        assertTrue(nc.toString(), nc.getClass().equals(c.getClass()));
        assertTrue(nc.toString(), nc.getTriggerId().equals(nt.getId()));
        assertTrue(nc.toString(), nc.getTriggerMode().equals(c.getTriggerMode()));
        assertTrue(nc.toString(), nc.getDataId().equals("NewDataId"));
        assertTrue(nc.toString(), nc.getConditionSetIndex() == c.getConditionSetIndex());
        assertTrue(nc.toString(), nc.getConditionSetSize() == c.getConditionSetSize());

        Collection<Dampening> nds = db.getTriggerDampenings(nt.getId(), null);
        assertTrue(nds.toString(), nds.size() == 1);
        Dampening nd = nds.iterator().next();
        assertTrue(nd.toString(), nd.getTriggerId().equals(nt.getId()));
        assertTrue(nd.toString(), nd.getTriggerMode().equals(d.getTriggerMode()));
        assertTrue(nd.toString(), nd.getEvalTrueSetting() == d.getEvalTrueSetting());
        assertTrue(nd.toString(), nd.getEvalTotalSetting() == d.getEvalTotalSetting());
        assertTrue(nd.toString(), nd.getEvalTimeSetting() == d.getEvalTimeSetting());
    }

    @Test
    public void tagTest() throws Exception {

        DbDefinitionsServiceImpl db = new DbDefinitionsServiceImpl(new TestAlertsService(), ds);
        db.init();

        Trigger t = db.getTrigger("trigger-1");
        assertNotNull(t);

        Collection<Condition> cs = db.getTriggerConditions(t.getId(), null);
        assertTrue(cs.toString(), cs.size() == 1);
        Condition c = cs.iterator().next();

        // check for the implicit tag
        List<Tag> tags = db.getTriggerTags("trigger-1", "dataId");
        assertTrue(tags.toString(), tags.size() == 1);
        Tag tag = tags.get(0);
        assertEquals("trigger-1", tag.getTriggerId());
        assertEquals("dataId", tag.getCategory());
        assertEquals(c.getDataId(), tag.getName());
        assertEquals(false, tag.isVisible());

        Tag newTag = new Tag("trigger-1", "testcategory", "testname", true);
        db.addTag(newTag);

        tags = db.getTriggerTags("trigger-1", null);
        assertTrue(tags.toString(), tags.size() == 2);
        tag = tags.get(1); // new one should be second by the implicit sort
        assertEquals("trigger-1", tag.getTriggerId());
        assertEquals("testcategory", tag.getCategory());
        assertEquals("testname", tag.getName());
        assertEquals(true, tag.isVisible());

        db.removeTags("trigger-1", "testcategory", "testname");
        tags = db.getTriggerTags("trigger-1", null);
        assertTrue(tags.toString(), tags.size() == 1);
        tag = tags.get(0);
        assertEquals("trigger-1", tag.getTriggerId());
        assertEquals("dataId", tag.getCategory());

        tags = db.getTriggerTags("dummy", null);
        assertTrue(tags.toString(), tags.size() == 0);
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
