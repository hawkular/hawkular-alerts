/**
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
package org.hawkular.alerts.rest

import org.hawkular.alerts.api.model.trigger.Trigger
import org.jboss.logging.Logger
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

/**
 * These tests are intended to reproduce scenarios based on UI flows.
 *
 * Alerts UI should have two main areas:
 * - Dashboard == queries about specific alerts
 * - Definitions == CRUD operations about Triggers/Conditions/Dampenings/Notifiers
 *
 * A Trigger definition, from UI perspective can have:
 * - Specific properties for the trigger definition itself.
 * - A maximum of 4 conditions associated with it.
 * - Dampening informacion.
 * - A list of notifiers associated with this definition.
 *
 * @author Lucas Ponce
 */
class FlowUITest extends AbstractTestBase {
    private static final Logger log = Logger.getLogger(FlowUITest.class);

    String getRESTPrefix(String conditionClass) {
        if (conditionClass == null && conditionClass.isEmpty()) return ""

        if (conditionClass.equals("AvailabilityCondition")) {
            return "conditions/availability"
        } else if (conditionClass.equals("CompareCondition")) {
            return "conditions/compare"
        } else if (conditionClass.equals("StringCondition")) {
            return "conditions/string"
        } else if (conditionClass.equals("ThresholdCondition")) {
            return "conditions/threshold"
        } else if (conditionClass.equals("ThresholdRangeCondition")) {
            return "conditions/range"
        } else {
            return ""
        }
     }

    @Test
    void flowBrowseDefinitions() {
        /*
            Enter in the Alerts UI - Go the list of the triggers
         */
        def resp = client.get(path: "triggers")
        assertEquals(200, resp.status)

        def triggers = resp.data
        assert triggers.size() > 0

        for (int i = 0; i < triggers.size(); i++) {
            Trigger t = triggers[i]
            log.info(t.toString())

            /*
                Get all conditions for a trigger
             */
            resp = client.get(path: "triggers/" + t.getId() + "/conditions")
            if ( 204 == resp.status ) {
                continue;
            }
            assertEquals(200, resp.status)

            log.info("Conditions for " + t.getId());
            def conditions = resp.data
            for (int j = 0; j < conditions.size(); j++) {
                def prefix = getRESTPrefix(conditions[j].className)
                resp = client.get(path: prefix + "/" + conditions[j].conditionId);
                assertEquals(200, resp.status)
                def conditionsProperties = resp.data;
                log.info("Condition: " + conditionsProperties)
            }
            /*
                Get all dampenings for a trigger
             */
            try {
                resp = client.get(path: "trigger/dampening/" + t.getId())
                def dampening = resp.data;
                log.info("Dampening: " + dampening)
            } catch (e) {
                log.info("No dampening found")
            }
            /*
                Get all notifiers for a trigger
             */
            log.info("Notifiers: " + t.getNotifiers())
            for (String notifierId : t.getNotifiers()) {
                resp = client.get(path: "notifiers/" + notifierId)
                def notifier = resp.data
                log.info("Notifier: " + notifier)
            }
        }
    }

}
