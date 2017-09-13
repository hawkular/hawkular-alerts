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

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public final class Order {
    private final String field;
    private final Direction direction;

    public Order(String field, Direction direction) {
        this.field = field;
        this.direction = direction;
    }

    public static Order by(String field, Direction direction) {
        return new Order(field, direction);
    }

    public static Order unspecified() {
        return new Order(null, Direction.ASCENDING);
    }

    public Direction getDirection() {
        return direction;
    }

    public String getField() {
        return field;
    }

    public boolean isSpecific() {
        return field != null;
    }

    public boolean isAscending() {
        return direction == Direction.ASCENDING;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order)) return false;

        Order order = (Order) o;

        return field.equals(order.field) && direction == order.direction;
    }

    @Override
    public int hashCode() {
        int result = field.hashCode();
        result = 31 * result + direction.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Order[" + "direction=" + direction + ", field='" + field + '\'' + ']';
    }

    public enum Direction {
        ASCENDING("asc"), DESCENDING("desc");

        private final String shortString;

        Direction(String shortString) {
            this.shortString = shortString;
        }

        public static Direction fromShortString(String shortString) {
            switch (shortString) {
                case "asc":
                    return ASCENDING;
                case "desc":
                    return DESCENDING;
                default:
                    throw new IllegalArgumentException("Unkown short ordering direction representation: " +
                            shortString);
            }
        }

        public String getShortString() {
            return shortString;
        }
    }
}
