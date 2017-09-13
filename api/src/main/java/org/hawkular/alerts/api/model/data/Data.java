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
package org.hawkular.alerts.api.model.data;

import static org.hawkular.alerts.api.util.Util.isEmpty;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.hawkular.alerts.api.doc.DocModel;
import org.hawkular.alerts.api.doc.DocModelProperty;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * A base class for incoming data into alerts subsystem.  All {@link Data} has TenantId, Id and a timestamp. An Id
 * should be unique within the tenant. The timestamp is used to ensure that data is time-ordered when being sent into
 * the alerting engine.  If not assigned the timestamp will be assigned to current time.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@DocModel(description = "A base class for incoming data into alerts subsystem. + \n" +
        "All Data has TenantId, Id and a timestamp. + \n" +
        "An Id should be unique within the tenant. + \n" +
        "The timestamp is used to ensure that data is time-ordered when being sent into the alerting engine. + \n" +
        "If not assigned the timestamp will be assigned to current time.")
public class Data implements Comparable<Data>, Serializable {

    private static final long serialVersionUID = 1L;

    public static final String SOURCE_NONE = "_none_";

    @DocModelProperty(description = "Tenant id owner of this data.",
            position = 0,
            allowableValues = "Tenant is overwritten from Hawkular-Tenant HTTP header parameter request")
    @JsonInclude
    protected String tenantId;

    @DocModelProperty(description = "Extended mechanism to match trigger conditions against Data with [source, dataId] " +
            "identifiers. In this way it is possible to qualify triggers and data with a source such that a trigger " +
            "only evaluates data having the same source.",
            position = 1)
    @JsonInclude
    protected String source;

    @DocModelProperty(description = "Data id unique within the tenant.",
            position = 2,
            required = true)
    @JsonInclude
    protected String id;

    @DocModelProperty(description = "Timestamp for the data.",
            position = 3,
            defaultValue = "If not assigned, timestamp will be assigned to current time.")
    @JsonInclude
    protected long timestamp;

    @DocModelProperty(description = "Value for single-value condition types.",
            position = 4)
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
    @DocModelProperty(description = "Properties defined by the user for this data. Context is propagated " +
            "on generated Events/Alerts.",
            position = 5)
    @JsonInclude(Include.NON_EMPTY)
    protected Map<String, String> context;

    /** For JSON Construction ONLY */
    public Data() {
        this(null, 0L, null);
    }

    /** For REST API USE ONLY, tenantId set automatically via REST Handler.
     */
    public Data(String id, long timestamp, String value) {
        this(null, null, id, timestamp, value, null, null);
    }

    /** For REST API USE ONLY, tenantId set automatically via REST Handler.
     */
    public Data(String source, String id, long timestamp, String value) {
        this(null, source, id, timestamp, value, null, null);
    }

    /**
     * Construct a single-value datum with no context data.
     * @param tenantId not null
     * @param id not null, unique within tenant
     * @param timestamp in millis, if less than 1 assigned currentTime.
     * @param value the value
     */
    public Data(String tenantId, String source, String id, long timestamp, String value) {
        this(tenantId, source, id, timestamp, value, null, null);
    }

    /**
     * Construct a single-value datum with context data.
     * @param tenantId not null
     * @param id not null, unique within tenant
     * @param timestamp in millis, if less than 1 assigned currentTime.
     * @param value the value
     * @param context optional, contextual name-value pairs to be stored with the data.
     */
    public Data(String tenantId, String source, String id, long timestamp, String value, Map<String, String> context) {
        this(tenantId, source, id, timestamp, value, null, null);
    }

    /**
     * Construct a multi-value datum with no context data.
     * @param tenantId not null
     * @param id not null, unique within tenant
     * @param timestamp in millis, if less than 1 assigned currentTime.
     * @param values the values
     */
    public Data(String tenantId, String source, String id, long timestamp, Map<String, String> values) {
        this(tenantId, source, id, timestamp, null, values, null);
    }

    /**
     * Construct a multi-value datum with context data.
     * @param tenantId not null
     * @param id not null
     * @param timestamp in millis, if less than 1 assigned currentTime.
     * @param values the values
     * @param context optional, contextual name-value pairs to be stored with the data.
     */
    public Data(String tenantId, String source, String id, long timestamp, Map<String, String> values,
            Map<String, String> context) {
        this(tenantId, source, id, timestamp, null, values, null);
    }

    /**
     * @param tenantId not null
     * @param id not null
     * @param timestamp in millis, if less than 1 assigned currentTime.
     * @param value the value, mutually exclusive with values
     * @param context optional, contextual name-value pairs to be stored with the data.
     */
    private Data(String tenantId, String source, String id, long timestamp, String value, Map<String, String> values,
            Map<String, String> context) {
        this.tenantId = tenantId;
        this.source = isEmpty(source) ? SOURCE_NONE : source;
        this.id = id;
        this.timestamp = (timestamp <= 0) ? System.currentTimeMillis() : timestamp;
        this.value = value;
        this.context = context;
    }

    public static Data forNumeric(String tenantId, String id, long timestamp, Double value) {
        return new Data(tenantId, null, id, timestamp, String.valueOf(value));
    }

    public static Data forNumeric(String tenantId, String source, String id, long timestamp, Double value) {
        return new Data(tenantId, source, id, timestamp, String.valueOf(value));
    }

    public static Data forNumeric(String tenantId, String id, long timestamp, Double value,
            Map<String, String> context) {
        return new Data(tenantId, null, id, timestamp, String.valueOf(value), null, context);
    }

    public static Data forNumeric(String tenantId, String source, String id, long timestamp, Double value,
            Map<String, String> context) {
        return new Data(tenantId, source, id, timestamp, String.valueOf(value), null, context);
    }

    public static Data forString(String tenantId, String id, long timestamp, String value) {
        return new Data(tenantId, null, id, timestamp, value);
    }

    public static Data forString(String tenantId, String source, String id, long timestamp, String value) {
        return new Data(tenantId, source, id, timestamp, value);
    }

    public static Data forString(String tenantId, String id, long timestamp, String value,
            Map<String, String> context) {
        return new Data(tenantId, null, id, timestamp, value, null, context);
    }

    public static Data forString(String tenantId, String source, String id, long timestamp, String value,
            Map<String, String> context) {
        return new Data(tenantId, source, id, timestamp, value, null, context);
    }

    public static Data forAvailability(String tenantId, String id, long timestamp, AvailabilityType value) {
        return new Data(tenantId, null, id, timestamp, value.name());
    }

    public static Data forAvailability(String tenantId, String source, String id, long timestamp,
            AvailabilityType value) {
        return new Data(tenantId, source, id, timestamp, value.name());
    }

    public static Data forAvailability(String tenantId, String id, long timestamp,
            AvailabilityType value, Map<String, String> context) {
        return new Data(tenantId, null, id, timestamp, value.name(), null, context);
    }

    public static Data forAvailability(String tenantId, String source, String id, long timestamp,
            AvailabilityType value, Map<String, String> context) {
        return new Data(tenantId, source, id, timestamp, value.name(), null, context);
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
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
        result = prime * result + ((source == null) ? 0 : source.hashCode());
        result = prime * result + ((tenantId == null) ? 0 : tenantId.hashCode());
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
        if (source == null) {
            if (other.source != null)
                return false;
        } else if (!source.equals(other.source))
            return false;
        if (tenantId == null) {
            if (other.tenantId != null)
                return false;
        } else if (!tenantId.equals(other.tenantId))
            return false;
        if (timestamp != other.timestamp)
            return false;
        return true;
    }

    /* (non-Javadoc)
     * Natural Ordering provided: Id asc, TenantId asc, Source asc, Timestamp asc. This is important to ensure
     * that the engine naturally processes datums for the same dataId is ascending time order.
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(Data o) {
        int c = this.id.compareTo(o.id);
        if (0 != c)
            return c;

        c = this.tenantId.compareTo(o.tenantId);
        if (0 != c)
            return c;

        c = this.source.compareTo(o.source);
        if (0 != c)
            return c;

        return Long.compare(this.timestamp, o.timestamp);
    }

    /**
     * @return true if the two Data objects represent the same data (just different values and/or times)
     */
    public boolean same(Object obj) {
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
        if (tenantId == null) {
            if (other.tenantId != null)
                return false;
        } else if (!tenantId.equals(other.tenantId))
            return false;
        if (source == null) {
            if (other.source != null)
                return false;
        } else if (!source.equals(other.source))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "Data [tenantId=" + tenantId + ", id=" + id + ", timestamp=" + timestamp + ", value=" + value
                + ", context=" + context + "]";
    }

}
