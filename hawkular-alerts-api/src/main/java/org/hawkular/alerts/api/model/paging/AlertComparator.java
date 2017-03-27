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
package org.hawkular.alerts.api.model.paging;

import java.util.Comparator;

import org.hawkular.alerts.api.model.event.Alert;

/**
 *
 * @author Lucas Ponce
 */
public class AlertComparator implements Comparator<Alert> {

    public enum Field {
        ALERT_ID("alertId"),
        TRIGGER_DESCRIPTION("trigger.description"),
        TRIGGER_ID("trigger.id"),
        TRIGGER_NAME("trigger.name"),
        CTIME("ctime"),
        SEVERITY("severity"),
        STATUS("status"),
        STIME("stime"),
        CONTEXT("context");

        private String text;

        Field(String text) {
            this.text = text;
        }

        public String getText() {
            return this.text;
        }

        public static Field getField(String text) {
            if (text == null || text.trim().isEmpty()) {
                return ALERT_ID;
            }

            for (Field f : values()) {
                // context.<key>
                if (CONTEXT == f && text.toLowerCase().startsWith("context.")) {
                    return f;
                } else if (f.getText().compareToIgnoreCase(text) == 0) {
                    return f;
                }
            }
            return ALERT_ID;
        }

        public static String getContextKey(String context) {
            if (context == null || context.trim().isEmpty() || !context.toLowerCase().startsWith("context.")) {
                return "";
            }
            return context.substring(8);
        }
    };

    private Field field;
    private String contextKey;
    private Order.Direction direction;

    public AlertComparator() {
        this("alertId", Order.Direction.ASCENDING);
    }

    public AlertComparator(String field, Order.Direction direction) {
        this.field = Field.getField(field);
        if (Field.CONTEXT == this.field) {
            this.contextKey = Field.getContextKey(field);
        }
        this.direction = direction;
    }

    @Override
    public int compare(Alert o1, Alert o2) {
        if (o1 == null && o2 == null) {
            return 0;
        }
        if (o1 == null && o2 != null) {
            return 1;
        }
        if (o1 != null && o2 == null) {
            return -1;
        }
        int iOrder = direction == Order.Direction.ASCENDING ? 1 : -1;
        /*
            Using tenant comparator first
         */
        int tenantComparator = o1.getTenantId().compareTo(o2.getTenantId());
        if (tenantComparator != 0) {
            return tenantComparator * iOrder;
        }
        switch (field) {
            case ALERT_ID:
                return o1.getAlertId().compareTo(o2.getAlertId()) * iOrder;
            case CTIME:
                return (int) ((o1.getCtime() - o2.getCtime()) * iOrder);
            case STIME:
                return (int) ((o1.getCurrentLifecycle().getStime() - o2.getCurrentLifecycle().getStime()) * iOrder);
            case SEVERITY:
                if (o1.getSeverity() == null && o2.getSeverity() == null) {
                    return 0;
                }
                if (o1.getSeverity() == null && o2.getSeverity() != null) {
                    return 1;
                }
                if (o1.getSeverity() != null && o2.getSeverity() == null) {
                    return -1;
                }
                return o1.getSeverity().compareTo(o2.getSeverity()) * iOrder;
            case STATUS:
                if (o1.getStatus() == null && o2.getStatus() == null) {
                    return 0;
                }
                if (o1.getStatus() == null && o2.getStatus() != null) {
                    return 1;
                }
                if (o1.getStatus() != null && o2.getStatus() == null) {
                    return -1;
                }
                return o1.getStatus().compareTo(o2.getStatus()) * iOrder;
            case CONTEXT:
                if (o1.getContext() == null && o2.getContext() == null) {
                    return 0;
                }
                if (o1.getContext().isEmpty() && o2.getContext().isEmpty()) {
                    return 0;
                }
                if (!o1.getContext().containsKey(contextKey) && !o2.getContext().containsKey(contextKey)) {
                    return 0;
                }
                if (!o1.getContext().containsKey(contextKey) && o2.getContext().containsKey(contextKey)) {
                    return 1;
                }
                if (!o1.getContext().containsKey(contextKey) && !o2.getContext().containsKey(contextKey)) {
                    return -1;
                }
                return o1.getContext().get(contextKey).compareTo(o2.getContext().get(contextKey)) * iOrder;
            case TRIGGER_DESCRIPTION:
                String o1TriggerDesc = o1.getTrigger().getDescription();
                String o2TriggerDesc = o2.getTrigger().getDescription();
                if (o1TriggerDesc == null && o2TriggerDesc == null) {
                    return 0;
                }
                if (o1TriggerDesc == null && o2TriggerDesc != null) {
                    return 1;
                }
                if (o1TriggerDesc != null && o2TriggerDesc == null) {
                    return -1;
                }
                return o1TriggerDesc.compareTo(o2TriggerDesc) * iOrder;
            case TRIGGER_ID:
                String o1TriggerId = o1.getTrigger().getId();
                String o2TriggerId = o2.getTrigger().getId();
                if (o1TriggerId == null && o2TriggerId == null) {
                    return 0;
                }
                if (o1TriggerId == null && o2TriggerId != null) {
                    return 1;
                }
                if (o1TriggerId != null && o2TriggerId == null) {
                    return -1;
                }
                return o1TriggerId.compareTo(o2TriggerId) * iOrder;
            case TRIGGER_NAME:
                String o1TriggerName = o1.getTrigger().getName();
                String o2TriggerName = o2.getTrigger().getName();
                if (o1TriggerName == null && o2TriggerName == null) {
                    return 0;
                }
                if (o1TriggerName == null && o2TriggerName != null) {
                    return 1;
                }
                if (o1TriggerName != null && o2TriggerName == null) {
                    return -1;
                }
                return o1TriggerName.compareTo(o2TriggerName) * iOrder;
        }
        return 0;
    }
}
