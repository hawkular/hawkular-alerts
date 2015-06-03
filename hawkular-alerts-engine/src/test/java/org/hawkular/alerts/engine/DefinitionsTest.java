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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.api.model.condition.Alert;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.condition.ThresholdCondition;
import org.hawkular.alerts.api.model.condition.ThresholdConditionEval;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.data.NumericData;
import org.hawkular.alerts.api.model.trigger.Tag;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.services.AlertsCriteria;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.junit.Test;

/**
 *
 * @author Lucas Ponce
 */
public abstract class DefinitionsTest {

    /*
        TenantId = 28026b36-8fe4-4332-84c8-524e173a68bf
        User = jdoe
     */
    private final String TEST_TENANT = "28026b36-8fe4-4332-84c8-524e173a68bf";
    static DefinitionsService definitionsService;
    static AlertsService alertsService;

    @Test
    public void test000InitScheme() throws Exception {
        assertTrue(definitionsService.getAllTriggers().size() > 0);
        assertTrue(definitionsService.getAllConditions().size() > 0);
        assertTrue(definitionsService.getAllDampenings().size() > 0);
        assertTrue(definitionsService.getAllActions().size() > 0);
    }

    @Test
    public void test001CopyTrigger() throws Exception {
        Trigger t = definitionsService.getTrigger(TEST_TENANT, "trigger-1");
        assertTrue(t != null);

        Collection<Condition> cs = definitionsService.getTriggerConditions(TEST_TENANT, t.getId(), null);
        assertTrue(cs.toString(), cs.size() == 1);
        Condition c = cs.iterator().next();

        Collection<Dampening> ds = definitionsService.getTriggerDampenings(TEST_TENANT, t.getId(), null);
        assertTrue(cs.toString(), ds.size() == 1);
        Dampening d = ds.iterator().next();

        Map<String, String> dataIdMap = new HashMap<>(1);
        dataIdMap.put(c.getDataId(), "NewDataId");

        Trigger nt = definitionsService.copyTrigger(TEST_TENANT, t.getId(), dataIdMap);
        assertNotNull(nt);
        assertTrue(nt.toString(), !nt.getId().equals(t.getId()));
        assertTrue(nt.toString(), nt.getName().equals(t.getName()));
        assertTrue(nt.toString(), nt.getDescription().equals(t.getDescription()));
        assertTrue(nt.toString(), nt.getFiringMatch().equals(t.getFiringMatch()));
        assertTrue(nt.toString(), nt.getAutoResolveMatch().equals(t.getAutoResolveMatch()));

        Collection<Condition> ncs = definitionsService.getTriggerConditions(TEST_TENANT, nt.getId(), null);
        assertTrue(ncs.toString(), ncs.size() == 1);
        Condition nc = ncs.iterator().next();
        assertTrue(nc.toString(), nc.getClass().equals(c.getClass()));
        assertTrue(nc.toString(), nc.getTriggerId().equals(nt.getId()));
        assertTrue(nc.toString(), nc.getTriggerMode().equals(c.getTriggerMode()));
        assertTrue(nc.toString(), nc.getDataId().equals("NewDataId"));
        assertTrue(nc.toString(), nc.getConditionSetIndex() == c.getConditionSetIndex());
        assertTrue(nc.toString(), nc.getConditionSetSize() == c.getConditionSetSize());

        Collection<Dampening> nds = definitionsService.getTriggerDampenings(TEST_TENANT, nt.getId(), null);
        assertTrue(nds.toString(), nds.size() == 1);
        Dampening nd = nds.iterator().next();
        assertTrue(nd.toString(), nd.getTriggerId().equals(nt.getId()));
        assertTrue(nd.toString(), nd.getTriggerMode().equals(d.getTriggerMode()));
        assertTrue(nd.toString(), nd.getEvalTrueSetting() == d.getEvalTrueSetting());
        assertTrue(nd.toString(), nd.getEvalTotalSetting() == d.getEvalTotalSetting());
        assertTrue(nd.toString(), nd.getEvalTimeSetting() == d.getEvalTimeSetting());
    }

    @Test
    public void test002BasicTags() throws Exception {
        Trigger t = definitionsService.getTrigger(TEST_TENANT, "trigger-1");
        assertNotNull(t);

        Collection<Condition> cs = definitionsService.getTriggerConditions(TEST_TENANT, t.getId(), null);
        assertTrue(cs.toString(), cs.size() == 1);
        Condition c = cs.iterator().next();

        // check for the implicit tag
        List<Tag> tags = definitionsService.getTriggerTags(TEST_TENANT, "trigger-1", "dataId");
        assertTrue(tags.toString(), tags.size() == 1);
        Tag tag = tags.get(0);
        assertEquals("trigger-1", tag.getTriggerId());
        assertEquals("dataId", tag.getCategory());
        assertEquals(c.getDataId(), tag.getName());
        assertEquals(false, tag.isVisible());

        Tag newTag = new Tag("trigger-1", "testcategory", "testname", true);
        definitionsService.addTag(TEST_TENANT, newTag);

        tags = definitionsService.getTriggerTags(TEST_TENANT, "trigger-1", null);
        assertTrue(tags.toString(), tags.size() == 2);
        tag = tags.get(1); // new one should be second by the implicit sort
        assertEquals("trigger-1", tag.getTriggerId());
        assertEquals("testcategory", tag.getCategory());
        assertEquals("testname", tag.getName());
        assertEquals(true, tag.isVisible());

        definitionsService.removeTags(TEST_TENANT, "trigger-1", "testcategory", "testname");
        tags = definitionsService.getTriggerTags(TEST_TENANT, "trigger-1", null);
        assertTrue(tags.toString(), tags.size() == 1);
        tag = tags.get(0);
        assertEquals("trigger-1", tag.getTriggerId());
        assertEquals("dataId", tag.getCategory());

        tags = definitionsService.getTriggerTags(TEST_TENANT, "dummy", null);
        assertTrue(tags.toString(), tags.size() == 0);
    }

    @Test
    public void test003BasicAlert() throws Exception {
        Trigger t = definitionsService.getTrigger(TEST_TENANT, "trigger-1");
        assertNotNull(t);

        Collection<Condition> cs = definitionsService.getTriggerConditions(TEST_TENANT, t.getId(), null);
        assertTrue(cs.toString(), cs.size() == 1);

        ThresholdCondition threshold = (ThresholdCondition)cs.iterator().next();
        long dataTime = System.currentTimeMillis();
        NumericData data = new NumericData("NumericData-01", dataTime, 5.0d);
        ThresholdConditionEval eval = new ThresholdConditionEval(threshold, data);
        Set<ConditionEval> evalSet = new HashSet<>();
        evalSet.add(eval);
        List<Set<ConditionEval>> evals = new ArrayList<>();
        evals.add(evalSet);
        Alert alert = new Alert(TEST_TENANT, t.getId(), evals);
        List<Alert> alerts = new ArrayList<>();
        alerts.add(alert);

        alertsService.addAlerts(alerts);

        // No filter
        List<Alert> result = alertsService.getAlerts(TEST_TENANT, null);
        assertTrue(result.toString(), result.size() == 1);

        // Specific trigger
        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setTriggerId("trigger-1");
        result = alertsService.getAlerts(TEST_TENANT, criteria);
        assertTrue(result.toString(), result.size() == 1);

        criteria = new AlertsCriteria();
        List<String> triggerIds = new ArrayList<>();
        triggerIds.add("trigger-1");
        triggerIds.add("trigger-2");
        criteria.setTriggerIds(triggerIds);
        result = alertsService.getAlerts(TEST_TENANT, criteria);
        assertTrue(result.toString(), result.size() == 1);

        // No trigger
        criteria = new AlertsCriteria();
        criteria.setTriggerId("trigger-2");
        result = alertsService.getAlerts(TEST_TENANT, criteria);
        assertTrue(result.toString(), result.size() == 0);

        criteria = new AlertsCriteria();
        triggerIds = new ArrayList<>();
        triggerIds.add("trigger-2");
        triggerIds.add("trigger-3");
        criteria.setTriggerIds(triggerIds);
        result = alertsService.getAlerts(TEST_TENANT, criteria);
        assertTrue(result.toString(), result.size() == 0);

        // Specific time
        criteria = new AlertsCriteria();
        criteria.setStartTime(dataTime - 100);
        criteria.setEndTime(dataTime + 100);
        result = alertsService.getAlerts(TEST_TENANT, criteria);
        assertTrue(result.toString(), result.size() == 1);

        // Out of time interval
        criteria = new AlertsCriteria();
        criteria.setStartTime(dataTime + 10000);
        criteria.setEndTime(dataTime + 20000);
        result = alertsService.getAlerts(TEST_TENANT, criteria);
        assertTrue(result.toString(), result.size() == 0);

        // Using tags
        criteria = new AlertsCriteria();
        Tag tag = new Tag();
        tag.setTenantId(TEST_TENANT);
        tag.setCategory("dataId");
        criteria.setTag(tag);
        result = alertsService.getAlerts(TEST_TENANT, criteria);
        assertTrue(result.toString(), result.size() == 1);

        // More specific tags
        criteria = new AlertsCriteria();
        tag = new Tag();
        tag.setTenantId(TEST_TENANT);
        tag.setName("NumericData-01");
        criteria.setTag(tag);
        result = alertsService.getAlerts(TEST_TENANT, criteria);
        assertTrue(result.toString(), result.size() == 1);

        // Using alertId
        criteria = new AlertsCriteria();
        criteria.setAlertId(alert.getAlertId());
        result = alertsService.getAlerts(TEST_TENANT, criteria);
        assertTrue(result.toString(), result.size() == 1);

        // Using status
        criteria = new AlertsCriteria();
        criteria.setStatus(alert.getStatus());
        result = alertsService.getAlerts(TEST_TENANT, criteria);
        assertTrue(result.toString(), result.size() == 1);

        criteria = new AlertsCriteria();
        criteria.setStatus(Alert.Status.RESOLVED);
        result = alertsService.getAlerts(TEST_TENANT, criteria);
        assertTrue(result.toString(), result.size() == 0);

        // Combine triggerId and ctime
        criteria = new AlertsCriteria();
        criteria.setTriggerId(alert.getTriggerId());
        criteria.setStartTime(dataTime - 100);
        result = alertsService.getAlerts(TEST_TENANT, criteria);
        assertTrue(result.toString(), result.size() == 1);

        // Combine triggerId, ctime and alertsId
        criteria = new AlertsCriteria();
        criteria.setTriggerId(alert.getTriggerId());
        criteria.setStartTime(dataTime - 100);
        criteria.setAlertId(alert.getAlertId());
        result = alertsService.getAlerts(TEST_TENANT, criteria);
        assertTrue(result.toString(), result.size() == 1);

        // Combine triggerIds, ctime and statuses
        criteria = new AlertsCriteria();
        ArrayList<String> triggersIds = new ArrayList<>();
        triggersIds.add(alert.getTriggerId());
        criteria.setTriggerIds(triggersIds);
        criteria.setStartTime(dataTime - 100);
        HashSet<Alert.Status> statuses = new HashSet<>();
        statuses.add(Alert.Status.RESOLVED);
        criteria.setStatusSet(statuses);
        result = alertsService.getAlerts(TEST_TENANT, criteria);
        assertTrue(result.toString(), result.size() == 0);
    }



}
