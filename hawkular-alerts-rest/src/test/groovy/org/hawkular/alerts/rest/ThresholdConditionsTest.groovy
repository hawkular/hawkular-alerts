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

import org.hawkular.alerts.api.model.trigger.Trigger
import org.hawkular.alerts.api.model.condition.ThresholdCondition
import org.jboss.logging.Logger
import org.junit.Test

import static org.hawkular.alerts.api.model.condition.ThresholdCondition.Operator
import static org.junit.Assert.assertEquals

/**
 * Conditions REST tests.
 *
 * @author Lucas Ponce
 */
class ThresholdConditionsTest extends AbstractTestBase {
    private static final Logger log = Logger.getLogger(ThresholdConditionsTest.class);

    @Test
    void findInitialThresholdConditions() {
        def resp = client.get(path: "conditions/threshold")
        def data = resp.data
        assertEquals(200, resp.status)
        assert data.size() > 0
        for (int i = 0; i < data.size(); i++) {
            ThresholdCondition c = data[i]
            log.info(c.toString())
        }
    }

    @Test
    void createThresholdCondition() {
        Trigger testTrigger = new Trigger("test-trigger-4", "No-Metric");

        // make sure clean test trigger exists
        client.delete(path: "triggers/test-trigger-4")
        def resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        ThresholdCondition testCond = new ThresholdCondition("test-trigger-4", 1, 1,
                                                             "No-Metric", Operator.GT, 10.12);

        resp = client.post(path: "conditions/threshold", body: testCond)
        assertEquals(200, resp.status)

        resp = client.get(path: "conditions/threshold/" + testCond.getConditionId());
        assertEquals(200, resp.status)
        assertEquals("GT", resp.data.operator)

        testCond.setOperator(Operator.LTE)
        resp = client.put(path: "conditions/threshold/" + testCond.getConditionId(), body: testCond)
        assertEquals(200, resp.status)

        resp = client.get(path: "conditions/threshold/" + testCond.getConditionId())
        assertEquals(200, resp.status)
        assertEquals("LTE", resp.data.operator)

        resp = client.delete(path: "conditions/threshold/" + testCond.getConditionId())
        assertEquals(200, resp.status)
    }

}
