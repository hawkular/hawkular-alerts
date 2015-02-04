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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.CompareCondition;
import org.hawkular.alerts.api.model.condition.StringCondition;
import org.hawkular.alerts.api.model.condition.ThresholdCondition;
import org.hawkular.alerts.api.model.condition.ThresholdRangeCondition;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.data.Availability;
import org.hawkular.alerts.api.model.data.Availability.AvailabilityType;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.data.NumericData;
import org.hawkular.alerts.api.model.data.StringData;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.engine.impl.DroolsRulesEngineImpl;
import org.hawkular.alerts.engine.rules.RulesEngine;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Basic test of RulesEngine implementation.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class RulesEngineTest {
    private static final Logger log = LoggerFactory.getLogger(RulesEngineTest.class);

    Set < Data > datums;

    @Before
    public void initDatum() {
        datums = new HashSet<Data>();
        Random r = new Random();
        for (int i = 0; i < 10; ++i) {
            NumericData m = new NumericData("NumericData-01", i, r.nextDouble() * 20);
            datums.add(m);
        }
        for (int i = 0; i < 10; ++i) {
            NumericData m = new NumericData("NumericData-02", i, r.nextDouble() * 20);
            datums.add(m);
        }
        for (int i = 0; i < 10; ++i) {
            NumericData m = new NumericData("NumericData-03", i, r.nextDouble() * 20);
            datums.add(m);
        }

        datums.add(new StringData("StringData-01", 1, "Barney"));
        datums.add(new StringData("StringData-01", 2, "Fred and Barney"));
        datums.add(new StringData("StringData-02", 3, "Fred Flintstone"));

        datums.add(new Availability("Availability-01", 1, AvailabilityType.UP));
        datums.add(new Availability("Availability-01", 2, AvailabilityType.UP));
        datums.add(new Availability("Availability-01", 3, AvailabilityType.UNAVAILABLE));
    }

    @Test
    public void basicAlertsTest() {

        List alerts = new ArrayList();
        RulesEngine rulesEngine = new DroolsRulesEngineImpl();
        rulesEngine.addGlobal("log", log);
        rulesEngine.addGlobal("alerts", alerts);

        // go !
        Trigger t1 = new Trigger("trigger-1", "NumericData-01-low");
        ThresholdCondition t1c1 = new ThresholdCondition("trigger-1", 1, 1,
                "NumericData-01",
                ThresholdCondition.Operator.LT, 10.0);
        Dampening t1d = new Dampening("trigger-1", Dampening.Type.STRICT, 2, 2, 0);

        Trigger t2 = new Trigger("trigger-2", "NumericData-01-02-high");
        ThresholdCondition t2c1 = new ThresholdCondition("trigger-2", 2, 1,
                "NumericData-01",
                ThresholdCondition.Operator.GTE, 15.0);
        ThresholdCondition t2c2 = new ThresholdCondition("trigger-2", 2, 2,
                "NumericData-02",
                ThresholdCondition.Operator.GTE, 15.0);

        Trigger t3 = new Trigger("trigger-3", "NumericData-03-range");
        ThresholdRangeCondition t3c1 = new ThresholdRangeCondition("trigger-3",  1, 1,
                "NumericData-03",
                ThresholdRangeCondition.Operator.INCLUSIVE,
                ThresholdRangeCondition.Operator.INCLUSIVE,
                10.0, 15.0,
                true);

        Trigger t4 = new Trigger("trigger-4", "CompareData-01-d1-lthalf-d2");
        CompareCondition t4c1 = new CompareCondition("trigger-4", 1, 1,
                "NumericData-01",
                CompareCondition.Operator.LT, 0.5, "NumericData-02");

        Trigger t5 = new Trigger("trigger-5", "StringData-01-starts");
        StringCondition t5c1 = new StringCondition("trigger-5", 1, 1,
                "StringData-01",
                StringCondition.Operator.STARTS_WITH, "Fred", false);

        Trigger t6 = new Trigger("trigger-6", "Availability-01-NOT-UP");
        AvailabilityCondition t6c1 = new AvailabilityCondition("trigger-6", 1, 1,
                "Availability-01",
                AvailabilityCondition.Operator.NOT_UP);

        rulesEngine.addFact(t1);
        rulesEngine.addFact(t1c1);
        rulesEngine.addFact(t1d);

        rulesEngine.addFact(t2);
        rulesEngine.addFact(t2c1);
        rulesEngine.addFact(t2c2);

        rulesEngine.addFact(t3);
        rulesEngine.addFact(t3c1);

        rulesEngine.addFact(t4);
        rulesEngine.addFact(t4c1);

        rulesEngine.addFact(t5);
        rulesEngine.addFact(t5c1);

        rulesEngine.addFact(t6);
        rulesEngine.addFact(t6c1);

        rulesEngine.addFacts(datums);

        rulesEngine.fire();
    }
}
