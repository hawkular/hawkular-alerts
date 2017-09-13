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
        ENABLED("enabled"),
        NAME("name"),
        SEVERITY("severity"),
        CONTEXT("context");

        private String name;

        Field(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

        public static Field getField(String name) {
            if (name == null || name.trim().isEmpty()) {
                return NAME;
            }

            for (Field f : values()) {
                // context.<key>
                if (CONTEXT == f && name.toLowerCase().startsWith("context.")) {
                    return f;
                } else if (f.getName().compareToIgnoreCase(name) == 0) {
                    return f;
                }
            }
            return NAME;
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

    public TriggerComparator() {
        this(Field.NAME.getName(), Order.Direction.ASCENDING);
    }

    public TriggerComparator(String fieldName, Order.Direction direction) {
        this.field = Field.getField(fieldName);
        if (Field.CONTEXT == this.field) {
            this.contextKey = Field.getContextKey(fieldName);
        }
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

            case ENABLED:
                if (o1.isEnabled() == o2.isEnabled()) {
                    return 0;
                }
                return (o1.isEnabled() ? 1 : -1) * iOrder;

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
        }
        return 0;
    }
}
