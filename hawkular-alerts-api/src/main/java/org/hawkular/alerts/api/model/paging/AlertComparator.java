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
package org.hawkular.alerts.api.model.paging;

import java.util.Comparator;
import org.hawkular.alerts.api.model.condition.Alert;

/**
 *
 * @author Lucas Ponce
 */
public class AlertComparator implements Comparator<Alert> {

    public enum Field {
        ALERT_ID("alertId"),
        TRIGGER_ID("triggerId"),
        CTIME("ctime"),
        SEVERITY("severity"),
        STATUS("status");

        private String text;

        Field(String text) {
            this.text = text;
        }

        public String getText() {
            return this.text;
        }

        public static Field getField(String text) {
            if (text == null || text.isEmpty()) {
                return ALERT_ID;
            }
            for (Field f : values()) {
                if (f.getText().compareToIgnoreCase(text) == 0) {
                    return f;
                }
            }
            return ALERT_ID;
        }
    };

    private Field field;
    private Order.Direction direction;

    public AlertComparator() {
        this(Field.ALERT_ID, Order.Direction.ASCENDING);
    }

    public AlertComparator(Field field, Order.Direction direction) {
        this.field = field;
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
        switch (field) {
            case ALERT_ID:
                return o1.getAlertId().compareTo(o2.getAlertId()) * iOrder;
            case TRIGGER_ID:
                if (o1.getTriggerId() == null && o2.getTriggerId() == null) {
                    return 0;
                }
                if (o1.getTriggerId() == null && o2.getTriggerId() != null) {
                    return 1;
                }
                if (o1.getTriggerId() != null && o2.getTriggerId() == null) {
                    return -1;
                }
                return o1.getTriggerId().compareTo(o2.getTriggerId()) * iOrder;
            case CTIME:
                return (int)((o1.getCtime() - o2.getCtime()) * iOrder);
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
        }
        return 0;
    }
}
