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
import org.hawkular.alerts.api.model.condition.StringCondition
import org.jboss.logging.Logger
import org.junit.Test

import static org.hawkular.alerts.api.model.condition.StringCondition.Operator
import static org.junit.Assert.assertEquals

/**
 * Conditions REST tests.
 *
 * @author Lucas Ponce
 */
class StringConditionsTest extends AbstractTestBase {
    private static final Logger log = Logger.getLogger(StringConditionsTest.class);

    @Test
    void findInitialStringConditions() {
        def resp = client.get(path: "conditions/string")
        def data = resp.data
        assertEquals(200, resp.status)
        assert data.size() > 0
        for (int i = 0; i < data.size(); i++) {
            StringCondition c = data[i]
            log.info(c.toString())
        }
    }

    @Test
    void createStringCondition() {
        Trigger testTrigger = new Trigger("test-trigger-3", "No-Metric");

        // make sure clean test trigger exists
        client.delete(path: "triggers/test-trigger-3")
        def resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        StringCondition testCond = new StringCondition("test-trigger-3", 1, 1,
                                                       "No-Metric", Operator.CONTAINS, "test", false);

        resp = client.post(path: "conditions/string", body: testCond)
        assertEquals(200, resp.status)

        resp = client.get(path: "conditions/string/" + testCond.getConditionId());
        assertEquals(200, resp.status)
        assertEquals("CONTAINS", resp.data.operator)

        testCond.setOperator(Operator.ENDS_WITH)
        resp = client.put(path: "conditions/string/" + testCond.getConditionId(), body: testCond)
        assertEquals(200, resp.status)

        resp = client.get(path: "conditions/string/" + testCond.getConditionId())
        assertEquals(200, resp.status)
        assertEquals("ENDS_WITH", resp.data.operator)

        resp = client.delete(path: "conditions/string/" + testCond.getConditionId())
        assertEquals(200, resp.status)
    }

}
