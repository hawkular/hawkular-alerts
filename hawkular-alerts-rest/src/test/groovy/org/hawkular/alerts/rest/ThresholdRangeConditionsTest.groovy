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

import org.hawkular.alerts.api.model.condition.ThresholdRangeCondition
import org.jboss.logging.Logger
import org.junit.Test

import static org.hawkular.alerts.api.model.condition.ThresholdRangeCondition.Operator
import static org.junit.Assert.assertEquals

/**
 * Conditions REST tests.
 *
 * @author Lucas Ponce
 */
class ThresholdRangeConditionsTest extends AbstractTestBase {
    private static final Logger log = Logger.getLogger(ThresholdRangeConditionsTest.class);

    @Test
    void findInitialThresholdRangeConditions() {
        def resp = client.get(path: "conditions/range")
        def data = resp.data
        assertEquals(200, resp.status)
        assert data.size() > 0
        for (int i = 0; i < data.size(); i++) {
            ThresholdRangeCondition c = data[i]
            log.info(c.toString())
        }
    }

    @Test
    void createThresholdRangeCondition() {
        ThresholdRangeCondition testCond = new ThresholdRangeCondition("test-trigger-5", 1, 1,
                                                                       "No-Metric",
                                                                       Operator.INCLUSIVE, Operator.EXCLUSIVE,
                                                                       10.51, 10.99, true);

        def resp = client.post(path: "conditions/range", body: testCond)
        assertEquals(200, resp.status)

        resp = client.get(path: "conditions/range/" + testCond.getConditionId());
        assertEquals(200, resp.status)
        assertEquals("EXCLUSIVE", resp.data.operatorHigh)

        testCond.setOperatorHigh(Operator.INCLUSIVE)
        resp = client.put(path: "conditions/range/" + testCond.getConditionId(), body: testCond)
        assertEquals(200, resp.status)

        resp = client.get(path: "conditions/range/" + testCond.getConditionId())
        assertEquals(200, resp.status)
        assertEquals("INCLUSIVE", resp.data.operatorHigh)

        resp = client.delete(path: "conditions/range/" + testCond.getConditionId())
        assertEquals(200, resp.status)
    }

}
