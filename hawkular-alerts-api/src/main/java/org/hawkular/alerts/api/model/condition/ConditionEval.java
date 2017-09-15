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
package org.hawkular.alerts.api.model.condition;

import java.io.Serializable;
import java.util.Map;

import org.hawkular.alerts.api.json.JacksonDeserializer;
import org.hawkular.alerts.api.model.condition.Condition.Type;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * An evaluation state of a specific condition.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@ApiModel(description = "A base class to represent an evaluation state of a specific condition.",
        subTypes = { AvailabilityConditionEval.class, CompareConditionEval.class, EventConditionEval.class,
            ExternalConditionEval.class, MissingConditionEval.class, RateConditionEval.class, StringConditionEval.class,
            ThresholdConditionEval.class, ThresholdRangeConditionEval.class })
@JsonDeserialize(using = JacksonDeserializer.ConditionEvalDeserializer.class)
public abstract class ConditionEval implements Serializable {

    private static final long serialVersionUID = 1L;

    // result of the condition evaluation
    @ApiModelProperty(value = "Result of the condition evaluation.",
            position = 0)
    @JsonIgnore
    protected boolean match;

    // time of condition evaluation (i.e. creation time)
    @ApiModelProperty(value = "Time of condition evaluation.",
            position = 1)
    @JsonInclude
    protected long evalTimestamp;

    // time stamped on the data used in the eval
    @ApiModelProperty(value = "Time stamped on the data used in the evaluation.",
            position = 2)
    @JsonInclude
    protected long dataTimestamp;

    @ApiModelProperty(value = "The type of the condition eval defined. Each type has its specific properties defined " +
            "on its subtype of condition eval.",
            position = 3)
    @JsonInclude
    protected Condition.Type type;

    @ApiModelProperty(value = "Properties defined by the user at Data level on the dataId used for this evaluation.",
            position = 4)
    @JsonInclude(Include.NON_EMPTY)
    protected Map<String, String> context;

    @ApiModelProperty(value = "A canonical display string of the evaluation (the result of a call to #getLog()).",
            position = 5)
    @JsonInclude(Include.NON_EMPTY)
    protected String displayString;

    public ConditionEval() {
        // for json assembly
    }

    public ConditionEval(Type type, boolean match, long dataTimestamp, Map<String, String> context) {
        this.type = type;
        this.match = match;
        this.dataTimestamp = dataTimestamp;
        this.evalTimestamp = System.currentTimeMillis();
        this.context = context;
        this.displayString = null; // for construction speed, lazily update when requested or when serialized
    }

    public boolean isMatch() {
        return match;
    }

    public void setMatch(boolean match) {
        this.match = match;
    }

    public long getEvalTimestamp() {
        return evalTimestamp;
    }

    public void setEvalTimestamp(long evalTimestamp) {
        this.evalTimestamp = evalTimestamp;
    }

    public long getDataTimestamp() {
        return dataTimestamp;
    }

    public void setDataTimestamp(long dataTimestamp) {
        this.dataTimestamp = dataTimestamp;
    }

    public Condition.Type getType() {
        return type;
    }

    public void setType(Condition.Type type) {
        this.type = type;
    }

    public Map<String, String> getContext() {
        return context;
    }

    public void setContext(Map<String, String> context) {
        this.context = context;
    }

    public String getDisplayString() {
        if (null == this.displayString) {
            updateDisplayString();
        }
        return this.displayString;
    }

    public void setDisplayString(String displayString) {
        this.displayString = displayString;
    }

    @JsonIgnore
    public abstract String getTenantId();

    @JsonIgnore
    public abstract String getTriggerId();

    @JsonIgnore
    public abstract int getConditionSetSize();

    @JsonIgnore
    public abstract int getConditionSetIndex();

    /**
     * @return The condition expression with the values used to determine the match. Note that this
     * String does not include whether the match is true or false.  That can be determined via {@link #isMatch()}.
     */
    @JsonIgnore
    public abstract void updateDisplayString();

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        ConditionEval that = (ConditionEval) o;

        if (evalTimestamp != that.evalTimestamp)
            return false;
        if (dataTimestamp != that.dataTimestamp)
            return false;
        if (type != that.type)
            return false;
        return !(context != null ? !context.equals(that.context) : that.context != null);

    }

    @Override
    public int hashCode() {
        int result = (int) (evalTimestamp ^ (evalTimestamp >>> 32));
        result = 31 * result + (int) (dataTimestamp ^ (dataTimestamp >>> 32));
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (context != null ? context.hashCode() : 0);
        return result;
    }
}
