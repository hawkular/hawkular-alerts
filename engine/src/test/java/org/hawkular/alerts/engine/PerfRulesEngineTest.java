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
package org.hawkular.alerts.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.CompareCondition;
import org.hawkular.alerts.api.model.condition.StringCondition;
import org.hawkular.alerts.api.model.condition.ThresholdCondition;
import org.hawkular.alerts.api.model.condition.ThresholdRangeCondition;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.data.AvailabilityType;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.engine.impl.DroolsRulesEngineImpl;
import org.hawkular.alerts.engine.service.RulesEngine;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * <p>
 * Performance tests of RulesEngine implementation.
 * </p>
 * These tests are intended to be used to validate the rules design under heavy load of data and rules.
 * These are unit tests, so the focus is on the rules engine process itself it is not tested the integration between
 * other components.
 *
 * @author Lucas Ponce
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PerfRulesEngineTest {
    private static final MsgLogger log = MsgLogging.getMsgLogger(PerfRulesEngineTest.class);

    RulesEngine rulesEngine = new DroolsRulesEngineImpl();
    List<Alert> alerts = new ArrayList<>();
    Set<Dampening> pendingTimeouts = new HashSet<>();
    TreeSet<Data> datums = new TreeSet<Data>();

    @Before
    public void before() {
        rulesEngine.addGlobal("log", log);
        rulesEngine.addGlobal("alerts", alerts);
        rulesEngine.addGlobal("pendingTimeouts", pendingTimeouts);
    }

    @After
    public void after() {
        rulesEngine.reset();
        alerts.clear();
        pendingTimeouts.clear();
        datums.clear();
    }

    @SuppressWarnings("unchecked")
    private void perfThreshold(String test, int nDefinitions, int nData, int nQueue) throws Exception {
        List definitions = new ArrayList();

        for (int i = 0; i < nDefinitions; i++) {

            Trigger tN = new Trigger("tenant", "trigger-" + i, "Threshold-LT");
            ThresholdCondition tNc1 = new ThresholdCondition("tenant", "trigger-" + i,
                    "NumericData-" + i,
                    ThresholdCondition.Operator.LT, 10.0);
            tN.setEnabled(true);
            definitions.add(tN);
            definitions.add(tNc1);
        }

        if (nQueue > 0) {
            for (int i = 0; i < nData; i++) {
                for (int j = 0; j < nQueue; j++) {
                    datums.add(Data.forNumeric("tenant", "NumericData-" + i, ((i * nQueue) + j) * 1000, 5.0));
                }
            }
        } else {
            for (int i = 0; i < nData; i++) {
                datums.add(Data.forNumeric("tenant", "NumericData-" + i, i * 1000, 5.0));
            }
        }

        rulesEngine.addFacts(definitions);
        rulesEngine.addData(datums);

        long start = System.currentTimeMillis();

        rulesEngine.fire();

        long stop = System.currentTimeMillis();

        if (nQueue > 0) {
            assert alerts.size() == nData * nQueue : alerts;
        } else {
            assert alerts.size() == nData : alerts;
        }

        report(test, nDefinitions, nData, start, stop);
    }

    @SuppressWarnings("unchecked")
    private void perfRange(String test, int nDefinitions, int nData, int nQueue) throws Exception {
        List definitions = new ArrayList();

        for (int i = 0; i < nDefinitions; i++) {
            Trigger tN = new Trigger("tenant", "trigger-" + i, "Range");
            ThresholdRangeCondition tNc1 = new ThresholdRangeCondition("tenant", "trigger-" + i,
                                                                       "NumericData-" + i,
                                                                       ThresholdRangeCondition.Operator.INCLUSIVE,
                                                                       ThresholdRangeCondition.Operator.INCLUSIVE,
                                                                       10.0, 15.0,
                                                                       true);
            tN.setEnabled(true);
            definitions.add(tN);
            definitions.add(tNc1);
        }

        if (nQueue > 0) {
            for (int i = 0; i < nData; i++) {
                for (int j = 0; j < nQueue; j++) {
                    datums.add(Data.forNumeric("tenant", "NumericData-" + i, ((i * nQueue) + j) * 1000, 12.5));
                }
            }
        } else {
            for (int i = 0; i < nData; i++) {
                datums.add(Data.forNumeric("tenant", "NumericData-" + i, i * 1000, 12.5));
            }
        }

        rulesEngine.addFacts(definitions);
        rulesEngine.addData(datums);

        long start = System.currentTimeMillis();

        rulesEngine.fire();

        long stop = System.currentTimeMillis();

        if (nQueue > 0) {
            assert alerts.size() == nData * nQueue : alerts;
        } else {
            assert alerts.size() == nData : alerts;
        }

        report(test, nDefinitions, nData, start, stop);
    }

    @SuppressWarnings("unchecked")
    private void perfCompare(String test, int nDefinitions, int nData, int nQueue) throws Exception {
        List definitions = new ArrayList();

        for (int i = 0; i < nDefinitions; i++) {
            Trigger tN = new Trigger("tenant", "trigger-" + i, "Compare-D1-LT-Half-D2");
            CompareCondition tNc1 = new CompareCondition("tenant", "trigger-" + i,
                                                         "NumericData-a-" + i,
                                                         CompareCondition.Operator.LT, 0.5, "NumericData-b-" + i);

            tN.setEnabled(true);
            definitions.add(tN);
            definitions.add(tNc1);
        }

        if (nQueue > 0) {
            for (int i = 0; i < nData; i++) {
                for (int j = 0; j < nQueue; j++) {
                    datums.add(Data.forNumeric("tenant", "NumericData-a-" + i, ((i * nQueue) + j) * 1000, 10d));
                    datums.add(Data.forNumeric("tenant", "NumericData-b-" + i, ((i * nQueue) + j) * 1000, 30d));
                }
            }
        } else {
            for (int i = 0; i < nData; i++) {
                datums.add(Data.forNumeric("tenant", "NumericData-a-" + i, i * 1000, 10d));
                datums.add(Data.forNumeric("tenant", "NumericData-b-" + i, i * 1000, 30d));
            }
        }

        rulesEngine.addFacts(definitions);
        rulesEngine.addData(datums);

        long start = System.currentTimeMillis();

        rulesEngine.fire();

        long stop = System.currentTimeMillis();

        if (nQueue > 0) {
            assert alerts.size() == nData * nQueue : alerts;
        } else {
            assert alerts.size() == nData : alerts;
        }

        report(test, nDefinitions, nData, start, stop);
    }

    @SuppressWarnings("unchecked")
    private void perfString(String test, int nDefinitions, int nData, int nQueue) throws Exception {
        List definitions = new ArrayList();

        for (int i = 0; i < nDefinitions; i++) {
            Trigger tN = new Trigger("tenant", "trigger-" + i, "String-StartsWith");
            StringCondition tNc1 = new StringCondition("tenant", "trigger-" + i,
                                                       "StringData-" + i,
                                                       StringCondition.Operator.STARTS_WITH, "Fred", false);
            tN.setEnabled(true);
            definitions.add(tN);
            definitions.add(tNc1);
        }

        if (nQueue > 0) {
            for (int i = 0; i < nData; i++) {
                for (int j = 0; j < nQueue; j++) {
                    datums.add(Data.forString("tenant", "StringData-" + i, (i * nQueue) + j, "Fred And Barney"));
                }
            }
        } else {
            for (int i = 0; i < nData; i++) {
                datums.add(Data.forString("tenant", "StringData-" + i, i, "Fred And Barney"));
            }
        }

        rulesEngine.addFacts(definitions);
        rulesEngine.addData(datums);

        long start = System.currentTimeMillis();

        rulesEngine.fire();

        long stop = System.currentTimeMillis();

        if (nQueue > 0) {
            assert alerts.size() == nData * nQueue : alerts;
        } else {
            assert alerts.size() == nData : alerts;
        }

        report(test, nDefinitions, nData, start, stop);
    }

    @SuppressWarnings("unchecked")
    private void perfAvailability(String test, int nDefinitions, int nData, int nQueue) throws Exception {
        List definitions = new ArrayList();

        for (int i = 0; i < nDefinitions; i++) {
            Trigger tN = new Trigger("tenant", "trigger-" + i, "Avail-DOWN");
            AvailabilityCondition tNc1 = new AvailabilityCondition("tenant", "trigger-" + i, "AvailData-" + i,
                    AvailabilityCondition.Operator.NOT_UP);
            tN.setEnabled(true);
            definitions.add(tN);
            definitions.add(tNc1);
        }

        if (nQueue > 0) {
            for (int i = 0; i < nData; i++) {
                for (int j = 0; j < nQueue; j++) {
                    datums.add(Data.forAvailability("tenant", "AvailData-" + i, (i * nQueue) + j,
                            AvailabilityType.DOWN));
                }
            }
        } else {
            for (int i = 0; i < nData; i++) {
                datums.add(Data.forAvailability("tenant", "AvailData-" + i, i, AvailabilityType.DOWN));
            }
        }

        rulesEngine.addFacts(definitions);
        rulesEngine.addData(datums);

        long start = System.currentTimeMillis();

        rulesEngine.fire();

        long stop = System.currentTimeMillis();

        if (nQueue > 0) {
            assert alerts.size() == nData * nQueue : alerts;
        } else {
            assert alerts.size() == nData : alerts;
        }

        report(test, nDefinitions, nData, start, stop);
    }

    @SuppressWarnings("unchecked")
    private void perfMixedTwoConditions(String test, int nDefinitions, int nData, int nQueue) throws Exception {
        List definitions = new ArrayList();

        for (int i = 0; i < nDefinitions; i++) {

            Trigger tN = new Trigger("tenant", "trigger-" + i, "Threshold-LT");
            ThresholdCondition tNc1 = new ThresholdCondition("tenant", "trigger-" + i,
                    2,
                    1,
                    "NumericData-a-" + i,
                    ThresholdCondition.Operator.LT,
                    10.0);
            ThresholdRangeCondition tNc2 = new ThresholdRangeCondition("tenant", "trigger-" + i, 2, 2,
                    "NumericData-b-" + i,
                    ThresholdRangeCondition.Operator.INCLUSIVE,
                    ThresholdRangeCondition.Operator.INCLUSIVE,
                    10.0,
                    15.0,
                    true);

            tN.setEnabled(true);
            definitions.add(tN);
            definitions.add(tNc1);
            definitions.add(tNc2);
        }

        if (nQueue > 0) {
            for (int i = 0; i < nData; i++) {
                for (int j = 0; j < nQueue; j++) {
                    datums.add(Data.forNumeric("tenant", "NumericData-a-" + i, ((i * nQueue) + j) * 1000, 5.0));
                    datums.add(Data.forNumeric("tenant", "NumericData-b-" + i, ((i * nQueue) + j) * 1000, 12.5));
                }
            }
        } else {
            for (int i = 0; i < nData; i++) {
                datums.add(Data.forNumeric("tenant", "NumericData-a-" + i, i * 1000, 5.0));
                datums.add(Data.forNumeric("tenant", "NumericData-b-" + i, i * 1000, 12.5));

            }
        }

        rulesEngine.addFacts(definitions);
        rulesEngine.addData(datums);

        long start = System.currentTimeMillis();

        rulesEngine.fire();

        long stop = System.currentTimeMillis();

        if (nQueue > 0) {
            assert alerts.size() == nData * nQueue : alerts;
        } else {
            assert alerts.size() == nData : alerts;
        }

        report(test, nDefinitions, nData, start, stop);

    }

    @SuppressWarnings("unchecked")
    private void perfMixedLargeConditions(String test, int nDefinitions, int nConditions, int nData, int nQueue)
            throws Exception {
        List definitions = new ArrayList();

        for (int i = 0; i < nDefinitions; i++) {

            Trigger tN = new Trigger("tenant", "trigger-" + i, "Threshold-LT");
            definitions.add(tN);

            for (int j = 1; j <= nConditions; j++) {

                ThresholdCondition tNcj = new ThresholdCondition("tenant", "trigger-" + i,
                        nConditions,
                        j,
                        "NumericData-t" + i + "-c" + j,
                        ThresholdCondition.Operator.LT,
                        10.0);
                definitions.add(tNcj);
            }
            tN.setEnabled(true);
        }

        if (nQueue > 0) {
            for (int i = 0; i < nData; i++) {
                for (int j = 0; j < nQueue; j++) {
                    for (int k = 1; k <= nConditions; k++) {
                        datums.add(Data.forNumeric("tenant", "NumericData-t" + i + "-c" + k, ((i * nQueue) + j) * 1000,
                                5.0));
                    }
                }
            }
        } else {
            for (int i = 0; i < nData; i++) {
                for (int j = 1; j <= nConditions; j++) {
                    datums.add(Data.forNumeric("tenant", "NumericData-t" + i + "-c" + j, i * 1000, 5.0));
                }
            }
        }

        rulesEngine.addFacts(definitions);
        rulesEngine.addData(datums);

        long start = System.currentTimeMillis();

        rulesEngine.fire();

        long stop = System.currentTimeMillis();

        if (nQueue > 0) {
            assert alerts.size() == nData * nQueue : alerts;
        } else {
            assert alerts.size() == nData : alerts;
        }

        report(test, nDefinitions, nData, start, stop);

    }


    private void report(String description, int numDefinitions, int numData, long start, long stop) {
        log.info("Report: " + description + " -- Definitions: " + numDefinitions + " -- Data: " + numData + " -- " +
                         "Total: " + (stop - start) + " ms ");
    }

    @Test
    public void perf000ThresholdNoQueueSmall() throws Exception {
        perfThreshold("perf000ThresholdNoQueueSmall", 1000, 1000, 0);
    }

    @Test
    public void perf001ThresholdNoQueueMedium() throws Exception {
        perfThreshold("perf001ThresholdNoQueueMedium", 5000, 5000, 0);
    }

    @Test
    public void perf002ThresholdNoQueueLarge() throws Exception {
        perfThreshold("perf002ThresholdNoQueueLarge", 10000, 10000, 0);
    }

    @Test
    public void perf003RangeNoQueueSmall() throws Exception {
        perfRange("perf003RangeNoQueueSmall", 1000, 1000, 0);
    }

    @Test
    public void perf004RangeNoQueueMedium() throws Exception {
        perfRange("perf004RangeNoQueueMedium", 5000, 5000, 0);
    }

    @Test
    public void perf005RangeNoQueueLarge() throws Exception {
        perfRange("perf005RangeNoQueueLarge", 10000, 10000, 0);
    }

    @Test
    public void perf006CompareNoQueueSmall() throws Exception {
        /*
            We use 500 as nData as internally we feed 2 * nData so we compare same # objects inside the rule engine.
         */
        perfCompare("perf006CompareNoQueueSmall", 1000, 500, 0);
    }

    @Test
    public void perf007CompareNoQueueMedium() throws Exception {
        /*
            We use 2500 as nData as internally we feed 2 * nData so we compare same # objects inside the rule engine.
         */
        perfCompare("perf007CompareNoQueueMedium", 5000, 2500, 0);
    }

    @Test
    public void perf008CompareNoQueueLarge() throws Exception {
        /*
            We use 5000 as nData as internally we feed 2 * nData so we compare same # objects inside the rule engine.
         */
        perfCompare("perf008CompareNoQueueLarge", 10000, 5000, 0);
    }

    @Test
    public void perf009StringNoQueueSmall() throws Exception {
        perfString("perf009StringNoQueueSmall", 1000, 1000, 0);
    }

    @Test
    public void perf010StringNoQueueMedium() throws Exception {
        perfString("perf010StringNoQueueMedium", 5000, 5000, 0);
    }

    @Test
    public void perf011StringNoQueueLarge() throws Exception {
        perfString("perf011StringNoQueueLarge", 10000, 10000, 0);
    }

    @Test
    public void perf012AvailabilityNoQueueSmall() throws Exception {
        perfAvailability("perf012AvailabilityNoQueueSmall", 1000, 1000, 0);
    }

    @Test
    public void perf013AvailabilityNoQueueMedium() throws Exception {
        perfAvailability("perf013AvailabilityNoQueueMedium", 5000, 5000, 0);
    }

    @Test
    public void perf014AvailabilityNoQueueLarge() throws Exception {
        perfAvailability("perf014AvailabilityNoQueueLarge", 10000, 10000, 0);
    }

    @Test
    public void perf015MixedTwoConditionsNoQueueSmall() throws Exception {
        /*
            We use 500 as nData as internally we feed 2 * nData so we compare same # objects inside the rule engine.
         */
        perfMixedTwoConditions("perf015MixedTwoConditionsNoQueueSmall", 1000, 1000, 0);
    }

    @Test
    public void perf016MixedTwoConditionsNoQueueMedium() throws Exception {
        /*
            We use 2500 as nData as internally we feed 2 * nData so we compare same # objects inside the rule engine.
         */
        perfMixedTwoConditions("perf016MixedTwoConditionsNoQueueMedium", 5000, 2500, 0);
    }

    @Test
    public void perf017MixedTwoConditionsNoQueueLarge() throws Exception {
        /*
            We use 5000 as nData as internally we feed 2 * nData so we compare same # objects inside the rule engine.
         */
        perfMixedTwoConditions("perf017MixedTwoConditionsNoQueueLarge", 10000, 5000, 0);
    }

    @Test
    public void perf018ThresholdQueue() throws Exception {
        /*
            We have 10 data in the queue
         */
        perfThreshold("perf018ThresholdQueue", 1000, 1000, 10);
    }

    @Test
    public void perf019RangeQueue() throws Exception {
        /*
            We have 10 data in the queue
         */
        perfRange("perf019RangeQueue", 1000, 1000, 10);
    }

    @Test
    public void perf020CompareQueue() throws Exception {
        /*
            We have 10 data in the queue
         */
        perfCompare("perf020CompareQueue", 1000, 1000, 10);
    }

    @Test
    public void perf021StringQueue() throws Exception {
        /*
            We have 10 data in the queue
         */
        perfCompare("perf021StringQueue", 1000, 1000, 10);
    }

    @Test
    public void perf022AvailabilityQueue() throws Exception {
        /*
            We have 10 data in the queue
         */
        perfCompare("perf022AvailabilityQueue", 1000, 1000, 10);
    }

    @Test
    public void perf023LargeMixedConditionsNoQueueSmall() throws Exception {
        /*
            We have 25 conditions.
         */
        perfMixedLargeConditions("perf023LargeMixedConditions", 1000, 25, 1000, 0);
    }

    /*
        These tests require to increase the JVM setting.
        As we want to run this perf test from travis we will maintain them disabled for future uses.
     */

    // For manual testing
    public void perf024LargeMixedConditionsNoQueueMedium() throws Exception {
        /*
            We have 25 conditions.
         */
        perfMixedLargeConditions("perf023LargeMixedConditions", 5000, 25, 5000, 0);
    }

    // For manual testing
    public void perf025LargeMixedConditionsQueueSmall() throws Exception {
        /*
            We have 25 conditions.
            We have 10 data in the queue.
         */
        perfMixedLargeConditions("perf023LargeMixedConditions", 1000, 25, 1000, 10);
    }


    // For manual testing
    public void perf026LargeMixedConditionsQueueMedium() throws Exception {
        /*
            We have 25 conditions.
            We have 10 data in the queue.
         */
        perfMixedLargeConditions("perf023LargeMixedConditions", 5000, 25, 5000, 10);
    }
}
