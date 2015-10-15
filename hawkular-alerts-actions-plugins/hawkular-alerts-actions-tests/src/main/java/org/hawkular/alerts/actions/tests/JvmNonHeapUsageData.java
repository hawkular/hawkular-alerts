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
package org.hawkular.alerts.actions.tests;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.api.model.condition.Alert;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.condition.ThresholdRangeCondition;
import org.hawkular.alerts.api.model.condition.ThresholdRangeConditionEval;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.data.NumericData;
import org.hawkular.alerts.api.model.trigger.Mode;
import org.hawkular.alerts.api.model.trigger.Trigger;

/**
 * Provide test data for Non Heap Usage Alerts on Jvm resources
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class JvmNonHeapUsageData extends CommonData {

    public static Trigger trigger;
    public static ThresholdRangeCondition firingCondition;
    public static ThresholdRangeCondition autoResolveCondition;
    public static Dampening firingDampening;

    static {

        Map<String, String> context = new HashMap<>();
        context.put("resourceType", "App Server");
        context.put("resourceName", "thevault~Local");
        context.put("category", "JVM");

        String triggerId = "thevault~local-jvm-non-heap-usage-trigger";
        String triggerDescription = "JVM Non-Heap Usage for thevault~Local";
        String dataId = "thevault~local-jvm-non-heap-usage-data-id";

        trigger = new Trigger(TEST_TENANT,
                triggerId,
                triggerDescription,
                context);

        firingCondition = new ThresholdRangeCondition(trigger.getId(),
                Mode.FIRING,
                dataId,
                ThresholdRangeCondition.Operator.INCLUSIVE,
                ThresholdRangeCondition.Operator.INCLUSIVE,
                100d,
                300d,
                false);
        firingCondition.setTenantId(TEST_TENANT);
        firingCondition.getContext().put("description", "Non Heap Usage");
        firingCondition.getContext().put("unit", "Mb");

        autoResolveCondition = new ThresholdRangeCondition(trigger.getId(),
                Mode.FIRING,
                dataId,
                ThresholdRangeCondition.Operator.EXCLUSIVE,
                ThresholdRangeCondition.Operator.EXCLUSIVE,
                100d,
                300d,
                true);
        autoResolveCondition.setTenantId(TEST_TENANT);
        autoResolveCondition.getContext().put("description", "Non Heap Usage");
        autoResolveCondition.getContext().put("unit", "Mb");

        firingDampening = Dampening.forStrictTimeout(trigger.getId(),
                Mode.FIRING,
                10000);
        firingDampening.setTenantId(TEST_TENANT);

    }

    public static Alert getOpenAlert() {

        List<Set<ConditionEval>> satisfyingEvals = new ArrayList<>();

        NumericData rtBadData1 = new NumericData(firingCondition.getDataId(),
                System.currentTimeMillis(),
                315d);
        ThresholdRangeConditionEval eval1 = new ThresholdRangeConditionEval(firingCondition, rtBadData1);

        Set<ConditionEval> evalSet1 = new HashSet<>();
        evalSet1.add(eval1);
        satisfyingEvals.add(evalSet1);

        // 5 seconds later
        NumericData rtBadData2 = new NumericData(firingCondition.getDataId(),
                System.currentTimeMillis() + 5000,
                350d);
        ThresholdRangeConditionEval eval2 = new ThresholdRangeConditionEval(firingCondition, rtBadData2);

        Set<ConditionEval> evalSet2 = new HashSet<>();
        evalSet2.add(eval2);
        satisfyingEvals.add(evalSet2);

        Alert openAlert = new Alert(trigger.getTenantId(), trigger.getId(), trigger.getSeverity(), satisfyingEvals);
        openAlert.setTrigger(trigger);
        openAlert.setDampening(firingDampening);
        openAlert.setContext(trigger.getContext());

        return openAlert;
    }

    public static Alert resolveAlert(Alert unresolvedAlert) {
        List<Set<ConditionEval>> resolvedEvals = new ArrayList<>();

        NumericData rtGoodData = new NumericData(autoResolveCondition.getDataId(),
                System.currentTimeMillis() + 20000,
                150d);
        ThresholdRangeConditionEval eval1 = new ThresholdRangeConditionEval(autoResolveCondition, rtGoodData);
        Set<ConditionEval> evalSet1 = new HashSet<>();
        evalSet1.add(eval1);
        resolvedEvals.add(evalSet1);

        unresolvedAlert.setResolvedEvalSets(resolvedEvals);
        unresolvedAlert.setStatus(Alert.Status.RESOLVED);
        unresolvedAlert.setResolvedBy(RESOLVED_BY);
        unresolvedAlert.addNote(RESOLVED_BY, RESOLVED_NOTES);
        unresolvedAlert.setResolvedTime(System.currentTimeMillis());

        return unresolvedAlert;
    }

}
