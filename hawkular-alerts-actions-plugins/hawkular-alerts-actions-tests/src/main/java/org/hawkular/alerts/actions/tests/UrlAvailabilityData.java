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

import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.AvailabilityConditionEval;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.data.AvailabilityType;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.trigger.Mode;
import org.hawkular.alerts.api.model.trigger.Trigger;

/**
 * Provide test data for Data Alerts on Url resources
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class UrlAvailabilityData extends CommonData {

    public static Trigger trigger;
    public static AvailabilityCondition firingCondition;
    public static AvailabilityCondition autoResolveCondition;
    public static Dampening firingDampening;

    static {

        Map<String, String> context = new HashMap<>();
        context.put("resourceType", "URL");
        context.put("resourceName", "http://www.jboss.org");

        String triggerId = "jboss-url-availability-trigger";
        String triggerDescription = "Data for http://www.jboss.org";
        String dataId = "jboss-url-availability-data-id";

        trigger = new Trigger(TEST_TENANT,
                triggerId,
                triggerDescription,
                context);

        firingCondition = new AvailabilityCondition(trigger.getId(),
                Mode.FIRING,
                dataId,
                AvailabilityCondition.Operator.NOT_UP);
        firingCondition.setTenantId(TEST_TENANT);
        firingCondition.getContext().put("description", "Availability");

        autoResolveCondition = new AvailabilityCondition(trigger.getId(),
                Mode.AUTORESOLVE,
                dataId,
                AvailabilityCondition.Operator.UP);
        autoResolveCondition.setTenantId(TEST_TENANT);
        autoResolveCondition.getContext().put("description", "Availability");

        firingDampening = Dampening.forStrictTime(trigger.getId(),
                Mode.FIRING,
                10000);
        firingDampening.setTenantId(TEST_TENANT);

    }

    public static Alert getOpenAlert() {

        List<Set<ConditionEval>> satisfyingEvals = new ArrayList<>();

        Data avBadData1 = Data.forAvailability(firingCondition.getDataId(),
                System.currentTimeMillis(),
                AvailabilityType.DOWN);
        AvailabilityConditionEval eval1 = new AvailabilityConditionEval(firingCondition, avBadData1);

        Set<ConditionEval> evalSet1 = new HashSet<>();
        evalSet1.add(eval1);
        satisfyingEvals.add(evalSet1);

        // 5 seconds later
        Data avBadData2 = Data.forAvailability(firingCondition.getDataId(),
                System.currentTimeMillis() + 5000,
                AvailabilityType.DOWN);
        AvailabilityConditionEval eval2 = new AvailabilityConditionEval(firingCondition, avBadData2);

        Set<ConditionEval> evalSet2 = new HashSet<>();
        evalSet2.add(eval2);
        satisfyingEvals.add(evalSet2);

        Alert openAlert = new Alert(trigger.getTenantId(), trigger, firingDampening, satisfyingEvals);

        return openAlert;
    }

    public static Alert resolveAlert(Alert unresolvedAlert) {
        List<Set<ConditionEval>> resolvedEvals = new ArrayList<>();

        Data avGoodData = Data.forAvailability(autoResolveCondition.getDataId(), System.currentTimeMillis() + 20000,
                AvailabilityType.UP);
        AvailabilityConditionEval eval1 = new AvailabilityConditionEval(autoResolveCondition, avGoodData);
        Set<ConditionEval> evalSet1 = new HashSet<>();
        evalSet1.add(eval1);
        resolvedEvals.add(evalSet1);

        unresolvedAlert.setResolvedEvalSets(resolvedEvals);
        unresolvedAlert.setStatus(Alert.Status.RESOLVED);
        unresolvedAlert.setResolvedBy(RESOLVED_BY);
        unresolvedAlert.setResolvedNotes(RESOLVED_NOTES);
        unresolvedAlert.setResolvedTime(System.currentTimeMillis());

        return unresolvedAlert;
    }

}
