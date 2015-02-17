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

import static org.hawkular.alerts.api.model.trigger.Trigger.Mode.FIRE;

import org.hawkular.alerts.api.log.MsgLogger;
import org.hawkular.alerts.api.model.trigger.Trigger.Mode;

/**
 * A numeric comparison condition.
 * i.e. "X > 80% of Y" or "FreeSpace < 20% of TotalSpace"
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class CompareCondition extends Condition {
    private static final MsgLogger msgLog = MsgLogger.LOGGER;

    public enum Operator {
        LT, GT, LTE, GTE
    }

    private String data1Id;
    private Operator operator;
    private String data2Id;
    private Double data2Multiplier;

    public CompareCondition() {
        /*
            Default constructor is needed for JSON libraries in JAX-RS context.
         */
        this("DefaultId", 1, 1, null, null, null, null);
    }

    public CompareCondition(String triggerId, int conditionSetSize, int conditionSetIndex,
            String data1Id, Operator operator, Double data2Multiplier, String data2Id) {
        this(triggerId, FIRE, conditionSetSize, conditionSetIndex, data1Id, operator, data2Multiplier, data2Id);
    }

    public CompareCondition(String triggerId, Mode triggerMode, int conditionSetSize, int conditionSetIndex,
            String data1Id, Operator operator, Double data2Multiplier, String data2Id) {
        super(triggerId, triggerMode, conditionSetSize, conditionSetIndex);
        this.data1Id = data1Id;
        this.operator = operator;
        this.data2Id = data2Id;
        this.data2Multiplier = data2Multiplier;
    }

    public String getData1Id() {
        return data1Id;
    }

    public void setData1Id(String data1Id) {
        this.data1Id = data1Id;
    }

    public String getData2Id() {
        return data2Id;
    }

    public void setData2Id(String data2Id) {
        this.data2Id = data2Id;
    }

    public Double getData2Multiplier() {
        return data2Multiplier;
    }

    public void setData2Multiplier(Double data2Multiplier) {
        this.data2Multiplier = data2Multiplier;
    }

    public Operator getOperator() {
        return operator;
    }

    public void setOperator(Operator operator) {
        this.operator = operator;
    }

    public String getLog(double data1Value, double data2Value) {
        Double val = data2Multiplier * data2Value;
        return triggerId + " : " + data1Value + " " + operator.name() + " " +
                val + " (" + data2Multiplier + "*" + data2Value + ")";
    }

    public boolean match(double data1Value, double data2Value) {
        double threshold = (data2Multiplier * data2Value);
        switch (operator) {
            case LT:
                return data1Value < threshold;
            case GT:
                return data1Value > threshold;
            case LTE:
                return data1Value <= threshold;
            case GTE:
                return data1Value >= threshold;
            default:
                msgLog.warnUnknowOperatorOnCondition(operator.name(), this.getClass().getName());
                return false;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;

        CompareCondition that = (CompareCondition) o;

        if (data1Id != null ? !data1Id.equals(that.data1Id) : that.data1Id != null)
            return false;
        if (data2Id != null ? !data2Id.equals(that.data2Id) : that.data2Id != null)
            return false;
        if (data2Multiplier != null ? !data2Multiplier.equals(that.data2Multiplier) : that.data2Multiplier != null)
            return false;
        if (operator != that.operator)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (data1Id != null ? data1Id.hashCode() : 0);
        result = 31 * result + (operator != null ? operator.hashCode() : 0);
        result = 31 * result + (data2Id != null ? data2Id.hashCode() : 0);
        result = 31 * result + (data2Multiplier != null ? data2Multiplier.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "CompareCondition [data1Id=" + data1Id + ", operator=" + operator + ", data2Id=" + data2Id
                + ", data2Multiplier=" + data2Multiplier + ", toString()=" + super.toString() + "]";
    }

}
