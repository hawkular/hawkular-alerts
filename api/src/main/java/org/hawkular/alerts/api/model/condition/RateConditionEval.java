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

import org.hawkular.alerts.api.doc.DocModel;
import org.hawkular.alerts.api.doc.DocModelProperty;
import org.hawkular.alerts.api.model.condition.Condition.Type;
import org.hawkular.alerts.api.model.data.Data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * An evaluation state for rate condition.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@DocModel(description = "An evaluation state for rate condition.")
public class RateConditionEval extends ConditionEval {

    private static final long serialVersionUID = 1L;

    @DocModelProperty(description = "Rate condition linked with this state.", position = 0)
    @JsonInclude(Include.NON_NULL)
    private RateCondition condition;

    @DocModelProperty(description = "First (older) value for dataId used in the evaluation.", position = 1)
    @JsonInclude(Include.NON_NULL)
    private Double previousValue;

    @DocModelProperty(description = "Second (newer) value for dataId used in the evaluation.", position = 2)
    @JsonInclude(Include.NON_NULL)
    private Double value;

    @DocModelProperty(description = "Time for first (older) value for dataId used in the evaluation.", position = 3)
    @JsonInclude
    private long previousTime;

    @DocModelProperty(description = "Time for second (newer) value for dataId used in the evaluation.", position = 4)
    @JsonInclude
    private long time;

    @DocModelProperty(description = "Calculated rate for this evaluation.", position = 5)
    @JsonInclude(Include.NON_NULL)
    private Double rate;

    /**
     * Used for JSON deserialization, not for general use.
     */
    public RateConditionEval() {
        super(Type.RATE, false, 0, null);
        this.value = Double.NaN;
        this.previousValue = Double.NaN;
        this.time = 0L;
        this.previousTime = 0L;
        this.rate = Double.NaN;
    }

    public RateConditionEval(RateCondition condition, Data data, Data previousData) {
        super(Type.RATE, condition.match(data.getTimestamp(), Double.valueOf(data.getValue()),
                previousData.getTimestamp(), Double.valueOf(previousData.getValue())), data.getTimestamp(),
                data.getContext());
        this.condition = condition;
        this.time = data.getTimestamp();
        this.value = Double.valueOf(data.getValue());
        this.previousTime = previousData.getTimestamp();
        this.previousValue = Double.valueOf(previousData.getValue());
        this.rate = condition.getRate(this.time, this.value, this.previousTime, this.previousValue);
    }

    public RateCondition getCondition() {
        return condition;
    }

    public void setCondition(RateCondition condition) {
        this.condition = condition;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public Double getPreviousValue() {
        return previousValue;
    }

    public void setPreviousValue(Double previousValue) {
        this.previousValue = previousValue;
    }

    public long getPreviousTime() {
        return previousTime;
    }

    public void setPreviousTime(long previousTime) {
        this.previousTime = previousTime;
    }

    public Double getRate() {
        return rate;
    }

    public void setRate(Double rate) {
        this.rate = rate;
    }

    @Override
    public String getTenantId() {
        return condition.getTenantId();
    }

    @Override
    public String getTriggerId() {
        return condition.getTriggerId();
    }

    @Override
    public int getConditionSetSize() {
        return condition.getConditionSetSize();
    }

    @Override
    public int getConditionSetIndex() {
        return condition.getConditionSetIndex();
    }

    @Override
    public void updateDisplayString() {
        String s = String.format("Rate: %s[%.2f] %s %s %s per %s", condition.getDataId(), this.rate,
                condition.getDirection().name(), condition.getOperator().name(), condition.getThreshold(),
                condition.getPeriod().name());
        setDisplayString(s);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((condition == null) ? 0 : condition.hashCode());
        result = prime * result + (int) (previousTime ^ (previousTime >>> 32));
        result = prime * result + ((previousValue == null) ? 0 : previousValue.hashCode());
        result = prime * result + (int) (time ^ (time >>> 32));
        result = prime * result + ((value == null) ? 0 : value.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        RateConditionEval other = (RateConditionEval) obj;
        if (condition == null) {
            if (other.condition != null)
                return false;
        } else if (!condition.equals(other.condition))
            return false;
        if (previousTime != other.previousTime)
            return false;
        if (previousValue == null) {
            if (other.previousValue != null)
                return false;
        } else if (!previousValue.equals(other.previousValue))
            return false;
        if (time != other.time)
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
        return "RateConditionEval [condition=" + condition + ", value=" + value + ", previousValue=" + previousValue
                + ", time=" + time + ", previousTime=" + previousTime + ", match=" + match + ", evalTimestamp="
                + evalTimestamp + "]";
    }

}
