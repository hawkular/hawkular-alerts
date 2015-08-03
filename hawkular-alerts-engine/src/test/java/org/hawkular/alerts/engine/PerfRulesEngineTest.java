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
package org.hawkular.alerts.engine;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hawkular.alerts.api.model.condition.Alert;
import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.CompareCondition;
import org.hawkular.alerts.api.model.condition.StringCondition;
import org.hawkular.alerts.api.model.condition.ThresholdCondition;
import org.hawkular.alerts.api.model.condition.ThresholdRangeCondition;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.data.Availability;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.data.NumericData;
import org.hawkular.alerts.api.model.data.StringData;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.engine.impl.DroolsRulesEngineImpl;
import org.hawkular.alerts.engine.rules.RulesEngine;
import org.jboss.logging.Logger;
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
    private static final Logger log = Logger.getLogger(PerfRulesEngineTest.class);

    RulesEngine rulesEngine = new DroolsRulesEngineImpl();
    List<Alert> alerts = new ArrayList<>();
    Set<Dampening> pendingTimeouts = new HashSet<>();
    Set<Data> datums = new HashSet<Data>();

    @Before
    public void before() {
        PerfLogger perfLogger = new PerfLogger("PERFLOGGER");
        rulesEngine.addGlobal("log", perfLogger);
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

    private void perfThreshold(String test, int nDefinitions, int nData, int nQueue) throws Exception {
        List definitions = new ArrayList();

        for (int i = 0; i < nDefinitions; i++) {

            Trigger tN = new Trigger("trigger-" + i, "Threshold-LT");
            ThresholdCondition tNc1 = new ThresholdCondition("trigger-" + i,
                                                             "NumericData-" + i,
                                                             ThresholdCondition.Operator.LT, 10.0);
            tN.setEnabled(true);
            definitions.add(tN);
            definitions.add(tNc1);
        }

        if (nQueue > 0) {
            for (int i = 0; i < nData; i++) {
                for (int j = 0; j < nQueue; j++) {
                    datums.add(new NumericData("NumericData-" + i, (i * nQueue) + j, 5.0));
                }
            }
        } else {
            for (int i = 0; i < nData; i++) {
                datums.add(new NumericData("NumericData-" + i, i, 5.0));
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

    private void perfRange(String test, int nDefinitions, int nData, int nQueue) throws Exception {
        List definitions = new ArrayList();

        for (int i = 0; i < nDefinitions; i++) {
            Trigger tN = new Trigger("trigger-" + i, "Range");
            ThresholdRangeCondition tNc1 = new ThresholdRangeCondition("trigger-" + i,
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
                    datums.add(new NumericData("NumericData-" + i, (i * nQueue) + j, 12.5));
                }
            }
        } else {
            for (int i = 0; i < nData; i++) {
                datums.add(new NumericData("NumericData-" + i, i, 12.5));
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

    private void perfCompare(String test, int nDefinitions, int nData, int nQueue) throws Exception {
        List definitions = new ArrayList();

        for (int i = 0; i < nDefinitions; i++) {
            Trigger tN = new Trigger("trigger-" + i, "Compare-D1-LT-Half-D2");
            CompareCondition tNc1 = new CompareCondition("trigger-" + i,
                                                         "NumericData-a-" + i,
                                                         CompareCondition.Operator.LT, 0.5, "NumericData-b-" + i);

            tN.setEnabled(true);
            definitions.add(tN);
            definitions.add(tNc1);
        }

        if (nQueue > 0) {
            for (int i = 0; i < nData; i++) {
                for (int j = 0; j < nQueue; j++) {
                    datums.add(new NumericData("NumericData-a-" + i, (i * nQueue) + j, 10d));
                    datums.add(new NumericData("NumericData-b-" + i, (i * nQueue) + j, 30d));
                }
            }
        } else {
            for (int i = 0; i < nData; i++) {
                datums.add(new NumericData("NumericData-a-" + i, i, 10d));
                datums.add(new NumericData("NumericData-b-" + i, i, 30d));
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

    private void perfString(String test, int nDefinitions, int nData, int nQueue) throws Exception {
        List definitions = new ArrayList();

        for (int i = 0; i < nDefinitions; i++) {
            Trigger tN = new Trigger("trigger-" + i, "String-StartsWith");
            StringCondition tNc1 = new StringCondition("trigger-" + i,
                                                       "StringData-" + i,
                                                       StringCondition.Operator.STARTS_WITH, "Fred", false);
            tN.setEnabled(true);
            definitions.add(tN);
            definitions.add(tNc1);
        }

        if (nQueue > 0) {
            for (int i = 0; i < nData; i++) {
                for (int j = 0; j < nQueue; j++) {
                    datums.add(new StringData("StringData-" + i, (i * nQueue) + j, "Fred And Barney"));
                }
            }
        } else {
            for (int i = 0; i < nData; i++) {
                datums.add(new StringData("StringData-" + i, i, "Fred And Barney"));
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

    private void perfAvailability(String test, int nDefinitions, int nData, int nQueue) throws Exception {
        List definitions = new ArrayList();

        for (int i = 0; i < nDefinitions; i++) {
            Trigger tN = new Trigger("trigger-" + i, "Avail-DOWN");
            AvailabilityCondition tNc1 = new AvailabilityCondition("trigger-" + i,
                                                                   "AvailData-" + i,
                                                                   AvailabilityCondition.Operator.NOT_UP);
            tN.setEnabled(true);
            definitions.add(tN);
            definitions.add(tNc1);
        }

        if (nQueue > 0) {
            for (int i = 0; i < nData; i++) {
                for (int j = 0; j < nQueue; j++) {
                    datums.add(new Availability("AvailData-" + i, (i * nQueue) + j,
                                                Availability.AvailabilityType.DOWN));
                }
            }
        } else {
            for (int i = 0; i < nData; i++) {
                datums.add(new Availability("AvailData-" + i, i, Availability.AvailabilityType.DOWN));
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

    private void perfMixedTwoConditions(String test, int nDefinitions, int nData, int nQueue) throws Exception {
        List definitions = new ArrayList();

        for (int i = 0; i < nDefinitions; i++) {

            Trigger tN = new Trigger("trigger-" + i, "Threshold-LT");
            ThresholdCondition tNc1 = new ThresholdCondition("trigger-" + i, 2, 1,
                                                             "NumericData-a-" + i,
                                                             ThresholdCondition.Operator.LT, 10.0);
            ThresholdRangeCondition tNc2 = new ThresholdRangeCondition("trigger-" + i, 2, 2,
                                                                       "NumericData-b-" + i,
                                                                       ThresholdRangeCondition.Operator.INCLUSIVE,
                                                                       ThresholdRangeCondition.Operator.INCLUSIVE,
                                                                       10.0, 15.0,
                                                                       true);

            tN.setEnabled(true);
            definitions.add(tN);
            definitions.add(tNc1);
            definitions.add(tNc2);
        }

        if (nQueue > 0) {
            for (int i = 0; i < nData; i++) {
                for (int j = 0; j < nQueue; j++) {
                    datums.add(new NumericData("NumericData-a-" + i, (i * nQueue) + j, 5.0));
                    datums.add(new NumericData("NumericData-b-" + i, (i * nQueue) + j, 12.5));
                }
            }
        } else {
            for (int i = 0; i < nData; i++) {
                datums.add(new NumericData("NumericData-a-" + i, i, 5.0));
                datums.add(new NumericData("NumericData-b-" + i, i, 12.5));

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

    public class PerfLogger extends Logger {

        public PerfLogger(String name) {
            super(name);
        }

        @Override
        protected void doLog(Level level,
                             String loggerClassName,
                             Object message,
                             Object[] parameters,
                             Throwable thrown) {
            if (isEnabled(level)) {
                try {
                    final String text = parameters == null || parameters.length == 0 ?
                            String.valueOf(message) : MessageFormat.format(String.valueOf(message), parameters);
                    log.log(level, text, thrown);
                } catch (Throwable ignored) {
                }
            }
        }

        @Override
        protected void doLogf(Level level,
                              String loggerClassName,
                              String format,
                              Object[] parameters,
                              Throwable thrown) {
            if (isEnabled(level)) {
                try {
                    final String text = parameters == null ? String.format(format) : String.format(format, parameters);
                    log.log(level, text, thrown);
                } catch (Throwable ignored) {
                }
            }
        }

        @Override
        public boolean isEnabled(Level level) {
            if (level == Level.INFO || level == Level.WARN || level == Level.ERROR || level == Level.FATAL) {
                return true;
            }
            return false;
        }
    }

}
