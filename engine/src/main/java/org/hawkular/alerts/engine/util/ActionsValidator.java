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
package org.hawkular.alerts.engine.util;

import static org.hawkular.alerts.api.util.Util.isEmpty;

import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.trigger.TriggerAction;
import org.hawkular.alerts.log.AlertingLogger;
import org.hawkular.commons.log.MsgLogging;

/**
 * A Trigger can define a list of Actions that will be executed on Event/Alert generation.
 * A Trigger can optionally define constraints based on Alert's state and/or time intervals that indicates when an
 * action should be generated.
 *
 * This class is a helper to validate if an Event/Alert should be sent to the ActionsService based on the constraints
 * defined of a specific TriggerAction object.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class ActionsValidator {
    private static final AlertingLogger log = MsgLogging.getMsgLogger(AlertingLogger.class, ActionsValidator.class);
    /**
     * Validate if an Event should generate an Action based on the constraints defined on a TriggerAction.
     *
     * @param triggerAction a TriggerAction where status and time constraints are defined.
     * @param event a given Event to validate against a TriggerAction
     * @return true if the Event is validated and it should generated an action
     *         false on the contrary
     */
    public static boolean validate(TriggerAction triggerAction, Event event) {
        if (triggerAction == null || event == null) {
            return true;
        }
        if ((isEmpty(triggerAction.getStates()))
                && triggerAction.getCalendar() == null) {
            return true;
        }
        if (event instanceof Alert
                && triggerAction.getStates() != null
                && !triggerAction.getStates().isEmpty()
                && !triggerAction.getStates().contains( ((Alert)event).getStatus().name()) ) {
            return false;
        }
        if (triggerAction.getCalendar() != null) {
            try {
                return triggerAction.getCalendar().isSatisfiedBy(event.getCtime());
            } catch (Exception e) {
                log.debug(e.getMessage(), e);
                log.errorCannotValidateAction(e.getMessage());
            }
        }
        return true;
    }
}
