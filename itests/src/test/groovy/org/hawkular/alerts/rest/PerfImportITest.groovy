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
import org.hawkular.alerts.api.model.dampening.Dampening
import org.hawkular.alerts.api.model.export.Definitions
import org.hawkular.alerts.api.model.trigger.FullTrigger
import org.hawkular.alerts.api.model.trigger.Mode
import org.hawkular.alerts.api.model.trigger.Trigger
import org.hawkular.commons.log.MsgLogger
import org.hawkular.commons.log.MsgLogging
import org.junit.Test

import static org.junit.Assert.assertEquals

/**
 * CRUD REST operations taking figures for performance investigation.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
class PerfImportITest extends AbstractITestBase {

    static MsgLogger logger = MsgLogging.getMsgLogger(PerfImportITest.class)

    Definitions prepareDefinitions(String prefix, int numTriggers) {
        Definitions definitions = new Definitions();
        List<FullTrigger> triggers = new ArrayList<>(numTriggers);
        for (int i = 0; i < numTriggers; i++) {
            Trigger importTrigger = new Trigger(prefix + "-" + i, "Test-Import");
            Dampening d = Dampening.forRelaxedCount("", prefix + "-" + i, Mode.FIRING, 1, 2);
            List<Dampening> dampenings = new ArrayList<>(1);
            dampenings.add(d);
            ThresholdCondition testCond1 = new ThresholdCondition(prefix + "-" + i, Mode.FIRING,
                    "Metric-" + i, ThresholdCondition.Operator.GT, 10.12);
            ThresholdCondition testCond2 = new ThresholdCondition(prefix + "-" + i, Mode.FIRING,
                    "Metric-" + i, ThresholdCondition.Operator.LT, 4.10);
            List<Condition> conditions = new ArrayList<>(2);
            conditions.add( testCond1 );
            conditions.add( testCond2 );
            FullTrigger fullTrigger = new FullTrigger();
            fullTrigger.setTrigger(importTrigger);
            fullTrigger.setDampenings(dampenings);
            fullTrigger.setConditions(conditions);
            triggers.add(fullTrigger);
        }
        definitions.setTriggers(triggers);
        definitions.setActions(new ArrayList<>());
        return definitions;
    }

    void importTest(String prefix, numTriggers) {
        long startCall, endCall, timeCall;

        Definitions definitions = prepareDefinitions(prefix, numTriggers);

        startCall = System.currentTimeMillis();
        def resp = client.post(path: "import/all", body: definitions)
        endCall = System.currentTimeMillis();
        timeCall = (endCall - startCall);
        assertEquals(200, resp.status)

        logger.info("Import " + numTriggers + " took " + timeCall + " ms")
    }

    @Test
    void import1000() {
        logger.info("Test import1000");
        importTest("import1000", 1000);
    }

    @Test
    void import2000() {
        logger.info("Test import2000");
        importTest("import2000", 2000);
    }

    @Test
    void import3000() {
        logger.info("Test import3000");
        importTest("import3000", 3000);
    }

    @Test
    void import4000() {
        logger.info("Test import4000");
        importTest("import4000", 4000);
    }

    @Test
    void import5000() {
        logger.info("Test import5000");
        importTest("import5000", 5000);
    }

    @Test
    void import6000() {
        logger.info("Test import6000");
        importTest("import6000", 6000);
    }

    void importSequentialTest(String prefix, numTriggers, numConditions) {
        long startCall, endCall, timeCall;
        startCall = System.currentTimeMillis();
        for (int i = 0; i < numTriggers; i++) {
            client.delete(path: "triggers/" + prefix + "-Test-Import-Seq-" + i);
            Trigger importTrigger = new Trigger(prefix + "-Test-Import-Seq-" + i, "No-MetricX");
            def resp = client.post(path: "triggers", body: importTrigger);
            assertEquals(200, resp.status)
            Collection<Condition> conditions = new ArrayList<>(numConditions);
            for (int j = 0; j < numConditions; j ++) {
                ThresholdCondition testCond = new ThresholdCondition(prefix + "-Test-Import-Seq-" + i, Mode.FIRING,
                        "No-Metric-" + j, ThresholdCondition.Operator.GT, 10);
                conditions.add(testCond);
            }
            resp = client.put(path: "triggers/" + prefix + "-Test-Import-Seq-" + i + "/conditions/firing",
                    body: conditions)
            assertEquals(200, resp.status)
            if (i % 100 == 0) {
                endCall = System.currentTimeMillis();
                timeCall = (endCall - startCall);
                logger.info("Import " + i + " took " + timeCall + " ms")
            }
        }
        endCall = System.currentTimeMillis();
        timeCall = (endCall - startCall);
        logger.info("Import " + numTriggers + " took " + timeCall + " ms")
    }

    @Test
    void importSeq1000() {
        logger.info("Test importSeq1000");
        importSequentialTest("importSeq1000", 1000, 10);
    }

    @Test
    void importSeq2000() {
        logger.info("Test importSeq2000");
        importSequentialTest("importSeq2000", 2000, 10);
    }

    @Test
    void importSeq3000() {
        logger.info("Test importSeq3000");
        importSequentialTest("importSeq3000", 3000, 10);
    }

    @Test
    void importSeq4000() {
        logger.info("Test importSeq4000");
        importSequentialTest("importSeq4000", 4000, 10);
    }

    @Test
    void importSeq5000() {
        logger.info("Test importSeq5000");
        importSequentialTest("importSeq5000", 5000, 10);
    }

    @Test
    void importSeq6000() {
        logger.info("Test importSeq6000");
        importSequentialTest("importSeq6000", 6000, 10);
    }

    @Test
    void importSeq7000() {
        logger.info("Test importSeq7000");
        importSequentialTest("importSeq7000", 7000, 10);
    }

    @Test
    void importSeq8000() {
        logger.info("Test importSeq8000");
        importSequentialTest("importSeq8000", 8000, 10);
    }

    @Test
    void importSeq9000() {
        logger.info("Test importSeq9000");
        importSequentialTest("importSeq9000", 9000, 10);
    }

    @Test
    void importSeq10000() {
        logger.info("Test importSeq10000");
        importSequentialTest("importSeq10000", 10000, 10);
    }

}
