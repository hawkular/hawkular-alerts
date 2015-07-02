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
package org.hawkular.alerts.api.model.condition;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import org.hawkular.alerts.api.model.data.NumericData;

/**
 * An evaluation state for threshold condition.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class ThresholdConditionEval extends ConditionEval {

    @JsonInclude(Include.NON_NULL)
    private ThresholdCondition condition;

    @JsonInclude(Include.NON_NULL)
    private Double value;

    public ThresholdConditionEval() {
        super();
    }

    public ThresholdConditionEval(ThresholdCondition condition, NumericData data) {
        super(condition.match(data.getValue()), data.getTimestamp());
        this.condition = condition;
        this.value = data.getValue();
        if (this.condition != null) {
            this.type = this.condition.getType();
        }
    }

    public ThresholdCondition getCondition() {
        return condition;
    }

    public void setCondition(ThresholdCondition condition) {
        this.condition = condition;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
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
    public String getLog() {
        return condition.getLog(value) + ", evalTimestamp=" + evalTimestamp + ", dataTimestamp=" + dataTimestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ThresholdConditionEval that = (ThresholdConditionEval) o;

        if (condition != null ? !condition.equals(that.condition) : that.condition != null) return false;
        if (value != null ? !value.equals(that.value) : that.value != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (condition != null ? condition.hashCode() : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ThresholdConditionEval [evalTimestamp=" + evalTimestamp + ", " +
                "dataTimestamp=" + dataTimestamp + ", " +
                "condition=" + condition + ", " +
                "value=" + value + "]";
    }

}
