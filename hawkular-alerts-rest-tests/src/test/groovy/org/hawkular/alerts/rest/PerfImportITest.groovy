/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
package groovy.org.hawkular.alerts.rest

import groovyx.gpars.dataflow.Promise
import groovyx.net.http.ContentType
import groovyx.net.http.RESTClient
import org.hawkular.alerts.api.model.action.ActionDefinition
import org.hawkular.alerts.api.model.condition.Condition
import org.hawkular.alerts.api.model.condition.ThresholdCondition
import org.hawkular.alerts.api.model.dampening.Dampening
import org.hawkular.alerts.api.model.dampening.Dampening.Type
import org.hawkular.alerts.api.model.export.Definitions
import org.hawkular.alerts.api.model.trigger.FullTrigger
import org.hawkular.alerts.api.model.trigger.Mode
import org.hawkular.alerts.api.model.trigger.Trigger
import org.hawkular.alerts.rest.AbstractITestBase
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static groovyx.gpars.dataflow.Dataflow.task
import static org.junit.Assert.assertEquals

/**
 * CRUD REST operations taking figures for performance investigation.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
class PerfImportITest extends AbstractITestBase {

    static Logger logger = LoggerFactory.getLogger(PerfImportITest.class)

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
        importTest("import1000", 1000);
    }

    @Test
    void import2000() {
        importTest("import2000", 2000);
    }

    @Test
    void import3000() {
        importTest("import3000", 3000);
    }

    @Test
    void import4000() {
        importTest("import4000", 4000);
    }

    @Test
    void import5000() {
        importTest("import5000", 5000);
    }

    @Test
    void import6000() {
        importTest("import6000", 6000);
    }
}
