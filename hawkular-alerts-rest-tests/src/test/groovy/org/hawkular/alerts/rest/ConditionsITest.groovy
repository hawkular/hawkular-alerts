/*
 * Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.alerts.rest

import org.hawkular.alerts.api.json.GroupConditionsInfo
import org.hawkular.alerts.api.json.GroupMemberInfo
import org.hawkular.alerts.api.json.UnorphanMemberInfo
import org.hawkular.alerts.api.model.dampening.Dampening

import static org.hawkular.alerts.api.model.condition.AvailabilityCondition.Operator
import static org.hawkular.alerts.api.model.condition.NelsonCondition.NelsonRule
import static org.hawkular.alerts.api.model.condition.RateCondition.Direction
import static org.hawkular.alerts.api.model.condition.RateCondition.Period
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

import org.hawkular.alerts.api.model.condition.AvailabilityCondition
import org.hawkular.alerts.api.model.condition.CompareCondition
import org.hawkular.alerts.api.model.condition.Condition
import org.hawkular.alerts.api.model.condition.MissingCondition
import org.hawkular.alerts.api.model.condition.NelsonCondition
import org.hawkular.alerts.api.model.condition.RateCondition
import org.hawkular.alerts.api.model.condition.StringCondition
import org.hawkular.alerts.api.model.condition.ThresholdCondition
import org.hawkular.alerts.api.model.condition.ThresholdRangeCondition
import org.hawkular.alerts.api.model.trigger.Mode
import org.hawkular.alerts.api.model.trigger.Trigger
import org.junit.Test

/**
 * Conditions REST tests.
 *
 * @author Lucas Ponce
 */
class ConditionsITest extends AbstractITestBase {

    @Test
    void createAvailabilityCondition() {
        Trigger testTrigger = new Trigger("test-trigger-1", "No-Metric");

        // make sure clean test trigger exists
        client.delete(path: "triggers/test-trigger-1")
        def resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        AvailabilityCondition testCond = new AvailabilityCondition("test-trigger-1", Mode.FIRING,
                "No-Metric", Operator.NOT_UP);

        Collection<Condition> conditions = new ArrayList<>(1);
        conditions.add( testCond );
        resp = client.put(path: "triggers/test-trigger-1/conditions/firing", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        testCond.setOperator(Operator.UP)
        resp = client.put(path: "triggers/test-trigger-1/conditions/firing", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals("UP", resp.data[0].operator)

        conditions.clear();
        resp = client.put(path: "triggers/test-trigger-1/conditions/firing", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(0, resp.data.size())

        resp = client.delete(path: "triggers/test-trigger-1")
        assertEquals(200, resp.status)
    }

    @Test
    void createCompareCondition() {
        Trigger testTrigger = new Trigger("test-trigger-2", "No-Metric");

        // make sure clean test trigger exists
        client.delete(path: "triggers/test-trigger-2")
        def resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        CompareCondition testCond = new CompareCondition("test-trigger-2", Mode.FIRING,
                "No-Metric-1", CompareCondition.Operator.LT, 1.0, "No-Metric-2");

        Collection<Condition> conditions = new ArrayList<>(1);
        conditions.add( testCond );
        resp = client.put(path: "triggers/test-trigger-2/conditions/firing", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        testCond.setOperator(CompareCondition.Operator.GT)
        resp = client.put(path: "triggers/test-trigger-2/conditions/firing", body: conditions)
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/test-trigger-2/conditions")
        assertEquals(1, resp.data.size())
        assertEquals("GT", resp.data[0].operator)

        conditions.clear();
        resp = client.put(path: "triggers/test-trigger-2/conditions/firing", body: conditions)
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/test-trigger-2/conditions")
        assertEquals(0, resp.data.size())

        resp = client.delete(path: "triggers/test-trigger-2")
        assertEquals(200, resp.status)
    }

    @Test
    void createStringCondition() {
        Trigger testTrigger = new Trigger("test-trigger-3", "No-Metric");

        // make sure clean test trigger exists
        client.delete(path: "triggers/test-trigger-3")
        def resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        StringCondition testCond = new StringCondition("test-trigger-3", Mode.FIRING,
                "No-Metric", StringCondition.Operator.CONTAINS, "test", false);

        Collection<Condition> conditions = new ArrayList<>(1);
        conditions.add( testCond );
        resp = client.put(path: "triggers/test-trigger-3/conditions/firing", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        testCond.setOperator(StringCondition.Operator.ENDS_WITH)
        resp = client.put(path: "triggers/test-trigger-3/conditions/firing", body: conditions)
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/test-trigger-3/conditions")
        assertEquals(1, resp.data.size())
        assertEquals("ENDS_WITH", resp.data[0].operator)

        conditions.clear();
        resp = client.put(path: "triggers/test-trigger-3/conditions/firing", body: conditions)
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/test-trigger-3/conditions")
        assertEquals(0, resp.data.size())

        resp = client.delete(path: "triggers/test-trigger-3")
        assertEquals(200, resp.status)
    }

    @Test
    void createThresholdCondition() {
        Trigger testTrigger = new Trigger("test-trigger-4", "No-Metric");

        // make sure clean test trigger exists
        client.delete(path: "triggers/test-trigger-4")
        def resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        ThresholdCondition testCond1 = new ThresholdCondition("test-trigger-4", Mode.FIRING,
                "No-Metric", ThresholdCondition.Operator.LT, 10.12);

        ThresholdCondition testCond2 = new ThresholdCondition("test-trigger-4", Mode.FIRING,
                "No-Metric", ThresholdCondition.Operator.GT, 4.10);

        Collection<Condition> conditions = new ArrayList<>(2);
        conditions.add( testCond1 );
        conditions.add( testCond2 );
        resp = client.put(path: "triggers/test-trigger-4/conditions/firing", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(2, resp.data.size())

        resp = client.get(path: "triggers/test-trigger-4/conditions")
        assertEquals(200, resp.status)
        assertEquals(2, resp.data.size())

        testCond1.setOperator(ThresholdCondition.Operator.LTE)
        resp = client.put(path: "triggers/test-trigger-4/conditions/firing", body: conditions)
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/test-trigger-4/conditions")
        assertEquals(2, resp.data.size())
        def ops = [resp.data[0].operator, resp.data[1].operator].sort();
        assertEquals("GT", ops[0])
        assertEquals("LTE", ops[1])

        conditions.clear();
        conditions.add(testCond2);
        resp = client.put(path: "triggers/test-trigger-4/conditions/firing", body: conditions)
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/test-trigger-4/conditions")
        assertEquals(1, resp.data.size())

        resp = client.delete(path: "triggers/test-trigger-4")
        assertEquals(200, resp.status)
    }

    @Test
    void createThresholdRangeCondition() {
        Trigger testTrigger = new Trigger("test-trigger-5", "No-Metric");

        // make sure clean test trigger exists
        client.delete(path: "triggers/test-trigger-5")
        def resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        ThresholdRangeCondition testCond = new ThresholdRangeCondition("test-trigger-5", Mode.FIRING,
                "No-Metric", ThresholdRangeCondition.Operator.INCLUSIVE, ThresholdRangeCondition.Operator.EXCLUSIVE,
                10.51, 10.99, true);

        Collection<Condition> conditions = new ArrayList<>(1);
        conditions.add( testCond );
        resp = client.put(path: "triggers/test-trigger-5/conditions/firing", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        testCond.setOperatorHigh(ThresholdRangeCondition.Operator.INCLUSIVE)
        resp = client.put(path: "triggers/test-trigger-5/conditions/firing", body: conditions)
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/test-trigger-5/conditions")
        assertEquals(1, resp.data.size())
        assertEquals("INCLUSIVE", resp.data[0].operatorHigh)

        conditions.clear();
        resp = client.put(path: "triggers/test-trigger-5/conditions/firing", body: conditions)
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/test-trigger-5/conditions")
        assertEquals(0, resp.data.size())

        resp = client.delete(path: "triggers/test-trigger-5")
        assertEquals(200, resp.status)
    }

    @Test
    void createMissingCondition() {
        Trigger testTrigger = new Trigger("test-trigger-6", "No-Metric");

        // make sure clean test trigger exists
        client.delete(path: "triggers/test-trigger-6")
        def resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        MissingCondition testCond = new MissingCondition("test-trigger-6", Mode.FIRING,
                "No-Metric-1", 1234);

        Collection<Condition> conditions = new ArrayList<>(1);
        conditions.add( testCond );
        resp = client.put(path: "triggers/test-trigger-6/conditions/firing", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        testCond.setInterval(5432)
        resp = client.put(path: "triggers/test-trigger-6/conditions/firing", body: conditions)
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/test-trigger-6/conditions")
        assertEquals(1, resp.data.size())
        assertEquals(5432, resp.data[0].interval)

        conditions.clear();
        resp = client.put(path: "triggers/test-trigger-6/conditions/firing", body: conditions)
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/test-trigger-6/conditions")
        assertEquals(0, resp.data.size())

        resp = client.delete(path: "triggers/test-trigger-6")
        assertEquals(200, resp.status)
    }

    @Test
    void createNelsonCondition() {
        Trigger testTrigger = new Trigger("test-trigger-7", "No-Metric");

        // make sure clean test trigger exists
        client.delete(path: "triggers/test-trigger-7")
        def resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        Set<NelsonRule> activeRules = new HashSet<>(3);
        activeRules.add(NelsonRule.Rule3);
        activeRules.add(NelsonRule.Rule5);
        activeRules.add(NelsonRule.Rule7);
        NelsonCondition testCond = new NelsonCondition("test-trigger-7", Mode.FIRING, 1, 1,
            "No-Metric-1", activeRules, 10);

        Collection<Condition> conditions = new ArrayList<>(1);
        conditions.add( testCond );
        resp = client.put(path: "triggers/test-trigger-7/conditions/firing", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        resp = client.get(path: "triggers/test-trigger-7/conditions")
        assertEquals(1, resp.data.size())
        assertTrue(resp.data[0].activeRules.contains("Rule3"))
        assertTrue(resp.data[0].activeRules.contains("Rule5"))
        assertTrue(resp.data[0].activeRules.contains("Rule7"))
        assertEquals(10, resp.data[0].sampleSize)

        activeRules.remove(NelsonRule.Rule5)
        testCond.setActiveRules(activeRules)
        testCond.setSampleSize(20)
        resp = client.put(path: "triggers/test-trigger-7/conditions/firing", body: conditions)
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/test-trigger-7/conditions")
        assertEquals(1, resp.data.size())
        assertEquals(2, resp.data[0].activeRules.size())
        assertTrue(resp.data[0].activeRules.contains("Rule3"))
        assertTrue(resp.data[0].activeRules.contains("Rule7"))
        assertEquals(20, resp.data[0].sampleSize)

        conditions.clear();
        resp = client.put(path: "triggers/test-trigger-7/conditions/firing", body: conditions)
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/test-trigger-7/conditions")
        assertEquals(0, resp.data.size())

        resp = client.delete(path: "triggers/test-trigger-7")
        assertEquals(200, resp.status)
    }

    @Test
    void createRateCondition() {
        Trigger testTrigger = new Trigger("test-trigger-8", "No-Metric");

        // make sure clean test trigger exists
        client.delete(path: "triggers/test-trigger-8")
        def resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        RateCondition testCond = new RateCondition("test-trigger-8", Mode.FIRING,
                "No-Metric-1", Direction.DECREASING, Period.WEEK, RateCondition.Operator.GT, 10.0);

        Collection<Condition> conditions = new ArrayList<>(1);
        conditions.add( testCond );
        resp = client.put(path: "triggers/test-trigger-8/conditions/firing", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals("DECREASING", resp.data[0].direction)
        assertEquals("WEEK", resp.data[0].period)
        assertEquals("GT", resp.data[0].operator)

        testCond.setDirection(Direction.INCREASING)
        resp = client.put(path: "triggers/test-trigger-8/conditions/firing", body: conditions)
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/test-trigger-8/conditions")
        assertEquals(1, resp.data.size())
        assertEquals("INCREASING", resp.data[0].direction)

        conditions.clear();
        resp = client.put(path: "triggers/test-trigger-8/conditions/firing", body: conditions)
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/test-trigger-8/conditions")
        assertEquals(0, resp.data.size())

        resp = client.delete(path: "triggers/test-trigger-8")
        assertEquals(200, resp.status)
    }

    @Test
    void createMultipleTriggerModeConditions() {
        Trigger testTrigger = new Trigger("test-multiple-mode-conditions", "No-Metric")

        // make sure clean test trigger exists
        client.delete(path: "triggers/test-multiple-mode-conditions")
        def resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        ThresholdCondition testCond1 = new ThresholdCondition("test-multiple-mode-conditions", Mode.FIRING,
                "No-Metric", ThresholdCondition.Operator.GT, 10.12);

        ThresholdCondition testCond2 = new ThresholdCondition("test-multiple-mode-conditions", Mode.AUTORESOLVE,
                "No-Metric", ThresholdCondition.Operator.LT, 4.10);

        Collection<Condition> conditions = new ArrayList<>(2);
        conditions.add( testCond1 );
        conditions.add( testCond2 );
        resp = client.put(path: "triggers/test-multiple-mode-conditions/conditions", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(2, resp.data.size())

        resp = client.get(path: "triggers/test-multiple-mode-conditions/conditions")
        Set<String> modes = new HashSet<>();
        modes.add(resp.data[0].triggerMode)
        modes.add(resp.data[1].triggerMode)
        assertEquals(2, modes.size())

        conditions = Arrays.asList(testCond2)

        resp = client.put(path: "triggers/test-multiple-mode-conditions/conditions", body: conditions)
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/test-multiple-mode-conditions/conditions")
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals("AUTORESOLVE", resp.data[0].triggerMode);

        conditions = Collections.EMPTY_LIST

        resp = client.put(path: "triggers/test-multiple-mode-conditions/conditions", body: conditions)
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/test-multiple-mode-conditions/conditions")
        assertEquals(200, resp.status)
        assertEquals(0, resp.data.size())

        resp = client.delete(path: "triggers/test-multiple-mode-conditions")
        assertEquals(200, resp.status)
    }

    @Test
    void createMultipleTriggerModeConditionsInGroups() {
        Trigger groupTrigger = new Trigger("group-trigger", "group-trigger");
        groupTrigger.setEnabled(false);

        // remove if it exists
        def resp = client.delete(path: "triggers/groups/group-trigger", query: [keepNonOrphans:false,keepOrphans:false])
        assert(200 == resp.status || 404 == resp.status)

        // create the group
        resp = client.post(path: "triggers/groups", body: groupTrigger)
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/group-trigger")
        assertEquals(200, resp.status)
        groupTrigger = (Trigger)resp.data;
        assertEquals( true, groupTrigger.isGroup() );

        ThresholdCondition cond1 = new ThresholdCondition("group-trigger", Mode.FIRING, "DataId1-Token",
                ThresholdCondition.Operator.GT, 10.0);
        ThresholdCondition cond2 = new ThresholdCondition("group-trigger", Mode.AUTORESOLVE, "DataId2-Token",
                ThresholdCondition.Operator.LT, 20.0);

        Map<String, Map<String, String>> dataIdMemberMap = new HashMap<>();
        GroupConditionsInfo groupConditionsInfo = new GroupConditionsInfo(Arrays.asList(cond1, cond2), dataIdMemberMap)

        resp = client.put(path: "triggers/groups/group-trigger/conditions", body: groupConditionsInfo)
        assertEquals(resp.toString(), 200, resp.status)
        assertEquals(2, resp.data.size())

        // get the group conditions
        resp = client.get(path: "triggers/group-trigger/conditions")
        assertEquals(200, resp.status)
        assertEquals(2, resp.data.size());

        Set<String> modes = new HashSet<>();
        modes.add(resp.data[0].triggerMode)
        modes.add(resp.data[1].triggerMode)
        assertEquals(2, modes.size())

        groupConditionsInfo.setConditions(Arrays.asList(cond2))

        resp = client.put(path: "triggers/groups/group-trigger/conditions", body: groupConditionsInfo)
        assertEquals(resp.toString(), 200, resp.status)

        resp = client.get(path: "triggers/group-trigger/conditions")
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size());
        assertEquals("AUTORESOLVE", resp.data[0].triggerMode);

        groupConditionsInfo.setConditions(Collections.EMPTY_LIST)

        resp = client.put(path: "triggers/groups/group-trigger/conditions", body: groupConditionsInfo)
        assertEquals(resp.toString(), 200, resp.status)

        resp = client.get(path: "triggers/group-trigger/conditions")
        assertEquals(200, resp.status)
        assertEquals(0, resp.data.size());

        // remove group trigger
        resp = client.delete(path: "triggers/groups/group-trigger")
        assertEquals(200, resp.status)
    }
}
