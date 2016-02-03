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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.api.model.Severity;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.AvailabilityConditionEval;
import org.hawkular.alerts.api.model.condition.CompareCondition;
import org.hawkular.alerts.api.model.condition.CompareCondition.Operator;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.condition.ThresholdCondition;
import org.hawkular.alerts.api.model.condition.ThresholdConditionEval;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.data.AvailabilityType;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.event.EventCategory;
import org.hawkular.alerts.api.model.paging.ActionComparator;
import org.hawkular.alerts.api.model.paging.AlertComparator;
import org.hawkular.alerts.api.model.paging.EventComparator;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.model.paging.TriggerComparator;
import org.hawkular.alerts.api.model.trigger.Mode;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.model.trigger.TriggerAction;
import org.hawkular.alerts.api.services.ActionsCriteria;
import org.hawkular.alerts.api.services.ActionsService;
import org.hawkular.alerts.api.services.AlertsCriteria;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.api.services.EventsCriteria;
import org.hawkular.alerts.api.services.TriggersCriteria;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 *
 * @author Lucas Ponce
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public abstract class PersistenceTest {

    /*
        TenantId = 28026b36-8fe4-4332-84c8-524e173a68bf
        User = jdoe
     */
    public static final String TENANT = "28026b36-8fe4-4332-84c8-524e173a68bf";
    static DefinitionsService definitionsService;
    static AlertsService alertsService;
    static ActionsService actionsService;

    @Test
    public void test000InitScheme() throws Exception {
        System.out.println("test000InitScheme...");

        assertTrue(definitionsService.getAllTriggers().size() > 0);
        assertTrue(definitionsService.getAllConditions().size() > 0);
        assertTrue(definitionsService.getAllDampenings().size() > 0);
        assertTrue(definitionsService.getAllActionDefinitionIds().size() > 0);
    }

    @Test
    public void test0010GroupTrigger() throws Exception {
        System.out.println("test0010GroupTrigger...");

        Trigger t = definitionsService.getTrigger(TENANT, "trigger-7");
        assertNotNull(t);

        t = copyTrigger(t, "group-trigger");
        assertNotNull(t);

        Collection<Condition> cs = definitionsService.getTriggerConditions(TENANT, t.getId(), null);
        assertEquals(cs.toString(), 1, cs.size());
        Condition c = cs.iterator().next();

        Collection<Dampening> ds = definitionsService.getTriggerDampenings(TENANT, t.getId(), null);
        assertEquals(ds.toString(), 1, ds.size());
        Dampening d = ds.iterator().next();

        Map<String, String> dataIdMap = new HashMap<>(1);
        dataIdMap.put("NumericData-Token", "NumericData-01");

        Map<String, String> context = new HashMap<>(1);
        context.put("context", "context-1");

        Trigger nt1 = definitionsService.addMemberTrigger(TENANT, t.getId(), "member-1-trigger", "member-1",
                null, context, null, dataIdMap);
        assertNotNull(nt1);
        dataIdMap.put("NumericData-Token", "NumericData-02");
        context.put("context", "context-2");
        Trigger nt2 = definitionsService.addMemberTrigger(TENANT, t.getId(), "member-2-trigger", "member-2",
                null, context, null, dataIdMap);
        assertNotNull(nt2);

        Collection<Trigger> memberren = definitionsService.getMemberTriggers(TENANT, "group-trigger", false);
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

        Collection<Condition> ncs = definitionsService.getTriggerConditions(TENANT, nt1.getId(), null);
        assertTrue(ncs.toString(), ncs.size() == 1);
        Condition nc = ncs.iterator().next();
        assertTrue(nc.toString(), nc.getClass().equals(c.getClass()));
        assertTrue(nc.toString(), nc.getTriggerId().equals(nt1.getId()));
        assertTrue(nc.toString(), nc.getTriggerMode().equals(c.getTriggerMode()));
        assertTrue(nc.toString(), nc.getDataId().equals("NumericData-01"));
        assertTrue(nc.toString(), nc.getConditionSetIndex() == c.getConditionSetIndex());
        assertTrue(nc.toString(), nc.getConditionSetSize() == c.getConditionSetSize());

        ncs = definitionsService.getTriggerConditions(TENANT, nt2.getId(), null);
        assertTrue(ncs.toString(), ncs.size() == 1);
        nc = ncs.iterator().next();
        assertTrue(nc.toString(), nc.getClass().equals(c.getClass()));
        assertTrue(nc.toString(), nc.getTriggerId().equals(nt2.getId()));
        assertTrue(nc.toString(), nc.getTriggerMode().equals(c.getTriggerMode()));
        assertTrue(nc.toString(), nc.getDataId().equals("NumericData-02"));
        assertTrue(nc.toString(), nc.getConditionSetIndex() == c.getConditionSetIndex());
        assertTrue(nc.toString(), nc.getConditionSetSize() == c.getConditionSetSize());

        Collection<Dampening> nds = definitionsService.getTriggerDampenings(TENANT, nt1.getId(), null);
        assertTrue(nds.toString(), nds.size() == 1);
        Dampening nd = nds.iterator().next();
        assertTrue(nd.toString(), nd.getTriggerId().equals(nt1.getId()));
        assertTrue(nd.toString(), nd.getTriggerMode().equals(d.getTriggerMode()));
        assertTrue(nd.toString(), nd.getEvalTrueSetting() == d.getEvalTrueSetting());
        assertTrue(nd.toString(), nd.getEvalTotalSetting() == d.getEvalTotalSetting());
        assertTrue(nd.toString(), nd.getEvalTimeSetting() == d.getEvalTimeSetting());

        nds = definitionsService.getTriggerDampenings(TENANT, nt2.getId(), null);
        assertTrue(nds.toString(), nds.size() == 1);
        nd = nds.iterator().next();
        assertTrue(nd.toString(), nd.getTriggerId().equals(nt2.getId()));
        assertTrue(nd.toString(), nd.getTriggerMode().equals(d.getTriggerMode()));
        assertTrue(nd.toString(), nd.getEvalTrueSetting() == d.getEvalTrueSetting());
        assertTrue(nd.toString(), nd.getEvalTotalSetting() == d.getEvalTotalSetting());
        assertTrue(nd.toString(), nd.getEvalTimeSetting() == d.getEvalTimeSetting());

        nt1.setName("member-1-update");
        try {
            definitionsService.updateTrigger(TENANT, nt1);
            fail("Member trigger update should have failed.");
        } catch (IllegalArgumentException e) {
            // expected
        }

        nt1 = definitionsService.orphanMemberTrigger(TENANT, nt1.getId());
        assertTrue(nt1.toString(), nt1.isOrphan());

        nt1.setName("member-1-update");
        nt1.setContext(null);
        nt1.setDescription("Updated");
        nt1.setEnabled(false);
        try {
            nt1 = definitionsService.updateTrigger(TENANT, nt1);
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
        nt1 = definitionsService.unorphanMemberTrigger(TENANT, nt1.getId(), context, null, dataIdMap);
        assertNotNull(nt1);
        assertTrue(nt1.toString(), !nt1.isOrphan());
        assertTrue(nt1.toString(), "member-1-trigger".equals(nt1.getId()));
        assertTrue(nt1.toString(), "member-1-update".equals(nt1.getName())); // name changes are maintained
        assertNotNull(nt1.getContext());
        assertTrue(nt1.toString(), "context-1".equals(nt1.getContext().get("context")));
        assertTrue(nt1.toString(), nt1.getDescription().equals("Updated"));
        assertTrue(nt1.toString(), nt1.isEnabled());
        assertTrue(nt1.toString(), nt1.getFiringMatch().equals(t.getFiringMatch()));
        assertTrue(nt1.toString(), nt1.getAutoResolveMatch().equals(t.getAutoResolveMatch()));

        ncs = definitionsService.getTriggerConditions(TENANT, nt1.getId(), null);
        assertTrue(ncs.toString(), ncs.size() == 1);
        nc = ncs.iterator().next();
        assertTrue(nc.toString(), nc.getClass().equals(c.getClass()));
        assertTrue(nc.toString(), nc.getTriggerId().equals(nt1.getId()));
        assertTrue(nc.toString(), nc.getTriggerMode().equals(c.getTriggerMode()));
        assertTrue(nc.toString(), nc.getDataId().equals("NumericData-01"));
        assertTrue(nc.toString(), nc.getConditionSetIndex() == c.getConditionSetIndex());
        assertTrue(nc.toString(), nc.getConditionSetSize() == c.getConditionSetSize());

        definitionsService.removeGroupTrigger(TENANT, "group-trigger", false, false);

        t = definitionsService.getTrigger(TENANT, "group-trigger");
        assertNull(t);
        t = definitionsService.getTrigger(TENANT, "member-1-trigger");
        assertNull(t);
        t = definitionsService.getTrigger(TENANT, "member-2-trigger");
        assertNull(t);
    }

    private Trigger copyTrigger(Trigger t, String newTriggerId) throws Exception {
        Collection<Condition> allConditions = definitionsService.getTriggerConditions(TENANT, t.getId(), null);
        Collection<Dampening> allDampenings = definitionsService.getTriggerDampenings(TENANT, t.getId(), null);

        String id = t.getId();
        t.setId(newTriggerId);
        if (t.isGroup()) {
            definitionsService.addGroupTrigger(TENANT, t);
        } else {
            definitionsService.addTrigger(TENANT, t);
        }
        t.setId(id);

        Trigger nt = definitionsService.getTrigger(TENANT, newTriggerId);

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
                definitionsService.setGroupConditions(TENANT, newTriggerId, mode, conditions, null);
            } else {
                definitionsService.setConditions(TENANT, newTriggerId, mode, conditions);
            }
        }

        for (Dampening d : allDampenings) {
            d.setTriggerId(newTriggerId);
            if (t.isGroup()) {
                definitionsService.addGroupDampening(TENANT, d);
            } else {
                definitionsService.addDampening(TENANT, d);
            }
        }

        return nt;
    }

    @Test
    public void test0020GroupTriggerUpdate() throws Exception {
        System.out.println("test0020GroupTriggerUpdate...");

        Trigger t = definitionsService.getTrigger(TENANT, "trigger-7");
        assertNotNull(t);

        t = copyTrigger(t, "group-trigger");
        assertNotNull(t);

        Map<String, String> dataIdMap = new HashMap<>(1);
        dataIdMap.put("NumericData-Token", "NumericData-01");

        Map<String, String> context = new HashMap<>(1);
        context.put("context", "context-1");

        Trigger nt1 = definitionsService.addMemberTrigger(TENANT, t.getId(), "member-1-trigger", "member-1",
                null, context, null, dataIdMap);
        assertNotNull(nt1);
        dataIdMap.put("NumericData-Token", "NumericData-02");
        context.put("context", "context-2");
        Trigger nt2 = definitionsService.addMemberTrigger(TENANT, t.getId(), "member-2-trigger", "member-2",
                null, context, null, dataIdMap);
        assertNotNull(nt2);

        Collection<Trigger> memberren = definitionsService.getMemberTriggers(TENANT, "group-trigger", false);
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

        nt1 = definitionsService.orphanMemberTrigger(TENANT, nt1.getId());
        assertTrue(nt1.toString(), nt1.isOrphan());

        t.setContext(null);
        t.setDescription("Updated");
        t.setEnabled(false);
        t = definitionsService.updateGroupTrigger(TENANT, t);

        assertNotNull(t);
        assertTrue(t.toString(), t.isGroup());
        assertTrue(t.toString(), "group-trigger".equals(t.getId()));
        assertTrue(t.toString(), t.getContext().isEmpty());
        assertTrue(t.toString(), "Updated".equals(t.getDescription()));
        assertTrue(t.toString(), !t.isEnabled());

        memberren = definitionsService.getMemberTriggers(TENANT, "group-trigger", false);
        assertTrue(memberren != null);
        assertEquals(1, memberren.size());

        memberren = definitionsService.getMemberTriggers(TENANT, "group-trigger", true);
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

        definitionsService.removeGroupTrigger(TENANT, "group-trigger", true, true);
        t = definitionsService.getTrigger(TENANT, "group-trigger");
        assertNull(t);
        t = definitionsService.getTrigger(TENANT, "member-1-trigger");
        assertNotNull(t);
        t = definitionsService.getTrigger(TENANT, "member-2-trigger");
        assertNotNull(t);

        definitionsService.removeTrigger(TENANT, "member-1-trigger");
        t = definitionsService.getTrigger(TENANT, "member-1-trigger");
        assertNull(t);
        definitionsService.removeTrigger(TENANT, "member-2-trigger");
        t = definitionsService.getTrigger(TENANT, "member-2-trigger");
        assertNull(t);
    }

    @Test
    public void test0021GroupCondition() throws Exception {
        System.out.println("test0021GroupCondition...");

        Trigger t = definitionsService.getTrigger(TENANT, "trigger-7");
        assertNotNull(t);

        t = copyTrigger(t, "group-trigger");
        assertNotNull(t);

        Map<String, String> dataIdMap = new HashMap<>(1);
        dataIdMap.put("NumericData-Token", "NumericData-01");
        Trigger nt1 = definitionsService.addMemberTrigger(TENANT, t.getId(), "member-1-trigger", "Member-1",
                null, null, null, dataIdMap);
        assertNotNull(nt1);

        dataIdMap.put("NumericData-Token", "NumericData-02");
        Trigger nt2 = definitionsService.addMemberTrigger(TENANT, t.getId(), "member-2-trigger", "Member-2",
                null, null, null, dataIdMap);
        assertNotNull(nt2);

        Collection<Condition> groupConditions = definitionsService.getTriggerConditions(TENANT, "group-trigger",
                null);
        assertNotNull(groupConditions);
        assertEquals(1, groupConditions.size());

        groupConditions = new ArrayList<>(groupConditions);

        CompareCondition compareCondition = new CompareCondition(TENANT, t.getId(), Mode.FIRING, "Data1Id-Token",
                Operator.LT,
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

        Collection<Condition> conditionSet = definitionsService.setGroupConditions(TENANT, "group-trigger",
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

        Collection<Trigger> members = definitionsService.getMemberTriggers(TENANT, "group-trigger", true);
        assertTrue(members != null);
        assertEquals(2, members.size());
        for (Trigger member : members) {
            conditionSet = definitionsService.getTriggerConditions(TENANT, member.getId(), null);
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
        conditionSet = definitionsService.setGroupConditions(TENANT, "group-trigger", Mode.FIRING,
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

        members = definitionsService.getMemberTriggers(TENANT, "group-trigger", true);
        assertTrue(members != null);
        assertEquals(2, members.size());
        for (Trigger member : members) {
            conditionSet = definitionsService.getTriggerConditions(TENANT, member.getId(), null);
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

        conditionSet = definitionsService.setGroupConditions(TENANT, "group-trigger", Mode.FIRING,
                groupConditions, dataIdMemberMap);
        assertNotNull(conditionSet);
        assertEquals(1, conditionSet.size());
        ci = conditionSet.iterator();
        c = ci.next();
        assertTrue(c.toString(), Condition.Type.COMPARE != c.getType());

        members = definitionsService.getMemberTriggers(TENANT, "group-trigger", true);
        assertTrue(members != null);
        assertEquals(2, members.size());
        for (Trigger member : members) {
            conditionSet = definitionsService.getTriggerConditions(TENANT, member.getId(), null);
            assertEquals(1, conditionSet.size());
            ci = conditionSet.iterator();
            c = ci.next();
            assertTrue(c.toString(), Condition.Type.COMPARE != c.getType());
        }

        definitionsService.removeGroupTrigger(TENANT, "group-trigger", false, false);

        t = definitionsService.getTrigger(TENANT, "group-trigger");
        assertNull(t);
        t = definitionsService.getTrigger(TENANT, "member-1-trigger");
        assertNull(t);
        t = definitionsService.getTrigger(TENANT, "member-2-trigger");
        assertNull(t);
    }

    @Test
    public void test0022GroupDampening() throws Exception {
        System.out.println("test0022GroupDampening...");

        Trigger t = definitionsService.getTrigger(TENANT, "trigger-7");
        assertNotNull(t);

        t = copyTrigger(t, "group-trigger");
        assertNotNull(t);

        Map<String, String> dataIdMap = new HashMap<>(1);
        dataIdMap.put("NumericData-Token", "NumericData-01");

        Trigger nt1 = definitionsService.addMemberTrigger(TENANT, t.getId(), "member-1-trigger", "Member-1",
                null, null, null, dataIdMap);
        assertNotNull(nt1);
        dataIdMap.put("NumericData-Token", "NumericData-02");
        Trigger nt2 = definitionsService.addMemberTrigger(TENANT, t.getId(), "member-2-trigger", "Member-2",
                null, null, null, dataIdMap);
        assertNotNull(nt2);

        Dampening groupDampening = Dampening.forStrict(TENANT, "group-trigger", Mode.FIRING, 10);

        Dampening d = definitionsService.addGroupDampening(TENANT, groupDampening);
        assertNotNull(d);

        Collection<Dampening> ds = definitionsService.getTriggerDampenings(TENANT, "group-trigger", null);
        assertEquals(1, ds.size());
        d = ds.iterator().next();
        assertEquals(d.toString(), t.getId(), d.getTriggerId());
        assertEquals(d.toString(), Mode.FIRING, d.getTriggerMode());
        assertEquals(d.toString(), Dampening.Type.STRICT, d.getType());
        assertEquals(d.toString(), 10, d.getEvalTrueSetting());

        Collection<Trigger> memberren = definitionsService.getMemberTriggers(TENANT, "group-trigger", true);
        assertTrue(memberren != null);
        assertEquals(2, memberren.size());
        for (Trigger member : memberren) {
            ds = definitionsService.getTriggerDampenings(TENANT, member.getId(), null);
            assertEquals(1, ds.size());
            d = ds.iterator().next();
            assertEquals(d.toString(), member.getId(), d.getTriggerId());
            assertEquals(d.toString(), Mode.FIRING, d.getTriggerMode());
            assertEquals(d.toString(), Dampening.Type.STRICT, d.getType());
            assertEquals(d.toString(), 10, d.getEvalTrueSetting());
        }

        groupDampening = Dampening.forRelaxedCount(TENANT, "group-trigger", Mode.FIRING, 5, 10);
        d = definitionsService.updateGroupDampening(TENANT, groupDampening);
        assertNotNull(d);

        ds = definitionsService.getTriggerDampenings(TENANT, "group-trigger", null);
        assertEquals(1, ds.size());
        d = ds.iterator().next();
        assertEquals(d.toString(), t.getId(), d.getTriggerId());
        assertEquals(d.toString(), Mode.FIRING, d.getTriggerMode());
        assertEquals(d.toString(), Dampening.Type.RELAXED_COUNT, d.getType());
        assertEquals(d.toString(), 5, d.getEvalTrueSetting());
        assertEquals(d.toString(), 10, d.getEvalTotalSetting());

        memberren = definitionsService.getMemberTriggers(TENANT, "group-trigger", true);
        assertTrue(memberren != null);
        assertEquals(2, memberren.size());
        for (Trigger member : memberren) {
            ds = definitionsService.getTriggerDampenings(TENANT, member.getId(), null);
            assertEquals(1, ds.size());
            d = ds.iterator().next();
            assertEquals(d.toString(), member.getId(), d.getTriggerId());
            assertEquals(d.toString(), Mode.FIRING, d.getTriggerMode());
            assertEquals(d.toString(), Dampening.Type.RELAXED_COUNT, d.getType());
            assertEquals(d.toString(), 5, d.getEvalTrueSetting());
            assertEquals(d.toString(), 10, d.getEvalTotalSetting());
        }

        definitionsService.removeGroupDampening(TENANT, groupDampening.getDampeningId());
        ds = definitionsService.getTriggerDampenings(TENANT, "group-trigger", null);
        assertTrue(ds.isEmpty());

        memberren = definitionsService.getMemberTriggers(TENANT, "group-trigger", true);
        assertTrue(memberren != null);
        assertEquals(2, memberren.size());
        for (Trigger member : memberren) {
            ds = definitionsService.getTriggerDampenings(TENANT, member.getId(), null);
            assertTrue(ds.isEmpty());
        }

        definitionsService.removeGroupTrigger(TENANT, "group-trigger", false, false);

        t = definitionsService.getTrigger(TENANT, "group-trigger");
        assertNull(t);
        t = definitionsService.getTrigger(TENANT, "member-1-trigger");
        assertNull(t);
        t = definitionsService.getTrigger(TENANT, "member-2-trigger");
        assertNull(t);
    }

    @Test
    public void test0030BasicTags() throws Exception {
        System.out.println("test0030BasicTags...");

        Trigger t = definitionsService.getTrigger(TENANT, "trigger-1");
        assertNotNull(t);

        Collection<Condition> cs = definitionsService.getTriggerConditions(TENANT, t.getId(), null);
        assertTrue(cs.toString(), cs.size() == 1);
        Condition c = cs.iterator().next();

        Map<String, String> tags = new HashMap<>(t.getTags());
        tags.put("testname", "testvalue");
        t.setTags(tags);
        Map<String, String> newTag = new HashMap<>(1);
        definitionsService.updateTrigger(TENANT, t);

        t = definitionsService.getTrigger(TENANT, "trigger-1");
        assertEquals(3, t.getTags().size());
        assertEquals("tvalue1", t.getTags().get("tname1"));
        assertEquals("tvalue2", t.getTags().get("tname2"));
        assertEquals("testvalue", t.getTags().get("testname"));

        TriggersCriteria criteria = new TriggersCriteria();
        criteria.setTags(tags);
        tags.clear();
        tags.put("testname", "bogus");
        Collection<Trigger> triggers = definitionsService.getTriggers(TENANT, criteria, null);
        assertEquals(0, triggers.size());
        tags.clear();
        tags.put("bogus", "testvalue");
        triggers = definitionsService.getTriggers(TENANT, criteria, null);
        assertEquals(0, triggers.size());
        tags.clear();
        tags.put("testname", "testvalue");
        triggers = definitionsService.getTriggers(TENANT, criteria, null);
        assertEquals(1, triggers.size());
        tags.clear();
        tags.put("testname", "*");
        triggers = definitionsService.getTriggers(TENANT, criteria, null);
        assertEquals(1, triggers.size());
    }

    @Test
    public void test0035PagingTriggers() throws Exception {
        System.out.println("test0035PagingTriggers...");

        List<Trigger> result = definitionsService.getTriggers(TENANT, null, null);
        assertEquals(9, result.size());

        /*
            Ordering and paging by Id
         */
        Pager pager = Pager.builder().withPageSize(5).withStartPage(0)
                .orderByAscending(TriggerComparator.Field.ID.getName()).build();

        String first;
        String last;

        //System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        Page<Trigger> page = definitionsService.getTriggers(TENANT, null, pager);
        //System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        first = page.get(0).getId();

        assertEquals(9, page.getTotalSize());
        assertEquals(5, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            //System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = definitionsService.getTriggers(TENANT, null, pager);
            //System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(4, page.size());

        last = page.get(2).getId();

        //System.out.println("first trigger: " + first + " last trigger: " + last);

        assertTrue(first.compareTo(last) < 0);

        pager = Pager.builder().withPageSize(5).withStartPage(0)
                .orderByDescending(TriggerComparator.Field.ID.getName()).build();

        //System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        page = definitionsService.getTriggers(TENANT, null, pager);
        //System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        first = page.get(0).getId();

        assertEquals(9, page.getTotalSize());
        assertEquals(5, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            //System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = definitionsService.getTriggers(TENANT, null, pager);
            //System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(4, page.size());

        last = page.get(2).getId();

        //System.out.println("first alert: " + first + " last alert: " + last);

        assertTrue(first.compareTo(last) > 0);

        /*
            Ordering and paging by description
         */
        pager = Pager.builder().withPageSize(5).withStartPage(0)
                .orderByAscending(TriggerComparator.Field.DESCRIPTION.getName()).build();

        //System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        page = definitionsService.getTriggers(TENANT, null, pager);
        //System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        first = page.get(0).getDescription();

        assertEquals(9, page.getTotalSize());
        assertEquals(5, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            //System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = definitionsService.getTriggers(TENANT, null, pager);
            //System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(4, page.size());

        last = page.get(2).getDescription();

        //System.out.println("first trigger: " + first + " last trigger: " + last);

        assertTrue(first.compareTo(last) < 0);

        pager = Pager.builder().withPageSize(5).withStartPage(0)
                .orderByDescending(TriggerComparator.Field.DESCRIPTION.getName()).build();

        //System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        page = definitionsService.getTriggers(TENANT, null, pager);
        //System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        first = page.get(0).getDescription();

        assertEquals(9, page.getTotalSize());
        assertEquals(5, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            //System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = definitionsService.getTriggers(TENANT, null, pager);
            System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(4, page.size());

        last = page.get(2).getDescription();

        //System.out.println("first alert: " + first + " last alert: " + last);

        assertTrue(first.compareTo(last) > 0);

        /*
            Ordering and paging by name
         */
        pager = Pager.builder().withPageSize(5).withStartPage(0)
                .orderByAscending(TriggerComparator.Field.NAME.getName()).build();

        //System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        page = definitionsService.getTriggers(TENANT, null, pager);
        //System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        first = page.get(0).getName();

        assertEquals(9, page.getTotalSize());
        assertEquals(5, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            //System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = definitionsService.getTriggers(TENANT, null, pager);
            //System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(4, page.size());

        last = page.get(2).getName();

        //System.out.println("first trigger: " + first + " last trigger: " + last);

        assertTrue(first.compareTo(last) < 0);

        pager = Pager.builder().withPageSize(5).withStartPage(0)
                .orderByDescending(TriggerComparator.Field.NAME.getName()).build();

        //System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        page = definitionsService.getTriggers(TENANT, null, pager);
        //System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        first = page.get(0).getName();

        assertEquals(9, page.getTotalSize());
        assertEquals(5, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            //System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = definitionsService.getTriggers(TENANT, null, pager);
            //System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(4, page.size());

        last = page.get(2).getName();

        //System.out.println("first alert: " + first + " last alert: " + last);

        assertTrue(first.compareTo(last) > 0);
    }

    @Test
    public void test0040BasicAlert() throws Exception {
        System.out.println("test0040BasicAlert...");

        Trigger t = definitionsService.getTrigger(TENANT, "trigger-1");
        assertNotNull(t);

        Collection<Condition> cs = definitionsService.getTriggerConditions(TENANT, t.getId(), null);
        assertTrue(cs.toString(), cs.size() == 1);

        ThresholdCondition threshold = (ThresholdCondition) cs.iterator().next();
        long dataTime = System.currentTimeMillis();
        Data data = Data.forNumeric(TENANT, "NumericData-01", dataTime, 5.0d);
        ThresholdConditionEval eval = new ThresholdConditionEval(threshold, data);
        Set<ConditionEval> evalSet = new HashSet<>();
        evalSet.add(eval);
        List<Set<ConditionEval>> evals = new ArrayList<>();
        evals.add(evalSet);
        Alert alert = new Alert(TENANT, t, evals);
        List<Alert> alerts = new ArrayList<>();
        alerts.add(alert);

        alertsService.addAlerts(alerts);

        // No filter
        List<Alert> result = alertsService.getAlerts(TENANT, null, null);
        assertEquals(result.toString(), 1, result.size());

        // Specific trigger
        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setTriggerId("trigger-1");
        result = alertsService.getAlerts(TENANT, criteria, null);
        assertTrue(result.toString(), result.size() == 1);

        criteria = new AlertsCriteria();
        List<String> triggerIds = new ArrayList<>();
        triggerIds.add("trigger-1");
        triggerIds.add("trigger-2");
        criteria.setTriggerIds(triggerIds);
        result = alertsService.getAlerts(TENANT, criteria, null);
        assertTrue(result.toString(), result.size() == 1);

        // No trigger
        criteria = new AlertsCriteria();
        criteria.setTriggerId("trigger-2");
        result = alertsService.getAlerts(TENANT, criteria, null);
        assertTrue(result.toString(), result.size() == 0);

        criteria = new AlertsCriteria();
        triggerIds = new ArrayList<>();
        triggerIds.add("trigger-2");
        triggerIds.add("trigger-3");
        criteria.setTriggerIds(triggerIds);
        result = alertsService.getAlerts(TENANT, criteria, null);
        assertTrue(result.toString(), result.size() == 0);

        // Specific time
        criteria = new AlertsCriteria();
        criteria.setStartTime(dataTime - 100);
        criteria.setEndTime(dataTime + 100);
        result = alertsService.getAlerts(TENANT, criteria, null);
        assertTrue(result.toString(), result.size() == 1);

        // Out of time interval
        criteria = new AlertsCriteria();
        criteria.setStartTime(dataTime + 10000);
        criteria.setEndTime(dataTime + 20000);
        result = alertsService.getAlerts(TENANT, criteria, null);
        assertTrue(result.toString(), result.size() == 0);

        // Using tags
        criteria = new AlertsCriteria();
        criteria.addTag("tname1", "*");
        result = alertsService.getAlerts(TENANT, criteria, null);
        assertTrue(result.toString(), result.size() == 1);

        // More specific tags
        criteria = new AlertsCriteria();
        criteria.addTag("tname2", "tvalue2");
        result = alertsService.getAlerts(TENANT, criteria, null);
        assertTrue(result.toString(), result.size() == 1);

        // Using alertId
        criteria = new AlertsCriteria();
        criteria.setAlertId(alert.getAlertId());
        result = alertsService.getAlerts(TENANT, criteria, null);
        assertTrue(result.toString(), result.size() == 1);

        // Using status
        criteria = new AlertsCriteria();
        criteria.setStatus(alert.getStatus());
        result = alertsService.getAlerts(TENANT, criteria, null);
        assertTrue(result.toString(), result.size() == 1);

        criteria = new AlertsCriteria();
        criteria.setStatus(Alert.Status.RESOLVED);
        result = alertsService.getAlerts(TENANT, criteria, null);
        assertTrue(result.toString(), result.size() == 0);

        // Combine triggerId and ctime
        criteria = new AlertsCriteria();
        criteria.setTriggerId(alert.getTriggerId());
        criteria.setStartTime(dataTime - 100);
        result = alertsService.getAlerts(TENANT, criteria, null);
        assertTrue(result.toString(), result.size() == 1);

        // Combine triggerId, ctime and alertsId
        criteria = new AlertsCriteria();
        criteria.setTriggerId(alert.getTriggerId());
        criteria.setStartTime(dataTime - 100);
        criteria.setAlertId(alert.getAlertId());
        result = alertsService.getAlerts(TENANT, criteria, null);
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
        result = alertsService.getAlerts(TENANT, criteria, null);
        assertTrue(result.toString(), result.size() == 0);

        criteria = new AlertsCriteria();
        criteria.setTriggerId(alert.getTriggerId());
        int numDeleted = alertsService.deleteAlerts(TENANT, criteria);
        assertEquals(1, numDeleted);
    }

    @Test
    public void test0050PagingAlerts() throws Exception {
        System.out.println("test0050PagingAlerts...");

        Trigger t = definitionsService.getTrigger(TENANT, "trigger-6");
        assertNotNull(t);

        Collection<Condition> cs = definitionsService.getTriggerConditions(TENANT, t.getId(), null);
        assertTrue(cs.toString(), cs.size() == 1);

        AvailabilityCondition availability = (AvailabilityCondition) cs.iterator().next();

        List<Alert> alerts = new ArrayList<>();

        for (int i = 0; i < 107; i++) {
            long dataTime = System.currentTimeMillis();
            Data data = Data.forAvailability(TENANT, "Availability-01", dataTime, AvailabilityType.DOWN);
            AvailabilityConditionEval eval = new AvailabilityConditionEval(availability, data);
            Set<ConditionEval> evalSet = new HashSet<>();
            evalSet.add(eval);
            List<Set<ConditionEval>> evals = new ArrayList<>();
            evals.add(evalSet);
            Alert alert = new Alert(TENANT, t, evals);
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

        List<Alert> result = alertsService.getAlerts(TENANT, null, null);
        assertEquals(107, result.size());

        /*
            Ordering and paging by alertId
         */
        Pager pager = Pager.builder().withPageSize(10).withStartPage(0)
                .orderByAscending(AlertComparator.Field.ALERT_ID.getText()).build();

        String firstAlertId;
        String lastAlertId;

        //System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        Page<Alert> page = alertsService.getAlerts(TENANT, null, pager);
        //System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        firstAlertId = page.get(0).getAlertId();

        assertEquals(107, page.getTotalSize());
        assertEquals(10, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            //System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = alertsService.getAlerts(TENANT, null, pager);
            //System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(7, page.size());

        lastAlertId = page.get(6).getAlertId();

        //System.out.println("first alert: " + firstAlertId + " last alert: " + lastAlertId);

        assertTrue(firstAlertId.compareTo(lastAlertId) < 0);

        pager = Pager.builder().withPageSize(10).withStartPage(0)
                .orderByDescending(AlertComparator.Field.ALERT_ID.getText()).build();

        //System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        page = alertsService.getAlerts(TENANT, null, pager);
        //System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        firstAlertId = page.get(0).getAlertId();

        assertEquals(107, page.getTotalSize());
        assertEquals(10, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            //System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = alertsService.getAlerts(TENANT, null, pager);
            //System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(7, page.size());

        lastAlertId = page.get(6).getAlertId();

        //System.out.println("first alert: " + firstAlertId + " last alert: " + lastAlertId);

        assertTrue(firstAlertId.compareTo(lastAlertId) > 0);

        /*
            Ordering and paging by ctime
         */
        pager = Pager.builder().withPageSize(10).withStartPage(0)
                .orderByAscending(AlertComparator.Field.CTIME.getText()).build();

        //System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        page = alertsService.getAlerts(TENANT, null, pager);
        //System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        long firstCtime = page.get(0).getCtime();

        assertEquals(107, page.getTotalSize());
        assertEquals(10, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            //System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = alertsService.getAlerts(TENANT, null, pager);
            //System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(7, page.size());

        long lastCtime = page.get(6).getCtime();

        //System.out.println("first ctime: " + firstCtime + " last ctime: " + lastCtime);

        assertTrue(firstCtime < lastCtime);

        pager = Pager.builder().withPageSize(10).withStartPage(0)
                .orderByDescending(AlertComparator.Field.CTIME.getText()).build();

        //System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        page = alertsService.getAlerts(TENANT, null, pager);
        //System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        firstCtime = page.get(0).getCtime();

        assertEquals(107, page.getTotalSize());
        assertEquals(10, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            //System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = alertsService.getAlerts(TENANT, null, pager);
            //System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(7, page.size());

        lastCtime = page.get(6).getCtime();

        //System.out.println("first ctime: " + firstCtime + " last ctime: " + lastCtime);

        assertTrue(firstCtime > lastCtime);

        /*
            Ordering and paging by severity
         */
        pager = Pager.builder().withPageSize(10).withStartPage(0)
                .orderByAscending(AlertComparator.Field.SEVERITY.getText()).build();

        //System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        page = alertsService.getAlerts(TENANT, null, pager);
        //System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        Severity firstSeverity = page.get(0).getSeverity();

        assertEquals(107, page.getTotalSize());
        assertEquals(10, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            //System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = alertsService.getAlerts(TENANT, null, pager);
            //System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(7, page.size());

        Severity lastSeverity = page.get(6).getSeverity();

        //System.out.println("first severity: " + firstSeverity + " last severity: " + lastSeverity);

        assertTrue(firstSeverity.compareTo(lastSeverity) < 0);

        pager = Pager.builder().withPageSize(10).withStartPage(0)
                .orderByDescending(AlertComparator.Field.SEVERITY.getText()).build();

        //System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        page = alertsService.getAlerts(TENANT, null, pager);
        //System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        firstSeverity = page.get(0).getSeverity();

        assertEquals(107, page.getTotalSize());
        assertEquals(10, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            //System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = alertsService.getAlerts(TENANT, null, pager);
            //System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(7, page.size());

        lastSeverity = page.get(6).getSeverity();

        //System.out.println("first severity: " + firstSeverity + " last severity: " + lastSeverity);

        assertTrue(firstSeverity.compareTo(lastSeverity) > 0);

        /*
            Ordering and paging by status
         */
        pager = Pager.builder().withPageSize(10).withStartPage(0)
                .orderByAscending(AlertComparator.Field.STATUS.getText()).build();

        //System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        page = alertsService.getAlerts(TENANT, null, pager);
        //System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        Alert.Status firstStatus = page.get(0).getStatus();

        assertEquals(107, page.getTotalSize());
        assertEquals(10, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            //System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = alertsService.getAlerts(TENANT, null, pager);
            //System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(7, page.size());

        Alert.Status lastStatus = page.get(6).getStatus();

        //System.out.println("first status: " + firstStatus + " last status: " + lastStatus);

        assertTrue(firstStatus.compareTo(lastStatus) < 0);

        pager = Pager.builder().withPageSize(10).withStartPage(0)
                .orderByDescending(AlertComparator.Field.STATUS.getText()).build();

        //System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        page = alertsService.getAlerts(TENANT, null, pager);
        //System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        firstStatus = page.get(0).getStatus();

        assertEquals(107, page.getTotalSize());
        assertEquals(10, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            //System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = alertsService.getAlerts(TENANT, null, pager);
            //System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(7, page.size());

        lastStatus = page.get(6).getStatus();

        //System.out.println("first status: " + firstStatus + " last status: " + lastStatus);

        assertTrue(firstStatus.compareTo(lastStatus) > 0);
    }

    public void test0051SortOnAlertsContext() throws Exception {
        Trigger t = definitionsService.getTrigger(TENANT, "trigger-6");
        assertNotNull(t);

        Collection<Condition> cs = definitionsService.getTriggerConditions(TENANT, t.getId(), null);
        assertTrue(cs.toString(), cs.size() == 1);

        AvailabilityCondition availability = (AvailabilityCondition) cs.iterator().next();

        List<Alert> alerts = new ArrayList<>();

        for (int i = 0; i < 107; i++) {
            long dataTime = System.currentTimeMillis();
            Data data = Data.forAvailability(TENANT, "Availability-01", dataTime, AvailabilityType.DOWN);
            AvailabilityConditionEval eval = new AvailabilityConditionEval(availability, data);
            Set<ConditionEval> evalSet = new HashSet<>();
            evalSet.add(eval);
            List<Set<ConditionEval>> evals = new ArrayList<>();
            evals.add(evalSet);
            Alert alert = new Alert(TENANT, t, evals);
            alert.getContext().put("random", String.valueOf(Math.random()));
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

        List<Alert> result = alertsService.getAlerts(TENANT, null, null);
        assertEquals(107, result.size());

        /*
            Ordering and paging by alertId
         */
        Pager pager = Pager.builder().withPageSize(10).withStartPage(0)
                .orderByAscending("context.random").build();

        String firstContext;
        String lastContext;

        //System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        Page<Alert> page = alertsService.getAlerts(TENANT, null, pager);
        //System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        firstContext = page.get(0).getContext().get("random");

        assertEquals(107, page.getTotalSize());
        assertEquals(10, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            //System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = alertsService.getAlerts(TENANT, null, pager);
            //System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(7, page.size());

        lastContext = page.get(6).getContext().get("random");

        //System.out.println("first alert: " + firstContext + " last alert: " + lastContext);

        assertTrue(firstContext.compareTo(lastContext) < 0);
    }

    @Test
    public void test0060BasicEvent() throws Exception {
        System.out.println("test0060BasicEvent...");

        Trigger t = definitionsService.getTrigger(TENANT, "trigger-8");
        assertNotNull(t);

        Collection<Condition> cs = definitionsService.getTriggerConditions(TENANT, t.getId(), null);
        assertTrue(cs.toString(), cs.size() == 1);

        ThresholdCondition threshold = (ThresholdCondition) cs.iterator().next();
        long dataTime = System.currentTimeMillis();
        Data data = Data.forNumeric(TENANT, "NumericData-01", dataTime, 5.0d);
        ThresholdConditionEval eval = new ThresholdConditionEval(threshold, data);
        Set<ConditionEval> evalSet = new HashSet<>();
        evalSet.add(eval);
        List<Set<ConditionEval>> evals = new ArrayList<>();
        evals.add(evalSet);
        Event event = new Event(TENANT, t, null, evals);
        List<Event> events = new ArrayList<>();
        events.add(event);

        alertsService.persistEvents(events);

        // No filter
        List<Event> result = alertsService.getEvents(TENANT, null, null);
        assertTrue(result.toString(), !result.isEmpty());

        // Specific trigger
        EventsCriteria criteria = new EventsCriteria();
        criteria.setTriggerId("trigger-8");
        result = alertsService.getEvents(TENANT, criteria, null);
        assertTrue(result.toString(), result.size() == 1);
        Event e = result.get(0);
        assertEquals(t, e.getTrigger());
        assertEquals(evals, e.getEvalSets());
        assertEquals(EventCategory.TRIGGER.name(), e.getCategory());

        criteria = new EventsCriteria();
        List<String> triggerIds = new ArrayList<>();
        triggerIds.add("trigger-8");
        triggerIds.add("trigger-9");
        criteria.setTriggerIds(triggerIds);
        result = alertsService.getEvents(TENANT, criteria, null);
        assertTrue(result.toString(), result.size() == 1);

        // No trigger
        criteria = new EventsCriteria();
        criteria.setTriggerId("trigger-9");
        result = alertsService.getEvents(TENANT, criteria, null);
        assertEquals(0, result.size());

        criteria = new EventsCriteria();
        triggerIds = new ArrayList<>();
        triggerIds.add("trigger-9");
        triggerIds.add("trigger-10");
        criteria.setTriggerIds(triggerIds);
        result = alertsService.getEvents(TENANT, criteria, null);
        assertEquals(0, result.size());

        // Specific time
        criteria = new EventsCriteria();
        criteria.setStartTime(dataTime - 100);
        criteria.setEndTime(dataTime + 100);
        result = alertsService.getEvents(TENANT, criteria, null);
        assertEquals(1, result.size());

        // Out of time interval
        criteria = new EventsCriteria();
        criteria.setStartTime(dataTime + 10000);
        criteria.setEndTime(dataTime + 20000);
        result = alertsService.getEvents(TENANT, criteria, null);
        assertEquals(0, result.size());

        // Using tags
        criteria = new EventsCriteria();
        criteria.addTag("trigger8-name1", "*");
        result = alertsService.getEvents(TENANT, criteria, null);
        assertEquals(1, result.size());

        // More specific tags
        criteria = new EventsCriteria();
        criteria.addTag("trigger8-name2", "value2");
        result = alertsService.getEvents(TENANT, criteria, null);
        assertEquals(1, result.size());

        // Using eventId
        criteria = new EventsCriteria();
        criteria.setEventId(event.getId());
        result = alertsService.getEvents(TENANT, criteria, null);
        assertEquals(1, result.size());

        // Using category
        criteria = new EventsCriteria();
        criteria.setCategory(event.getCategory());
        result = alertsService.getEvents(TENANT, criteria, null);
        assertEquals(1, result.size());

        // Using bad category
        criteria = new EventsCriteria();
        criteria.setCategory("UNKNOWN");
        result = alertsService.getEvents(TENANT, criteria, null);
        assertEquals(0, result.size());

        // Combine triggerId and ctime
        criteria = new EventsCriteria();
        criteria.setTriggerId(event.getTrigger().getId());
        criteria.setStartTime(dataTime - 100);
        result = alertsService.getEvents(TENANT, criteria, null);
        assertEquals(1, result.size());

        // Combine triggerId, ctime and alertsId
        criteria = new EventsCriteria();
        criteria.setTriggerId(event.getTrigger().getId());
        criteria.setStartTime(dataTime - 100);
        criteria.setEventId(event.getId());
        result = alertsService.getEvents(TENANT, criteria, null);
        assertEquals(1, result.size());

        // Combine triggerIds, ctime and category
        criteria = new EventsCriteria();
        ArrayList<String> triggersIds = new ArrayList<>();
        triggersIds.add(event.getTrigger().getId());
        criteria.setTriggerIds(triggersIds);
        criteria.setStartTime(dataTime - 100);
        HashSet<String> categories = new HashSet<>();
        categories.add("UNKNOWN");
        criteria.setCategories(categories);
        result = alertsService.getEvents(TENANT, criteria, null);
        assertEquals(0, result.size());

        criteria = new EventsCriteria();
        criteria.setTriggerId(event.getTrigger().getId());
        int numDeleted = alertsService.deleteEvents(TENANT, criteria);
        assertEquals(1, numDeleted);
    }

    @Test
    public void test0070PagingEvents() throws Exception {
        System.out.println("test0070PagingEvents...");

        Trigger t = definitionsService.getTrigger(TENANT, "trigger-8");
        assertNotNull(t);

        Collection<Condition> cs = definitionsService.getTriggerConditions(TENANT, t.getId(), null);
        assertTrue(cs.toString(), cs.size() == 1);

        ThresholdCondition threshold = (ThresholdCondition) cs.iterator().next();

        List<Event> events = new ArrayList<>();

        for (int i = 0; i < 107; i++) {
            long dataTime = System.currentTimeMillis();
            Data data = Data.forNumeric(TENANT, "NumericData-01", dataTime, 5.0d + i);
            ThresholdConditionEval eval = new ThresholdConditionEval(threshold, data);
            Set<ConditionEval> evalSet = new HashSet<>();
            evalSet.add(eval);
            List<Set<ConditionEval>> evals = new ArrayList<>();
            evals.add(evalSet);
            Event event = new Event(TENANT, t, null, evals);
            int iEvent = i % 3;
            switch (iEvent) {
                case 2:
                    event.setCategory("C2");
                    event.setText("T2");
                    break;
                case 1:
                    event.setCategory("C1");
                    event.setText("T1");
                    break;
                case 0:
                    event.setCategory("C0");
                    event.setText("T0");
            }
            events.add(event);
            Thread.sleep(2); // events for the same trigger must not come in at the same exact ms.
        }

        alertsService.persistEvents(events);

        List<Event> result = alertsService.getEvents(TENANT, null, null);
        assertEquals(107, result.size());

        /*
            Ordering and paging by Id
         */
        Pager pager = Pager.builder().withPageSize(10).withStartPage(0)
                .orderByAscending(EventComparator.Field.ID.getName()).build();

        String firstEventId;
        String lastEventId;

        //System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        Page<Event> page = alertsService.getEvents(TENANT, null, pager);
        //System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        firstEventId = page.get(0).getId();

        assertEquals(107, page.getTotalSize());
        assertEquals(10, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            //System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = alertsService.getEvents(TENANT, null, pager);
            //System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(7, page.size());

        lastEventId = page.get(6).getId();

        //System.out.println("first event: " + firstEventId + " last event: " + lastEventId);

        assertTrue(firstEventId.compareTo(lastEventId) < 0);

        pager = Pager.builder().withPageSize(10).withStartPage(0)
                .orderByDescending(EventComparator.Field.ID.getName()).build();

        //System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        page = alertsService.getEvents(TENANT, null, pager);
        //System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        firstEventId = page.get(0).getId();

        assertEquals(107, page.getTotalSize());
        assertEquals(10, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            //System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = alertsService.getEvents(TENANT, null, pager);
            //System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(7, page.size());

        lastEventId = page.get(6).getId();

        //System.out.println("first eventt: " + firstEventId + " last event: " + lastEventId);

        assertTrue(firstEventId.compareTo(lastEventId) > 0);

        /*
            Ordering and paging by ctime
         */
        pager = Pager.builder().withPageSize(10).withStartPage(0)
                .orderByAscending(EventComparator.Field.CTIME.getName()).build();

        //System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        page = alertsService.getEvents(TENANT, null, pager);
        //System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        long firstCtime = page.get(0).getCtime();

        assertEquals(107, page.getTotalSize());
        assertEquals(10, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            //System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = alertsService.getEvents(TENANT, null, pager);
            //System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(7, page.size());

        long lastCtime = page.get(6).getCtime();

        //System.out.println("first ctime: " + firstCtime + " last ctime: " + lastCtime);

        assertTrue(firstCtime < lastCtime);

        pager = Pager.builder().withPageSize(10).withStartPage(0)
                .orderByDescending(EventComparator.Field.CTIME.getName()).build();

        //System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        page = alertsService.getEvents(TENANT, null, pager);
        //System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        firstCtime = page.get(0).getCtime();

        assertEquals(107, page.getTotalSize());
        assertEquals(10, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            //System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = alertsService.getEvents(TENANT, null, pager);
            //System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(7, page.size());

        lastCtime = page.get(6).getCtime();

        //System.out.println("first ctime: " + firstCtime + " last ctime: " + lastCtime);

        assertTrue(firstCtime > lastCtime);

        /*
            Ordering and paging by category
         */
        pager = Pager.builder().withPageSize(10).withStartPage(0)
                .orderByAscending(EventComparator.Field.CATEGORY.getName()).build();

        //System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        page = alertsService.getEvents(TENANT, null, pager);
        //System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        String firstCategory = page.get(0).getCategory();

        assertEquals(107, page.getTotalSize());
        assertEquals(10, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            //System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = alertsService.getEvents(TENANT, null, pager);
            //System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(7, page.size());

        String lastCategory = page.get(6).getCategory();

        //System.out.println("first category: " + firstCategory + " last category: " + lastCategory);

        assertTrue(firstCategory.compareTo(lastCategory) < 0);

        pager = Pager.builder().withPageSize(10).withStartPage(0)
                .orderByDescending(EventComparator.Field.CATEGORY.getName()).build();

        //System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        page = alertsService.getEvents(TENANT, null, pager);
        //System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        firstCategory = page.get(0).getCategory();

        assertEquals(107, page.getTotalSize());
        assertEquals(10, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            //System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = alertsService.getEvents(TENANT, null, pager);
            //System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(7, page.size());

        lastCategory = page.get(6).getCategory();

        //System.out.println("first category: " + firstCategory + " last category: " + lastCategory);

        assertTrue(firstCategory.compareTo(lastCategory) > 0);

        /*
            Ordering and paging by event text
         */
        pager = Pager.builder().withPageSize(10).withStartPage(0)
                .orderByAscending(EventComparator.Field.TEXT.getName()).build();

        //System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        page = alertsService.getEvents(TENANT, null, pager);
        //System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        String firstText = page.get(0).getText();

        assertEquals(107, page.getTotalSize());
        assertEquals(10, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            //System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = alertsService.getEvents(TENANT, null, pager);
            //System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(7, page.size());

        String lastText = page.get(6).getText();

        //System.out.println("first status: " + firstText + " last status: " + lastText);

        assertTrue(firstText.compareTo(lastText) < 0);

        pager = Pager.builder().withPageSize(10).withStartPage(0)
                .orderByDescending(EventComparator.Field.TEXT.getName()).build();

        //System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        page = alertsService.getEvents(TENANT, null, pager);
        //System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        firstText = page.get(0).getText();

        assertEquals(107, page.getTotalSize());
        assertEquals(10, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            //System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = alertsService.getEvents(TENANT, null, pager);
            //System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(7, page.size());

        lastText = page.get(6).getText();

        //System.out.println("first text: " + firstText + " last text: " + lastText);

        assertTrue(firstText.compareTo(lastText) > 0);

        //cleanup
        EventsCriteria criteria = new EventsCriteria();
        criteria.setTriggerId(t.getId());
        int numDeleted = alertsService.deleteEvents(TENANT, criteria);
        assertEquals(107, numDeleted);

    }

    public void test0080BasicActionsHistory() throws Exception {
        for (int i = 0; i < 107; i++) {
            Event testEvent = new Event(TENANT, "test-trigger", "test-dataid", "test-category", "test-text");
            TriggerAction triggerAction = new TriggerAction(TENANT, "testplugin", "send-to-this-group");
            Thread.sleep(2);
            actionsService.send(triggerAction, testEvent);
        }

        List<Action> actions = actionsService.getActions(TENANT, null, null);
        assertEquals(107, actions.size());
    }

    @Test
    public void test0090SearchActionsHistory() throws Exception {
        System.out.println("test0090SearchActionsHistory...");

        for (int i = 0; i < 10; i++) {
            Alert testAlert = new Alert();
            testAlert.setTenantId(TENANT);
            testAlert.setId("test-trigger");
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
            /*
                ActionsService.updateResult() insert an action but don't send them to the plugins architecture.
                Used for testing the persistence flow.
             */
            actionsService.updateResult(action1);
            actionsService.updateResult(action2);
            actionsService.updateResult(action3);
            actionsService.updateResult(action4);
        }

        System.out.print("Actions are asynchronous. Give them some time.");
        for (int i = 0; i < 30; i++) {
            System.out.print(".");
            Thread.sleep(200);
        }

        List<Action> actions = actionsService.getActions(TENANT, null, null);
        assertEquals(10 * 4, actions.size());

        ActionsCriteria criteria = new ActionsCriteria();
        criteria.setStartTime(2L);

        actions = actionsService.getActions(TENANT, criteria, null);
        assertEquals(8 * 4, actions.size());

        criteria.setStartTime(2L);
        criteria.setEndTime(3L);

        actions = actionsService.getActions(TENANT, criteria, null);
        assertEquals(2 * 4, actions.size());

        criteria = new ActionsCriteria();
        criteria.setActionPlugin("plugin1");

        actions = actionsService.getActions(TENANT, criteria, null);
        assertEquals(10 * 2, actions.size());

        criteria = new ActionsCriteria();
        criteria.setActionPlugins(Arrays.asList("plugin1", "plugin2"));

        actions = actionsService.getActions(TENANT, criteria, null);
        assertEquals(10 * 4, actions.size());

        criteria = new ActionsCriteria();
        criteria.setActionId("action1");
        actions = actionsService.getActions(TENANT, criteria, null);
        assertEquals(10 * 2, actions.size());

        criteria = new ActionsCriteria();
        criteria.setActionIds(Arrays.asList("action1", "action2"));
        actions = actionsService.getActions(TENANT, criteria, null);
        assertEquals(10 * 4, actions.size());

        criteria = new ActionsCriteria();
        criteria.setAlertId("test-alert1");
        actions = actionsService.getActions(TENANT, criteria, null);
        assertEquals(1 * 4, actions.size());

        criteria = new ActionsCriteria();
        criteria.setAlertIds(Arrays.asList("test-alert1", "test-alert2", "test-alert3"));
        actions = actionsService.getActions(TENANT, criteria, null);
        assertEquals(3 * 4, actions.size());

        criteria = new ActionsCriteria();
        criteria.setResult("result1");
        actions = actionsService.getActions(TENANT, criteria, null);
        assertEquals(10 * 1, actions.size());

        criteria = new ActionsCriteria();
        criteria.setResults(Arrays.asList("result1", "result2"));
        actions = actionsService.getActions(TENANT, criteria, null);
        assertEquals(10 * 2, actions.size());

        criteria = new ActionsCriteria();
        criteria.setStartTime(2L);
        criteria.setActionPlugin("plugin1");
        criteria.setActionId("action1");
        actions = actionsService.getActions(TENANT, criteria, null);
        assertEquals(8 * 1, actions.size());
    }

    @Test
    public void test00100PaginationActionsHistory() throws Exception {
        System.out.println(" test00100PaginationActionsHistory...");

        for (int i = 0; i < 103; i++) {
            Alert testAlert = new Alert();
            testAlert.setTenantId(TENANT);
            testAlert.setId("test-trigger");
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
            actionsService.updateResult(action1);
            actionsService.updateResult(action2);
            actionsService.updateResult(action3);
            actionsService.updateResult(action4);
        }

        System.out.print("Actions are asynchronous. Give them some time.");
        for (int i = 0; i < 30; i++) {
            System.out.print(".");
            Thread.sleep(200);
        }

        List<Action> actions = actionsService.getActions(TENANT, null, null);
        assertEquals(103 * 4, actions.size());

        Pager pager = Pager.builder().withPageSize(10).withStartPage(0)
                .orderByAscending(ActionComparator.Field.ALERT_ID.getText()).build();

        //System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        Page<Action> page = actionsService.getActions(TENANT, null, pager);
        //System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        Action firstAction = page.get(0);

        assertEquals(103 * 4, page.getTotalSize());
        assertEquals(10, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            //System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = actionsService.getActions(TENANT, null, pager);
            //System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(2, page.size());

        Action lastAction = page.get(1);

        assertTrue(firstAction.getEvent().getId().compareTo(lastAction.getEvent().getId()) < 0);

        pager = Pager.builder().withPageSize(10).withStartPage(0)
                .orderByDescending(ActionComparator.Field.RESULT.getText()).build();

        //System.out.println("1st Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
        page = actionsService.getActions(TENANT, null, pager);
        //System.out.println("1st Page size: " + page.size() + " totalSize: " + page.getTotalSize());

        firstAction = page.get(0);

        assertEquals(103 * 4, page.getTotalSize());
        assertEquals(10, page.size());

        while (pager.getEnd() < page.getTotalSize()) {
            pager = pager.nextPage();
            //System.out.println("Pager: " + pager + " pager.getEnd(): " + pager.getEnd());
            page = actionsService.getActions(TENANT, null, pager);
            //System.out.println("Page size: " + page.size() + " totalSize: " + page.getTotalSize());
        }

        assertEquals(2, page.size());

        lastAction = page.get(1);

        assertTrue(firstAction.getResult().compareTo(lastAction.getResult()) > 0);
    }

    @Test
    public void test0110ThinActionsHistory() throws Exception {
        System.out.println("test0110ThinActionsHistory...");

        for (int i = 0; i < 103; i++) {
            Alert testAlert = new Alert();
            testAlert.setTenantId(TENANT);
            testAlert.setId("test-trigger");
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
            actionsService.updateResult(action1);
            actionsService.updateResult(action2);
            actionsService.updateResult(action3);
            actionsService.updateResult(action4);
        }

        System.out.print("Actions are asynchronous. Give them some time.");
        for (int i = 0; i < 30; i++) {
            System.out.print(".");
            Thread.sleep(200);
        }

        ActionsCriteria criteria = new ActionsCriteria();
        criteria.setThin(true);
        List<Action> actions = actionsService.getActions(TENANT, criteria, null);
        assertEquals(103 * 4, actions.size());

        for (Action action : actions) {
            //System.out.println(action);
            assertNull(action.getEvent());
        }
    }

    @Test
    public void test0120BasicNotesOnAlert() throws Exception {
        System.out.println(" test0120BasicNotesOnAlert...");

        Trigger t = new Trigger("non-existence-trigger", "non-existence-trigger");
        Alert testAlert = new Alert(TENANT, t, null);

        alertsService.addAlerts(Collections.singletonList(testAlert));

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setTriggerId("non-existence-trigger");

        List<Alert> alerts = alertsService.getAlerts(TENANT, criteria, null);

        assertTrue(alerts != null && alerts.size() == 1);

        Alert updatedAlert = alerts.get(0);

        alertsService.addNote(TENANT, updatedAlert.getAlertId(), "user1", "notes1");
        alertsService.addNote(TENANT, updatedAlert.getAlertId(), "user2", "notes2");
        alertsService.addNote(TENANT, updatedAlert.getAlertId(), "user3", "notes3");

        Alert updatedAlertWithNotes = alertsService.getAlert(TENANT, updatedAlert.getAlertId(), false);

        assertTrue(updatedAlertWithNotes != null && updatedAlertWithNotes.getNotes().size() == 3);
    }

    @Test
    public void test0130AlertTags() throws Exception {
        System.out.println(" test0130Tags...");

        Trigger t = new Trigger("non-existence-trigger", "non-existence-trigger");
        t.addTag("TriggerTag1Name", "TriggerTag1Value");
        t.addTag("TriggerTag2Name", "TriggerTag2Value");

        Alert testAlert = new Alert(TENANT, t, null);

        alertsService.addAlerts(Collections.singletonList(testAlert));

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setThin(true);
        criteria.addTag("TriggerTag1Name", "TriggerTag1Value");

        List<Alert> alerts = alertsService.getAlerts(TENANT, criteria, null);
        assertTrue(alerts != null);
        assertEquals(1, alerts.size());
        Alert alert = alerts.get(0);
        assertEquals(2, alert.getTags().size());
        assertEquals("TriggerTag1Value", alert.getTags().get("TriggerTag1Name"));
        assertEquals("TriggerTag2Value", alert.getTags().get("TriggerTag2Name"));

        // make sure second tag also works for fetch
        criteria.getTags().clear();
        criteria.addTag("TriggerTag2Name", "TriggerTag2Value");

        alerts = alertsService.getAlerts(TENANT, criteria, null);
        assertTrue(alerts != null);
        assertEquals(1, alerts.size());
        alert = alerts.get(0);
        assertEquals(2, alert.getTags().size());
        assertEquals("TriggerTag1Value", alert.getTags().get("TriggerTag1Name"));
        assertEquals("TriggerTag2Value", alert.getTags().get("TriggerTag2Name"));

        // add non-trigger tags to the alert
        ArrayList<String> alertIds = new ArrayList<>(2);
        alertIds.add(alert.getAlertId());
        alertIds.add("bogus"); // non-existent alertIds should just get ignored

        Map<String, String> alertTags = new HashMap<>();
        alertTags.put("TriggerTag1Name", "TriggerTag1Value"); // it should be OK to re-apply an existing tag
        alertTags.put("AlertTag1Name", "AlertTag1Value");
        alertTags.put("AlertTag2Name", "AlertTag2Value");

        alertsService.addAlertTags(TENANT, alertIds, alertTags);

        // all four tags should now be searchable
        criteria.getTags().clear();
        criteria.addTag("TriggerTag1Name", "TriggerTag1Value");
        alerts = alertsService.getAlerts(TENANT, criteria, null);
        assertTrue(alerts != null);
        assertEquals(1, alerts.size());
        alert = alerts.get(0);
        assertEquals(4, alert.getTags().size());
        assertEquals("TriggerTag1Value", alert.getTags().get("TriggerTag1Name"));
        assertEquals("TriggerTag2Value", alert.getTags().get("TriggerTag2Name"));
        assertEquals("AlertTag1Value", alert.getTags().get("AlertTag1Name"));
        assertEquals("AlertTag2Value", alert.getTags().get("AlertTag2Name"));

        criteria.getTags().clear();
        criteria.addTag("TriggerTag2Name", "TriggerTag2Value");
        alerts = alertsService.getAlerts(TENANT, criteria, null);
        assertTrue(alerts != null);
        assertEquals(1, alerts.size());
        alert = alerts.get(0);
        assertEquals(4, alert.getTags().size());
        assertEquals("TriggerTag1Value", alert.getTags().get("TriggerTag1Name"));
        assertEquals("TriggerTag2Value", alert.getTags().get("TriggerTag2Name"));
        assertEquals("AlertTag1Value", alert.getTags().get("AlertTag1Name"));
        assertEquals("AlertTag2Value", alert.getTags().get("AlertTag2Name"));

        criteria.getTags().clear();
        criteria.addTag("AlertTag1Name", "AlertTag1Value");
        alerts = alertsService.getAlerts(TENANT, criteria, null);
        assertTrue(alerts != null);
        assertEquals(1, alerts.size());
        alert = alerts.get(0);
        assertEquals(4, alert.getTags().size());
        assertEquals("TriggerTag1Value", alert.getTags().get("TriggerTag1Name"));
        assertEquals("TriggerTag2Value", alert.getTags().get("TriggerTag2Name"));
        assertEquals("AlertTag1Value", alert.getTags().get("AlertTag1Name"));
        assertEquals("AlertTag2Value", alert.getTags().get("AlertTag2Name"));

        criteria.getTags().clear();
        criteria.addTag("AlertTag2Name", "AlertTag2Value");
        alerts = alertsService.getAlerts(TENANT, criteria, null);
        assertTrue(alerts != null);
        assertEquals(1, alerts.size());
        alert = alerts.get(0);
        assertEquals(4, alert.getTags().size());
        assertEquals("TriggerTag1Value", alert.getTags().get("TriggerTag1Name"));
        assertEquals("TriggerTag2Value", alert.getTags().get("TriggerTag2Name"));
        assertEquals("AlertTag1Value", alert.getTags().get("AlertTag1Name"));
        assertEquals("AlertTag2Value", alert.getTags().get("AlertTag2Name"));

        // Now, remove two tags
        Collection<String> doomedTags = new ArrayList<>();
        doomedTags.add("TriggerTag1Name");
        doomedTags.add("AlertTag1Name");
        doomedTags.add("Bogus");  // it should be OK to try and remove a non-existing tag

        alertsService.removeAlertTags(TENANT, alertIds, doomedTags);

        // Only two tags should now be searchable
        criteria.getTags().clear();
        criteria.addTag("TriggerTag1Name", "TriggerTag1Value");
        alerts = alertsService.getAlerts(TENANT, criteria, null);
        assertTrue(alerts != null);
        assertEquals(0, alerts.size());

        criteria.getTags().clear();
        criteria.addTag("TriggerTag2Name", "TriggerTag2Value");
        alerts = alertsService.getAlerts(TENANT, criteria, null);
        assertTrue(alerts != null);
        assertEquals(1, alerts.size());
        alert = alerts.get(0);
        assertEquals(2, alert.getTags().size());
        assertEquals("TriggerTag2Value", alert.getTags().get("TriggerTag2Name"));
        assertEquals("AlertTag2Value", alert.getTags().get("AlertTag2Name"));

        criteria.getTags().clear();
        criteria.addTag("AlertTag1Name", "AlertTag1Value");
        alerts = alertsService.getAlerts(TENANT, criteria, null);
        assertTrue(alerts != null);
        assertEquals(0, alerts.size());

        criteria.getTags().clear();
        criteria.addTag("AlertTag2Name", "AlertTag2Value");
        alerts = alertsService.getAlerts(TENANT, criteria, null);
        assertTrue(alerts != null);
        assertEquals(1, alerts.size());
        alert = alerts.get(0);
        assertEquals(2, alert.getTags().size());
        assertEquals("TriggerTag2Value", alert.getTags().get("TriggerTag2Name"));
        assertEquals("AlertTag2Value", alert.getTags().get("AlertTag2Name"));
    }

    @Test
    public void test0140EventTags() throws Exception {
        System.out.println(" test0130Tags...");

        Trigger t = new Trigger("non-existence-trigger", "non-existence-trigger");
        t.addTag("TriggerTag1Name", "TriggerTag1Value");
        t.addTag("TriggerTag2Name", "TriggerTag2Value");

        Event testEvent = new Event(TENANT, t, null, null);

        alertsService.persistEvents(Collections.singletonList(testEvent));

        EventsCriteria criteria = new EventsCriteria();
        criteria.setThin(true);
        criteria.addTag("TriggerTag1Name", "TriggerTag1Value");

        List<Event> events = alertsService.getEvents(TENANT, criteria, null);
        assertTrue(events != null);
        assertEquals(1, events.size());
        Event event = events.get(0);
        assertEquals(2, event.getTags().size());
        assertEquals("TriggerTag1Value", event.getTags().get("TriggerTag1Name"));
        assertEquals("TriggerTag2Value", event.getTags().get("TriggerTag2Name"));

        // make sure second tag also works for fetch
        criteria.getTags().clear();
        criteria.addTag("TriggerTag2Name", "TriggerTag2Value");

        events = alertsService.getEvents(TENANT, criteria, null);
        assertTrue(events != null);
        assertEquals(1, events.size());
        event = events.get(0);
        assertEquals(2, event.getTags().size());
        assertEquals("TriggerTag1Value", event.getTags().get("TriggerTag1Name"));
        assertEquals("TriggerTag2Value", event.getTags().get("TriggerTag2Name"));

        // add non-trigger tags to the event
        ArrayList<String> eventIds = new ArrayList<>(2);
        eventIds.add(event.getId());
        eventIds.add("bogus"); // non-existent eventIds should just get ignored

        Map<String, String> eventTags = new HashMap<>();
        eventTags.put("TriggerTag1Name", "TriggerTag1Value"); // it should be OK to re-apply an existing tag
        eventTags.put("EventTag1Name", "EventTag1Value");
        eventTags.put("EventTag2Name", "EventTag2Value");

        alertsService.addEventTags(TENANT, eventIds, eventTags);

        // all four tags should now be searchable
        criteria.getTags().clear();
        criteria.addTag("TriggerTag1Name", "TriggerTag1Value");
        events = alertsService.getEvents(TENANT, criteria, null);
        assertTrue(events != null);
        assertEquals(1, events.size());
        event = events.get(0);
        assertEquals(4, event.getTags().size());
        assertEquals("TriggerTag1Value", event.getTags().get("TriggerTag1Name"));
        assertEquals("TriggerTag2Value", event.getTags().get("TriggerTag2Name"));
        assertEquals("EventTag1Value", event.getTags().get("EventTag1Name"));
        assertEquals("EventTag2Value", event.getTags().get("EventTag2Name"));

        criteria.getTags().clear();
        criteria.addTag("TriggerTag2Name", "TriggerTag2Value");
        events = alertsService.getEvents(TENANT, criteria, null);
        assertTrue(events != null);
        assertEquals(1, events.size());
        event = events.get(0);
        assertEquals(4, event.getTags().size());
        assertEquals("TriggerTag1Value", event.getTags().get("TriggerTag1Name"));
        assertEquals("TriggerTag2Value", event.getTags().get("TriggerTag2Name"));
        assertEquals("EventTag1Value", event.getTags().get("EventTag1Name"));
        assertEquals("EventTag2Value", event.getTags().get("EventTag2Name"));

        criteria.getTags().clear();
        criteria.addTag("EventTag1Name", "EventTag1Value");
        events = alertsService.getEvents(TENANT, criteria, null);
        assertTrue(events != null);
        assertEquals(1, events.size());
        event = events.get(0);
        assertEquals(4, event.getTags().size());
        assertEquals("TriggerTag1Value", event.getTags().get("TriggerTag1Name"));
        assertEquals("TriggerTag2Value", event.getTags().get("TriggerTag2Name"));
        assertEquals("EventTag1Value", event.getTags().get("EventTag1Name"));
        assertEquals("EventTag2Value", event.getTags().get("EventTag2Name"));

        criteria.getTags().clear();
        criteria.addTag("EventTag2Name", "EventTag2Value");
        events = alertsService.getEvents(TENANT, criteria, null);
        assertTrue(events != null);
        assertEquals(1, events.size());
        event = events.get(0);
        assertEquals(4, event.getTags().size());
        assertEquals("TriggerTag1Value", event.getTags().get("TriggerTag1Name"));
        assertEquals("TriggerTag2Value", event.getTags().get("TriggerTag2Name"));
        assertEquals("EventTag1Value", event.getTags().get("EventTag1Name"));
        assertEquals("EventTag2Value", event.getTags().get("EventTag2Name"));

        // Now, remove two tags
        Collection<String> doomedTags = new ArrayList<>();
        doomedTags.add("TriggerTag1Name");
        doomedTags.add("EventTag1Name");
        doomedTags.add("Bogus");  // it should be OK to try and remove a non-existing tag

        alertsService.removeEventTags(TENANT, eventIds, doomedTags);

        // Only two tags should now be searchable
        criteria.getTags().clear();
        criteria.addTag("TriggerTag1Name", "TriggerTag1Value");
        events = alertsService.getEvents(TENANT, criteria, null);
        assertTrue(events != null);
        assertEquals(0, events.size());

        criteria.getTags().clear();
        criteria.addTag("TriggerTag2Name", "TriggerTag2Value");
        events = alertsService.getEvents(TENANT, criteria, null);
        assertTrue(events != null);
        assertEquals(1, events.size());
        event = events.get(0);
        assertEquals(2, event.getTags().size());
        assertEquals("TriggerTag2Value", event.getTags().get("TriggerTag2Name"));
        assertEquals("EventTag2Value", event.getTags().get("EventTag2Name"));

        criteria.getTags().clear();
        criteria.addTag("EventTag1Name", "EventTag1Value");
        events = alertsService.getEvents(TENANT, criteria, null);
        assertTrue(events != null);
        assertEquals(0, events.size());

        criteria.getTags().clear();
        criteria.addTag("EventTag2Name", "EventTag2Value");
        events = alertsService.getEvents(TENANT, criteria, null);
        assertTrue(events != null);
        assertEquals(1, events.size());
        event = events.get(0);
        assertEquals(2, event.getTags().size());
        assertEquals("TriggerTag2Value", event.getTags().get("TriggerTag2Name"));
        assertEquals("EventTag2Value", event.getTags().get("EventTag2Name"));
    }

}
