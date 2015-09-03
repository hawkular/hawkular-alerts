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

import java.util.HashMap;
import java.util.Map;

import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.dampening.Dampening;
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

        String triggerId = "thevault~local-web-multiple-jvm-metrics-trigger";
        String triggerDescription = "Multiple JVM Metrics for thevault~Local";

    }


}
