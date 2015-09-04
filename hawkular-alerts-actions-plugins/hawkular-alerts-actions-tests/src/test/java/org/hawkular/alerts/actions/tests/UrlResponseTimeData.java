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
import org.hawkular.alerts.api.model.condition.ThresholdCondition;
import org.hawkular.alerts.api.model.condition.ThresholdConditionEval;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.data.NumericData;
import org.hawkular.alerts.api.model.trigger.Mode;
import org.hawkular.alerts.api.model.trigger.Trigger;

/**
 * Provide test data for Response Time Alerts on Url resources
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class UrlResponseTimeData extends CommonData {

    public static Trigger trigger;
    public static ThresholdCondition firingCondition;
    public static ThresholdCondition autoResolveCondition;
    public static Dampening firingDampening;

    static {

        Map<String, String> context = new HashMap<>();
        context.put("resourceType", "URL");
        context.put("resourceName", "http://www.jboss.org");

        String triggerId = "jboss-url-response-time-trigger";
        String triggerDescription = "Response Time for http://www.jboss.org";
        String dataId = "jboss-url-response-time-data-id";

        trigger = new Trigger(TEST_TENANT,
                triggerId,
                triggerDescription,
                context);

        firingCondition = new ThresholdCondition(trigger.getId(),
                Mode.FIRING,
                dataId,
                ThresholdCondition.Operator.GT,
                1000d);
        firingCondition.setTenantId(TEST_TENANT);
        firingCondition.getContext().put("description", "Response Time");
        firingCondition.getContext().put("unit", "ms");

        autoResolveCondition = new ThresholdCondition(trigger.getId(),
                Mode.AUTORESOLVE,
                dataId,
                ThresholdCondition.Operator.LTE,
                1000d);
        autoResolveCondition.setTenantId(TEST_TENANT);
        autoResolveCondition.getContext().put("description", "Response Time");
        autoResolveCondition.getContext().put("unit", "ms");

        firingDampening = Dampening.forStrictTimeout(trigger.getId(),
                Mode.FIRING,
                10000);
        firingDampening.setTenantId(TEST_TENANT);

    }

    public static Alert getOpenAlert() {

        List<Set<ConditionEval>> satisfyingEvals = new ArrayList<>();

        NumericData rtBadData1 = new NumericData(firingCondition.getDataId(),
                System.currentTimeMillis(),
                1900d);
        ThresholdConditionEval eval1 = new ThresholdConditionEval(firingCondition, rtBadData1);

        Set<ConditionEval> evalSet1 = new HashSet<>();
        evalSet1.add(eval1);
        satisfyingEvals.add(evalSet1);

        // 5 seconds later
        NumericData rtBadData2 = new NumericData(firingCondition.getDataId(),
                System.currentTimeMillis() + 5000,
                1800d);
        ThresholdConditionEval eval2 = new ThresholdConditionEval(firingCondition, rtBadData2);

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
                900d);
        ThresholdConditionEval eval1 = new ThresholdConditionEval(autoResolveCondition, rtGoodData);
        Set<ConditionEval> evalSet1 = new HashSet<>();
        evalSet1.add(eval1);
        resolvedEvals.add(evalSet1);

        unresolvedAlert.setResolvedEvalSets(resolvedEvals);
        unresolvedAlert.setStatus(Alert.Status.RESOLVED);
        unresolvedAlert.setResolvedBy(RESOLVED_BY);
        unresolvedAlert.setResolvedNotes(RESOLVED_NOTES);

        return unresolvedAlert;
    }

}
