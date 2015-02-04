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
class AvailabilityConditionsTest extends AbstractTestBase {
    private static final Logger log = Logger.getLogger(AvailabilityConditionsTest.class);

    @Test
    void findInitialAvailabilityConditions() {
        def resp = client.get(path: "conditions/availability")
        def data = resp.data
        assertEquals(200, resp.status)
        assert data.size() > 0
        for (int i = 0; i < data.size(); i++) {
            AvailabilityCondition c = data[i]
            log.info(c.toString())
        }
    }

    @Test
    void createAvailabilityCondition() {
        AvailabilityCondition testCond = new AvailabilityCondition("test-trigger-1", 1, 1,
                                                                   "No-Metric", Operator.NOT_UP);

        def resp = client.post(path: "conditions/availability", body: testCond)
        assertEquals(200, resp.status)

        resp = client.get(path: "conditions/availability/" + testCond.getConditionId());
        assertEquals(200, resp.status)
        assertEquals("NOT_UP", resp.data.operator)

        testCond.setOperator(Operator.UP)
        resp = client.put(path: "conditions/availability/" + testCond.getConditionId(), body: testCond)
        assertEquals(200, resp.status)

        resp = client.get(path: "conditions/availability/" + testCond.getConditionId())
        assertEquals(200, resp.status)
        assertEquals("UP", resp.data.operator)

        resp = client.delete(path: "conditions/availability/" + testCond.getConditionId())
        assertEquals(200, resp.status)
    }

}
