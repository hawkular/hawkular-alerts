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

import org.hawkular.alerts.api.model.trigger.Trigger;

/**
 * Default ordering: Name Ascending
 *
 * @author Lucas Ponce
 * @author Jay Shaughnessy
 */
public class TriggerComparator implements Comparator<Trigger> {

    public enum Field {
        ID("id"),
        DESCRIPTION("description"),
        NAME("name");

        private String name;

        Field(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

        public static Field getName(String name) {
            if (name == null || name.isEmpty()) {
                return NAME;
            }
            for (Field f : values()) {
                if (f.getName().compareToIgnoreCase(name) == 0) {
                    return f;
                }
            }
            return NAME;
        }
    };

    private Field field;
    private Order.Direction direction;

    public TriggerComparator() {
        this(Field.NAME, Order.Direction.ASCENDING);
    }

    public TriggerComparator(Field field, Order.Direction direction) {
        this.field = field;
        this.direction = direction;
    }

    @Override
    public int compare(Trigger o1, Trigger o2) {
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
            case DESCRIPTION:
                if (o1.getDescription() == null && o2.getDescription() == null) {
                    return 0;
                }
                if (o1.getDescription() == null && o2.getDescription() != null) {
                    return 1;
                }
                if (o1.getDescription() != null && o2.getDescription() == null) {
                    return -1;
                }
                return o1.getDescription().compareTo(o2.getDescription()) * iOrder;
            case NAME:
                if (o1.getDescription() == null && o2.getName() == null) {
                    return 0;
                }
                if (o1.getName() == null && o2.getName() != null) {
                    return 1;
                }
                if (o1.getName() != null && o2.getName() == null) {
                    return -1;
                }
                return o1.getName().compareTo(o2.getName()) * iOrder;
        }
        return 0;
    }
}
