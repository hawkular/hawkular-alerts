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
    private final String value;

    @Expose
    private final String type;

    public AlertData() {
        this(null, null, null);
    }

    public AlertData(String id, String value, String type) {
        this.id = id;
        this.value = value;
        this.type = type;
    }

    public Data convert() {
        if (type != null && !type.isEmpty() && type.equalsIgnoreCase("numeric")) {
            return new NumericData(id, Double.valueOf(value));
        } else if (type != null && !type.isEmpty() && type.equalsIgnoreCase("availability")) {
            return new Availability(id, Availability.AvailabilityType.valueOf(value));
        } else {
            return new StringData(id, value);
        }
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AlertData alertData = (AlertData) o;

        if (id != null ? !id.equals(alertData.id) : alertData.id != null) return false;
        if (type != null ? !type.equals(alertData.type) : alertData.type != null) return false;
        if (value != null ? !value.equals(alertData.value) : alertData.value != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (value != null ? value.hashCode() : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "AlertData{" +
                "id='" + id + '\'' +
                ", value='" + value + '\'' +
                ", type='" + type + '\'' +
                '}';
    }
}
