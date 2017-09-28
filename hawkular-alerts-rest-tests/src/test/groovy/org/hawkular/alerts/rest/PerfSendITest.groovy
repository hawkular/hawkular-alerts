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
import org.hawkular.alerts.api.model.trigger.Mode
import org.hawkular.alerts.api.model.trigger.Trigger
import org.hawkular.commons.log.MsgLogger
import org.hawkular.commons.log.MsgLogging
import org.junit.Test

import static org.junit.Assert.assertEquals

/**
 * Send REST operations taking figures for performance investigation.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
class PerfSendITest extends AbstractITestBase {

    static MsgLogger logger = MsgLogging.getMsgLogger(PerfSendITest.class)

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

    /*
        This test is designed to study performance on REST endpoints.
        This is a serial version where all calls happen on cascade.
     */
    @Test
    void sendDataThroughREST() {

        prepareTriggers();

        // Some warm up time (takes several seconds for final updates to DataDrivenGroupCacheManager)
        Thread.sleep(15000L);

        // We simulate calls to alerting
        // Each call will have metric per each trigger
        int numSendDataCalls = 100;
        long startCall, endCall, timeCall, numCalls = 0, totalTimeCalls = 0, more1sec = 0;

        for (int i = 0; i < numSendDataCalls; i++) {

            Collection<Data> datums = new ArrayList<Data>();

            for (int z = 0; z < numTriggers; z++) {
                datums.add(Data.forNumeric(testTenant, "Fake-Metric-" + z,
                        System.currentTimeMillis(), Math.random() * 100));
            }

            startCall = System.currentTimeMillis();
            def resp = client.post(path: "data", body: datums);
            endCall = System.currentTimeMillis();
            numCalls++;
            timeCall = (endCall - startCall);
            if (timeCall > 1000) {
                more1sec++;
            }
            totalTimeCalls += timeCall;

            assertEquals(200, resp.status)

            logger.info("Iteration: " + i);
            logger.info("Total calls: " + numCalls);
            logger.info("Avg: " + ((double)totalTimeCalls / (double)numCalls) + " ms");
            logger.info("> 1 sec: " + more1sec);

            // We simulate a buffer delay from the caller system
            Thread.sleep(100);
        }

        int maxProcessingTime = 60;
        for (int i = 0; i < maxProcessingTime; i++) {
            logger.info("Giving [" + i + "] s for processing...");
            Thread.sleep(1000L);
        }

        cleanTriggers();
    }
}
