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

    public Availability() {
        this(null, 0, AvailabilityType.UP);
    }

    /**
     * @param id the id
     * @param timestamp the timestamp
     * @param value Must be a valid {@link AvailabilityType} name.
     */
    public Availability(String id, long timestamp, String value) {
        super(id, timestamp, (null == value) ? AvailabilityType.UP : AvailabilityType.valueOf(value),
                Type.AVAILABILITY);
    }

    public Availability(String id, long timestamp, AvailabilityType value) {
        super(id, timestamp, (null == value) ? AvailabilityType.UP : value, Type.AVAILABILITY);
    }

    public AvailabilityType getValue() {
        return (AvailabilityType) value;
    }

    public void setValue(AvailabilityType value) {
        this.value = (null == value) ? AvailabilityType.UP : value;
    }

    @Override
    public String toString() {
        return "Availability [id=" + id + ", timestamp=" + timestamp + ", value=" + value + "]";
    }

    @Override
    int compareValue(Object value1, Object value2) {
        AvailabilityType v1 = (AvailabilityType) value1;
        AvailabilityType v2 = (AvailabilityType) value2;
        return v1.compareTo(v2);
    }
}
