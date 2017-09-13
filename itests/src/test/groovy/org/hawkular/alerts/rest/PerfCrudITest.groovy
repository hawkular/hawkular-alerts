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

import groovyx.gpars.dataflow.Promise
import groovyx.net.http.ContentType
import groovyx.net.http.RESTClient
import org.hawkular.alerts.api.model.condition.Condition
import org.hawkular.alerts.api.model.condition.ThresholdCondition
import org.hawkular.alerts.api.model.dampening.Dampening
import org.hawkular.alerts.api.model.dampening.Dampening.Type
import org.hawkular.alerts.api.model.trigger.Mode
import org.hawkular.alerts.api.model.trigger.Trigger
import org.hawkular.commons.log.MsgLogger
import org.hawkular.commons.log.MsgLogging
import org.junit.Test

import static groovyx.gpars.dataflow.Dataflow.task
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

/**
 * CRUD REST operations taking figures for performance investigation.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
class PerfCrudITest extends AbstractITestBase {

    static MsgLogger logger = MsgLogging.getMsgLogger(PerfCrudITest.class)

    /*
        This test is designed to study performance on REST endpoints.
        This is a serial version where all calls happen on cascade.
     */
    @Test
    void serialWithoutAutoResolve() {

        int numTriggers = 2000;
        long startCall, endCall, timeCall, numCalls = 0, totalTimeCalls = 0, more1sec = 0;
        long numPostTrigger = 0, numDeleteTrigger = 0;
        long totalPostTrigger = 0, totalDeleteTrigger = 0;
        long numPostDampening = 0, numGetDampening = 0, numPutDampening = 0, numDeleteDampening = 0;
        long totalPostDampening = 0, totalGetDampening = 0, totalPutDampening = 0, totalDeleteDampening = 0;
        long numPutConditions = 0, numGetConditions = 0;
        long totalPutConditions = 0, totalGetConditions = 0;

        for (int i = 0; i < numTriggers; i++) {

            // Triggger

            startCall = System.currentTimeMillis();
            client.delete(path: "triggers/test-crud-" + i);
            endCall = System.currentTimeMillis();
            numCalls++;
            timeCall = (endCall - startCall);
            numDeleteTrigger++;
            totalDeleteTrigger += timeCall;
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
            numPostTrigger++;
            totalPostTrigger += timeCall;
            if (timeCall > 1000) {
                more1sec++;
            }
            totalTimeCalls += timeCall;

            // Dampening

            Dampening d = Dampening.forRelaxedCount("", "test-crud-" + i, Mode.FIRING, 1, 2);

            startCall = System.currentTimeMillis();
            resp = client.post(path: "triggers/test-crud-" + i + "/dampenings", body: d);
            endCall = System.currentTimeMillis();
            assertEquals(200, resp.status)
            d = resp.data // get the assigned dampeningId
            numCalls++;
            timeCall = (endCall - startCall);
            numPostDampening++;
            totalPostDampening += timeCall;
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
            numGetDampening++;
            totalGetDampening += timeCall;
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
            numPutDampening++;
            totalPutDampening += timeCall;
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
            numGetDampening++;
            totalGetDampening += timeCall;
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
            numGetDampening++;
            totalGetDampening += timeCall;
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
            numGetDampening++;
            totalGetDampening += timeCall;
            if (timeCall > 1000) {
                more1sec++;
            }
            totalTimeCalls += timeCall;

            // Conditions

            ThresholdCondition testCond1 = new ThresholdCondition("test-crud-" + i, Mode.FIRING,
                    "No-Metric", ThresholdCondition.Operator.GT, 10.12);
            ThresholdCondition testCond2 = new ThresholdCondition("test-crud-" + i, Mode.FIRING,
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
            numPutConditions++;
            totalPutConditions += timeCall;
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
            numGetConditions++;
            totalGetConditions += timeCall;
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
            numPutConditions++;
            totalPutConditions += timeCall;
            if (timeCall > 1000) {
                more1sec++;
            }
            totalTimeCalls += timeCall;

            startCall = System.currentTimeMillis();
            resp = client.get(path: "triggers/test-crud-" + i + "/conditions")
            endCall = System.currentTimeMillis();
            assertEquals(2, resp.data.size())
            Set<String> operators = new HashSet<>()
            operators.add(resp.data[0].operator)
            operators.add(resp.data[1].operator)
            assertTrue(operators.contains("LTE"))
            assertTrue(operators.contains("LT"))
            numCalls++;
            timeCall = (endCall - startCall);
            numGetConditions++;
            totalGetConditions += timeCall;
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
            numPutConditions++;
            totalPutConditions += timeCall;
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
            numGetConditions++;
            totalGetConditions += timeCall;
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
            numDeleteDampening++;
            totalDeleteDampening += timeCall;
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
            numDeleteTrigger++;
            totalDeleteTrigger += timeCall;
            if (timeCall > 1000) {
                more1sec++;
            }
            totalTimeCalls += timeCall;

            logger.info("Iteration: " + i);
            logger.info("Total calls: " + numCalls);
            logger.info("Avg: " + ((double)totalTimeCalls / (double)numCalls) + " ms");
            logger.info("> 1 sec: " + more1sec);
            logger.info("POST   Trigger #: " + numPostTrigger);
            logger.info("POST   Trigger Avg: " + ((double)totalPostTrigger / (double)numPostTrigger) + " ms");
            logger.info("DELETE Trigger #: " + numDeleteTrigger);
            logger.info("DELETE Trigger Avg: " + ((double)totalDeleteTrigger / (double)numDeleteTrigger) + " ms");
            logger.info("GET    Dampening #: " + numGetDampening);
            logger.info("GET    Dampening Avg: " + ((double)totalGetDampening / (double)numGetDampening) + " ms");
            logger.info("POST   Dampening #: " + numPostDampening);
            logger.info("POST   Dampening Avg: " + ((double)totalPostDampening / (double)numPostDampening) + " ms");
            logger.info("PUT    Dampening #: " + numPutDampening);
            logger.info("PUT    Dampening Avg: " + ((double)totalPutDampening / (double)numPutDampening) + " ms");
            logger.info("DELETE Dampening #: " + numDeleteDampening);
            logger.info("DELETE Dampening Avg: " + ((double)totalDeleteDampening / (double)numDeleteDampening) + " ms");
            logger.info("GET    Conditions #: " + numGetConditions);
            logger.info("GET    Conditions Avg: " + ((double)totalGetConditions / (double)numGetConditions) + " ms");
            logger.info("PUT    Conditions #: " + numPutConditions);
            logger.info("PUT    Conditions Avg: " + ((double)totalPutConditions / (double)numPutConditions) + " ms");
        }

    }

    /*
        This test is designed to study performance on REST endpoints.
        This is a serial version where all calls happen on cascade.
     */
    @Test
    void serialWithAutoResolve() {

        int numTriggers = 2000;
        long startCall, endCall, timeCall, numCalls = 0, totalTimeCalls = 0, more1sec = 0;
        long numPostTrigger = 0, numDeleteTrigger = 0;
        long totalPostTrigger = 0, totalDeleteTrigger = 0;
        long numPostDampening = 0;
        long totalPostDampening = 0;
        long numPutConditionsFiring = 0, numPutConditionsAutoresolve = 0;
        long totalPutConditionsFiring = 0, totalPutConditionsAutoresolve = 0;

        for (int i = 0; i < numTriggers; i++) {

            // Triggger

            startCall = System.currentTimeMillis();
            client.delete(path: "triggers/test-crud-" + i);
            endCall = System.currentTimeMillis();
            numCalls++;
            timeCall = (endCall - startCall);
            numDeleteTrigger++;
            totalDeleteTrigger += timeCall;
            if (timeCall > 1000) {
                logger.warn("WARNING: >1s call with " + timeCall + " ms (delete)")
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
            numPostTrigger++;
            totalPostTrigger += timeCall;
            if (timeCall > 1000) {
                logger.warn("WARNING: >1s call with " + timeCall + " ms (trigger)")
                more1sec++;
            }
            totalTimeCalls += timeCall;

            // Dampening

            Dampening d = Dampening.forRelaxedCount("", "test-crud-" + i, Mode.FIRING, 1, 2);

            startCall = System.currentTimeMillis();
            resp = client.post(path: "triggers/test-crud-" + i + "/dampenings", body: d);
            endCall = System.currentTimeMillis();
            assertEquals(200, resp.status)
            numCalls++;
            timeCall = (endCall - startCall);
            numPostDampening++;
            totalPostDampening += timeCall;
            if (timeCall > 1000) {
                logger.warn("WARNING: >1s call with " + timeCall + " ms (dampenings)")
                more1sec++;
            }
            totalTimeCalls += timeCall;


            // Firing Conditions

            ThresholdCondition testCond1 = new ThresholdCondition("test-crud-" + i, Mode.FIRING,
                    "No-Metric", ThresholdCondition.Operator.GT, 10.12);
            ThresholdCondition testCond2 = new ThresholdCondition("test-crud-" + i, Mode.FIRING,
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
            numPutConditionsFiring++;
            totalPutConditionsFiring += timeCall;
            if (timeCall > 1000) {
                logger.warn("WARNING: >1s call with " + timeCall + " ms (firing)")
                more1sec++;
            }
            totalTimeCalls += timeCall;

            // Autoresolve Conditions

            ThresholdCondition testAutoCond1 = new ThresholdCondition("test-crud-" + i, Mode.AUTORESOLVE,
                    "No-Metric", ThresholdCondition.Operator.LTE, 10.12);
            ThresholdCondition testAutoCond2 = new ThresholdCondition("test-crud-" + i, Mode.AUTORESOLVE,
                    "No-Metric", ThresholdCondition.Operator.GTE, 4.10);
            Collection<Condition> autoConditions = new ArrayList<>(2);
            autoConditions.add( testAutoCond1 );
            autoConditions.add( testAutoCond2 );
            startCall = System.currentTimeMillis();
            resp = client.put(path: "triggers/test-crud-" + i + "/conditions/autoresolve", body: autoConditions)
            endCall = System.currentTimeMillis();
            assertEquals(200, resp.status)
            assertEquals(2, resp.data.size())
            numCalls++;
            timeCall = (endCall - startCall);
            numPutConditionsAutoresolve++;
            totalPutConditionsAutoresolve += timeCall;
            if (timeCall > 1000) {
                logger.warn("WARNING: >1s call with " + timeCall + " ms (autoresolve)")
                more1sec++;
            }
            totalTimeCalls += timeCall;

            logger.info("Iteration: " + i);
            logger.info("Total calls: " + numCalls);
            logger.info("Avg: " + ((double)totalTimeCalls / (double)numCalls) + " ms");
            logger.info("> 1 sec: " + more1sec);
            logger.info("POST   Trigger #: " + numPostTrigger);
            logger.info("POST   Trigger Avg: " + ((double)totalPostTrigger / (double)numPostTrigger) + " ms");
            logger.info("DELETE Trigger #: " + numDeleteTrigger);
            logger.info("DELETE Trigger Avg: " + ((double)totalDeleteTrigger / (double)numDeleteTrigger) + " ms");
            logger.info("POST   Dampening #: " + numPostDampening);
            logger.info("POST   Dampening Avg: " + ((double)totalPostDampening / (double)numPostDampening) + " ms");
            logger.info("PUT    Conditions Firing #: " + numPutConditionsFiring);
            logger.info("PUT    Conditions Firing Avg: "
                    + ((double)totalPutConditionsFiring / (double)numPutConditionsFiring) + " ms");
            logger.info("PUT    Conditions Autoresolve #: " + numPutConditionsFiring);
            logger.info("PUT    Conditions Autoresolve Avg: "
                    + ((double)totalPutConditionsAutoresolve / (double)numPutConditionsAutoresolve) + " ms");

        }

    }

    /*
        This test is designed to study performance on REST endpoints.
        This is a concurrent version where dampening and conditions are called concurrently.

        This is a pure experimental test.
     */
    void concurrentWithAutoResolve() {

        def numTriggers = 2000;
        def numCalls = 0
        def totalTimeCalls = 0
        def more1sec = 0

        for (int i = 0; i < numTriggers; i++) {

            // Triggger

            def startCall = System.currentTimeMillis();
            client.delete(path: "triggers/test-crud-" + i);
            def endCall = System.currentTimeMillis();
            numCalls++;
            def timeCall = (endCall - startCall);
            if (timeCall > 1000) {
                logger.warn("WARNING: >1s call with " + timeCall + " ms (delete)")
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
                logger.warn("WARNING: >1s call with " + timeCall + " ms (trigger)")
                more1sec++;
            }
            totalTimeCalls += timeCall;

            // Dampening

            def Promise dampeningPromise = task {

                RESTClient dClient = new RESTClient(baseURI, ContentType.JSON)
                dClient.handler.failure = { it }
                dClient.defaultRequestHeaders.Authorization = "Basic amRvZTpwYXNzd29yZA=="
                dClient.headers.put("Hawkular-Tenant", "28026b36-8fe4-4332-84c8-524e173a68bf")

                Dampening d = Dampening.forRelaxedCount("", "test-crud-" + i, Mode.FIRING, 1, 2);

                def dStartCall = System.currentTimeMillis();
                resp = dClient.post(path: "triggers/test-crud-" + i + "/dampenings", body: d);
                def dEndCall = System.currentTimeMillis();
                assertEquals(200, resp.status)
                def dTimeCall = (dEndCall - dStartCall);
                dClient.shutdown()

                return dTimeCall;
            }

            // Firing Conditions

            def Promise conditionsPromise = task {

                RESTClient cClient = new RESTClient(baseURI, ContentType.JSON)
                cClient.handler.failure = { it }
                cClient.defaultRequestHeaders.Authorization = "Basic amRvZTpwYXNzd29yZA=="
                cClient.headers.put("Hawkular-Tenant", "28026b36-8fe4-4332-84c8-524e173a68bf")

                ThresholdCondition testCond1 = new ThresholdCondition("test-crud-" + i, Mode.FIRING,
                        "No-Metric", ThresholdCondition.Operator.GT, 10.12);
                ThresholdCondition testCond2 = new ThresholdCondition("test-crud-" + i, Mode.FIRING,
                        "No-Metric", ThresholdCondition.Operator.LT, 4.10);
                Collection<Condition> conditions = new ArrayList<>(2);
                conditions.add( testCond1 );
                conditions.add( testCond2 );
                def cStartCall = System.currentTimeMillis();
                def cResp = cClient.put(path: "triggers/test-crud-" + i + "/conditions/firing", body: conditions)
                def cEndCall = System.currentTimeMillis();
                assertEquals(200, cResp.status)
                assertEquals(2, cResp.data.size())
                def cTimeCall = (cEndCall - cStartCall);
                cClient.shutdown()

                return cTimeCall;
            }

            // Autoresolve Conditions

            def Promise autoConditionsPromise = task {

                RESTClient acClient = new RESTClient(baseURI, ContentType.JSON)
                acClient.handler.failure = { it }
                acClient.defaultRequestHeaders.Authorization = "Basic amRvZTpwYXNzd29yZA=="
                acClient.headers.put("Hawkular-Tenant", "28026b36-8fe4-4332-84c8-524e173a68bf")

                ThresholdCondition testAutoCond1 = new ThresholdCondition("test-crud-" + i, Mode.FIRING,
                        "No-Metric", ThresholdCondition.Operator.LTE, 10.12);
                ThresholdCondition testAutoCond2 = new ThresholdCondition("test-crud-" + i, Mode.FIRING,
                        "No-Metric", ThresholdCondition.Operator.GTE, 4.10);
                Collection<Condition> autoConditions = new ArrayList<>(2);
                autoConditions.add( testAutoCond1 );
                autoConditions.add( testAutoCond2 );
                def acStartCall = System.currentTimeMillis();
                def acResp = acClient.put(path: "triggers/test-crud-" + i + "/conditions/autoresolve",
                        body: autoConditions)
                def acEndCall = System.currentTimeMillis();
                assertEquals(200, acResp.status)
                assertEquals(2, acResp.data.size())
                def acTimeCall = (acEndCall - acStartCall);
                acClient.shutdown()

                return acTimeCall;
            }


            def dampeningTimeCall= dampeningPromise.val;
            if (dampeningTimeCall > 1000) {
                logger.warn("WARNING: >1s call with " + dampeningTimeCall + " ms (dampening)")
                more1sec++
            }
            totalTimeCalls += dampeningTimeCall
            numCalls++;

            def conditionsTimeCall= conditionsPromise.val;
            if (conditionsTimeCall > 1000) {
                logger.warn("WARNING: >1s call with " + conditionsTimeCall + " ms (firing)")
                more1sec++
            }
            totalTimeCalls += conditionsTimeCall
            numCalls++;

            def autoConditionsTimeCall= autoConditionsPromise.val;
            if (autoConditionsTimeCall > 1000) {
                logger.warn("WARNING: >1s call with " + autoConditionsTimeCall + " ms (autoresolve)")
                more1sec++
            }
            totalTimeCalls += autoConditionsTimeCall
            numCalls++;

            logger.info("Iteration: " + i);
            logger.info("Total calls: " + numCalls);
            logger.info("Avg: " + ((double)totalTimeCalls / (double)numCalls) + " ms");
            logger.info("> 1 sec: " + more1sec);
        }

    }
}
