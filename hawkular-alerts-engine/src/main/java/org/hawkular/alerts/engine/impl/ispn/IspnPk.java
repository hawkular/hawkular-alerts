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
package org.hawkular.alerts.engine.impl.ispn;

import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.model.action.ActionDefinition;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.trigger.Trigger;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class IspnPk {

    public static String pk(Action action) {
        if (action == null) {
            return null;
        }
        return new StringBuilder("Action-")
                .append(action.getTenantId())
                .append("-")
                .append(action.getActionPlugin())
                .append("-")
                .append(action.getActionId())
                .append("-")
                .append(action.getEventId())
                .append("-")
                .append(action.getCtime())
                .toString();
    }

    public static String pk(String actionPlugin) {
        if (actionPlugin == null) {
            return null;
        }
        return new StringBuilder("ActionPlugin-").append(actionPlugin).toString();
    }

    public static String pk(ActionDefinition actionDefinition) {
        if (actionDefinition == null) {
            return null;
        }
        return new StringBuilder("ActionDefinition-")
                .append(actionDefinition.getTenantId())
                .append("-")
                .append(actionDefinition.getActionPlugin())
                .append("-")
                .append(actionDefinition.getActionId())
                .toString();
    }

    public static String pk(String tenantId, String actionPlugin, String actionId) {
        if (tenantId == null || actionPlugin == null || actionId == null) {
            return null;
        }
        return new StringBuilder("ActionDefinition-")
                .append(tenantId)
                .append("-")
                .append(actionPlugin)
                .append("-")
                .append(actionId)
                .toString();
    }

    public static String pk(Condition condition) {
        if (condition == null) {
            return null;
        }
        return new StringBuilder("Condition-")
                .append(condition.getConditionId())
                .toString();
    }

    public static String pk(Dampening dampening) {
        if (dampening == null) {
            return null;
        }
        return pkFromDampeningId(dampening.getDampeningId());
    }

    public static String pkFromDampeningId(String dampeningId) {
        if (dampeningId == null) {
            return null;
        }
        return new StringBuilder("Dampening-")
                .append(dampeningId)
                .toString();
    }

    public static String pk(Trigger trigger) {
        if (trigger == null) {
            return null;
        }
        return pkFromTriggerId(trigger.getTenantId(), trigger.getId());
    }

    public static String pkFromTriggerId(String tenantId, String triggerId) {
        if (tenantId == null || triggerId == null) {
            return null;
        }
        return new StringBuilder("Trigger-")
                .append(tenantId)
                .append("-")
                .append(triggerId)
                .toString();
    }

    public static String pk(Event event) {
        if (event == null) {
            return null;
        }
        return new StringBuilder("Event-")
                .append(event.getTenantId())
                .append("-")
                .append(event.getId())
                .toString();
    }

    public static String pkFromEventId(String tenantId, String eventId) {
        if (tenantId == null || eventId == null) {
            return null;
        }
        return new StringBuilder("Event-")
                .append(tenantId)
                .append("-")
                .append(eventId)
                .toString();
    }
}
