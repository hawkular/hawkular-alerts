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

import java.util.HashMap;
import java.util.Map;

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

    @JsonInclude(Include.NON_EMPTY)
    protected Map<String, String> context;

    // Default constructor is needed for JSON libraries in JAX-RS context.
    public Data() {
        this.id = null;
    }

    /**
     * @param id not null
     * @param timestamp in millis, if less than 1 assigned currentTime.
     * @param value the value
     * @param type the type of data
     */
    public Data(String id, long timestamp, Object value, Type type) {
        this(id, timestamp, value, type, null);
    }

    /**
     * @param id not null
     * @param timestamp in millis, if less than 1 assigned currentTime.
     * @param value the value
     * @param type the type of data
     * @param context optional, contextual name-value pairs to be stored with the data.
     */
    public Data(String id, long timestamp, Object value, Type type, Map<String, String> context) {
        this.id = id;
        this.timestamp = (timestamp <= 0) ? System.currentTimeMillis() : timestamp;
        this.value = value;
        this.type = type;
        this.context = context;
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

    public Map<String, String> getContext() {
        return context;
    }

    public void setContext(Map<String, String> context) {
        this.context = context;
    }

    public void addProperty(String name, String value) {
        if (null == name || null == value) {
            throw new IllegalArgumentException("Propety must have non-null name and value");
        }
        if (null == context) {
            context = new HashMap<>();
        }
        context.put(name, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Data data = (Data) o;

        if (timestamp != data.timestamp) return false;
        if (id != null ? !id.equals(data.id) : data.id != null) return false;
        if (value != null ? !value.equals(data.value) : data.value != null) return false;
        if (type != data.type) return false;
        return !(context != null ? !context.equals(data.context) : data.context != null);

    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (int) (timestamp ^ (timestamp >>> 32));
        result = 31 * result + (value != null ? value.hashCode() : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (context != null ? context.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Data [id=" + id + ", timestamp=" + timestamp + ", value=" + value + ", context=" + context + "]";
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
