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

import org.hawkular.alerts.api.model.condition.AvailabilityCondition
import org.hawkular.alerts.api.model.condition.Condition
import org.hawkular.alerts.api.model.condition.ThresholdCondition
import org.hawkular.alerts.api.model.data.Data
import org.hawkular.alerts.api.model.trigger.Mode
import org.hawkular.alerts.api.model.trigger.Trigger
import org.hawkular.alerts.api.model.trigger.TriggerAction
import org.hawkular.commons.log.MsgLogger
import org.hawkular.commons.log.MsgLogging
import org.junit.Test


import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

/**
 * Cluster REST tests.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
class ClusterITest extends AbstractITestBase {

    static MsgLogger logger = MsgLogging.getMsgLogger(ClusterITest.class)

    long startCall = 0, endCall = 0, timeCall = 0, totalCalls = 0, totalTime = 0, moreThan1Sec = 0;

    void startCall() {
        startCall = System.currentTimeMillis();
    }

    void endCall(String callDesc) {
        endCall = System.currentTimeMillis();
        timeCall = endCall - startCall;
        totalTime += timeCall;
        totalCalls++;
        if (timeCall > 1000) {
            moreThan1Sec++;
            logger.info("!!! > 1 sec : " + callDesc);
        }
    }

    void summary() {
        logger.info("Total calls: " + totalCalls);
        logger.info("Average: " + ((double)totalTime / (double)totalCalls) + " ms");
        logger.info("> 1 sec: " + moreThan1Sec);
    }

    long startWait = 0, endWait = 0, waitCall = 0, totalWait = 0, totalWaitTime = 0;

    void startWaitResult() {
        startWait = System.currentTimeMillis();
    }

    void endWaitResult(String msg) {
        endWait = System.currentTimeMillis();
        totalWait++;
        totalWaitTime = endWait - startWait;
        logger.info(msg + " took " + totalWaitTime + " ms");
    }

    @Test
    void clusterAvailabilityTest() {

        long startTest = System.currentTimeMillis();
        String start = String.valueOf(startTest);

        def resp = client.get(path: "")
        assert resp.status == 200 : resp.status

        int numTriggers = 40;

        /*
            Create several triggers that should be partitioned in a transparent way, so this test should work on
            any cluster size or even in standalone scenarios.
         */
        for (int i = 0; i < numTriggers; i++) {

            Trigger testTrigger = new Trigger("test-cluster-" + i , "http://www.mydemourl.com");

            startCall();
            resp = client.put(path: "delete", query: [triggerIds:"test-cluster-" + i])
            endCall("PUT /delete?triggerIds=test-cluster-" + i);
            logger.info "Deleted test-cluster-" + i + " - num: " + resp.data
            assert resp.status == 200 : resp.status

            // remove if it exists
            startCall();
            resp = client.delete(path: "triggers/test-cluster-" + i)
            endCall("DELETE /triggers/test-cluster-" + i);
            assert(200 == resp.status || 404 == resp.status)

            testTrigger.setAutoDisable(false);
            testTrigger.setAutoResolve(false);
            testTrigger.setAutoResolveAlerts(false);

            startCall();
            resp = client.post(path: "triggers", body: testTrigger)
            endCall("POST /triggers");
            assertEquals(200, resp.status)

            // ADD Firing condition
            AvailabilityCondition firingCond = new AvailabilityCondition("test-cluster-" + i,
                    Mode.FIRING, "test-cluster-" + i, AvailabilityCondition.Operator.NOT_UP);

            Collection<Condition> conditions = new ArrayList<>(1);
            conditions.add( firingCond );
            startCall();
            resp = client.put(path: "triggers/test-cluster-" + i + "/conditions/firing", body: conditions)
            endCall("PUT /triggers/test-cluster-" + i + "/conditions/firing");
            assertEquals(200, resp.status)
            assertEquals(1, resp.data.size())

            // ENABLE Trigger
            testTrigger.setEnabled(true);

            startCall();
            resp = client.put(path: "triggers/test-cluster-" + i, body: testTrigger)
            endCall("PUT /triggers/test-cluster-" + i);
            assertEquals(200, resp.status)

            // FETCH trigger and make sure it's as expected
            startCall();
            resp = client.get(path: "triggers/test-cluster-" + i);
            endCall("GET /triggers/test-cluster-" + i);
            assertEquals(200, resp.status)
            assertEquals("http://www.mydemourl.com", resp.data.name)
            assertEquals(true, resp.data.enabled)
            assertEquals(false, resp.data.autoDisable);
            assertEquals(false, resp.data.autoResolve);
            assertEquals(false, resp.data.autoResolveAlerts);

            // FETCH recent alerts for trigger, should not be any
            startCall();
            resp = client.get(path: "", query: [startTime:start,triggerIds:"test-cluster-" + i] )
            endCall("GET /?startTime=" + start + ",triggerIds=test-cluster-" + i);
            assertEquals(200, resp.status)
        }


        for (int i = 0; i < numTriggers; i++) {

            // Send in DOWN avail data to fire the trigger
            // Instead of going through the bus, in this test we'll use the alerts rest API directly to send data
            for (int j = 1000; j <= 5000; j+=1000) {
                Data avail = new Data("test-cluster-" + i, j, "DOWN");
                Collection<Data> datums = new ArrayList<>();
                datums.add(avail);
                startCall();
                resp = client.post(path: "data", body: datums);
                endCall("POST /data");
                assertEquals(200, resp.status)
            }
        }

        logger.info("Giving some time to digest the data....");
        Thread.sleep(2000);

        for (int i = 0; i < numTriggers; i++) {

            // The alert processing happens async, so give it a little time before failing...
            startWaitResult();
            for (int j = 0; j < 200; ++j ) {
                Thread.sleep(200);

                // FETCH recent alerts for trigger, there should be 5
                startCall();
                resp = client.get(path: "", query: [startTime:start,triggerIds:"test-cluster-" + i] )
                endCall("GET /?startTime=" + start + ",triggerIds=test-cluster-" + i);
                if ( resp.status == 200 && resp.data.size() == 5 ) {
                    break;
                }
            }
            endWaitResult("Query test-cluster-" + i);
            assertEquals(200, resp.status)
            assertEquals("test-cluster-" + i, 5, resp.data.size())
            assertEquals("OPEN", resp.data[0].status)
        }

        long endTest = System.currentTimeMillis();
        long total = endTest - startTest;

        logger.info("Total test: " + total + " ms");
        summary();
    }
}
