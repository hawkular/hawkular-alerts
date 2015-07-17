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

import java.util.Map;

import org.hawkular.alerts.api.model.condition.Condition.Type;
import org.hawkular.alerts.api.model.data.NumericData;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * An evaluation state for compare condition.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class CompareConditionEval extends ConditionEval {

    @JsonInclude(Include.NON_NULL)
    private CompareCondition condition;

    @JsonInclude(Include.NON_NULL)
    private Double value1;

    @JsonInclude(Include.NON_NULL)
    private Double value2;

    @JsonInclude(Include.NON_EMPTY)
    protected Map<String, String> context2;

    public CompareConditionEval() {
        super(Type.COMPARE, false, 0, null);
        this.condition = null;
        this.value1 = null;
        this.value2 = null;
        this.context2 = null;
    }

    public CompareConditionEval(CompareCondition condition, NumericData data1, NumericData data2) {
        super(Type.COMPARE, condition.match(data1.getValue(), data2.getValue()),
                ((data1.getTimestamp() > data1.getTimestamp()) ? data1.getTimestamp() : data2.getTimestamp()),
                data1.getContext());
        this.condition = condition;
        this.value1 = data1.getValue();
        this.value2 = data2.getValue();
        this.context2 = data2.getContext();
    }

    public CompareCondition getCondition() {
        return condition;
    }

    public void setCondition(CompareCondition condition) {
        this.condition = condition;
    }

    public Double getValue1() {
        return value1;
    }

    public void setValue1(Double value1) {
        this.value1 = value1;
    }

    public Double getValue2() {
        return value2;
    }

    public void setValue2(Double value2) {
        this.value2 = value2;
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

    public Map<String, String> getContext2() {
        return context2;
    }

    public void setContext2(Map<String, String> context2) {
        this.context2 = context2;
    }

    @Override
    public String getLog() {
        return condition.getLog(value1, value2) + ", evalTimestamp=" + evalTimestamp + ", dataTimestamp="
                + dataTimestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        CompareConditionEval that = (CompareConditionEval) o;

        if (condition != null ? !condition.equals(that.condition) : that.condition != null) return false;
        if (value1 != null ? !value1.equals(that.value1) : that.value1 != null) return false;
        if (value2 != null ? !value2.equals(that.value2) : that.value2 != null) return false;
        return !(context2 != null ? !context2.equals(that.context2) : that.context2 != null);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (condition != null ? condition.hashCode() : 0);
        result = 31 * result + (value1 != null ? value1.hashCode() : 0);
        result = 31 * result + (value2 != null ? value2.hashCode() : 0);
        result = 31 * result + (context2 != null ? context2.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "CompareConditionEval [evalTimestamp=" + evalTimestamp + ", " +
                "dataTimestamp=" + dataTimestamp + ", " +
                "condition=" + condition + ", " +
                "value1=" + value1 + ", " +
                "value2=" + value2 + "]";
    }

}
