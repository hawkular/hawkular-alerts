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
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class Data implements Comparable<Data> {

    @JsonInclude
    protected String id;

    @JsonInclude
    protected long timestamp;

    /** For single-value condition types. Null otherwise */
    @JsonInclude(Include.NON_EMPTY)
    protected String value;

    /** [FUTURE]
     * For multi-value condition types. Null otherwise. See the condition type for expected key-value information.
     * Note: if and when we need this we may want to get rid of the 'value' field and roll the single-value case
     * into this structure. */
    //@JsonInclude(Include.NON_EMPTY)
    //protected Map<String, String> values;

    /** Optional, non-evaluated contextual data to be kept with the datum */
    @JsonInclude(Include.NON_EMPTY)
    protected Map<String, String> context;

    public Data() {
        // json construction
    }

    /**
     * Construct a single-value datum with no context data.
     * @param id not null
     * @param timestamp in millis, if less than 1 assigned currentTime.
     * @param value the value
     */
    public Data(String id, long timestamp, String value) {
        this(id, timestamp, value, null, null);
    }

    /**
     * Construct a single-value datum with no context data.
     * @param id not null
     * @param timestamp in millis, if less than 1 assigned currentTime.
     * @param value the value
     * @param context optional, contextual name-value pairs to be stored with the data.
     */
    public Data(String id, long timestamp, String value, Map<String, String> context) {
        this(id, timestamp, value, null, null);
    }

    /**
     * Construct a multi-value datum with no context data.
     * @param id not null
     * @param timestamp in millis, if less than 1 assigned currentTime.
     * @param values the values
     */
    public Data(String id, long timestamp, Map<String, String> values) {
        this(id, timestamp, null, values, null);
    }

    /**
     * Construct a multi-value datum with no context data.
     * @param id not null
     * @param timestamp in millis, if less than 1 assigned currentTime.
     * @param values the values
     * @param context optional, contextual name-value pairs to be stored with the data.
     */
    public Data(String id, long timestamp, Map<String, String> values, Map<String, String> context) {
        this(id, timestamp, null, values, null);
    }

    /**
     * @param id not null
     * @param timestamp in millis, if less than 1 assigned currentTime.
     * @param value the value, mutually exclusive with values
     * @param context optional, contextual name-value pairs to be stored with the data.
     */
    private Data(String id, long timestamp, String value, Map<String, String> values, Map<String, String> context) {
        this.id = id;
        this.timestamp = (timestamp <= 0) ? System.currentTimeMillis() : timestamp;
        this.value = value;
        this.context = context;
    }

    public static Data forNumeric(String id, long timestamp, Double value) {
        return new Data(id, timestamp, String.valueOf(value));
    }

    public static Data forNumeric(String id, long timestamp, Double value, Map<String, String> context) {
        return new Data(id, timestamp, String.valueOf(value), null, context);
    }

    public static Data forAvailability(String id, long timestamp, AvailabilityType value) {
        return new Data(id, timestamp, value.name());
    }

    public static Data forAvailability(String id, long timestamp, AvailabilityType value, Map<String, String> context) {
        return new Data(id, timestamp, value.name(), null, context);
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

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Map<String, String> getContext() {
        return context;
    }

    public void setContext(Map<String, String> context) {
        this.context = context;
    }

    public void addProperty(String name, String value) {
        if (null == name || null == value) {
            throw new IllegalArgumentException("Property must have non-null name and value");
        }
        if (null == context) {
            context = new HashMap<>();
        }
        context.put(name, value);
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + (int) (timestamp ^ (timestamp >>> 32));
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
        return true;
    }

    /* (non-Javadoc)
     * Natural Ordering provided: Id asc, Timestamp asc. This is important to ensure that the engine
     * naturally processes datums for the same dataId is ascending time order.
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(Data o) {
        int c = this.id.compareTo(o.id);
        if (0 != c)
            return c;

        return Long.compare(this.timestamp, o.timestamp);
    }

    @Override
    public String toString() {
        return "Data [id=" + id + ", timestamp=" + timestamp + ", value=" + value + ", context=" + context + "]";
    }

}
