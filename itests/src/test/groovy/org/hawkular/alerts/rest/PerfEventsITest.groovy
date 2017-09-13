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

import org.hawkular.alerts.api.model.condition.Condition
import org.hawkular.alerts.api.model.condition.ThresholdCondition
import org.hawkular.alerts.api.model.data.Data
import org.hawkular.alerts.api.model.event.Event
import org.hawkular.alerts.api.model.trigger.Mode
import org.hawkular.alerts.api.model.trigger.Trigger
import org.hawkular.commons.log.MsgLogger
import org.hawkular.commons.log.MsgLogging
import org.junit.Test

import static org.junit.Assert.assertEquals

/**
 * Storing Events taking figures for performance investigation.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
class PerfEventsITest extends AbstractITestBase {

    static MsgLogger logger = MsgLogging.getMsgLogger(PerfEventsITest.class)

    int numTriggers = 5000;

    void prepareTriggers() {
        for (int i = 0; i < numTriggers; i++) {
            if (i % 100 == 0) {
                logger.info("Preparing... [" + i + "] triggers");
            }
            client.delete(path: "triggers/test-send-" + i);
            Trigger crudTrigger = new Trigger("test-send-" + i, "Fake-Metric");
            def resp = client.post(path: "triggers", body: crudTrigger);
            assertEquals(200, resp.status);
            ThresholdCondition testCond1 = new ThresholdCondition("test-send-" + i, Mode.FIRING,
                    "Fake-Metric-" + i, ThresholdCondition.Operator.GT, 50.12);
            Collection<Condition> conditions = new ArrayList<>(2);
            conditions.add( testCond1 );
            resp = client.put(path: "triggers/test-send-" + i + "/conditions/firing", body: conditions)
            assertEquals(200, resp.status);
            assertEquals(1, resp.data.size())
        }
    }

    void cleanTriggers() {
        for (int i = 0; i < numTriggers; i++) {
            if (i % 100 == 0) {
                logger.info("Cleaning... [" + i + "] triggers");
            }
            def resp = client.delete(path: "triggers/test-send-" + i)
            assertEquals(200, resp.status)
        }
    }

    @Test
    void persistEventsThroughREST() {
        int numSendDataCalls = 1000;
        int numEventsPerCategory = 1000;
        int totalEvents = 0;
        long startCall, endCall, timeCall, numCalls = 0, totalTimeCalls = 0, more1sec = 0;

        for (int i = 0; i < numSendDataCalls; i++) {
            for (int j = 0; j < numEventsPerCategory; j++) {
                Event eventX = new Event();
                eventX.setId("E[" + i + "," + j + "]");
                eventX.setCtime(System.currentTimeMillis());
                eventX.setCategory("PERF["+i+"]");
                eventX.setText("Random text: " + UUID.randomUUID().toString());
                startCall = System.currentTimeMillis();
                def resp = client.post(path: "events", body: eventX);
                endCall = System.currentTimeMillis();
                numCalls++;
                timeCall = (endCall - startCall);
                if (timeCall > 1000) {
                    more1sec++;
                }
                totalTimeCalls += timeCall;
                totalEvents++;

                if (totalEvents % 100 == 0) {
                    logger.infof("Stored [%s] Events", totalEvents);
                    Thread.sleep(100);
                }

                assertEquals(200, resp.status)
            }
        }

        logger.info("Total calls: " + numCalls);
        logger.info("Avg: " + ((double)totalTimeCalls / (double)numCalls) + " ms");
        logger.info("> 1 sec: " + more1sec);
    }

    @Test
    void sendEventsThroughREST() {
        int numSendDataCalls = 1000;
        int numEventsPerCategory = 1000;
        long startCall, endCall, timeCall, numCalls = 0, totalTimeCalls = 0, more1sec = 0;

        for (int i = 0; i < numSendDataCalls; i++) {
            Collection<Event> events = new ArrayList<>();

            for (int j = 0; j < numEventsPerCategory; j++) {
                Event eventX = new Event();
                eventX.setId("E[" + i + "," + j + "]");
                eventX.setCtime(System.currentTimeMillis());
                eventX.setCategory("PERF["+i+"]");
                eventX.setText("Random text: " + UUID.randomUUID().toString());
                events.add(eventX);
            }
            startCall = System.currentTimeMillis();
            def resp = client.post(path: "events/data", body: events);
            endCall = System.currentTimeMillis();
            numCalls++;
            timeCall = (endCall - startCall);
            if (timeCall > 1000) {
                more1sec++;
            }
            logger.infof("Iteration [%s]", i);
            totalTimeCalls += timeCall;
            assertEquals(200, resp.status)
        }

        logger.info("Total calls: " + numCalls);
        logger.info("Avg: " + ((double)totalTimeCalls / (double)numCalls) + " ms");
        logger.info("> 1 sec: " + more1sec);
    }

}
