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

import org.hawkular.alerts.api.model.event.Event;

/**
 *
 * @author Lucas Ponce
 */
public class EventComparator implements Comparator<Event> {

    public enum Field {
        ID("id"),
        CATEGORY("category"),
        CTIME("ctime"),
        TEXT("text"),
        TRIGGER_ID("triggerId");

        private String name;

        Field(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

        public static Field getField(String name) {
            if (name == null || name.isEmpty()) {
                return ID;
            }
            for (Field f : values()) {
                if (f.getName().compareToIgnoreCase(name) == 0) {
                    return f;
                }
            }
            return ID;
        }
    };

    private Field field;
    private Order.Direction direction;

    public EventComparator() {
        this(Field.ID, Order.Direction.ASCENDING);
    }

    public EventComparator(Field field, Order.Direction direction) {
        this.field = field;
        this.direction = direction;
    }

    @Override
    public int compare(Event o1, Event o2) {
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
            case ID:
                return o1.getId().compareTo(o2.getId()) * iOrder;

            case CATEGORY:
                if (o1.getCategory() == null && o2.getCategory() == null) {
                    return 0;
                }
                if (o1.getCategory() == null && o2.getCategory() != null) {
                    return 1;
                }
                if (o1.getCategory() != null && o2.getCategory() == null) {
                    return -1;
                }
                return o1.getCategory().compareTo(o2.getCategory()) * iOrder;

            case CTIME:
                return (int) ((o1.getCtime() - o2.getCtime()) * iOrder);

            case TEXT:
                if (o1.getText() == null && o2.getText() == null) {
                    return 0;
                }
                if (o1.getText() == null && o2.getText() != null) {
                    return 1;
                }
                if (o1.getText() != null && o2.getText() == null) {
                    return -1;
                }
                return o1.getText().compareTo(o2.getText()) * iOrder;

            case TRIGGER_ID:
                String o1TriggerId = null == o1.getTrigger() ? null : o1.getTrigger().getId();
                String o2TriggerId = null == o2.getTrigger() ? null : o2.getTrigger().getId();
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

        }
        return 0;
    }
}
