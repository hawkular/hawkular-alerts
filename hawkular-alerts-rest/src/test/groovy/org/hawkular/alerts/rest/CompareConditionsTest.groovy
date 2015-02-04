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
import org.jboss.logging.Logger
import org.junit.Test

import static org.hawkular.alerts.api.model.condition.CompareCondition.Operator
import static org.junit.Assert.assertEquals

/**
 * Conditions REST tests.
 *
 * @author Lucas Ponce
 */
class CompareConditionsTest extends AbstractTestBase {
    private static final Logger log = Logger.getLogger(CompareConditionsTest.class);

    @Test
    void findInitialCompareConditions() {
        def resp = client.get(path: "conditions/compare")
        def data = resp.data
        assertEquals(200, resp.status)
        assert data.size() > 0
        for (int i = 0; i < data.size(); i++) {
            CompareCondition c = data[i]
            log.info(c.toString())
        }
    }

    @Test
    void createCompareCondition() {
        CompareCondition testCond = new CompareCondition("test-trigger-2", 1, 1,
                                                         "No-Metric-1", Operator.LT, 1.0, "No-Metric-2");

        def resp = client.post(path: "conditions/compare", body: testCond)
        assertEquals(200, resp.status)

        resp = client.get(path: "conditions/compare/" + testCond.getConditionId());
        assertEquals(200, resp.status)
        assertEquals("LT", resp.data.operator)

        testCond.setOperator(Operator.GT)
        resp = client.put(path: "conditions/compare/" + testCond.getConditionId(), body: testCond)
        assertEquals(200, resp.status)

        resp = client.get(path: "conditions/compare/" + testCond.getConditionId())
        assertEquals(200, resp.status)
        assertEquals("GT", resp.data.operator)

        resp = client.delete(path: "conditions/compare/" + testCond.getConditionId())
        assertEquals(200, resp.status)
    }

}
