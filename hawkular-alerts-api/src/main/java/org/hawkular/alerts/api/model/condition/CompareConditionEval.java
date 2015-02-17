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

import org.hawkular.alerts.api.model.data.NumericData;

/**
 * An evaluation state for compare condition.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class CompareConditionEval extends ConditionEval {

    private CompareCondition condition;
    private Double value1;
    private Double value2;

    public CompareConditionEval() {
        super(false, 0);
        this.condition = null;
        this.value1 = null;
        this.value2 = null;
    }

    public CompareConditionEval(CompareCondition condition, NumericData data1, NumericData data2) {
        super(condition.match(data1.getValue(), data2.getValue()),
                ((data1.getTimestamp() > data1.getTimestamp()) ? data1.getTimestamp() : data2.getTimestamp()));
        this.condition = condition;
        this.value1 = data1.getValue();
        this.value2 = data2.getValue();
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

    @Override
    public String getLog() {
        return condition.getLog(value1, value2) + ", evalTimestamp=" + evalTimestamp + ", dataTimestamp="
                + dataTimestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;

        CompareConditionEval that = (CompareConditionEval) o;

        if (condition != null ? !condition.equals(that.condition) : that.condition != null)
            return false;
        if (value1 != null ? !value1.equals(that.value1) : that.value1 != null)
            return false;
        if (value2 != null ? !value2.equals(that.value2) : that.value2 != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (condition != null ? condition.hashCode() : 0);
        result = 31 * result + (value1 != null ? value1.hashCode() : 0);
        result = 31 * result + (value2 != null ? value2.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "CompareConditionEval [condition=" + condition + ", value1=" + value1 + ", value2=" + value2
                + ", toString()=" + super.toString() + "]";
    }

}
