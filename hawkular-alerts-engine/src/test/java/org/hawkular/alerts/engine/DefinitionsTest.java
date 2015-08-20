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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.api.model.Severity;
import org.hawkular.alerts.api.model.condition.Alert;
import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.AvailabilityConditionEval;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.condition.ThresholdCondition;
import org.hawkular.alerts.api.model.condition.ThresholdConditionEval;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.data.Availability;
import org.hawkular.alerts.api.model.data.NumericData;
import org.hawkular.alerts.api.model.paging.AlertComparator;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
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
    public static final String TEST_TENANT = "28026b36-8fe4-4332-84c8-524e173a68bf";
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
    public void test0010ParentTrigger() throws Exception {
        Trigger t = definitionsService.getTrigger(TEST_TENANT, "trigger-7");
        assertNotNull(t);

        t = copyTrigger(t, "parent-trigger");
        assertNotNull(t);

        Collection<Condition> cs = definitionsService.getTriggerConditions(TEST_TENANT, t.getId(), null);
        assertEquals(cs.toString(), 1, cs.size());
        Condition c = cs.iterator().next();

        Collection<Dampening> ds = definitionsService.getTriggerDampenings(TEST_TENANT, t.getId(), null);
        assertEquals(cs.toString(), 1, ds.size());
        Dampening d = ds.iterator().next();

        Map<String, String> dataIdMap = new HashMap<>(1);
        dataIdMap.put("NumericData-Token", "NumericData-01");

        Map<String, String> context = new HashMap<>(1);
        context.put("context", "context-1");

        Trigger nt1 = definitionsService.addChildTrigger(TEST_TENANT, t.getId(), "child-1-trigger", "child-1",
                context, dataIdMap);
        assertNotNull(nt1);
        dataIdMap.put("NumericData-Token", "NumericData-02");
        context.put("context", "context-2");
        Trigger nt2 = definitionsService.addChildTrigger(TEST_TENANT, t.getId(), "child-2-trigger", "child-2",
                context, dataIdMap);
        assertNotNull(nt2);

        Collection<Trigger> children = definitionsService.getChildTriggers(TEST_TENANT, "parent-trigger", false);
        assertTrue(children != null);
        assertEquals(2, children.size());
        Iterator<Trigger> i = children.iterator();
        nt1 = i.next();
        if (nt1.getId().equals("child-1-trigger")) {
            nt2 = i.next();
        } else {
            nt2 = nt1;
            nt1 = i.next();
        }

        assertTrue(nt1.toString(), "child-1-trigger".equals(nt1.getId()));
        assertTrue(nt1.toString(), "child-1".equals(nt1.getName()));
        assertNotNull(nt1.getContext());
        assertTrue(nt1.toString(), "context-1".equals(nt1.getContext().get("context")));
        assertTrue(nt1.toString(), nt1.getDescription().equals(t.getDescription()));
        assertTrue(nt1.toString(), nt1.isEnabled());
        assertTrue(nt1.toString(), nt1.getFiringMatch().equals(t.getFiringMatch()));
        assertTrue(nt1.toString(), nt1.getAutoResolveMatch().equals(t.getAutoResolveMatch()));

        assertTrue(nt2.toString(), "child-2-trigger".equals(nt2.getId()));
        assertTrue(nt2.toString(), "child-2".equals(nt2.getName()));
        assertNotNull(nt2.getContext());
        assertTrue(nt2.toString(), "context-2".equals(nt2.getContext().get("context")));
        assertTrue(nt2.toString(), nt2.getDescription().equals(t.getDescription()));
        assertTrue(nt1.toString(), nt1.isEnabled());
        assertTrue(nt2.toString(), nt2.getFiringMatch().equals(t.getFiringMatch()));
        assertTrue(nt2.toString(), nt2.getAutoResolveMatch().equals(t.getAutoResolveMatch()));

        Collection<Condition> ncs = definitionsService.getTriggerConditions(TEST_TENANT, nt1.getId(), null);
        assertTrue(ncs.toString(), ncs.size() == 1);
        Condition nc = ncs.iterator().next();
        assertTrue(nc.toString(), nc.getClass().equals(c.getClass()));
        assertTrue(nc.toString(), nc.getTriggerId().equals(nt1.getId()));
        assertTrue(nc.toString(), nc.getTriggerMode().equals(c.getTriggerMode()));
        assertTrue(nc.toString(), nc.getDataId().equals("NumericData-01"));
        assertTrue(nc.toString(), nc.getConditionSetIndex() == c.getConditionSetIndex());
        assertTrue(nc.toString(), nc.getConditionSetSize() == c.getConditionSetSize());

        ncs = definitionsService.getTriggerConditions(TEST_TENANT, nt2.getId(), null);
        assertTrue(ncs.toString(), ncs.size() == 1);
        nc = ncs.iterator().next();
        assertTrue(nc.toString(), nc.getClass().equals(c.getClass()));
        assertTrue(nc.toString(), nc.getTriggerId().equals(nt2.getId()));
        assertTrue(nc.toString(), nc.getTriggerMode().equals(c.getTriggerMode()));
        assertTrue(nc.toString(), nc.getDataId().equals("NumericData-02"));
        assertTrue(nc.toString(), nc.getConditionSetIndex() == c.getConditionSetIndex());
        assertTrue(nc.toString(), nc.getConditionSetSize() == c.getConditionSetSize());

        Collection<Dampening> nds = definitionsService.getTriggerDampenings(TEST_TENANT, nt1.getId(), null);
        assertTrue(nds.toString(), nds.size() == 1);
        Dampening nd = nds.iterator().next();
        assertTrue(nd.toString(), nd.getTriggerId().equals(nt1.getId()));
        assertTrue(nd.toString(), nd.getTriggerMode().equals(d.getTriggerMode()));
        assertTrue(nd.toString(), nd.getEvalTrueSetting() == d.getEvalTrueSetting());
        assertTrue(nd.toString(), nd.getEvalTotalSetting() == d.getEvalTotalSetting());
        assertTrue(nd.toString(), nd.getEvalTimeSetting() == d.getEvalTimeSetting());

        nds = definitionsService.getTriggerDampenings(TEST_TENANT, nt2.getId(), null);
        assertTrue(nds.toString(), nds.size() == 1);
        nd = nds.iterator().next();
        assertTrue(nd.toString(), nd.getTriggerId().equals(nt2.getId()));
        assertTrue(nd.toString(), nd.getTriggerMode().equals(d.getTriggerMode()));
        assertTrue(nd.toString(), nd.getEvalTrueSetting() == d.getEvalTrueSetting());
        assertTrue(nd.toString(), nd.getEvalTotalSetting() == d.getEvalTotalSetting());
        assertTrue(nd.toString(), nd.getEvalTimeSetting() == d.getEvalTimeSetting());

        nt1.setName("child-1-update");
        try {
            definitionsService.updateTrigger(TEST_TENANT, nt1);
            fail("Child trigger update should have failed.");
        } catch (IllegalArgumentException e) {
            // expected
        }

        nt1 = definitionsService.orphanChildTrigger(TEST_TENANT, nt1.getId());
        assertTrue(nt1.toString(), nt1.isOrphan());

        nt1.setName("child-1-update");
        nt1.setContext(null);
        nt1.setDescription("Updated");
        nt1.setEnabled(false);
        try {
            nt1 = definitionsService.updateTrigger(TEST_TENANT, nt1);
        } catch (IllegalArgumentException e) {
            fail("Orphan trigger update should have succeeded.");
        }
        assertNotNull(nt1);
        assertTrue(nt1.toString(), nt1.isOrphan());
        assertTrue(nt1.toString(), "child-1-trigger".equals(nt1.getId()));
        assertTrue(nt1.toString(), "child-1-update".equals(nt1.getName()));
        assertTrue(nt1.toString(), nt1.getContext().isEmpty());
        assertTrue(nt1.toString(), "Updated".equals(nt1.getDescription()));
        assertTrue(nt1.toString(), !nt1.isEnabled());

        dataIdMap.put("NumericData-Token", "NumericData-01");
        context.put("context", "context-1");
        nt1 = definitionsService.unorphanChildTrigger(TEST_TENANT, nt1.getId(), context, dataIdMap);
        assertNotNull(nt1);
        assertTrue(nt1.toString(), !nt1.isOrphan());
        assertTrue(nt1.toString(), "child-1-trigger".equals(nt1.getId()));
        assertTrue(nt1.toString(), "child-1-update".equals(nt1.getName())); // name changes are maintained
        assertNotNull(nt1.getContext());
        assertTrue(nt1.toString(), "context-1".equals(nt1.getContext().get("context")));
        assertTrue(nt1.toString(), nt1.getDescription().equals(t.getDescription()));
        assertTrue(nt1.toString(), nt1.isEnabled());
        assertTrue(nt1.toString(), nt1.getFiringMatch().equals(t.getFiringMatch()));
        assertTrue(nt1.toString(), nt1.getAutoResolveMatch().equals(t.getAutoResolveMatch()));

        ncs = definitionsService.getTriggerConditions(TEST_TENANT, nt1.getId(), null);
        assertTrue(ncs.toString(), ncs.size() == 1);
        nc = ncs.iterator().next();
        assertTrue(nc.toString(), nc.getClass().equals(c.getClass()));
        assertTrue(nc.toString(), nc.getTriggerId().equals(nt1.getId()));
        assertTrue(nc.toString(), nc.getTriggerMode().equals(c.getTriggerMode()));
        assertTrue(nc.toString(), nc.getDataId().equals("NumericData-01"));
        assertTrue(nc.toString(), nc.getConditionSetIndex() == c.getConditionSetIndex());
        assertTrue(nc.toString(), nc.getConditionSetSize() == c.getConditionSetSize());

        definitionsService.removeParentTrigger(TEST_TENANT, "parent-trigger", false, false);

        t = definitionsService.getTrigger(TEST_TENANT, "parent-trigger");
        assertNull(t);
        t = definitionsService.getTrigger(TEST_TENANT, "child-1-trigger");
        assertNull(t);
        t = definitionsService.getTrigger(TEST_TENANT, "child-2-trigger");
        assertNull(t);
    }

    private Trigger copyTrigger(Trigger t, String newTriggerId) throws Exception {
        Collection<Condition> conditions = definitionsService.getTriggerConditions(TEST_TENANT, t.getId(), null);
        Collection<Dampening> dampenings = definitionsService.getTriggerDampenings(TEST_TENANT, t.getId(), null);

        String id = t.getId();
        t.setId(newTriggerId);
        definitionsService.addTrigger(TEST_TENANT, t);
        t.setId(id);

        Trigger nt = definitionsService.getTrigger(TEST_TENANT, newTriggerId);
        for (Condition c : conditions) {
            c.setTriggerId(newTriggerId);
            definitionsService.addCondition(TEST_TENANT, newTriggerId, c.getTriggerMode(), c);
        }
        for (Dampening d : dampenings) {
            d.setTriggerId(newTriggerId);
            definitionsService.addDampening(TEST_TENANT, d);
        }

        return nt;
    }

    @Test
    public void test0020ParentTriggerUpdate() throws Exception {
        Trigger t = definitionsService.getTrigger(TEST_TENANT, "trigger-7");
        assertNotNull(t);

        t = copyTrigger(t, "parent-trigger");
        assertNotNull(t);

        Collection<Condition> cs = definitionsService.getTriggerConditions(TEST_TENANT, t.getId(), null);
        assertEquals(cs.toString(), 1, cs.size());
        Condition c = cs.iterator().next();

        Collection<Dampening> ds = definitionsService.getTriggerDampenings(TEST_TENANT, t.getId(), null);
        assertEquals(cs.toString(), 1, ds.size());
        Dampening d = ds.iterator().next();

        Map<String, String> dataIdMap = new HashMap<>(1);
        dataIdMap.put("NumericData-Token", "NumericData-01");

        Map<String, String> context = new HashMap<>(1);
        context.put("context", "context-1");

        Trigger nt1 = definitionsService.addChildTrigger(TEST_TENANT, t.getId(), "child-1-trigger", "child-1",
                context, dataIdMap);
        assertNotNull(nt1);
        dataIdMap.put("NumericData-Token", "NumericData-02");
        context.put("context", "context-2");
        Trigger nt2 = definitionsService.addChildTrigger(TEST_TENANT, t.getId(), "child-2-trigger", "child-2",
                context, dataIdMap);
        assertNotNull(nt2);

        Collection<Trigger> children = definitionsService.getChildTriggers(TEST_TENANT, "parent-trigger", false);
        assertTrue(children != null);
        assertEquals(2, children.size());
        Iterator<Trigger> i = children.iterator();
        nt1 = i.next();
        if (nt1.getId().equals("child-1-trigger")) {
            nt2 = i.next();
        } else {
            nt2 = nt1;
            nt1 = i.next();
        }

        nt1 = definitionsService.orphanChildTrigger(TEST_TENANT, nt1.getId());
        assertTrue(nt1.toString(), nt1.isOrphan());

        t.setContext(null);
        t.setDescription("Updated");
        t.setEnabled(false);
        t = definitionsService.updateTrigger(TEST_TENANT, t);

        assertNotNull(t);
        assertTrue(t.toString(), t.isParent());
        assertTrue(t.toString(), "parent-trigger".equals(t.getId()));
        assertTrue(t.toString(), t.getContext().isEmpty());
        assertTrue(t.toString(), "Updated".equals(t.getDescription()));
        assertTrue(t.toString(), !t.isEnabled());

        children = definitionsService.getChildTriggers(TEST_TENANT, "parent-trigger", false);
        assertTrue(children != null);
        assertEquals(1, children.size());

        children = definitionsService.getChildTriggers(TEST_TENANT, "parent-trigger", true);
        assertTrue(children != null);
        assertEquals(2, children.size());
        i = children.iterator();
        nt1 = i.next();
        if (nt1.getId().equals("child-1-trigger")) {
            nt2 = i.next();
        } else {
            nt2 = nt1;
            nt1 = i.next();
        }

        assertTrue(nt1.toString(), nt1.isOrphan());
        assertTrue(nt1.toString(), "child-1-trigger".equals(nt1.getId()));
        assertTrue(nt1.toString(), "child-1".equals(nt1.getName()));
        assertNotNull(nt1.getContext());
        assertTrue(nt1.toString(), "context-1".equals(nt1.getContext().get("context")));
        assertTrue(nt1.toString(), !"Updated".equals(nt1.getDescription()));
        assertTrue(nt1.toString(), nt1.isEnabled());

        assertTrue(nt1.toString(), !nt2.isOrphan());
        assertTrue(nt2.toString(), "child-2-trigger".equals(nt2.getId()));
        assertTrue(nt2.toString(), "child-2".equals(nt2.getName()));
        assertTrue(nt2.toString(), nt2.getContext().isEmpty());
        assertTrue(nt2.toString(), "Updated".equals(nt2.getDescription()));
        assertTrue(nt2.toString(), !nt2.isEnabled());

        definitionsService.removeParentTrigger(TEST_TENANT, "parent-trigger", true, true);
        t = definitionsService.getTrigger(TEST_TENANT, "parent-trigger");
        assertNull(t);
        t = definitionsService.getTrigger(TEST_TENANT, "child-1-trigger");
        assertNotNull(t);
        t = definitionsService.getTrigger(TEST_TENANT, "child-2-trigger");
        assertNotNull(t);

        definitionsService.removeTrigger(TEST_TENANT, "child-1-trigger");
        t = definitionsService.getTrigger(TEST_TENANT, "child-1-trigger");
        assertNull(t);
        definitionsService.removeTrigger(TEST_TENANT, "child-2-trigger");
        t = definitionsService.getTrigger(TEST_TENANT, "child-2-trigger");
        assertNull(t);
    }

    @Test
    public void test0030BasicTags() throws Exception {
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
        assertEquals(tags.toString(), 1, tags.size());
        tag = tags.get(0);
        assertEquals("trigger-1", tag.getTriggerId());
        assertEquals("dataId", tag.getCategory());

        tags = definitionsService.getTriggerTags(TEST_TENANT, "dummy", null);
        assertTrue(tags.toString(), tags.size() == 0);
    }

    @Test
    public void test0040BasicAlert() throws Exception {
        Trigger t = definitionsService.getTrigger(TEST_TENANT, "trigger-1");
        assertNotNull(t);

        Collection<Condition> cs = definitionsService.getTriggerConditions(TEST_TENANT, t.getId(), null);
        assertTrue(cs.toString(), cs.size() == 1);

        ThresholdCondition threshold = (ThresholdCondition) cs.iterator().next();
        long dataTime = System.currentTimeMillis();
        NumericData data = new NumericData("NumericData-01", dataTime, 5.0d);
        ThresholdConditionEval eval = new ThresholdConditionEval(threshold, data);
        Set<ConditionEval> evalSet = new HashSet<>();
        evalSet.add(eval);
        List<Set<ConditionEval>> evals = new ArrayList<>();
        evals.add(evalSet);
        Alert alert = new Alert(TEST_TENANT, t.getId(), t.getSeverity(), evals);
        List<Alert> alerts = new ArrayList<>();
        alerts.add(alert);

        alertsService.addAlerts(alerts);

        // No filter
        List<Alert> result = alertsService.getAlerts(TEST_TENANT, null, null);
        assertEquals(result.toString(), 1, result.size());

        // Specific trigger
        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setTriggerId("trigger-1");
        result = alertsService.getAlerts(TEST_TENANT, criteria, null);
        assertTrue(result.toString(), result.size() == 1);

        criteria = new AlertsCriteria();
        List<String> triggerIds = new ArrayList<>();
        triggerIds.add("trigger-1");
        triggerIds.add("trigger-2");
        criteria.setTriggerIds(triggerIds);
        result = alertsService.getAlerts(TEST_TENANT, criteria, null);
        assertTrue(result.toString(), result.size() == 1);

        // No trigger
        criteria = new AlertsCriteria();
        criteria.setTriggerId("trigger-2");
        result = alertsService.getAlerts(TEST_TENANT, criteria, null);
        assertTrue(result.toString(), result.size() == 0);

        criteria = new AlertsCriteria();
        triggerIds = new ArrayList<>();
        triggerIds.add("trigger-2");
        triggerIds.add("trigger-3");
        criteria.setTriggerIds(triggerIds);
        result = alertsService.getAlerts(TEST_TENANT, criteria, null);
        assertTrue(result.toString(), result.size() == 0);

        // Specific time
        criteria = new AlertsCriteria();
        criteria.setStartTime(dataTime - 100);
        criteria.setEndTime(dataTime + 100);
        result = alertsService.getAlerts(TEST_TENANT, criteria, null);
        assertTrue(result.toString(), result.size() == 1);

        // Out of time interval
        criteria = new AlertsCriteria();
        criteria.setStartTime(dataTime + 10000);
        criteria.setEndTime(dataTime + 20000);
        result = alertsService.getAlerts(TEST_TENANT, criteria, null);
        assertTrue(result.toString(), result.size() == 0);

        // Using tags
        criteria = new AlertsCriteria();
        Tag tag = new Tag();
        tag.setTenantId(TEST_TENANT);
        tag.setCategory("dataId");
        criteria.setTag(tag);
        result = alertsService.getAlerts(TEST_TENANT, criteria, null);
        assertTrue(result.toString(), result.size() == 1);

        // More specific tags
        criteria = new AlertsCriteria();
        tag = new Tag();
        tag.setTenantId(TEST_TENANT);
        tag.setName("NumericData-01");
        criteria.setTag(tag);
        result = alertsService.getAlerts(TEST_TENANT, criteria, null);
        assertTrue(result.toString(), result.size() == 1);

        // Using alertId
        criteria = new AlertsCriteria();
        criteria.setAlertId(alert.getAlertId());
        result = alertsService.getAlerts(TEST_TENANT, criteria, null);
        assertTrue(result.toString(), result.size() == 1);

        // Using status
        criteria = new AlertsCriteria();
        criteria.setStatus(alert.getStatus());
        result = alertsService.getAlerts(TEST_TENANT, criteria, null);
        assertTrue(result.toString(), result.size() == 1);

        criteria = new AlertsCriteria();
        criteria.setStatus(Alert.Status.RESOLVED);
        result = alertsService.getAlerts(TEST_TENANT, criteria, null);
        assertTrue(result.toString(), result.size() == 0);

        // Combine triggerId and ctime
        criteria = new AlertsCriteria();
        criteria.setTriggerId(alert.getTriggerId());
        criteria.setStartTime(dataTime - 100);
        result = alertsService.getAlerts(TEST_TENANT, criteria, null);
        assertTrue(result.toString(), result.size() == 1);

        // Combine triggerId, ctime and alertsId
        criteria = new AlertsCriteria();
        criteria.setTriggerId(alert.getTriggerId());
        criteria.setStartTime(dataTime - 100);
        criteria.setAlertId(alert.getAlertId());
        result = alertsService.getAlerts(TEST_TENANT, criteria, null);
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
        result = alertsService.getAlerts(TEST_TENANT, criteria, null);
        assertTrue(result.toString(), result.size() == 0);
    }

    @Test
    public void test0050PagingAlerts() throws Exception {
        Trigger t = definitionsService.getTrigger(TEST_TENANT, "trigger-6");
        assertNotNull(t);

        Collection<Condition> cs = definitionsService.getTriggerConditions(TEST_TENANT, t.getId(), null);
        assertTrue(cs.toString(), cs.size() == 1);

        AvailabilityCondition availability = (AvailabilityCondition) cs.iterator().next();

        List<Alert> alerts = new ArrayList<>();

        for (int i = 0; i < 107; i++) {
            long dataTime = System.currentTimeMillis();
            Availability data = new Availability("Availability-01", dataTime, Availability.AvailabilityType.DOWN);
            AvailabilityConditionEval eval = new AvailabilityConditionEval(availability, data);
            Set<ConditionEval> evalSet = new HashSet<>();
            evalSet.add(eval);
            List<Set<ConditionEval>> evals = new ArrayList<>();
            evals.add(evalSet);
            Alert alert = new Alert(TEST_TENANT, t.getId(), t.getSeverity(), evals);
            int iAlert = i % 3;
            switch (iAlert) {
                case 2:
                    alert.setStatus(Alert.Status.OPEN);
                    alert.setSeverity(Severity.CRITICAL);
                    break;
                case 1:
                    alert.setStatus(Alert.Status.ACKNOWLEDGED);
                    alert.setSeverity(Severity.LOW);
                    break;
                case 0:
                    alert.setStatus(Alert.Status.RESOLVED);
                    alert.setSeverity(Severity.MEDIUM);
            }
            alerts.add(alert);
            Thread.sleep(2);
        }

        alertsService.addAlerts(alerts);

        List<Alert> result = alertsService.getAlerts(TEST_TENANT, null, null);
        assertEquals(107, result.size());

        /*
            Ordering and paging by alertId
         */
        Pager pager = Pager.builder().withPageSize(10).withStartPage(0)
                .orderByAscending(AlertComparator.Field.ALERT_ID.getText()).build();

        String firstAlertId;
        String lastAlertId;

        System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        Page<Alert> page = alertsService.getAlerts(TEST_TENANT, null, pager);
        System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        firstAlertId = page.get(0).getAlertId();

        assertEquals(107, page.getTotalSize());
        assertEquals(10, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = alertsService.getAlerts(TEST_TENANT, null, pager);
            System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(7, page.size());

        lastAlertId = page.get(6).getAlertId();

        System.out.println("first alert: " + firstAlertId + " last alert: " + lastAlertId);

        assertTrue(firstAlertId.compareTo(lastAlertId) < 0);

        pager = Pager.builder().withPageSize(10).withStartPage(0)
                .orderByDescending(AlertComparator.Field.ALERT_ID.getText()).build();

        System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        page = alertsService.getAlerts(TEST_TENANT, null, pager);
        System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        firstAlertId = page.get(0).getAlertId();

        assertEquals(107, page.getTotalSize());
        assertEquals(10, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = alertsService.getAlerts(TEST_TENANT, null, pager);
            System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(7, page.size());

        lastAlertId = page.get(6).getAlertId();

        System.out.println("first alert: " + firstAlertId + " last alert: " + lastAlertId);

        assertTrue(firstAlertId.compareTo(lastAlertId) > 0);

        /*
            Ordering and paging by ctime
         */
        pager = Pager.builder().withPageSize(10).withStartPage(0)
                .orderByAscending(AlertComparator.Field.CTIME.getText()).build();

        System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        page = alertsService.getAlerts(TEST_TENANT, null, pager);
        System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        long firstCtime = page.get(0).getCtime();

        assertEquals(107, page.getTotalSize());
        assertEquals(10, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = alertsService.getAlerts(TEST_TENANT, null, pager);
            System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(7, page.size());

        long lastCtime = page.get(6).getCtime();

        System.out.println("first ctime: " + firstCtime + " last ctime: " + lastCtime);

        assertTrue(firstCtime < lastCtime);

        pager = Pager.builder().withPageSize(10).withStartPage(0)
                .orderByDescending(AlertComparator.Field.CTIME.getText()).build();

        System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        page = alertsService.getAlerts(TEST_TENANT, null, pager);
        System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        firstCtime = page.get(0).getCtime();

        assertEquals(107, page.getTotalSize());
        assertEquals(10, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = alertsService.getAlerts(TEST_TENANT, null, pager);
            System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(7, page.size());

        lastCtime = page.get(6).getCtime();

        System.out.println("first ctime: " + firstCtime + " last ctime: " + lastCtime);

        assertTrue(firstCtime > lastCtime);

        /*
            Ordering and paging by severity
         */
        pager = Pager.builder().withPageSize(10).withStartPage(0)
                .orderByAscending(AlertComparator.Field.SEVERITY.getText()).build();

        System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        page = alertsService.getAlerts(TEST_TENANT, null, pager);
        System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        Severity firstSeverity = page.get(0).getSeverity();

        assertEquals(107, page.getTotalSize());
        assertEquals(10, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = alertsService.getAlerts(TEST_TENANT, null, pager);
            System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(7, page.size());

        Severity lastSeverity = page.get(6).getSeverity();

        System.out.println("first severity: " + firstSeverity + " last severity: " + lastSeverity);

        assertTrue(firstSeverity.compareTo(lastSeverity) < 0);

        pager = Pager.builder().withPageSize(10).withStartPage(0)
                .orderByDescending(AlertComparator.Field.SEVERITY.getText()).build();

        System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        page = alertsService.getAlerts(TEST_TENANT, null, pager);
        System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        firstSeverity = page.get(0).getSeverity();

        assertEquals(107, page.getTotalSize());
        assertEquals(10, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = alertsService.getAlerts(TEST_TENANT, null, pager);
            System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(7, page.size());

        lastSeverity = page.get(6).getSeverity();

        System.out.println("first severity: " + firstSeverity + " last severity: " + lastSeverity);

        assertTrue(firstSeverity.compareTo(lastSeverity) > 0);

        /*
            Ordering and paging by status
         */
        pager = Pager.builder().withPageSize(10).withStartPage(0)
                .orderByAscending(AlertComparator.Field.STATUS.getText()).build();

        System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        page = alertsService.getAlerts(TEST_TENANT, null, pager);
        System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        Alert.Status firstStatus = page.get(0).getStatus();

        assertEquals(107, page.getTotalSize());
        assertEquals(10, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = alertsService.getAlerts(TEST_TENANT, null, pager);
            System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(7, page.size());

        Alert.Status lastStatus = page.get(6).getStatus();

        System.out.println("first status: " + firstStatus + " last status: " + lastStatus);

        assertTrue(firstStatus.compareTo(lastStatus) < 0);

        pager = Pager.builder().withPageSize(10).withStartPage(0)
                .orderByDescending(AlertComparator.Field.STATUS.getText()).build();

        System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        page = alertsService.getAlerts(TEST_TENANT, null, pager);
        System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        firstStatus = page.get(0).getStatus();

        assertEquals(107, page.getTotalSize());
        assertEquals(10, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = alertsService.getAlerts(TEST_TENANT, null, pager);
            System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(7, page.size());

        lastStatus = page.get(6).getStatus();

        System.out.println("first status: " + firstStatus + " last status: " + lastStatus);

        assertTrue(firstStatus.compareTo(lastStatus) > 0);
    }

}
