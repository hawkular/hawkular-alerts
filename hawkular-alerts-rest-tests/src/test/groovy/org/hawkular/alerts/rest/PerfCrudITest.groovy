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
package org.hawkular.alerts.rest

import org.hawkular.alerts.api.model.condition.Condition
import org.hawkular.alerts.api.model.condition.ThresholdCondition
import org.hawkular.alerts.api.model.dampening.Dampening
import org.hawkular.alerts.api.model.dampening.Dampening.Type
import org.hawkular.alerts.api.model.trigger.Mode
import org.hawkular.alerts.api.model.trigger.Trigger
import org.junit.Test

import static org.junit.Assert.assertEquals

/**
 * CRUD REST operations taking figures for performance investigation.
 *
 * @author Lucas Ponce
 */
class PerfCrudITest extends AbstractITestBase {

    @Test
    void fullCycle() {

        int numTriggers = 2000;
        long startCall = 0, endCall = 0, timeCall = 0, numCalls = 0, totalTimeCalls = 0, more1sec = 0;

        for (int i = 0; i < numTriggers; i++) {

            // Triggger

            startCall = System.currentTimeMillis();
            client.delete(path: "triggers/test-crud-" + i);
            endCall = System.currentTimeMillis();
            numCalls++;
            timeCall = (endCall - startCall);
            if (timeCall > 1000) {
                more1sec++;
            }
            totalTimeCalls += timeCall;

            Trigger crudTrigger = new Trigger("test-crud-" + i, "No-Metric");

            startCall = System.currentTimeMillis();
            def resp = client.post(path: "triggers", body: crudTrigger);
            endCall = System.currentTimeMillis();
            assertEquals(200, resp.status)
            numCalls++;
            timeCall = (endCall - startCall);
            if (timeCall > 1000) {
                more1sec++;
            }
            totalTimeCalls += timeCall;

            // Dampening

            Dampening d = Dampening.forRelaxedCount("test-crud-" + i, Mode.FIRING, 1, 2);

            startCall = System.currentTimeMillis();
            resp = client.post(path: "triggers/test-crud-" + i + "/dampenings", body: d);
            endCall = System.currentTimeMillis();
            assertEquals(200, resp.status)
            numCalls++;
            timeCall = (endCall - startCall);
            if (timeCall > 1000) {
                more1sec++;
            }
            totalTimeCalls += timeCall;

            startCall = System.currentTimeMillis();
            resp = client.get(path: "triggers/test-crud-" + i + "/dampenings/" + d.getDampeningId());
            endCall = System.currentTimeMillis();
            assertEquals(200, resp.status)
            assertEquals("RELAXED_COUNT", resp.data.type)
            numCalls++;
            timeCall = (endCall - startCall);
            if (timeCall > 1000) {
                more1sec++;
            }
            totalTimeCalls += timeCall;

            d.setType(Type.STRICT)
            startCall = System.currentTimeMillis();
            resp = client.put(path: "triggers/test-crud-" + i + "/dampenings/" + d.getDampeningId(), body: d);
            endCall = System.currentTimeMillis();
            assertEquals(200, resp.status)
            numCalls++;
            timeCall = (endCall - startCall);
            if (timeCall > 1000) {
                more1sec++;
            }
            totalTimeCalls += timeCall;

            startCall = System.currentTimeMillis();
            resp = client.get(path: "triggers/test-crud-" + i + "/dampenings/" + d.getDampeningId());
            endCall = System.currentTimeMillis();
            assertEquals(200, resp.status)
            assertEquals("STRICT", resp.data.type)
            numCalls++;
            timeCall = (endCall - startCall);
            if (timeCall > 1000) {
                more1sec++;
            }
            totalTimeCalls += timeCall;

            startCall = System.currentTimeMillis();
            resp = client.get(path: "triggers/test-crud-" + i + "/dampenings")
            endCall = System.currentTimeMillis();
            assertEquals(200, resp.status)
            assertEquals(1, resp.data.size())
            numCalls++;
            timeCall = (endCall - startCall);
            if (timeCall > 1000) {
                more1sec++;
            }
            totalTimeCalls += timeCall;

            startCall = System.currentTimeMillis();
            resp = client.get(path: "triggers/test-crud-" + i + "/dampenings/mode/FIRING")
            endCall = System.currentTimeMillis();
            assertEquals(200, resp.status)
            assertEquals(1, resp.data.size())
            assertEquals("test-crud-" + i, resp.data[0].triggerId)
            numCalls++;
            timeCall = (endCall - startCall);
            if (timeCall > 1000) {
                more1sec++;
            }
            totalTimeCalls += timeCall;

            startCall = System.currentTimeMillis();
            resp = client.delete(path: "triggers/test-crud-" + i + "/dampenings/" + d.getDampeningId())
            endCall = System.currentTimeMillis();
            assertEquals(200, resp.status)
            numCalls++;
            timeCall = (endCall - startCall);
            if (timeCall > 1000) {
                more1sec++;
            }
            totalTimeCalls += timeCall;

            // Conditions

            ThresholdCondition testCond1 = new ThresholdCondition("test-crud-" + i,
                    "No-Metric", ThresholdCondition.Operator.GT, 10.12);
            ThresholdCondition testCond2 = new ThresholdCondition("test-crud-" + i,
                    "No-Metric", ThresholdCondition.Operator.LT, 4.10);
            Collection<Condition> conditions = new ArrayList<>(2);
            conditions.add( testCond1 );
            conditions.add( testCond2 );
            startCall = System.currentTimeMillis();
            resp = client.put(path: "triggers/test-crud-" + i + "/conditions/firing", body: conditions)
            endCall = System.currentTimeMillis();
            assertEquals(200, resp.status)
            assertEquals(2, resp.data.size())
            numCalls++;
            timeCall = (endCall - startCall);
            if (timeCall > 1000) {
                more1sec++;
            }
            totalTimeCalls += timeCall;

            startCall = System.currentTimeMillis();
            resp = client.get(path: "triggers/test-crud-" + i + "/conditions")
            endCall = System.currentTimeMillis();
            assertEquals(200, resp.status)
            assertEquals(2, resp.data.size())
            numCalls++;
            timeCall = (endCall - startCall);
            if (timeCall > 1000) {
                more1sec++;
            }
            totalTimeCalls += timeCall;


            testCond1.setOperator(ThresholdCondition.Operator.LTE)
            startCall = System.currentTimeMillis();
            resp = client.put(path: "triggers/test-crud-" + i + "/conditions/firing", body: conditions)
            endCall = System.currentTimeMillis();
            assertEquals(200, resp.status)
            numCalls++;
            timeCall = (endCall - startCall);
            if (timeCall > 1000) {
                more1sec++;
            }
            totalTimeCalls += timeCall;

            startCall = System.currentTimeMillis();
            resp = client.get(path: "triggers/test-crud-" + i + "/conditions")
            endCall = System.currentTimeMillis();
            assertEquals(2, resp.data.size())
            assertEquals("LTE", resp.data[0].operator)
            numCalls++;
            timeCall = (endCall - startCall);
            if (timeCall > 1000) {
                more1sec++;
            }
            totalTimeCalls += timeCall;


            conditions.clear();
            conditions.add(testCond2);
            startCall = System.currentTimeMillis();
            resp = client.put(path: "triggers/test-crud-" + i + "/conditions/firing", body: conditions)
            endCall = System.currentTimeMillis();
            assertEquals(200, resp.status)
            numCalls++;
            timeCall = (endCall - startCall);
            if (timeCall > 1000) {
                more1sec++;
            }
            totalTimeCalls += timeCall;

            startCall = System.currentTimeMillis();
            resp = client.get(path: "triggers/test-crud-" + i + "/conditions")
            endCall = System.currentTimeMillis();
            assertEquals(1, resp.data.size())
            numCalls++;
            timeCall = (endCall - startCall);
            if (timeCall > 1000) {
                more1sec++;
            }
            totalTimeCalls += timeCall;

            startCall = System.currentTimeMillis();
            resp = client.delete(path: "triggers/test-crud-" + i)
            endCall = System.currentTimeMillis();
            assertEquals(200, resp.status)
            numCalls++;
            timeCall = (endCall - startCall);
            if (timeCall > 1000) {
                more1sec++;
            }
            totalTimeCalls += timeCall;

            println("Iteration: " + i);
            println("Total calls: " + numCalls);
            println("Avg: " + ((double)totalTimeCalls / (double)numCalls) + " ms");
            println("> 1 sec: " + more1sec);

        }


    }

}
