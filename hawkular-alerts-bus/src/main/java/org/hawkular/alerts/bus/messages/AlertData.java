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
package org.hawkular.alerts.bus.messages;

import com.google.gson.annotations.Expose;

import org.hawkular.alerts.api.model.data.Availability;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.data.NumericData;
import org.hawkular.alerts.api.model.data.StringData;

/**
 * A basic data to be consumed by the alerts subsystem.
 * This data will come from a JSON representation, so it will define target type as a field member.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class AlertData {

    @Expose
    private final String id;

    @Expose
    private final long timestamp;

    @Expose
    private final String value;

    @Expose
    private final String type;

    public AlertData() {
        this(null, 0, null, null);
    }

    public AlertData(String id, long timestamp, String value, String type) {
        this.id = id;
        this.timestamp = timestamp;
        this.value = value;
        this.type = type;
    }

    public Data convert() {
        if (type != null && !type.isEmpty() && type.equalsIgnoreCase("numeric")) {
            return new NumericData(id, timestamp, Double.valueOf(value));
        } else if (type != null && !type.isEmpty() && type.equalsIgnoreCase("availability")) {
            return new Availability(id, timestamp, Availability.AvailabilityType.valueOf(value));
        } else {
            return new StringData(id, timestamp, value);
        }
    }

    public String getId() {
        return id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + (int) (timestamp ^ (timestamp >>> 32));
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        result = prime * result + ((value == null) ? 0 : value.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AlertData other = (AlertData) obj;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        if (timestamp != other.timestamp)
            return false;
        if (type == null) {
            if (other.type != null)
                return false;
        } else if (!type.equals(other.type))
            return false;
        if (value == null) {
            if (other.value != null)
                return false;
        } else if (!value.equals(other.value))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "AlertData [id=" + id + ", timestamp=" + timestamp + ", value=" + value + ", type=" + type + "]";
    }

}
