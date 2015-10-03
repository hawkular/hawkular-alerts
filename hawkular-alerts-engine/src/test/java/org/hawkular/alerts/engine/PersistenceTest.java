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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.api.model.Severity;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.model.condition.Alert;
import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.AvailabilityConditionEval;
import org.hawkular.alerts.api.model.condition.CompareCondition;
import org.hawkular.alerts.api.model.condition.CompareCondition.Operator;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.condition.ThresholdCondition;
import org.hawkular.alerts.api.model.condition.ThresholdConditionEval;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.data.Availability;
import org.hawkular.alerts.api.model.data.NumericData;
import org.hawkular.alerts.api.model.paging.ActionComparator;
import org.hawkular.alerts.api.model.paging.AlertComparator;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.model.trigger.Mode;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.services.ActionsCriteria;
import org.hawkular.alerts.api.services.ActionsService;
import org.hawkular.alerts.api.services.AlertsCriteria;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.junit.Test;

/**
 *
 * @author Lucas Ponce
 */
public abstract class PersistenceTest {

    /*
        TenantId = 28026b36-8fe4-4332-84c8-524e173a68bf
        User = jdoe
     */
    public static final String TEST_TENANT = "28026b36-8fe4-4332-84c8-524e173a68bf";
    static DefinitionsService definitionsService;
    static AlertsService alertsService;
    static ActionsService actionsService;

    @Test
    public void test000InitScheme() throws Exception {
        assertTrue(definitionsService.getAllTriggers().size() > 0);
        assertTrue(definitionsService.getAllConditions().size() > 0);
        assertTrue(definitionsService.getAllDampenings().size() > 0);
        assertTrue(definitionsService.getAllActions().size() > 0);
    }

    @Test
    public void test0010GroupTrigger() throws Exception {
        Trigger t = definitionsService.getTrigger(TEST_TENANT, "trigger-7");
        assertNotNull(t);

        t = copyTrigger(t, "group-trigger");
        assertNotNull(t);

        Collection<Condition> cs = definitionsService.getTriggerConditions(TEST_TENANT, t.getId(), null);
        assertEquals(cs.toString(), 1, cs.size());
        Condition c = cs.iterator().next();

        Collection<Dampening> ds = definitionsService.getTriggerDampenings(TEST_TENANT, t.getId(), null);
        assertEquals(ds.toString(), 1, ds.size());
        Dampening d = ds.iterator().next();

        Map<String, String> dataIdMap = new HashMap<>(1);
        dataIdMap.put("NumericData-Token", "NumericData-01");

        Map<String, String> context = new HashMap<>(1);
        context.put("context", "context-1");

        Trigger nt1 = definitionsService.addMemberTrigger(TEST_TENANT, t.getId(), "member-1-trigger", "member-1",
                context, dataIdMap);
        assertNotNull(nt1);
        dataIdMap.put("NumericData-Token", "NumericData-02");
        context.put("context", "context-2");
        Trigger nt2 = definitionsService.addMemberTrigger(TEST_TENANT, t.getId(), "member-2-trigger", "member-2",
                context, dataIdMap);
        assertNotNull(nt2);

        Collection<Trigger> memberren = definitionsService.getMemberTriggers(TEST_TENANT, "group-trigger", false);
        assertTrue(memberren != null);
        assertEquals(2, memberren.size());
        Iterator<Trigger> i = memberren.iterator();
        nt1 = i.next();
        if (nt1.getId().equals("member-1-trigger")) {
            nt2 = i.next();
        } else {
            nt2 = nt1;
            nt1 = i.next();
        }

        assertTrue(nt1.toString(), "member-1-trigger".equals(nt1.getId()));
        assertTrue(nt1.toString(), "member-1".equals(nt1.getName()));
        assertNotNull(nt1.getContext());
        assertTrue(nt1.toString(), "context-1".equals(nt1.getContext().get("context")));
        assertTrue(nt1.toString(), nt1.getDescription().equals(t.getDescription()));
        assertTrue(nt1.toString(), nt1.isEnabled());
        assertTrue(nt1.toString(), nt1.getFiringMatch().equals(t.getFiringMatch()));
        assertTrue(nt1.toString(), nt1.getAutoResolveMatch().equals(t.getAutoResolveMatch()));

        assertTrue(nt2.toString(), "member-2-trigger".equals(nt2.getId()));
        assertTrue(nt2.toString(), "member-2".equals(nt2.getName()));
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

        nt1.setName("member-1-update");
        try {
            definitionsService.updateTrigger(TEST_TENANT, nt1);
            fail("Member trigger update should have failed.");
        } catch (IllegalArgumentException e) {
            // expected
        }

        nt1 = definitionsService.orphanMemberTrigger(TEST_TENANT, nt1.getId());
        assertTrue(nt1.toString(), nt1.isOrphan());

        nt1.setName("member-1-update");
        nt1.setContext(null);
        nt1.setDescription("Updated");
        nt1.setEnabled(false);
        try {
            nt1 = definitionsService.updateTrigger(TEST_TENANT, nt1);
        } catch (IllegalArgumentException e) {
            fail("Orphan trigger update should have succeeded:" + e.getMessage());
        }
        assertNotNull(nt1);
        assertTrue(nt1.toString(), nt1.isOrphan());
        assertTrue(nt1.toString(), "member-1-trigger".equals(nt1.getId()));
        assertTrue(nt1.toString(), "member-1-update".equals(nt1.getName()));
        assertTrue(nt1.toString(), nt1.getContext().isEmpty());
        assertTrue(nt1.toString(), "Updated".equals(nt1.getDescription()));
        assertTrue(nt1.toString(), !nt1.isEnabled());

        dataIdMap.put("NumericData-Token", "NumericData-01");
        context.put("context", "context-1");
        nt1 = definitionsService.unorphanMemberTrigger(TEST_TENANT, nt1.getId(), context, dataIdMap);
        assertNotNull(nt1);
        assertTrue(nt1.toString(), !nt1.isOrphan());
        assertTrue(nt1.toString(), "member-1-trigger".equals(nt1.getId()));
        assertTrue(nt1.toString(), "member-1-update".equals(nt1.getName())); // name changes are maintained
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

        definitionsService.removeGroupTrigger(TEST_TENANT, "group-trigger", false, false);

        t = definitionsService.getTrigger(TEST_TENANT, "group-trigger");
        assertNull(t);
        t = definitionsService.getTrigger(TEST_TENANT, "member-1-trigger");
        assertNull(t);
        t = definitionsService.getTrigger(TEST_TENANT, "member-2-trigger");
        assertNull(t);
    }

    private Trigger copyTrigger(Trigger t, String newTriggerId) throws Exception {
        Collection<Condition> allConditions = definitionsService.getTriggerConditions(TEST_TENANT, t.getId(), null);
        Collection<Dampening> allDampenings = definitionsService.getTriggerDampenings(TEST_TENANT, t.getId(), null);

        String id = t.getId();
        t.setId(newTriggerId);
        if (t.isGroup()) {
            definitionsService.addGroupTrigger(TEST_TENANT, t);
        } else {
            definitionsService.addTrigger(TEST_TENANT, t);
        }
        t.setId(id);

        Trigger nt = definitionsService.getTrigger(TEST_TENANT, newTriggerId);

        Collection<Condition> conditions = new ArrayList<>();
        for (Mode mode : Mode.values()) {
            conditions.clear();
            for (Condition c : allConditions) {
                if (c.getTriggerMode() == mode) {
                    c.setTriggerId(newTriggerId);
                    conditions.add(c);
                }
            }
            if (conditions.isEmpty()) {
                continue;
            }
            if (t.isGroup()) {
                definitionsService.setGroupConditions(TEST_TENANT, newTriggerId, mode, conditions, null);
            } else {
                definitionsService.setConditions(TEST_TENANT, newTriggerId, mode, conditions);
            }
        }

        for (Dampening d : allDampenings) {
            d.setTriggerId(newTriggerId);
            if (t.isGroup()) {
                definitionsService.addGroupDampening(TEST_TENANT, d);
            } else {
                definitionsService.addDampening(TEST_TENANT, d);
            }
        }

        return nt;
    }

    @Test
    public void test0020GroupTriggerUpdate() throws Exception {
        Trigger t = definitionsService.getTrigger(TEST_TENANT, "trigger-7");
        assertNotNull(t);

        t = copyTrigger(t, "group-trigger");
        assertNotNull(t);

        Map<String, String> dataIdMap = new HashMap<>(1);
        dataIdMap.put("NumericData-Token", "NumericData-01");

        Map<String, String> context = new HashMap<>(1);
        context.put("context", "context-1");

        Trigger nt1 = definitionsService.addMemberTrigger(TEST_TENANT, t.getId(), "member-1-trigger", "member-1",
                context, dataIdMap);
        assertNotNull(nt1);
        dataIdMap.put("NumericData-Token", "NumericData-02");
        context.put("context", "context-2");
        Trigger nt2 = definitionsService.addMemberTrigger(TEST_TENANT, t.getId(), "member-2-trigger", "member-2",
                context, dataIdMap);
        assertNotNull(nt2);

        Collection<Trigger> memberren = definitionsService.getMemberTriggers(TEST_TENANT, "group-trigger", false);
        assertTrue(memberren != null);
        assertEquals(2, memberren.size());
        Iterator<Trigger> i = memberren.iterator();
        nt1 = i.next();
        if (nt1.getId().equals("member-1-trigger")) {
            nt2 = i.next();
        } else {
            nt2 = nt1;
            nt1 = i.next();
        }

        nt1 = definitionsService.orphanMemberTrigger(TEST_TENANT, nt1.getId());
        assertTrue(nt1.toString(), nt1.isOrphan());

        t.setContext(null);
        t.setDescription("Updated");
        t.setEnabled(false);
        t = definitionsService.updateGroupTrigger(TEST_TENANT, t);

        assertNotNull(t);
        assertTrue(t.toString(), t.isGroup());
        assertTrue(t.toString(), "group-trigger".equals(t.getId()));
        assertTrue(t.toString(), t.getContext().isEmpty());
        assertTrue(t.toString(), "Updated".equals(t.getDescription()));
        assertTrue(t.toString(), !t.isEnabled());

        memberren = definitionsService.getMemberTriggers(TEST_TENANT, "group-trigger", false);
        assertTrue(memberren != null);
        assertEquals(1, memberren.size());

        memberren = definitionsService.getMemberTriggers(TEST_TENANT, "group-trigger", true);
        assertTrue(memberren != null);
        assertEquals(2, memberren.size());
        i = memberren.iterator();
        nt1 = i.next();
        if (nt1.getId().equals("member-1-trigger")) {
            nt2 = i.next();
        } else {
            nt2 = nt1;
            nt1 = i.next();
        }

        assertTrue(nt1.toString(), nt1.isOrphan());
        assertTrue(nt1.toString(), "member-1-trigger".equals(nt1.getId()));
        assertTrue(nt1.toString(), "member-1".equals(nt1.getName()));
        assertNotNull(nt1.getContext());
        assertTrue(nt1.toString(), "context-1".equals(nt1.getContext().get("context")));
        assertTrue(nt1.toString(), !"Updated".equals(nt1.getDescription()));
        assertTrue(nt1.toString(), nt1.isEnabled());

        assertTrue(nt1.toString(), !nt2.isOrphan());
        assertTrue(nt2.toString(), "member-2-trigger".equals(nt2.getId()));
        assertTrue(nt2.toString(), "member-2".equals(nt2.getName()));
        assertTrue(nt2.toString(), nt2.getContext().isEmpty());
        assertTrue(nt2.toString(), "Updated".equals(nt2.getDescription()));
        assertTrue(nt2.toString(), !nt2.isEnabled());

        definitionsService.removeGroupTrigger(TEST_TENANT, "group-trigger", true, true);
        t = definitionsService.getTrigger(TEST_TENANT, "group-trigger");
        assertNull(t);
        t = definitionsService.getTrigger(TEST_TENANT, "member-1-trigger");
        assertNotNull(t);
        t = definitionsService.getTrigger(TEST_TENANT, "member-2-trigger");
        assertNotNull(t);

        definitionsService.removeTrigger(TEST_TENANT, "member-1-trigger");
        t = definitionsService.getTrigger(TEST_TENANT, "member-1-trigger");
        assertNull(t);
        definitionsService.removeTrigger(TEST_TENANT, "member-2-trigger");
        t = definitionsService.getTrigger(TEST_TENANT, "member-2-trigger");
        assertNull(t);
    }

    @Test
    public void test0021GroupCondition() throws Exception {
        Trigger t = definitionsService.getTrigger(TEST_TENANT, "trigger-7");
        assertNotNull(t);

        t = copyTrigger(t, "group-trigger");
        assertNotNull(t);

        Map<String, String> dataIdMap = new HashMap<>(1);
        dataIdMap.put("NumericData-Token", "NumericData-01");
        Trigger nt1 = definitionsService.addMemberTrigger(TEST_TENANT, t.getId(), "member-1-trigger", "Member-1",
                null, dataIdMap);
        assertNotNull(nt1);

        dataIdMap.put("NumericData-Token", "NumericData-02");
        Trigger nt2 = definitionsService.addMemberTrigger(TEST_TENANT, t.getId(), "member-2-trigger", "Member-2",
                null, dataIdMap);
        assertNotNull(nt2);

        Collection<Condition> groupConditions = definitionsService.getTriggerConditions(TEST_TENANT, "group-trigger",
                null);
        assertNotNull(groupConditions);
        assertEquals(1, groupConditions.size());

        groupConditions = new ArrayList<>(groupConditions);

        CompareCondition compareCondition = new CompareCondition(t.getId(), Mode.FIRING, "Data1Id-Token", Operator.LT,
                50.0D, "Data2Id-Token");
        groupConditions.add(compareCondition);

        Map<String, Map<String, String>> dataIdMemberMap = new HashMap<>(3);
        Map<String, String> numericDataMemberMap = new HashMap<>(1);
        Map<String, String> data1IdMemberMap = new HashMap<>(1);
        Map<String, String> data2IdMemberMap = new HashMap<>(1);
        numericDataMemberMap.put(nt1.getId(), "NumericData-01");
        numericDataMemberMap.put(nt2.getId(), "NumericData-02");
        data1IdMemberMap.put(nt1.getId(), "Data1Id-Member-1");
        data1IdMemberMap.put(nt2.getId(), "Data1Id-Member-2");
        data2IdMemberMap.put(nt1.getId(), "Data2Id-Member-1");
        data2IdMemberMap.put(nt2.getId(), "Data2Id-Member-2");
        dataIdMemberMap.put("NumericData-Token", numericDataMemberMap);
        dataIdMemberMap.put("Data1Id-Token", data1IdMemberMap);
        dataIdMemberMap.put("Data2Id-Token", data2IdMemberMap);

        Collection<Condition> conditionSet = definitionsService.setGroupConditions(TEST_TENANT, "group-trigger",
                Mode.FIRING, groupConditions, dataIdMemberMap);
        assertNotNull(conditionSet);
        assertEquals(2, conditionSet.size());
        Iterator<Condition> ci = conditionSet.iterator();
        Condition c = ci.next();
        if (Condition.Type.COMPARE != c.getType()) {
            c = ci.next();
        }
        CompareCondition cc = (CompareCondition) c;
        compareCondition = cc;
        assertEquals(cc.toString(), cc.getTriggerId(), t.getId());
        assertEquals(cc.toString(), Mode.FIRING, cc.getTriggerMode());
        assertEquals(cc.toString(), cc.getDataId(), "Data1Id-Token");
        assertEquals(cc.toString(), cc.getData2Id(), "Data2Id-Token");
        assertEquals(cc.toString(), Operator.LT, cc.getOperator());
        assertTrue(cc.toString(), cc.getData2Multiplier() == 50D);
        assertEquals(cc.toString(), 2, cc.getConditionSetIndex());
        assertEquals(cc.toString(), 2, cc.getConditionSetSize());

        Collection<Trigger> members = definitionsService.getMemberTriggers(TEST_TENANT, "group-trigger", true);
        assertTrue(members != null);
        assertEquals(2, members.size());
        for (Trigger member : members) {
            conditionSet = definitionsService.getTriggerConditions(TEST_TENANT, member.getId(), null);
            assertEquals(2, conditionSet.size());
            ci = conditionSet.iterator();
            c = ci.next();
            if (Condition.Type.COMPARE != c.getType()) {
                c = ci.next();
            }
            cc = (CompareCondition) c;
            assertEquals(cc.toString(), cc.getTriggerId(), member.getId());
            assertEquals(cc.toString(), Mode.FIRING, cc.getTriggerMode());
            assertEquals(cc.toString(), cc.getDataId(), "Data1Id-" + member.getName());
            assertEquals(cc.toString(), cc.getData2Id(), "Data2Id-" + member.getName());
            assertEquals(cc.toString(), Operator.LT, cc.getOperator());
            assertTrue(cc.toString(), cc.getData2Multiplier() == 50D);
            assertEquals(cc.toString(), 2, cc.getConditionSetSize());
            assertEquals(cc.toString(), 2, cc.getConditionSetIndex());
        }

        compareCondition.setOperator(Operator.GT);
        compareCondition.setData2Multiplier(75D);
        conditionSet = definitionsService.setGroupConditions(TEST_TENANT, "group-trigger", Mode.FIRING,
                groupConditions, dataIdMemberMap);
        assertNotNull(conditionSet);
        assertEquals(2, conditionSet.size());
        ci = conditionSet.iterator();
        c = ci.next();
        if (Condition.Type.COMPARE != c.getType()) {
            c = ci.next();
        }
        cc = (CompareCondition) c;
        compareCondition = cc;
        assertEquals(cc.toString(), cc.getTriggerId(), t.getId());
        assertEquals(cc.toString(), Mode.FIRING, cc.getTriggerMode());
        assertEquals(cc.toString(), cc.getDataId(), "Data1Id-Token");
        assertEquals(cc.toString(), cc.getData2Id(), "Data2Id-Token");
        assertEquals(cc.toString(), Operator.GT, cc.getOperator());
        assertTrue(cc.toString(), cc.getData2Multiplier() == 75D);
        assertEquals(cc.toString(), 2, cc.getConditionSetSize());
        assertEquals(cc.toString(), 2, cc.getConditionSetIndex());

        members = definitionsService.getMemberTriggers(TEST_TENANT, "group-trigger", true);
        assertTrue(members != null);
        assertEquals(2, members.size());
        for (Trigger member : members) {
            conditionSet = definitionsService.getTriggerConditions(TEST_TENANT, member.getId(), null);
            assertEquals(2, conditionSet.size());
            ci = conditionSet.iterator();
            c = ci.next();
            if (Condition.Type.COMPARE != c.getType()) {
                c = ci.next();
            }
            cc = (CompareCondition) c;
            assertEquals(cc.toString(), cc.getTriggerId(), member.getId());
            assertEquals(cc.toString(), Mode.FIRING, cc.getTriggerMode());
            assertEquals(cc.toString(), cc.getDataId(), "Data1Id-" + member.getName());
            assertEquals(cc.toString(), cc.getData2Id(), "Data2Id-" + member.getName());
            assertEquals(cc.toString(), Operator.GT, cc.getOperator());
            assertTrue(cc.toString(), cc.getData2Multiplier() == 75D);
            assertEquals(cc.toString(), 2, cc.getConditionSetSize());
            assertEquals(cc.toString(), 2, cc.getConditionSetIndex());
        }

        groupConditions.remove(compareCondition);
        dataIdMemberMap.remove("Data1Id-Token");
        dataIdMemberMap.remove("Data2Id-Token");

        conditionSet = definitionsService.setGroupConditions(TEST_TENANT, "group-trigger", Mode.FIRING,
                groupConditions, dataIdMemberMap);
        assertNotNull(conditionSet);
        assertEquals(1, conditionSet.size());
        ci = conditionSet.iterator();
        c = ci.next();
        assertTrue(c.toString(), Condition.Type.COMPARE != c.getType());

        members = definitionsService.getMemberTriggers(TEST_TENANT, "group-trigger", true);
        assertTrue(members != null);
        assertEquals(2, members.size());
        for (Trigger member : members) {
            conditionSet = definitionsService.getTriggerConditions(TEST_TENANT, member.getId(), null);
            assertEquals(1, conditionSet.size());
            ci = conditionSet.iterator();
            c = ci.next();
            assertTrue(c.toString(), Condition.Type.COMPARE != c.getType());
        }

        definitionsService.removeGroupTrigger(TEST_TENANT, "group-trigger", false, false);

        t = definitionsService.getTrigger(TEST_TENANT, "group-trigger");
        assertNull(t);
        t = definitionsService.getTrigger(TEST_TENANT, "member-1-trigger");
        assertNull(t);
        t = definitionsService.getTrigger(TEST_TENANT, "member-2-trigger");
        assertNull(t);
    }

    @Test
    public void test0022GroupDampening() throws Exception {
        Trigger t = definitionsService.getTrigger(TEST_TENANT, "trigger-7");
        assertNotNull(t);

        t = copyTrigger(t, "group-trigger");
        assertNotNull(t);

        Map<String, String> dataIdMap = new HashMap<>(1);
        dataIdMap.put("NumericData-Token", "NumericData-01");

        Trigger nt1 = definitionsService.addMemberTrigger(TEST_TENANT, t.getId(), "member-1-trigger", "Member-1",
                null, dataIdMap);
        assertNotNull(nt1);
        dataIdMap.put("NumericData-Token", "NumericData-02");
        Trigger nt2 = definitionsService.addMemberTrigger(TEST_TENANT, t.getId(), "member-2-trigger", "Member-2",
                null, dataIdMap);
        assertNotNull(nt2);

        Dampening groupDampening = Dampening.forStrict("group-trigger", Mode.FIRING, 10);

        Dampening d = definitionsService.addGroupDampening(TEST_TENANT, groupDampening);
        assertNotNull(d);

        Collection<Dampening> ds = definitionsService.getTriggerDampenings(TEST_TENANT, "group-trigger", null);
        assertEquals(1, ds.size());
        d = ds.iterator().next();
        assertEquals(d.toString(), t.getId(), d.getTriggerId());
        assertEquals(d.toString(), Mode.FIRING, d.getTriggerMode());
        assertEquals(d.toString(), Dampening.Type.STRICT, d.getType());
        assertEquals(d.toString(), 10, d.getEvalTrueSetting());

        Collection<Trigger> memberren = definitionsService.getMemberTriggers(TEST_TENANT, "group-trigger", true);
        assertTrue(memberren != null);
        assertEquals(2, memberren.size());
        for (Trigger member : memberren) {
            ds = definitionsService.getTriggerDampenings(TEST_TENANT, member.getId(), null);
            assertEquals(1, ds.size());
            d = ds.iterator().next();
            assertEquals(d.toString(), member.getId(), d.getTriggerId());
            assertEquals(d.toString(), Mode.FIRING, d.getTriggerMode());
            assertEquals(d.toString(), Dampening.Type.STRICT, d.getType());
            assertEquals(d.toString(), 10, d.getEvalTrueSetting());
        }

        groupDampening = Dampening.forRelaxedCount("group-trigger", Mode.FIRING, 5, 10);
        d = definitionsService.updateGroupDampening(TEST_TENANT, groupDampening);
        assertNotNull(d);

        ds = definitionsService.getTriggerDampenings(TEST_TENANT, "group-trigger", null);
        assertEquals(1, ds.size());
        d = ds.iterator().next();
        assertEquals(d.toString(), t.getId(), d.getTriggerId());
        assertEquals(d.toString(), Mode.FIRING, d.getTriggerMode());
        assertEquals(d.toString(), Dampening.Type.RELAXED_COUNT, d.getType());
        assertEquals(d.toString(), 5, d.getEvalTrueSetting());
        assertEquals(d.toString(), 10, d.getEvalTotalSetting());

        memberren = definitionsService.getMemberTriggers(TEST_TENANT, "group-trigger", true);
        assertTrue(memberren != null);
        assertEquals(2, memberren.size());
        for (Trigger member : memberren) {
            ds = definitionsService.getTriggerDampenings(TEST_TENANT, member.getId(), null);
            assertEquals(1, ds.size());
            d = ds.iterator().next();
            assertEquals(d.toString(), member.getId(), d.getTriggerId());
            assertEquals(d.toString(), Mode.FIRING, d.getTriggerMode());
            assertEquals(d.toString(), Dampening.Type.RELAXED_COUNT, d.getType());
            assertEquals(d.toString(), 5, d.getEvalTrueSetting());
            assertEquals(d.toString(), 10, d.getEvalTotalSetting());
        }

        definitionsService.removeGroupDampening(TEST_TENANT, groupDampening.getDampeningId());
        ds = definitionsService.getTriggerDampenings(TEST_TENANT, "group-trigger", null);
        assertTrue(ds.isEmpty());

        memberren = definitionsService.getMemberTriggers(TEST_TENANT, "group-trigger", true);
        assertTrue(memberren != null);
        assertEquals(2, memberren.size());
        for (Trigger member : memberren) {
            ds = definitionsService.getTriggerDampenings(TEST_TENANT, member.getId(), null);
            assertTrue(ds.isEmpty());
        }

        definitionsService.removeGroupTrigger(TEST_TENANT, "group-trigger", false, false);

        t = definitionsService.getTrigger(TEST_TENANT, "group-trigger");
        assertNull(t);
        t = definitionsService.getTrigger(TEST_TENANT, "member-1-trigger");
        assertNull(t);
        t = definitionsService.getTrigger(TEST_TENANT, "member-2-trigger");
        assertNull(t);
    }

    @Test
    public void test0030BasicTags() throws Exception {
        Trigger t = definitionsService.getTrigger(TEST_TENANT, "trigger-1");
        assertNotNull(t);

        Collection<Condition> cs = definitionsService.getTriggerConditions(TEST_TENANT, t.getId(), null);
        assertTrue(cs.toString(), cs.size() == 1);
        Condition c = cs.iterator().next();

        Map<String, String> tags = new HashMap<>(t.getTags());
        tags.put("testname", "testvalue");
        t.setTags(tags);
        Map<String, String> newTag = new HashMap<>(1);
        definitionsService.updateTrigger(TEST_TENANT, t);

        t = definitionsService.getTrigger(TEST_TENANT, "trigger-1");
        assertEquals(3, t.getTags().size());
        assertEquals("tvalue1", t.getTags().get("tname1"));
        assertEquals("tvalue2", t.getTags().get("tname2"));
        assertEquals("testvalue", t.getTags().get("testname"));

        Collection<Trigger> triggers = definitionsService.getTriggersByTag(TEST_TENANT, "testname", "bogus");
        assertEquals(0, triggers.size());
        triggers = definitionsService.getTriggersByTag(TEST_TENANT, "bogus", "testvalue");
        assertEquals(0, triggers.size());
        triggers = definitionsService.getTriggersByTag(TEST_TENANT, "testname", "testvalue");
        assertEquals(1, triggers.size());
        triggers = definitionsService.getTriggersByTag(TEST_TENANT, "testname", "*");
        assertEquals(1, triggers.size());
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
        criteria.addTag("tname1", "*");
        result = alertsService.getAlerts(TEST_TENANT, criteria, null);
        assertTrue(result.toString(), result.size() == 1);

        // More specific tags
        criteria = new AlertsCriteria();
        criteria.addTag("tname2", "tvalue2");
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

    @Test
    public void test0060BasicActionsHistory() throws Exception {
        for (int i = 0; i < 107; i++) {
            Alert testAlert = new Alert(TEST_TENANT, "test-trigger", Severity.CRITICAL, null);
            Action action = new Action(testAlert.getTenantId(), "testplugin", "send-to-this-groups", testAlert);
            Thread.sleep(2);
            actionsService.send(action);
        }

        List<Action> actions = actionsService.getActions(TEST_TENANT, null, null);
        assertEquals(107, actions.size());
    }

    @Test
    public void test0070SearchActionsHistory() throws Exception {
        for (int i = 0; i < 10; i++) {
            Alert testAlert = new Alert();
            testAlert.setTenantId(TEST_TENANT);
            testAlert.setTriggerId("test-trigger");
            testAlert.setSeverity(Severity.CRITICAL);
            testAlert.setCtime(i);
            testAlert.setAlertId("test-alert" + i);
            Action action1 = new Action(testAlert.getTenantId(), "plugin1", "action1", testAlert);
            Action action2 = new Action(testAlert.getTenantId(), "plugin1", "action2", testAlert);
            Action action3 = new Action(testAlert.getTenantId(), "plugin2", "action1", testAlert);
            Action action4 = new Action(testAlert.getTenantId(), "plugin2", "action2", testAlert);
            action1.setCtime(i);
            action2.setCtime(i);
            action3.setCtime(i);
            action4.setCtime(i);
            action1.setResult("result1");
            action2.setResult("result2");
            action3.setResult("result3");
            action4.setResult("result4");
            actionsService.send(action1);
            actionsService.send(action2);
            actionsService.send(action3);
            actionsService.send(action4);
        }

        List<Action> actions = actionsService.getActions(TEST_TENANT, null, null);
        assertEquals(10 * 4, actions.size());

        ActionsCriteria criteria = new ActionsCriteria();
        criteria.setStartTime(2L);

        actions = actionsService.getActions(TEST_TENANT, criteria, null);
        assertEquals(8 * 4, actions.size());

        criteria.setStartTime(2L);
        criteria.setEndTime(3L);

        actions = actionsService.getActions(TEST_TENANT, criteria, null);
        assertEquals(2 * 4, actions.size());

        criteria = new ActionsCriteria();
        criteria.setActionPlugin("plugin1");

        actions = actionsService.getActions(TEST_TENANT, criteria, null);
        assertEquals(10 * 2, actions.size());

        criteria = new ActionsCriteria();
        criteria.setActionPlugins(Arrays.asList("plugin1", "plugin2"));

        actions = actionsService.getActions(TEST_TENANT, criteria, null);
        assertEquals(10 * 4, actions.size());

        criteria = new ActionsCriteria();
        criteria.setActionId("action1");
        actions = actionsService.getActions(TEST_TENANT, criteria, null);
        assertEquals(10 * 2, actions.size());

        criteria = new ActionsCriteria();
        criteria.setActionIds(Arrays.asList("action1", "action2"));
        actions = actionsService.getActions(TEST_TENANT, criteria, null);
        assertEquals(10 * 4, actions.size());

        criteria = new ActionsCriteria();
        criteria.setAlertId("test-alert1");
        actions = actionsService.getActions(TEST_TENANT, criteria, null);
        assertEquals(1 * 4, actions.size());

        criteria = new ActionsCriteria();
        criteria.setAlertIds(Arrays.asList("test-alert1", "test-alert2", "test-alert3"));
        actions = actionsService.getActions(TEST_TENANT, criteria, null);
        assertEquals(3 * 4, actions.size());

        criteria = new ActionsCriteria();
        criteria.setResult("result1");
        actions = actionsService.getActions(TEST_TENANT, criteria, null);
        assertEquals(10 * 1, actions.size());

        criteria = new ActionsCriteria();
        criteria.setResults(Arrays.asList("result1", "result2"));
        actions = actionsService.getActions(TEST_TENANT, criteria, null);
        assertEquals(10 * 2, actions.size());

        criteria = new ActionsCriteria();
        criteria.setStartTime(2L);
        criteria.setActionPlugin("plugin1");
        criteria.setActionId("action1");
        actions = actionsService.getActions(TEST_TENANT, criteria, null);
        assertEquals(8 * 1, actions.size());
    }

    @Test
    public void test0080PaginationActionsHistory() throws Exception {
        for (int i = 0; i < 103; i++) {
            Alert testAlert = new Alert();
            testAlert.setTenantId(TEST_TENANT);
            testAlert.setTriggerId("test-trigger");
            testAlert.setSeverity(Severity.CRITICAL);
            testAlert.setCtime(i);
            testAlert.setAlertId("test-alert" + i);
            Action action1 = new Action(testAlert.getTenantId(), "plugin1", "action1", testAlert);
            Action action2 = new Action(testAlert.getTenantId(), "plugin1", "action2", testAlert);
            Action action3 = new Action(testAlert.getTenantId(), "plugin2", "action1", testAlert);
            Action action4 = new Action(testAlert.getTenantId(), "plugin2", "action2", testAlert);
            action1.setCtime(i);
            action2.setCtime(i);
            action3.setCtime(i);
            action4.setCtime(i);
            action1.setResult("result1");
            action2.setResult("result2");
            action3.setResult("result3");
            action4.setResult("result4");
            actionsService.send(action1);
            actionsService.send(action2);
            actionsService.send(action3);
            actionsService.send(action4);
        }

        List<Action> actions = actionsService.getActions(TEST_TENANT, null, null);
        assertEquals(103 * 4, actions.size());

        Pager pager = Pager.builder().withPageSize(10).withStartPage(0)
                .orderByAscending(ActionComparator.Field.ALERT_ID.getText()).build();

        System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        Page<Action> page = actionsService.getActions(TEST_TENANT, null, pager);
        System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        Action firstAction = page.get(0);

        assertEquals(103 * 4, page.getTotalSize());
        assertEquals(10, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = actionsService.getActions(TEST_TENANT, null, pager);
            System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(2, page.size());

        Action lastAction = page.get(1);

        assertTrue(firstAction.getAlert().getAlertId().compareTo(lastAction.getAlert().getAlertId()) < 0);

        pager = Pager.builder().withPageSize(10).withStartPage(0)
                .orderByDescending(ActionComparator.Field.RESULT.getText()).build();

        System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        page = actionsService.getActions(TEST_TENANT, null, pager);
        System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        firstAction = page.get(0);

        assertEquals(103 * 4, page.getTotalSize());
        assertEquals(10, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = actionsService.getActions(TEST_TENANT, null, pager);
            System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(2, page.size());

        lastAction = page.get(1);

        assertTrue(firstAction.getResult().compareTo(lastAction.getResult()) > 0);
    }
}
