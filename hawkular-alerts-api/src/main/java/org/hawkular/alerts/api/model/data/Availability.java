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
package org.hawkular.alerts.api.model.data;

/**
 * An availability incoming data.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class Availability extends Data {

    public enum AvailabilityType {
        UP, DOWN, UNAVAILABLE
    }

    private AvailabilityType value;

    public Availability() {
        this(null, 0, null);
    }

    public Availability(String id, long timestamp, AvailabilityType value) {
        super(id, timestamp);
        this.value = value;
    }

    public AvailabilityType getValue() {
        return value;
    }

    public void setValue(AvailabilityType value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;

        Availability that = (Availability) o;

        if (value != that.value)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Availability [value=" + value + ", getId()=" + getId() + ", getTimestamp()=" + getTimestamp() + "]";
    }

}
