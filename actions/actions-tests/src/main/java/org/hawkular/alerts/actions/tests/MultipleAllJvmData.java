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
package org.hawkular.alerts.actions.tests;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.condition.ThresholdCondition;
import org.hawkular.alerts.api.model.condition.ThresholdConditionEval;
import org.hawkular.alerts.api.model.condition.ThresholdRangeCondition;
import org.hawkular.alerts.api.model.condition.ThresholdRangeConditionEval;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.trigger.Match;
import org.hawkular.alerts.api.model.trigger.Mode;
import org.hawkular.alerts.api.model.trigger.Trigger;

/**
 * Provide test data for multiple conditions alert on Jvm resources
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class MultipleAllJvmData extends CommonData {

    public static Trigger trigger;
    public static Condition[] firingConditions;
    public static Condition[] autoResolveConditions;
    public static Dampening firingDampening;

    static {
        Map<String, String> context = new HashMap<>();
        context.put("resourceType", "App Server");
        context.put("resourceName", "thevault~Local");
        context.put("category", "JVM");

        firingConditions = new Condition[3];
        autoResolveConditions = new Condition[3];
        String[] dataId = new String[3];

        String triggerId = "thevault~local-web-multiple-jvm-metrics-trigger";
        String triggerDescription = "Multiple JVM Metrics for thevault~Local";
        dataId[0] = "thevault~local-jvm-garbage-collection-data-id";
        dataId[1] = "thevault~local-jvm-heap-usage-data-id";
        dataId[2] = "thevault~local-jvm-non-heap-usage-data-id";

        trigger = new Trigger(TENANT,
                triggerId,
                triggerDescription,
                context);
        trigger.setFiringMatch(Match.ALL);
        trigger.setAutoResolveMatch(Match.ALL);

        firingConditions[0] = new ThresholdCondition(TENANT, trigger.getId(),
                Mode.FIRING,
                3,
                1,
                dataId[0],
                ThresholdCondition.Operator.GT,
                1000d);
        firingConditions[0].getContext().put("description", "GC Duration");
        firingConditions[0].getContext().put("unit", "ms");

        autoResolveConditions[0] = new ThresholdCondition(TENANT, trigger.getId(),
                Mode.AUTORESOLVE,
                3,
                1,
                dataId[0],
                ThresholdCondition.Operator.LTE,
                1000d);
        autoResolveConditions[0].getContext().put("description", "GC Duration");
        autoResolveConditions[0].getContext().put("unit", "ms");


        firingConditions[1] = new ThresholdRangeCondition(TENANT, trigger.getId(),
                Mode.FIRING,
                3,
                2,
                dataId[1],
                ThresholdRangeCondition.Operator.INCLUSIVE,
                ThresholdRangeCondition.Operator.INCLUSIVE,
                100d,
                300d,
                false);
        firingConditions[1].getContext().put("description", "Heap Usage");
        firingConditions[1].getContext().put("unit", "Mb");

        autoResolveConditions[1] = new ThresholdRangeCondition(TENANT, trigger.getId(),
                Mode.FIRING,
                3,
                2,
                dataId[1],
                ThresholdRangeCondition.Operator.EXCLUSIVE,
                ThresholdRangeCondition.Operator.EXCLUSIVE,
                100d,
                300d,
                true);
        autoResolveConditions[1].getContext().put("description", "Heap Usage");
        autoResolveConditions[1].getContext().put("unit", "Mb");


        firingConditions[2] = new ThresholdRangeCondition(TENANT, trigger.getId(),
                Mode.FIRING,
                3,
                3,
                dataId[2],
                ThresholdRangeCondition.Operator.INCLUSIVE,
                ThresholdRangeCondition.Operator.INCLUSIVE,
                100d,
                300d,
                false);
        firingConditions[2].getContext().put("description", "Non Heap Usage");
        firingConditions[2].getContext().put("unit", "Mb");

        autoResolveConditions[2] = new ThresholdRangeCondition(TENANT, trigger.getId(),
                Mode.FIRING,
                3,
                3,
                dataId[2],
                ThresholdRangeCondition.Operator.EXCLUSIVE,
                ThresholdRangeCondition.Operator.EXCLUSIVE,
                100d,
                200d,
                true);
        autoResolveConditions[2].getContext().put("description", "Non Heap Usage");
        autoResolveConditions[2].getContext().put("unit", "Mb");

        firingDampening = Dampening.forStrictTimeout(TENANT, trigger.getId(),
                Mode.FIRING, 10000);
    }

    public static Alert getOpenAlert() {

        List<Set<ConditionEval>> satisfyingEvals = new ArrayList<>();

        Data rtBadData1a = Data.forNumeric(TENANT, firingConditions[0].getDataId(),
                System.currentTimeMillis(),
                1900d);
        ThresholdConditionEval eval1a = new ThresholdConditionEval((ThresholdCondition) firingConditions[0],
                rtBadData1a);

        Set<ConditionEval> evalSet1 = new HashSet<>();

        evalSet1.add(eval1a);

        Data rtBadData1b = Data.forNumeric(TENANT, firingConditions[1].getDataId(),
                System.currentTimeMillis(),
                315d);
        ThresholdRangeConditionEval eval1b = new ThresholdRangeConditionEval((ThresholdRangeCondition)
                firingConditions[1], rtBadData1b);

        evalSet1.add(eval1b);

        Data rtBadData1c = Data.forNumeric(TENANT, firingConditions[2].getDataId(),
                System.currentTimeMillis(),
                215d);
        ThresholdRangeConditionEval eval1c = new ThresholdRangeConditionEval((ThresholdRangeCondition)
                firingConditions[2], rtBadData1c);

        evalSet1.add(eval1c);

        satisfyingEvals.add(evalSet1);

        // 5 seconds later
        Data rtBadData2a = Data.forNumeric(TENANT, firingConditions[0].getDataId(),
                System.currentTimeMillis() + 5000,
                1800d);
        ThresholdConditionEval eval2a = new ThresholdConditionEval((ThresholdCondition) firingConditions[0],
                rtBadData2a);

        Set<ConditionEval> evalSet2 = new HashSet<>();

        evalSet2.add(eval2a);

        Data rtBadData2b = Data.forNumeric(TENANT, firingConditions[1].getDataId(),
                System.currentTimeMillis() + 5000,
                350d);
        ThresholdRangeConditionEval eval2b = new ThresholdRangeConditionEval((ThresholdRangeCondition)
                firingConditions[1], rtBadData2b);

        evalSet2.add(eval2b);

        Data rtBadData2c = Data.forNumeric(TENANT, firingConditions[2].getDataId(),
                System.currentTimeMillis() + 5000,
                250d);
        ThresholdRangeConditionEval eval2c = new ThresholdRangeConditionEval((ThresholdRangeCondition)
                firingConditions[2], rtBadData2c);

        evalSet2.add(eval2c);

        satisfyingEvals.add(evalSet2);

        Alert openAlert = new Alert(trigger.getTenantId(), trigger, firingDampening, satisfyingEvals);

        return openAlert;
    }

    public static Alert resolveAlert(Alert unresolvedAlert) {
        List<Set<ConditionEval>> resolvedEvals = new ArrayList<>();

        Data rtGoodDataA = Data.forNumeric(TENANT, autoResolveConditions[0].getDataId(),
                System.currentTimeMillis() + 20000,
                900d);
        ThresholdConditionEval eval1A = new ThresholdConditionEval((ThresholdCondition) autoResolveConditions[0],
                rtGoodDataA);
        Set<ConditionEval> evalSet1 = new HashSet<>();
        evalSet1.add(eval1A);

        Data rtGoodDataB = Data.forNumeric(TENANT, autoResolveConditions[1].getDataId(),
                System.currentTimeMillis() + 20000,
                150d);
        ThresholdRangeConditionEval eval1B = new ThresholdRangeConditionEval((ThresholdRangeCondition)
                autoResolveConditions[1], rtGoodDataB);

        evalSet1.add(eval1B);

        Data rtGoodData = Data.forNumeric(TENANT, autoResolveConditions[2].getDataId(),
                System.currentTimeMillis() + 20000,
                125d);
        ThresholdRangeConditionEval eval1C = new ThresholdRangeConditionEval((ThresholdRangeCondition)
                autoResolveConditions[2], rtGoodData);

        evalSet1.add(eval1C);

        resolvedEvals.add(evalSet1);

        return resolveAlert(unresolvedAlert, resolvedEvals);
    }


}
