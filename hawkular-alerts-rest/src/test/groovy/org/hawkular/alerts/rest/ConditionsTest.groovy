/**
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
package org.hawkular.alerts.rest

import org.hawkular.alerts.api.model.condition.CompareCondition
import org.hawkular.alerts.api.model.condition.StringCondition
import org.hawkular.alerts.api.model.condition.ThresholdCondition
import org.hawkular.alerts.api.model.condition.ThresholdRangeCondition
import org.hawkular.alerts.api.model.trigger.Trigger
import org.hawkular.alerts.api.model.condition.AvailabilityCondition
import org.jboss.logging.Logger
import org.junit.Test

import static org.hawkular.alerts.api.model.condition.AvailabilityCondition.Operator
import static org.junit.Assert.assertEquals

/**
 * Conditions REST tests.
 *
 * @author Lucas Ponce
 */
class ConditionsTest extends AbstractTestBase {
    private static final Logger log = Logger.getLogger(ConditionsTest.class);

    @Test
    void createAvailabilityCondition() {
        Trigger testTrigger = new Trigger("test-trigger-1", "No-Metric");

        // make sure clean test trigger exists
        client.delete(path: "triggers/test-trigger-1")
        def resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        AvailabilityCondition testCond = new AvailabilityCondition("test-trigger-1",
                "No-Metric", Operator.NOT_UP);

        resp = client.post(path: "triggers/test-trigger-1/conditions", body: testCond)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        testCond.setOperator(Operator.UP)
        resp = client.put(path: "triggers/test-trigger-1/conditions/" + testCond.conditionId, body: testCond)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals("UP", resp.data[0].operator)

        resp = client.delete(path: "triggers/test-trigger-1/conditions/" + testCond.conditionId)
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

        CompareCondition testCond = new CompareCondition("test-trigger-2",
                "No-Metric-1", CompareCondition.Operator.LT, 1.0, "No-Metric-2");

        resp = client.post(path: "triggers/test-trigger-2/conditions", body: testCond)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        testCond.setOperator(CompareCondition.Operator.GT)
        resp = client.put(path: "triggers/test-trigger-2/conditions/" + testCond.getConditionId(), body: testCond)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals("GT", resp.data[0].operator)

        resp = client.delete(path: "triggers/test-trigger-2/conditions/" + testCond.getConditionId())
        assertEquals(200, resp.status)

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

        StringCondition testCond = new StringCondition("test-trigger-3",
                "No-Metric", StringCondition.Operator.CONTAINS, "test", false);

        resp = client.post(path: "triggers/test-trigger-3/conditions", body: testCond)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        testCond.setOperator(StringCondition.Operator.ENDS_WITH)
        resp = client.put(path: "triggers/test-trigger-3/conditions/" + testCond.getConditionId(), body: testCond)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals("ENDS_WITH", resp.data[0].operator)

        resp = client.delete(path: "triggers/test-trigger-3/conditions/" + testCond.getConditionId())
        assertEquals(200, resp.status)

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

        ThresholdCondition testCond = new ThresholdCondition("test-trigger-4",
                "No-Metric", ThresholdCondition.Operator.GT, 10.12);

        resp = client.post(path: "triggers/test-trigger-4/conditions", body: testCond)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        testCond.setOperator(ThresholdCondition.Operator.LTE)
        resp = client.put(path: "triggers/test-trigger-4/conditions/" + testCond.getConditionId(), body: testCond)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals("LTE", resp.data[0].operator)

        resp = client.delete(path: "triggers/test-trigger-4/conditions/" + testCond.getConditionId())
        assertEquals(200, resp.status)

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

        ThresholdRangeCondition testCond = new ThresholdRangeCondition("test-trigger-5",
                "No-Metric",
                ThresholdRangeCondition.Operator.INCLUSIVE, ThresholdRangeCondition.Operator.EXCLUSIVE,
                10.51, 10.99, true);

        resp = client.post(path: "triggers/test-trigger-5/conditions", body: testCond)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        testCond.setOperatorHigh(ThresholdRangeCondition.Operator.INCLUSIVE)
        resp = client.put(path: "triggers/test-trigger-5/conditions/" + testCond.getConditionId(), body: testCond)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals("INCLUSIVE", resp.data[0].operatorHigh)

        resp = client.delete(path: "triggers/test-trigger-5/conditions/" + testCond.getConditionId())
        assertEquals(200, resp.status)

        resp = client.delete(path: "triggers/test-trigger-5")
        assertEquals(200, resp.status)
    }

}
