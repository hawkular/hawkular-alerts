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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.hawkular.alerts.api.model.condition.Alert;
import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.AvailabilityConditionEval;
import org.hawkular.alerts.api.model.condition.CompareCondition;
import org.hawkular.alerts.api.model.condition.CompareConditionEval;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.condition.StringCondition;
import org.hawkular.alerts.api.model.condition.StringConditionEval;
import org.hawkular.alerts.api.model.condition.ThresholdCondition;
import org.hawkular.alerts.api.model.condition.ThresholdConditionEval;
import org.hawkular.alerts.api.model.condition.ThresholdRangeCondition;
import org.hawkular.alerts.api.model.condition.ThresholdRangeConditionEval;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.data.Availability;
import org.hawkular.alerts.api.model.data.Availability.AvailabilityType;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.data.NumericData;
import org.hawkular.alerts.api.model.data.StringData;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.model.trigger.Trigger.Mode;
import org.hawkular.alerts.engine.impl.DroolsRulesEngineImpl;
import org.hawkular.alerts.engine.rules.RulesEngine;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Basic test of RulesEngine implementation.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class RulesEngineTest {
    private static final Logger log = Logger.getLogger(RulesEngineTest.class);

    RulesEngine rulesEngine = new DroolsRulesEngineImpl();
    List<Alert> alerts = new ArrayList<>();
    Set<Dampening> pendingTimeouts = new HashSet<>();
    Map<Trigger, List<Set<ConditionEval>>> autoResolvedTriggers = new HashMap<>();
    Set<Trigger> disabledTriggers = new CopyOnWriteArraySet<>();
    Set<Data> datums = new HashSet<Data>();

    @Before
    public void before() {
        rulesEngine.addGlobal("log", log);
        rulesEngine.addGlobal("alerts", alerts);
        rulesEngine.addGlobal("pendingTimeouts", pendingTimeouts);
        rulesEngine.addGlobal("autoResolvedTriggers", autoResolvedTriggers);
        rulesEngine.addGlobal("disabledTriggers", disabledTriggers);
    }

    @After
    public void after() {
        rulesEngine.reset();
        alerts.clear();
        pendingTimeouts.clear();
        datums.clear();
    }

    @Test
    public void thresholdTest() {
        // 1 alert
        Trigger t1 = new Trigger("trigger-1", "Threshold-LT");
        ThresholdCondition t1c1 = new ThresholdCondition("trigger-1", 1, 1,
                "NumericData-01",
                ThresholdCondition.Operator.LT, 10.0);
        // 2 alert3
        Trigger t2 = new Trigger("trigger-2", "Threshold-LTE");
        ThresholdCondition t2c1 = new ThresholdCondition("trigger-2", 1, 1,
                "NumericData-01",
                ThresholdCondition.Operator.LTE, 10.0);
        // 1 alert
        Trigger t3 = new Trigger("trigger-3", "Threshold-GT");
        ThresholdCondition t3c1 = new ThresholdCondition("trigger-3", 1, 1,
                "NumericData-01",
                ThresholdCondition.Operator.GT, 10.0);
        // 2 alerts
        Trigger t4 = new Trigger("trigger-4", "Threshold-GTE");
        ThresholdCondition t4c1 = new ThresholdCondition("trigger-4", 1, 1,
                "NumericData-01",
                ThresholdCondition.Operator.GTE, 10.0);

        datums.add(new NumericData("NumericData-01", 1, 10.0));
        datums.add(new NumericData("NumericData-01", 2, 5.0));
        datums.add(new NumericData("NumericData-01", 3, 15.0));

        // default dampening

        t1.setEnabled(true);
        t2.setEnabled(true);
        t3.setEnabled(true);
        t4.setEnabled(true);

        rulesEngine.addFact(t1);
        rulesEngine.addFact(t1c1);
        rulesEngine.addFact(t2);
        rulesEngine.addFact(t2c1);
        rulesEngine.addFact(t3);
        rulesEngine.addFact(t3c1);
        rulesEngine.addFact(t4);
        rulesEngine.addFact(t4c1);

        rulesEngine.addData(datums);

        rulesEngine.fire();

        assertEquals(alerts.toString(), 6, alerts.size());
        Collections.sort(alerts, (Alert a1, Alert a2) -> a1.getTriggerId().compareTo(a2.getTriggerId()));

        Alert a = alerts.get(0);
        assertEquals(a.getTriggerId(), "trigger-1", a.getTriggerId());
        assertEquals(a.getEvalSets().toString(), 1, a.getEvalSets().size());
        Set<ConditionEval> eval = a.getEvalSets().get(0);
        assertEquals(eval.toString(), 1, eval.size());
        ThresholdConditionEval e = (ThresholdConditionEval) eval.iterator().next();
        assertEquals(e.toString(), 1, e.getConditionSetIndex());
        assertEquals(e.toString(), 1, e.getConditionSetSize());
        assertEquals("trigger-1", e.getTriggerId());
        assertTrue(e.isMatch());
        Double v = e.getValue();
        assertTrue(e.toString(), v.equals(5.0D));
        assertEquals(e.getCondition().toString(), "NumericData-01", e.getCondition().getDataId());

        a = alerts.get(1);
        assertEquals(a.getTriggerId(), "trigger-2", a.getTriggerId());
        assertEquals(a.getEvalSets().toString(), 1, a.getEvalSets().size());
        eval = a.getEvalSets().get(0);
        assertEquals(eval.toString(), 1, eval.size());
        e = (ThresholdConditionEval) eval.iterator().next();
        assertEquals(e.toString(), 1, e.getConditionSetIndex());
        assertEquals(e.toString(), 1, e.getConditionSetSize());
        assertEquals("trigger-2", e.getTriggerId());
        assertTrue(e.isMatch());
        v = e.getValue();
        assertTrue(e.toString(), v.equals(5.0D) || v.equals(10.0D));
        assertEquals(e.getCondition().toString(), "NumericData-01", e.getCondition().getDataId());

        a = alerts.get(2);
        assertEquals(a.getTriggerId(), "trigger-2", a.getTriggerId());
        assertEquals(a.getEvalSets().toString(), 1, a.getEvalSets().size());
        eval = a.getEvalSets().get(0);
        assertEquals(eval.toString(), 1, eval.size());
        e = (ThresholdConditionEval) eval.iterator().next();
        assertEquals(e.toString(), 1, e.getConditionSetIndex());
        assertEquals(e.toString(), 1, e.getConditionSetSize());
        assertEquals("trigger-2", e.getTriggerId());
        assertTrue(e.isMatch());
        assertTrue(!v.equals(e.getValue()));
        v = e.getValue();
        assertTrue(e.toString(), v.equals(5.0D) || v.equals(10.0D));
        assertEquals(e.getCondition().toString(), "NumericData-01", e.getCondition().getDataId());

        assertTrue(e.getCondition().getDataId().equals("NumericData-01"));

        a = alerts.get(3);
        assertEquals(a.getTriggerId(), "trigger-3", a.getTriggerId());
        assertEquals(a.getEvalSets().toString(), 1, a.getEvalSets().size());
        eval = a.getEvalSets().get(0);
        assertEquals(eval.toString(), 1, eval.size());
        e = (ThresholdConditionEval) eval.iterator().next();
        assertEquals(e.toString(), 1, e.getConditionSetIndex());
        assertEquals(e.toString(), 1, e.getConditionSetSize());
        assertEquals("trigger-3", e.getTriggerId());
        assertTrue(e.isMatch());
        v = e.getValue();
        assertTrue(e.toString(), v.equals(15.0D));
        assertEquals(e.getCondition().toString(), "NumericData-01", e.getCondition().getDataId());

        a = alerts.get(4);
        assertEquals(a.getTriggerId(), "trigger-4", a.getTriggerId());
        assertEquals(a.getEvalSets().toString(), 1, a.getEvalSets().size());
        eval = a.getEvalSets().get(0);
        assertEquals(eval.toString(), 1, eval.size());
        e = (ThresholdConditionEval) eval.iterator().next();
        assertEquals(e.toString(), 1, e.getConditionSetIndex());
        assertEquals(e.toString(), 1, e.getConditionSetSize());
        assertEquals("trigger-4", e.getTriggerId());
        assertTrue(e.isMatch());
        v = e.getValue();
        assertTrue(e.toString(), v.equals(15.0D) || v.equals(10.0D));
        assertEquals(e.getCondition().toString(), "NumericData-01", e.getCondition().getDataId());

        a = alerts.get(5);
        assertEquals(a.getTriggerId(), "trigger-4", a.getTriggerId());
        assertEquals(a.getEvalSets().toString(), 1, a.getEvalSets().size());
        eval = a.getEvalSets().get(0);
        assertEquals(eval.toString(), 1, eval.size());
        e = (ThresholdConditionEval) eval.iterator().next();
        assertEquals(e.toString(), 1, e.getConditionSetIndex());
        assertEquals(e.toString(), 1, e.getConditionSetSize());
        assertEquals("trigger-4", e.getTriggerId());
        assertTrue(e.isMatch());
        assertTrue(!v.equals(e.getValue()));
        v = e.getValue();
        assertTrue(e.toString(), v.equals(15.0D) || v.equals(10.0D));
        assertEquals(e.getCondition().toString(), "NumericData-01", e.getCondition().getDataId());
    }

    @Test
    public void thresholdRangeTest() {
        Trigger t1 = new Trigger("trigger-1", "NumericData-01-");
        // should fire 2 alerts
        ThresholdRangeCondition t1c1 = new ThresholdRangeCondition("trigger-1", 1, 1,
                "NumericData-01",
                ThresholdRangeCondition.Operator.INCLUSIVE,
                ThresholdRangeCondition.Operator.INCLUSIVE,
                10.0, 15.0,
                true);
        // should fine 0 alerts
        Trigger t2 = new Trigger("trigger-2", "NumericData-01");
        ThresholdRangeCondition t2c1 = new ThresholdRangeCondition("trigger-2", 1, 1,
                "NumericData-01",
                ThresholdRangeCondition.Operator.EXCLUSIVE,
                ThresholdRangeCondition.Operator.EXCLUSIVE,
                10.0, 15.0,
                true);
        // should fire 1 alert
        Trigger t3 = new Trigger("trigger-3", "NumericData-01");
        ThresholdRangeCondition t3c1 = new ThresholdRangeCondition("trigger-3", 1, 1,
                "NumericData-01",
                ThresholdRangeCondition.Operator.INCLUSIVE,
                ThresholdRangeCondition.Operator.INCLUSIVE,
                10.0, 15.0,
                false);

        datums.add(new NumericData("NumericData-01", 1, 10.0));
        datums.add(new NumericData("NumericData-01", 2, 5.0));
        datums.add(new NumericData("NumericData-01", 3, 15.0));

        // default dampening

        t1.setEnabled(true);
        t2.setEnabled(true);
        t3.setEnabled(true);

        rulesEngine.addFact(t1);
        rulesEngine.addFact(t1c1);
        rulesEngine.addFact(t2);
        rulesEngine.addFact(t2c1);
        rulesEngine.addFact(t3);
        rulesEngine.addFact(t3c1);

        rulesEngine.addData(datums);

        rulesEngine.fire();

        assertEquals(alerts.toString(), 3, alerts.size());
        Collections.sort(alerts, (Alert a1, Alert a2) -> a1.getTriggerId().compareTo(a2.getTriggerId()));
        Alert a = alerts.get(0);
        assertTrue(a.getTriggerId(), a.getTriggerId().equals("trigger-1"));
        assertEquals(1, a.getEvalSets().size());
        Set<ConditionEval> evals = a.getEvalSets().get(0);
        assertEquals(evals.toString(), 1, evals.size());
        ThresholdRangeConditionEval e = (ThresholdRangeConditionEval) evals.iterator().next();
        assertEquals(1, e.getConditionSetIndex());
        assertEquals(1, e.getConditionSetSize());
        assertTrue(e.toString(), e.getTriggerId().equals("trigger-1"));
        assertTrue(e.isMatch());
        Double v = e.getValue();
        assertTrue(e.toString(), v.equals(10.0D) || v.equals(15.0D));
        assertTrue(e.getCondition().toString(), e.getCondition().getDataId().equals("NumericData-01"));

        a = alerts.get(1);
        assertTrue(a.getTriggerId(), a.getTriggerId().equals("trigger-1"));
        assertEquals(1, a.getEvalSets().size());
        evals = a.getEvalSets().get(0);
        assertEquals(evals.toString(), 1, evals.size());
        e = (ThresholdRangeConditionEval) evals.iterator().next();
        assertEquals(1, e.getConditionSetIndex());
        assertEquals(1, e.getConditionSetSize());
        assertTrue(e.toString(), e.getTriggerId().equals("trigger-1"));
        assertTrue(e.isMatch());
        assertTrue(!v.equals(e.getValue()));
        v = e.getValue();
        assertTrue(e.toString(), v.equals(10.0D) || v.equals(15.0D));
        assertTrue(e.getCondition().toString(), e.getCondition().getDataId().equals("NumericData-01"));

        a = alerts.get(2);
        assertTrue(a.getTriggerId(), a.getTriggerId().equals("trigger-3"));
        assertEquals(1, a.getEvalSets().size());
        evals = a.getEvalSets().get(0);
        assertEquals(evals.toString(), 1, evals.size());
        e = (ThresholdRangeConditionEval) evals.iterator().next();
        assertEquals(1, e.getConditionSetIndex());
        assertEquals(1, e.getConditionSetSize());
        assertTrue(e.toString(), e.getTriggerId().equals("trigger-3"));
        assertTrue(e.isMatch());
        v = e.getValue();
        assertTrue(e.toString(), v.equals(5.0D));
        assertTrue(e.getCondition().toString(), e.getCondition().getDataId().equals("NumericData-01"));
    }

    @Test
    public void compareTest() {
        Trigger t1 = new Trigger("trigger-1", "Compare-D1-LT-Half-D2");
        CompareCondition t1c1 = new CompareCondition("trigger-1", 1, 1,
                "NumericData-01",
                CompareCondition.Operator.LT, 0.5, "NumericData-02");
        Trigger t2 = new Trigger("trigger-2", "Compare-D1-LTE-Half-D2");
        CompareCondition t2c1 = new CompareCondition("trigger-2", 1, 1,
                "NumericData-01",
                CompareCondition.Operator.LTE, 0.5, "NumericData-02");
        Trigger t3 = new Trigger("trigger-3", "Compare-D1-GT-Half-D2");
        CompareCondition t3c1 = new CompareCondition("trigger-3", 1, 1,
                "NumericData-01",
                CompareCondition.Operator.GT, 0.5, "NumericData-02");
        Trigger t4 = new Trigger("trigger-4", "Compare-D1-GTE-Half-D2");
        CompareCondition t4c1 = new CompareCondition("trigger-4", 1, 1,
                "NumericData-01",
                CompareCondition.Operator.GTE, 0.5, "NumericData-02");

        // default dampening

        t1.setEnabled(true);
        t2.setEnabled(true);
        t3.setEnabled(true);
        t4.setEnabled(true);

        rulesEngine.addFact(t1);
        rulesEngine.addFact(t1c1);
        rulesEngine.addFact(t2);
        rulesEngine.addFact(t2c1);
        rulesEngine.addFact(t3);
        rulesEngine.addFact(t3c1);
        rulesEngine.addFact(t4);
        rulesEngine.addFact(t4c1);

        // note that for compare conditions both datums need to be in WM for the same rules execution.  This
        // has some subtleties.  Because one rule execution will only see the the oldest datum for a specific
        // dataId, we need several rule executions to test all of the above triggers.

        // Test LT (also LTE)
        datums.add(new NumericData("NumericData-01", 1, 10.0));
        datums.add(new NumericData("NumericData-02", 2, 30.0));

        rulesEngine.addData(datums);

        rulesEngine.fire();

        assertEquals(alerts.toString(), 2, alerts.size());
        Collections.sort(alerts, (Alert a1, Alert a2) -> a1.getTriggerId().compareTo(a2.getTriggerId()));

        Alert a = alerts.get(0);
        assertTrue(a.getTriggerId(), a.getTriggerId().equals("trigger-1"));
        assertEquals(1, a.getEvalSets().size());
        Set<ConditionEval> evals = a.getEvalSets().get(0);
        assertEquals(evals.toString(), 1, evals.size());
        CompareConditionEval e = (CompareConditionEval) evals.iterator().next();
        assertEquals(1, e.getConditionSetIndex());
        assertEquals(1, e.getConditionSetSize());
        assertTrue(e.toString(), e.getTriggerId().equals("trigger-1"));
        assertTrue(e.isMatch());
        Double v1 = e.getValue1();
        Double v2 = e.getValue2();
        assertTrue(e.toString(), v1.equals(10.0D));
        assertTrue(e.toString(), v2.equals(30.0D));
        assertTrue(e.getCondition().toString(), e.getCondition().getDataId().equals("NumericData-01"));
        assertTrue(e.getCondition().toString(), e.getCondition().getData2Id().equals("NumericData-02"));

        a = alerts.get(1);
        assertTrue(a.getTriggerId(), a.getTriggerId().equals("trigger-2"));
        assertEquals(1, a.getEvalSets().size());
        evals = a.getEvalSets().get(0);
        assertEquals(evals.toString(), 1, evals.size());
        e = (CompareConditionEval) evals.iterator().next();
        assertEquals(1, e.getConditionSetIndex());
        assertEquals(1, e.getConditionSetSize());
        assertTrue(e.toString(), e.getTriggerId().equals("trigger-2"));
        assertTrue(e.isMatch());
        v1 = e.getValue1();
        v2 = e.getValue2();
        assertTrue(e.toString(), v1.equals(10.0D));
        assertTrue(e.toString(), v2.equals(30.0D));
        assertTrue(e.getCondition().toString(), e.getCondition().getDataId().equals("NumericData-01"));
        assertTrue(e.getCondition().toString(), e.getCondition().getData2Id().equals("NumericData-02"));

        // Test LTE + GTE
        datums.clear();
        alerts.clear();
        datums.add(new NumericData("NumericData-01", 1, 10.0));
        datums.add(new NumericData("NumericData-02", 2, 20.0));

        rulesEngine.addData(datums);

        rulesEngine.fire();

        assertEquals(alerts.toString(), 2, alerts.size());
        Collections.sort(alerts, (Alert a1, Alert a2) -> a1.getTriggerId().compareTo(a2.getTriggerId()));

        a = alerts.get(0);
        assertTrue(a.getTriggerId(), a.getTriggerId().equals("trigger-2"));
        assertEquals(1, a.getEvalSets().size());
        evals = a.getEvalSets().get(0);
        assertEquals(evals.toString(), 1, evals.size());
        e = (CompareConditionEval) evals.iterator().next();
        assertEquals(1, e.getConditionSetIndex());
        assertEquals(1, e.getConditionSetSize());
        assertTrue(e.toString(), e.getTriggerId().equals("trigger-2"));
        assertTrue(e.isMatch());
        v1 = e.getValue1();
        v2 = e.getValue2();
        assertTrue(e.toString(), v1.equals(10.0D));
        assertTrue(e.toString(), v2.equals(20.0D));
        assertTrue(e.getCondition().toString(), e.getCondition().getDataId().equals("NumericData-01"));
        assertTrue(e.getCondition().toString(), e.getCondition().getData2Id().equals("NumericData-02"));

        a = alerts.get(1);
        assertTrue(a.getTriggerId(), a.getTriggerId().equals("trigger-4"));
        assertEquals(1, a.getEvalSets().size());
        evals = a.getEvalSets().get(0);
        assertEquals(evals.toString(), 1, evals.size());
        e = (CompareConditionEval) evals.iterator().next();
        assertEquals(1, e.getConditionSetIndex());
        assertEquals(1, e.getConditionSetSize());
        assertTrue(e.toString(), e.getTriggerId().equals("trigger-4"));
        assertTrue(e.isMatch());
        v1 = e.getValue1();
        v2 = e.getValue2();
        assertTrue(e.toString(), v1.equals(10.0D));
        assertTrue(e.toString(), v2.equals(20.0D));
        assertTrue(e.getCondition().toString(), e.getCondition().getDataId().equals("NumericData-01"));
        assertTrue(e.getCondition().toString(), e.getCondition().getData2Id().equals("NumericData-02"));

        // Test GT (also GTE)
        datums.clear();
        alerts.clear();
        datums.add(new NumericData("NumericData-01", 1, 15.0));
        datums.add(new NumericData("NumericData-02", 2, 20.0));

        rulesEngine.addData(datums);

        rulesEngine.fire();

        assertEquals(alerts.toString(), 2, alerts.size());
        Collections.sort(alerts, (Alert a1, Alert a2) -> a1.getTriggerId().compareTo(a2.getTriggerId()));

        a = alerts.get(0);
        assertTrue(a.getTriggerId(), a.getTriggerId().equals("trigger-3"));
        assertEquals(1, a.getEvalSets().size());
        evals = a.getEvalSets().get(0);
        assertEquals(evals.toString(), 1, evals.size());
        e = (CompareConditionEval) evals.iterator().next();
        assertEquals(1, e.getConditionSetIndex());
        assertEquals(1, e.getConditionSetSize());
        assertTrue(e.toString(), e.getTriggerId().equals("trigger-3"));
        assertTrue(e.isMatch());
        v1 = e.getValue1();
        v2 = e.getValue2();
        assertTrue(e.toString(), v1.equals(15.0D));
        assertTrue(e.toString(), v2.equals(20.0D));
        assertTrue(e.getCondition().toString(), e.getCondition().getDataId().equals("NumericData-01"));
        assertTrue(e.getCondition().toString(), e.getCondition().getData2Id().equals("NumericData-02"));

        a = alerts.get(1);
        assertTrue(a.getTriggerId(), a.getTriggerId().equals("trigger-4"));
        assertEquals(1, a.getEvalSets().size());
        evals = a.getEvalSets().get(0);
        assertEquals(evals.toString(), 1, evals.size());
        e = (CompareConditionEval) evals.iterator().next();
        assertEquals(1, e.getConditionSetIndex());
        assertEquals(1, e.getConditionSetSize());
        assertTrue(e.toString(), e.getTriggerId().equals("trigger-4"));
        assertTrue(e.isMatch());
        v1 = e.getValue1();
        v2 = e.getValue2();
        assertTrue(e.toString(), v1.equals(15.0D));
        assertTrue(e.toString(), v2.equals(20.0D));
        assertTrue(e.getCondition().toString(), e.getCondition().getDataId().equals("NumericData-01"));
        assertTrue(e.getCondition().toString(), e.getCondition().getData2Id().equals("NumericData-02"));
    }

    @Test
    public void StringTest() {
        // StringData-01 Triggers
        // 2 alerts
        Trigger t1 = new Trigger("trigger-1", "String-StartsWith");
        StringCondition t1c1 = new StringCondition("trigger-1", 1, 1,
                "StringData-01",
                StringCondition.Operator.STARTS_WITH, "Fred", false);
        // 1 alert
        Trigger t2 = new Trigger("trigger-2", "String-Equal");
        StringCondition t3c1 = new StringCondition("trigger-2", 1, 1,
                "StringData-01",
                StringCondition.Operator.EQUAL, "Fred", false);
        // 1 alert
        Trigger t3 = new Trigger("trigger-3", "String-Contains");
        StringCondition t5c1 = new StringCondition("trigger-3", 1, 1,
                "StringData-01",
                StringCondition.Operator.CONTAINS, "And", false);
        // 1 alert
        Trigger t4 = new Trigger("trigger-4", "String-Match");
        StringCondition t6c1 = new StringCondition("trigger-4", 1, 1,
                "StringData-01",
                StringCondition.Operator.MATCH, "Fred.*Barney", false);

        // StringData-02 Triggers
        // 1 alert
        Trigger t5 = new Trigger("trigger-5", "String-EndsWith");
        StringCondition t2c1 = new StringCondition("trigger-5", 1, 1,
                "StringData-02",
                StringCondition.Operator.ENDS_WITH, "Fred", false);
        // 1 alert
        Trigger t6 = new Trigger("trigger-6", "String-StartsWith");
        StringCondition t4c1 = new StringCondition("trigger-6", 1, 1,
                "StringData-02", // note
                StringCondition.Operator.NOT_EQUAL, "Fred", false);

        datums.add(new StringData("StringData-01", 1, "Fred"));
        datums.add(new StringData("StringData-01", 2, "Fred And Barney"));

        datums.add(new StringData("StringData-02", 1, "Barney And Fred"));

        // default dampening

        t1.setEnabled(true);
        t2.setEnabled(true);
        t3.setEnabled(true);
        t4.setEnabled(true);
        t5.setEnabled(true);
        t6.setEnabled(true);

        rulesEngine.addFact(t1);
        rulesEngine.addFact(t1c1);
        rulesEngine.addFact(t2);
        rulesEngine.addFact(t2c1);
        rulesEngine.addFact(t3);
        rulesEngine.addFact(t3c1);
        rulesEngine.addFact(t4);
        rulesEngine.addFact(t4c1);
        rulesEngine.addFact(t5);
        rulesEngine.addFact(t5c1);
        rulesEngine.addFact(t6);
        rulesEngine.addFact(t6c1);

        rulesEngine.addData(datums);

        rulesEngine.fire();

        assertEquals(alerts.toString(), 7, alerts.size());
        Collections.sort(alerts, (Alert a1, Alert a2) -> a1.getTriggerId().compareTo(a2.getTriggerId()));

        Alert a = alerts.get(0);
        assertTrue(a.getTriggerId(), a.getTriggerId().equals("trigger-1"));
        assertEquals(1, a.getEvalSets().size());
        Set<ConditionEval> evals = a.getEvalSets().get(0);
        assertEquals(1, evals.size());
        StringConditionEval e = (StringConditionEval) evals.iterator().next();
        assertEquals(1, e.getConditionSetIndex());
        assertEquals(1, e.getConditionSetSize());
        assertEquals("trigger-1", e.getTriggerId());
        assertTrue(e.isMatch());
        String v = e.getValue();
        assertEquals("Fred", v);
        assertEquals("StringData-01", e.getCondition().getDataId());

        a = alerts.get(1);
        assertTrue(a.getTriggerId(), a.getTriggerId().equals("trigger-1"));
        assertEquals(1, a.getEvalSets().size());
        evals = a.getEvalSets().get(0);
        assertEquals(1, evals.size());
        e = (StringConditionEval) evals.iterator().next();
        assertEquals(1, e.getConditionSetIndex());
        assertEquals(1, e.getConditionSetSize());
        assertEquals("trigger-1", e.getTriggerId());
        assertTrue(e.isMatch());
        v = e.getValue();
        assertEquals("Fred And Barney", v);
        assertEquals("StringData-01", e.getCondition().getDataId());

        a = alerts.get(2);
        assertTrue(a.getTriggerId(), a.getTriggerId().equals("trigger-2"));
        assertEquals(1, a.getEvalSets().size());
        evals = a.getEvalSets().get(0);
        assertEquals(1, evals.size());
        e = (StringConditionEval) evals.iterator().next();
        assertEquals(1, e.getConditionSetIndex());
        assertEquals(1, e.getConditionSetSize());
        assertEquals("trigger-2", e.getTriggerId());
        assertTrue(e.isMatch());
        v = e.getValue();
        assertEquals("Fred", v);
        assertEquals("StringData-01", e.getCondition().getDataId());

        a = alerts.get(3);
        assertTrue(a.getTriggerId(), a.getTriggerId().equals("trigger-3"));
        assertEquals(1, a.getEvalSets().size());
        evals = a.getEvalSets().get(0);
        assertEquals(1, evals.size());
        e = (StringConditionEval) evals.iterator().next();
        assertEquals(1, e.getConditionSetIndex());
        assertEquals(1, e.getConditionSetSize());
        assertEquals("trigger-3", e.getTriggerId());
        assertTrue(e.isMatch());
        v = e.getValue();
        assertEquals("Fred And Barney", v);
        assertEquals("StringData-01", e.getCondition().getDataId());

        a = alerts.get(4);
        assertTrue(a.getTriggerId(), a.getTriggerId().equals("trigger-4"));
        assertEquals(1, a.getEvalSets().size());
        evals = a.getEvalSets().get(0);
        assertEquals(1, evals.size());
        e = (StringConditionEval) evals.iterator().next();
        assertEquals(1, e.getConditionSetIndex());
        assertEquals(1, e.getConditionSetSize());
        assertEquals("trigger-4", e.getTriggerId());
        assertTrue(e.isMatch());
        v = e.getValue();
        assertEquals("Fred And Barney", v);
        assertEquals("StringData-01", e.getCondition().getDataId());

        a = alerts.get(5);
        assertTrue(a.getTriggerId(), a.getTriggerId().equals("trigger-5"));
        assertEquals(1, a.getEvalSets().size());
        evals = a.getEvalSets().get(0);
        assertEquals(1, evals.size());
        e = (StringConditionEval) evals.iterator().next();
        assertEquals(1, e.getConditionSetIndex());
        assertEquals(1, e.getConditionSetSize());
        assertEquals("trigger-5", e.getTriggerId());
        assertTrue(e.isMatch());
        v = e.getValue();
        assertEquals("Barney And Fred", v);
        assertEquals("StringData-02", e.getCondition().getDataId());

        a = alerts.get(6);
        assertTrue(a.getTriggerId(), a.getTriggerId().equals("trigger-6"));
        assertEquals(1, a.getEvalSets().size());
        evals = a.getEvalSets().get(0);
        assertEquals(1, evals.size());
        e = (StringConditionEval) evals.iterator().next();
        assertEquals(1, e.getConditionSetIndex());
        assertEquals(1, e.getConditionSetSize());
        assertEquals("trigger-6", e.getTriggerId());
        assertTrue(e.isMatch());
        v = e.getValue();
        assertEquals("Barney And Fred", v);
        assertEquals("StringData-02", e.getCondition().getDataId());
    }

    @Test
    public void StringTestIgnoreCase() {
        // StringData-01 Triggers
        // 2 alerts
        Trigger t1 = new Trigger("trigger-1", "String-StartsWith");
        StringCondition t1c1 = new StringCondition("trigger-1", 1, 1,
                "StringData-01",
                StringCondition.Operator.STARTS_WITH, "FRED", true);
        // 1 alert
        Trigger t2 = new Trigger("trigger-2", "String-Equal");
        StringCondition t3c1 = new StringCondition("trigger-2", 1, 1,
                "StringData-01",
                StringCondition.Operator.EQUAL, "FRED", true);
        // 1 alert
        Trigger t3 = new Trigger("trigger-3", "String-Contains");
        StringCondition t5c1 = new StringCondition("trigger-3", 1, 1,
                "StringData-01",
                StringCondition.Operator.CONTAINS, "AND", true);
        // 1 alert
        Trigger t4 = new Trigger("trigger-4", "String-Match");
        StringCondition t6c1 = new StringCondition("trigger-4", 1, 1,
                "StringData-01",
                StringCondition.Operator.MATCH, "FRED.*barney", true);

        // StringData-02 Triggers
        // 1 alert
        Trigger t5 = new Trigger("trigger-5", "String-EndsWith");
        StringCondition t2c1 = new StringCondition("trigger-5", 1, 1,
                "StringData-02",
                StringCondition.Operator.ENDS_WITH, "FRED", true);
        // 1 alert
        Trigger t6 = new Trigger("trigger-6", "String-StartsWith");
        StringCondition t4c1 = new StringCondition("trigger-6", 1, 1,
                "StringData-02", // note
                StringCondition.Operator.NOT_EQUAL, "FRED", true);

        datums.add(new StringData("StringData-01", 1, "Fred"));
        datums.add(new StringData("StringData-01", 2, "Fred And Barney"));

        datums.add(new StringData("StringData-02", 1, "Barney And Fred"));

        // default dampening

        t1.setEnabled(true);
        t2.setEnabled(true);
        t3.setEnabled(true);
        t4.setEnabled(true);
        t5.setEnabled(true);
        t6.setEnabled(true);

        rulesEngine.addFact(t1);
        rulesEngine.addFact(t1c1);
        rulesEngine.addFact(t2);
        rulesEngine.addFact(t2c1);
        rulesEngine.addFact(t3);
        rulesEngine.addFact(t3c1);
        rulesEngine.addFact(t4);
        rulesEngine.addFact(t4c1);
        rulesEngine.addFact(t5);
        rulesEngine.addFact(t5c1);
        rulesEngine.addFact(t6);
        rulesEngine.addFact(t6c1);

        rulesEngine.addData(datums);

        rulesEngine.fire();

        assertEquals(alerts.toString(), 7, alerts.size());
        Collections.sort(alerts, (Alert a1, Alert a2) -> a1.getTriggerId().compareTo(a2.getTriggerId()));

        Alert a = alerts.get(0);
        assertEquals("trigger-1", a.getTriggerId());
        assertEquals(1, a.getEvalSets().size());
        Set<ConditionEval> evals = a.getEvalSets().get(0);
        assertEquals(1, evals.size());
        StringConditionEval e = (StringConditionEval) evals.iterator().next();
        assertEquals(1, e.getConditionSetIndex());
        assertEquals(1, e.getConditionSetSize());
        assertEquals("trigger-1", e.getTriggerId());
        assertTrue(e.isMatch());
        String v = e.getValue();
        assertEquals("Fred", v);
        assertEquals("StringData-01", e.getCondition().getDataId());

        a = alerts.get(1);
        assertEquals("trigger-1", a.getTriggerId());
        assertEquals(1, a.getEvalSets().size());
        evals = a.getEvalSets().get(0);
        assertEquals(1, evals.size());
        e = (StringConditionEval) evals.iterator().next();
        assertEquals(1, e.getConditionSetIndex());
        assertEquals(1, e.getConditionSetSize());
        assertEquals("trigger-1", e.getTriggerId());
        assertTrue(e.isMatch());
        v = e.getValue();
        assertEquals("Fred And Barney", v);
        assertEquals("StringData-01", e.getCondition().getDataId());

        a = alerts.get(2);
        assertEquals("trigger-2", a.getTriggerId());
        assertEquals(1, a.getEvalSets().size());
        evals = a.getEvalSets().get(0);
        assertEquals(1, evals.size());
        e = (StringConditionEval) evals.iterator().next();
        assertEquals(1, e.getConditionSetIndex());
        assertEquals(1, e.getConditionSetSize());
        assertEquals("trigger-2", e.getTriggerId());
        assertTrue(e.isMatch());
        v = e.getValue();
        assertEquals("Fred", v);
        assertEquals("StringData-01", e.getCondition().getDataId());

        a = alerts.get(3);
        assertEquals("trigger-3", a.getTriggerId());
        assertEquals(1, a.getEvalSets().size());
        evals = a.getEvalSets().get(0);
        assertEquals(1, evals.size());
        e = (StringConditionEval) evals.iterator().next();
        assertEquals(1, e.getConditionSetIndex());
        assertEquals(1, e.getConditionSetSize());
        assertEquals("trigger-3", e.getTriggerId());
        assertTrue(e.isMatch());
        v = e.getValue();
        assertEquals("Fred And Barney", v);
        assertEquals("StringData-01", e.getCondition().getDataId());

        a = alerts.get(4);
        assertEquals("trigger-4", a.getTriggerId());
        assertEquals(1, a.getEvalSets().size());
        evals = a.getEvalSets().get(0);
        assertEquals(1, evals.size());
        e = (StringConditionEval) evals.iterator().next();
        assertEquals(1, e.getConditionSetIndex());
        assertEquals(1, e.getConditionSetSize());
        assertEquals("trigger-4", e.getTriggerId());
        assertTrue(e.isMatch());
        v = e.getValue();
        assertEquals("Fred And Barney", v);
        assertEquals("StringData-01", e.getCondition().getDataId());

        a = alerts.get(5);
        assertEquals("trigger-5", a.getTriggerId());
        assertEquals(1, a.getEvalSets().size());
        evals = a.getEvalSets().get(0);
        assertEquals(1, evals.size());
        e = (StringConditionEval) evals.iterator().next();
        assertEquals(1, e.getConditionSetIndex());
        assertEquals(1, e.getConditionSetSize());
        assertEquals("trigger-5", e.getTriggerId());
        assertTrue(e.isMatch());
        v = e.getValue();
        assertEquals("Barney And Fred", v);
        assertEquals("StringData-02", e.getCondition().getDataId());

        a = alerts.get(6);
        assertEquals("trigger-6", a.getTriggerId());
        assertEquals(1, a.getEvalSets().size());
        evals = a.getEvalSets().get(0);
        assertEquals(1, evals.size());
        e = (StringConditionEval) evals.iterator().next();
        assertEquals(1, e.getConditionSetIndex());
        assertEquals(1, e.getConditionSetSize());
        assertEquals("trigger-6", e.getTriggerId());
        assertTrue(e.isMatch());
        v = e.getValue();
        assertEquals("Barney And Fred", v);
        assertEquals("StringData-02", e.getCondition().getDataId());
    }

    @Test
    public void AvailabilityTest() {
        Trigger t1 = new Trigger("trigger-1", "Avail-DOWN");
        AvailabilityCondition t1c1 = new AvailabilityCondition("trigger-1", 1, 1,
                "AvailData-01", AvailabilityCondition.Operator.NOT_UP);

        datums.add(new Availability("AvailData-01", 1, AvailabilityType.DOWN));
        datums.add(new Availability("AvailData-01", 2, AvailabilityType.UNAVAILABLE));
        datums.add(new Availability("AvailData-01", 3, AvailabilityType.UP));

        // default dampening

        t1.setEnabled(true);

        rulesEngine.addFact(t1);
        rulesEngine.addFact(t1c1);

        rulesEngine.addData(datums);

        rulesEngine.fire();

        assertEquals(alerts.toString(), 2, alerts.size());

        Alert a = alerts.get(0);
        assertEquals("trigger-1", a.getTriggerId());
        assertEquals(1, a.getEvalSets().size());
        Set<ConditionEval> evals = a.getEvalSets().get(0);
        assertEquals(1, evals.size());
        AvailabilityConditionEval e = (AvailabilityConditionEval) evals.iterator().next();
        assertEquals(1, e.getConditionSetIndex());
        assertEquals(1, e.getConditionSetSize());
        assertEquals("trigger-1", e.getTriggerId());
        assertTrue(e.isMatch());
        AvailabilityType v = e.getValue();
        assertEquals(AvailabilityType.DOWN, v);
        assertEquals("AvailData-01", e.getCondition().getDataId());

        a = alerts.get(1);
        assertEquals("trigger-1", a.getTriggerId());
        assertEquals(1, a.getEvalSets().size());
        evals = a.getEvalSets().get(0);
        assertEquals(1, evals.size());
        e = (AvailabilityConditionEval) evals.iterator().next();
        assertEquals(1, e.getConditionSetIndex());
        assertEquals(1, e.getConditionSetSize());
        assertEquals("trigger-1", e.getTriggerId());
        assertTrue(e.isMatch());
        v = e.getValue();
        assertEquals(AvailabilityType.UNAVAILABLE, v);
        assertEquals("AvailData-01", e.getCondition().getDataId());
    }

    @Test
    public void DampeningStrictTest() {
        Trigger t1 = new Trigger("trigger-1", "Avail-DOWN");
        AvailabilityCondition t1c1 = new AvailabilityCondition("trigger-1", 1, 1,
                "AvailData-01", AvailabilityCondition.Operator.DOWN);

        Dampening t1d = Dampening.forStrict("trigger-1", Mode.FIRING, 3);

        datums.add(new Availability("AvailData-01", 1, AvailabilityType.DOWN));
        datums.add(new Availability("AvailData-01", 2, AvailabilityType.UNAVAILABLE));
        datums.add(new Availability("AvailData-01", 3, AvailabilityType.UP));
        datums.add(new Availability("AvailData-01", 4, AvailabilityType.DOWN));
        datums.add(new Availability("AvailData-01", 5, AvailabilityType.DOWN));
        datums.add(new Availability("AvailData-01", 6, AvailabilityType.DOWN));
        datums.add(new Availability("AvailData-01", 7, AvailabilityType.UP));

        t1.setEnabled(true);

        rulesEngine.addFact(t1);
        rulesEngine.addFact(t1c1);
        rulesEngine.addFact(t1d);

        rulesEngine.addData(datums);

        rulesEngine.fire();

        assertEquals(alerts.toString(), 1, alerts.size());

        Alert a = alerts.get(0);
        assertEquals("trigger-1", a.getTriggerId());
        assertEquals(3, a.getEvalSets().size());
        long expectedTimestamp = 4;
        for (Set<ConditionEval> evalSet : a.getEvalSets()) {
            assertEquals(1, evalSet.size());
            AvailabilityConditionEval e = (AvailabilityConditionEval) evalSet.iterator().next();
            assertEquals(1, e.getConditionSetIndex());
            assertEquals(1, e.getConditionSetSize());
            assertEquals("trigger-1", e.getTriggerId());
            assertTrue(e.isMatch());
            assertEquals(expectedTimestamp++, e.getDataTimestamp());
            AvailabilityType v = e.getValue();
            assertEquals(AvailabilityType.DOWN, v);
            assertEquals("AvailData-01", e.getCondition().getDataId());
        }
    }

    @Test
    public void DampeningRelaxedCountTest() {
        Trigger t1 = new Trigger("trigger-1", "Avail-DOWN");
        AvailabilityCondition t1c1 = new AvailabilityCondition("trigger-1", 1, 1,
                "AvailData-01", AvailabilityCondition.Operator.DOWN);

        Dampening t1d = Dampening.forRelaxedCount("trigger-1", Mode.FIRING, 3, 5);

        datums.add(new Availability("AvailData-01", 1, AvailabilityType.DOWN));
        datums.add(new Availability("AvailData-01", 2, AvailabilityType.UNAVAILABLE));
        datums.add(new Availability("AvailData-01", 3, AvailabilityType.UP));
        datums.add(new Availability("AvailData-01", 4, AvailabilityType.DOWN));
        datums.add(new Availability("AvailData-01", 5, AvailabilityType.DOWN));
        datums.add(new Availability("AvailData-01", 6, AvailabilityType.DOWN));
        datums.add(new Availability("AvailData-01", 7, AvailabilityType.UP));

        t1.setEnabled(true);

        rulesEngine.addFact(t1);
        rulesEngine.addFact(t1c1);
        rulesEngine.addFact(t1d);

        rulesEngine.addData(datums);

        rulesEngine.fire();

        assertEquals(alerts.toString(), 1, alerts.size());

        Alert a = alerts.get(0);
        assertEquals("trigger-1", a.getTriggerId());
        assertEquals(3, a.getEvalSets().size());
        int i = 0;
        long[] expectedTimestamps = new long[] { 1, 4, 5 };
        for (Set<ConditionEval> evalSet : a.getEvalSets()) {
            assertEquals(1, evalSet.size());
            AvailabilityConditionEval e = (AvailabilityConditionEval) evalSet.iterator().next();
            assertEquals(1, e.getConditionSetIndex());
            assertEquals(1, e.getConditionSetSize());
            assertEquals("trigger-1", e.getTriggerId());
            assertTrue(e.isMatch());
            assertEquals(expectedTimestamps[i++], e.getDataTimestamp());
            AvailabilityType v = e.getValue();
            assertEquals(AvailabilityType.DOWN, v);
            assertEquals("AvailData-01", e.getCondition().getDataId());
        }
    }

    @Test
    public void DampeningRelaxedTimeTest() {
        Trigger t1 = new Trigger("trigger-1", "Avail-DOWN");
        AvailabilityCondition t1c1 = new AvailabilityCondition("trigger-1", 1, 1,
                "AvailData-01", AvailabilityCondition.Operator.DOWN);

        Dampening t1d = Dampening.forRelaxedTime("trigger-1", Mode.FIRING, 2, 500L);

        t1.setEnabled(true);

        rulesEngine.addFact(t1);
        rulesEngine.addFact(t1c1);
        rulesEngine.addFact(t1d);

        datums.add(new Availability("AvailData-01", 1, AvailabilityType.DOWN));

        rulesEngine.addData(datums);
        rulesEngine.fire();

        assertEquals(alerts.toString(), 0, alerts.size());

        try {
            Thread.sleep(750L);
        } catch (InterruptedException e1) {
        }

        datums.clear();
        datums.add(new Availability("AvailData-01", 2, AvailabilityType.DOWN));
        datums.add(new Availability("AvailData-01", 3, AvailabilityType.UP));
        datums.add(new Availability("AvailData-01", 4, AvailabilityType.DOWN));
        datums.add(new Availability("AvailData-01", 5, AvailabilityType.UP));

        rulesEngine.addData(datums);
        rulesEngine.fire();

        assertEquals(alerts.toString(), 1, alerts.size());

        Alert a = alerts.get(0);
        assertEquals("trigger-1", a.getTriggerId());
        assertEquals(2, a.getEvalSets().size());
        int i = 0;
        long[] expectedTimestamps = new long[] { 2, 4 };
        for (Set<ConditionEval> evalSet : a.getEvalSets()) {
            assertEquals(1, evalSet.size());
            AvailabilityConditionEval e = (AvailabilityConditionEval) evalSet.iterator().next();
            assertEquals(1, e.getConditionSetIndex());
            assertEquals(1, e.getConditionSetSize());
            assertEquals("trigger-1", e.getTriggerId());
            assertTrue(e.isMatch());
            assertEquals(expectedTimestamps[i++], e.getDataTimestamp());
            AvailabilityType v = e.getValue();
            assertEquals(AvailabilityType.DOWN, v);
            assertEquals("AvailData-01", e.getCondition().getDataId());
        }
    }

    @Test
    public void DampeningStrictTimeTest() {
        Trigger t1 = new Trigger("trigger-1", "Avail-DOWN");
        AvailabilityCondition t1c1 = new AvailabilityCondition("trigger-1", 1, 1,
                "AvailData-01", AvailabilityCondition.Operator.DOWN);

        Dampening t1d = Dampening.forStrictTime("trigger-1", Mode.FIRING, 250L);

        t1.setEnabled(true);

        rulesEngine.addFact(t1);
        rulesEngine.addFact(t1c1);
        rulesEngine.addFact(t1d);

        long start = System.currentTimeMillis();
        int i = 0;
        while ((alerts.size() == 0) && ((System.currentTimeMillis() - start) < 500)) {
            rulesEngine.addData(new Availability("AvailData-01", ++i, AvailabilityType.DOWN));
            rulesEngine.fire();
        }

        assertEquals(alerts.toString(), 1, alerts.size());

        Alert a = alerts.get(0);
        assertTrue(String.valueOf((alerts.get(0).getCtime() - start)), (alerts.get(0).getCtime() - start) >= 250);
        assertEquals("trigger-1", a.getTriggerId());
        assertTrue(String.valueOf(a.getEvalSets().size()), a.getEvalSets().size() >= 2);
        for (Set<ConditionEval> evalSet : a.getEvalSets()) {
            assertEquals(1, evalSet.size());
            AvailabilityConditionEval e = (AvailabilityConditionEval) evalSet.iterator().next();
            assertEquals(1, e.getConditionSetIndex());
            assertEquals(1, e.getConditionSetSize());
            assertEquals("trigger-1", e.getTriggerId());
            assertTrue(e.isMatch());
            AvailabilityType v = e.getValue();
            assertEquals(AvailabilityType.DOWN, v);
            assertEquals("AvailData-01", e.getCondition().getDataId());
        }
    }

    @Test
    public void DampeningStrictTimeoutTest() {
        Trigger t1 = new Trigger("trigger-1", "Avail-DOWN");
        AvailabilityCondition t1c1 = new AvailabilityCondition("trigger-1", 1, 1,
                "AvailData-01", AvailabilityCondition.Operator.DOWN);

        Dampening t1d = Dampening.forStrictTimeout("trigger-1", Mode.FIRING, 200L);

        t1.setEnabled(true);

        rulesEngine.addFact(t1);
        rulesEngine.addFact(t1c1);
        rulesEngine.addFact(t1d);

        assertTrue(alerts.isEmpty());
        assertTrue(pendingTimeouts.isEmpty());

        rulesEngine.addData(new Availability("AvailData-01", 1, AvailabilityType.DOWN));
        rulesEngine.fire();

        assertTrue(alerts.isEmpty());
        assertEquals(String.valueOf(pendingTimeouts), 1, pendingTimeouts.size());

        Dampening pendingTimeout = pendingTimeouts.iterator().next();
        pendingTimeout.setSatisfied(true);
        rulesEngine.updateFact(pendingTimeout);

        rulesEngine.fireNoData();

        assertEquals(alerts.toString(), 1, alerts.size());

        Alert a = alerts.get(0);
        assertEquals("trigger-1", a.getTriggerId());
        assertEquals(1, a.getEvalSets().size());
        Set<ConditionEval> evalSet = a.getEvalSets().get(0);
        assertEquals(1, evalSet.size());
        AvailabilityConditionEval e = (AvailabilityConditionEval) evalSet.iterator().next();
        assertEquals(1, e.getConditionSetIndex());
        assertEquals(1, e.getConditionSetSize());
        assertEquals("trigger-1", e.getTriggerId());
        assertTrue(e.isMatch());
        AvailabilityType v = e.getValue();
        assertEquals(AvailabilityType.DOWN, v);
        assertEquals("AvailData-01", e.getCondition().getDataId());
    }

    @Test
    public void multiConditionTest() {
        Trigger t1 = new Trigger("trigger-1", "Two-Conditions");
        ThresholdCondition t1c1 = new ThresholdCondition("trigger-1", 2, 1, "NumericData-01",
                ThresholdCondition.Operator.LT, 10.0);
        ThresholdRangeCondition t1c2 = new ThresholdRangeCondition("trigger-1", 2, 2, "NumericData-02",
                ThresholdRangeCondition.Operator.INCLUSIVE, ThresholdRangeCondition.Operator.EXCLUSIVE, 100.0, 200.0,
                true);

        // default dampening

        t1.setEnabled(true);

        rulesEngine.addFact(t1);
        rulesEngine.addFact(t1c1);
        rulesEngine.addFact(t1c2);

        // break up the arrivals of the relevant datums so that we get a more complicated series of evaluations.
        // remember that for any batch of datums:
        //   1) one datum for a specific dataId will will be processed at a time
        //   2) only the most recent conditionEvals will be used in a condition set tuple for a multi-condition trigger

        datums.add(new NumericData("NumericData-01", 1, 10.0));  // eval(d1,t1) = no match,
        datums.add(new NumericData("NumericData-01", 2, 5.0));   // eval(d1,t2) =    match, replaces eval(d1,t1)
        datums.add(new NumericData("NumericData-01", 3, 15.0));  // eval(d1,t3) = no match, replaces eval(d1,t2)

        rulesEngine.addData(datums);
        rulesEngine.fire();

        assertEquals(alerts.toString(), 0, alerts.size());

        datums.clear();
        // eval(d2,t4) = no match, tuple(eval(d1,t3), eval(d2,t4)) = false
        datums.add(new NumericData("NumericData-02", 4, 10.0));
        // eval(d2,t5) =    match, tuple(eval(d1,t3), eval(d2,t5)) = false
        datums.add(new NumericData("NumericData-02", 5, 150.0));

        rulesEngine.addData(datums);
        rulesEngine.fire();

        assertEquals(alerts.toString(), 0, alerts.size());

        datums.clear();
        // eval(d1,t6) =    match, tuple(eval(d1,t6), eval(d2,t5)) = true
        datums.add(new NumericData("NumericData-01", 6, 8.0));

        rulesEngine.addData(datums);
        rulesEngine.fire();

        assertEquals(alerts.toString(), 1, alerts.size());

        Alert a = alerts.get(0);
        assertEquals("trigger-1", a.getTriggerId());
        assertEquals(1, a.getEvalSets().size());
        Set<ConditionEval> evals = a.getEvalSets().get(0);
        assertEquals(2, evals.size());
        List<ConditionEval> evalsList = new ArrayList<>(evals);
        Collections.sort(
                evalsList,
                (ConditionEval c1, ConditionEval c2) -> Integer.compare(c1.getConditionSetIndex(),
                        c2.getConditionSetIndex()));
        Iterator<ConditionEval> i = evalsList.iterator();
        ThresholdConditionEval e = (ThresholdConditionEval) i.next();
        assertEquals(2, e.getConditionSetSize());
        assertEquals(1, e.getConditionSetIndex());
        assertEquals("trigger-1", e.getTriggerId());
        assertTrue(e.isMatch());
        Double v = e.getValue();
        assertTrue(v.equals(8.0));
        assertEquals("NumericData-01", e.getCondition().getDataId());

        ThresholdRangeConditionEval e2 = (ThresholdRangeConditionEval) i.next();
        assertEquals(2, e2.getConditionSetSize());
        assertEquals(2, e2.getConditionSetIndex());
        assertEquals("trigger-1", e2.getTriggerId());
        assertTrue(e2.isMatch());
        v = e2.getValue();
        assertTrue(v.equals(150.0));
        assertEquals("NumericData-02", e2.getCondition().getDataId());
    }

    @Test
    public void autoResolveTest() {
        // The single trigger has definitions for both FIRING and AUTORESOLVE modes
        Trigger t1 = new Trigger("trigger-1", "Avail-DOWN");

        // Firing Mode
        AvailabilityCondition fmt1c1 = new AvailabilityCondition("trigger-1", Mode.FIRING, 1, 1,
                "AvailData-01", AvailabilityCondition.Operator.DOWN);
        Dampening fmt1d = Dampening.forStrict("trigger-1", Mode.FIRING, 2);

        // AutoResolve Mode
        AvailabilityCondition smt1c1 = new AvailabilityCondition("trigger-1", Mode.AUTORESOLVE, 1, 1,
                "AvailData-01", AvailabilityCondition.Operator.UP);
        Dampening smt1d = Dampening.forStrict("trigger-1", Mode.AUTORESOLVE, 2);

        datums.add(new Availability("AvailData-01", 1, AvailabilityType.DOWN));
        datums.add(new Availability("AvailData-01", 2, AvailabilityType.UNAVAILABLE));
        datums.add(new Availability("AvailData-01", 3, AvailabilityType.UP));
        datums.add(new Availability("AvailData-01", 4, AvailabilityType.DOWN));
        datums.add(new Availability("AvailData-01", 5, AvailabilityType.DOWN));
        datums.add(new Availability("AvailData-01", 6, AvailabilityType.DOWN));
        datums.add(new Availability("AvailData-01", 7, AvailabilityType.DOWN));
        datums.add(new Availability("AvailData-01", 8, AvailabilityType.UP));

        t1.setEnabled(true);
        t1.setAutoDisable(false);
        t1.setAutoResolve(true);
        t1.setAutoResolveAlerts(true);

        rulesEngine.addFact(t1);
        rulesEngine.addFact(fmt1c1);
        rulesEngine.addFact(fmt1d);
        rulesEngine.addFact(smt1c1);
        rulesEngine.addFact(smt1d);

        // The Trigger should fire on the consecutive DOWN datums at T4,T5. It should then switch to
        // AutoResolve mode and not fire again at the next two consecutive down datums at T6,T7.  T8 should be the
        // first match for the AutoResolve dampening but it should not yet be satisfied until T9 (see below).
        rulesEngine.addData(datums);
        rulesEngine.fire();

        assertEquals(alerts.toString(), 1, alerts.size());
        assertTrue(disabledTriggers.toString(), disabledTriggers.isEmpty());
        assertTrue(autoResolvedTriggers.toString(), autoResolvedTriggers.isEmpty());

        Alert a = alerts.get(0);
        assertTrue(a.getStatus() == Alert.Status.OPEN);
        assertTrue(a.getTriggerId(), a.getTriggerId().equals("trigger-1"));
        assertTrue(a.getEvalSets().toString(), a.getEvalSets().size() == 2);
        long expectedTimestamp = 4;
        for (Set<ConditionEval> evalSet : a.getEvalSets()) {
            assertEquals(evalSet.toString(), 1, evalSet.size());
            AvailabilityConditionEval e = (AvailabilityConditionEval) evalSet.iterator().next();
            assertEquals(e.toString(), 1, e.getConditionSetIndex());
            assertEquals(e.toString(), 1, e.getConditionSetSize());
            assertEquals("trigger-1", e.getTriggerId());
            assertTrue(e.isMatch());
            assertEquals(expectedTimestamp++, e.getDataTimestamp());
            AvailabilityType v = e.getValue();
            assertEquals(AvailabilityType.DOWN, v);
            assertEquals("AvailData-01", e.getCondition().getDataId());
        }

        assertTrue(t1.toString(), t1.getMode() == Mode.AUTORESOLVE);

        alerts.clear();
        datums.clear();
        datums.add(new Availability("AvailData-01", 9, AvailabilityType.UP));

        rulesEngine.addData(datums);
        rulesEngine.fire();

        // The second consecutive UP should satisfy the AutoResolve requirements and return the Trigger to FIRING mode.
        assertTrue(alerts.isEmpty());
        assertTrue(disabledTriggers.isEmpty());
        assertEquals(1, autoResolvedTriggers.size());

        assertTrue(t1.toString(), t1.getMode() == Mode.FIRING);
    }

    @Test
    public void checkEqualityInRulesEngine() throws Exception {

        Trigger t1 = new Trigger("trigger-1", "Avail-DOWN");
        AvailabilityCondition fmt1c1 = new AvailabilityCondition("trigger-1", Mode.FIRING, 1, 1,
                "AvailData-01", AvailabilityCondition.Operator.DOWN);
        Availability adata = new Availability("AvailData-01", System.currentTimeMillis(), AvailabilityType.UP);
        AvailabilityConditionEval fmt1c1eval = new AvailabilityConditionEval(fmt1c1, adata);
        Dampening fmt1d = Dampening.forStrict("trigger-1", Mode.FIRING, 2);

        ThresholdCondition fmt1c2 = new ThresholdCondition("trigger-1", Mode.FIRING, 1, 1,
                "ThreData-01", ThresholdCondition.Operator.GT, 10d);
        NumericData ndata = new NumericData("ThreData-01", System.currentTimeMillis(), 20d);
        ThresholdConditionEval fmt1c2eval = new ThresholdConditionEval(fmt1c2, ndata);

        rulesEngine.addFact(t1);
        rulesEngine.addFact(fmt1c1);
        rulesEngine.addFact(fmt1c1eval);
        rulesEngine.addFact(fmt1d);
        rulesEngine.addFact(fmt1c2);
        rulesEngine.addFact(fmt1c2eval);

        assertTrue(rulesEngine.getFact(t1) != null);
        assertTrue(rulesEngine.getFact(fmt1c1) != null);
        assertTrue(rulesEngine.getFact(fmt1c1eval) != null);
        assertTrue(rulesEngine.getFact(fmt1d) != null);
        assertTrue(rulesEngine.getFact(fmt1c2) != null);
        assertTrue(rulesEngine.getFact(fmt1c2eval) != null);

        Gson gson = new GsonBuilder().create();

        String strt1 = gson.toJson(t1);
        String strfmt1c1 = gson.toJson(fmt1c1);
        String strfmt1c1eval = gson.toJson(fmt1c1eval);
        String strfmt1d = gson.toJson(fmt1d);
        String strfmt1c2 = gson.toJson(fmt1c2);
        String strfmt1c2eval = gson.toJson(fmt1c2eval);

        Trigger jsont1 = gson.fromJson(strt1, Trigger.class);
        AvailabilityCondition jsonfmt1c1 = gson.fromJson(strfmt1c1, AvailabilityCondition.class);
        AvailabilityConditionEval jsonfmt1c1eval = gson.fromJson(strfmt1c1eval, AvailabilityConditionEval.class);
        Dampening jsonfmt1d = gson.fromJson(strfmt1d, Dampening.class);
        ThresholdCondition jsonfmt1c2 = gson.fromJson(strfmt1c2, ThresholdCondition.class);
        ThresholdConditionEval jsonfmt1c2eval = gson.fromJson(strfmt1c2eval, ThresholdConditionEval.class);

        assertTrue(t1.equals(jsont1));
        assertTrue(fmt1c1.equals(jsonfmt1c1));
        assertTrue(fmt1c1eval.equals(jsonfmt1c1eval));
        assertTrue(fmt1d.equals(jsonfmt1d));
        assertTrue(fmt1c2.equals(jsonfmt1c2));
        assertTrue(fmt1c2eval.equals(jsonfmt1c2eval));

        assertTrue(rulesEngine.getFact(jsont1) != null);
        assertTrue(rulesEngine.getFact(jsonfmt1c1) != null);
        assertTrue(rulesEngine.getFact(jsonfmt1c1eval) != null);
        assertTrue(rulesEngine.getFact(jsonfmt1d) != null);
        assertTrue(rulesEngine.getFact(jsonfmt1c2) != null);
        assertTrue(rulesEngine.getFact(jsonfmt1c2eval) != null);
    }
}
