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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * A base class for incoming data into alerts subsystem.  All {@link Data} has an Id and a timestamp. The
 * timestamp is used to ensure that data is time-ordered when being sent into the alerting engine.  If
 * not assigned the timestamp will be assigned to current time.
 * <p>
 * This provides a  default implementation of {@link #compareTo(Data)}.  Subclasses must Override this if
 * they are unhappy with the Natural Ordering provided: Id asc, Timestamp asc, Value asc
 * </p>
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public abstract class Data implements Comparable<Data> {

    public enum Type {
        AVAILABILITY, NUMERIC, STRING
    };

    @JsonInclude
    protected String id;

    @JsonInclude
    protected long timestamp;

    @JsonInclude(Include.NON_NULL)
    protected Object value;

    @JsonInclude
    protected Type type;

    public Data() {
        /*
            Default constructor is needed for JSON libraries in JAX-RS context.
         */
        this.id = null;
    }

    /**
     * @param id not null
     * @param timestamp in millis, if less than 1 assigned currentTime.
     * @param value the value
     * @param type the type of data
     */
    public Data(String id, long timestamp, Object value, Type type) {
        this.id = id;
        this.timestamp = (timestamp <= 0) ? System.currentTimeMillis() : timestamp;
        this.value = value;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * @param timestamp in millis, if less than 1 assigned currentTime.
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = (timestamp <= 0) ? System.currentTimeMillis() : timestamp;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + (int) (timestamp ^ (timestamp >>> 32));
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
        Data other = (Data) obj;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        if (timestamp != other.timestamp)
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
        return "Data [id=" + id + ", timestamp=" + timestamp + ", value=" + value + "]";
    }

    @Override
    public int compareTo(Data o) {
        int c = this.id.compareTo(o.id);
        if (0 != c)
            return c;

        c = Long.compare(this.timestamp, o.timestamp);
        if (0 != c)
            return c;

        return compareValue(this.value, o.value);
    }

    /**
     * Subclasses must provide the natural comparison of their value type. Or, override {@link #compare(Data, Data)}
     * completely.
     * @param value1 the value1
     * @param value2 the value2
     * @return standard -1, 0, 1 compare value
     */
    abstract int compareValue(Object value1, Object value2);

}
