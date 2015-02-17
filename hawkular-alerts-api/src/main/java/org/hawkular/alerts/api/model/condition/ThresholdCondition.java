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

import org.hawkular.alerts.api.log.MsgLogger;
import org.hawkular.alerts.api.model.trigger.Trigger.Mode;

/**
 * A numeric threshold condition.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class ThresholdCondition extends Condition {
    private static final MsgLogger msgLog = MsgLogger.LOGGER;

    public enum Operator {
        LT, GT, LTE, GTE
    }

    private String dataId;
    private Operator operator;
    private Double threshold;

    public ThresholdCondition() {
        /*
            Default constructor is needed for JSON libraries in JAX-RS context.
         */
        this("DefaultId", 1, 1, null, null, null);
    }

    public ThresholdCondition(String triggerId, int conditionSetSize, int conditionSetIndex,
            String dataId, Operator operator, Double threshold) {

        this(triggerId, Mode.FIRE, conditionSetSize, conditionSetIndex, dataId, operator, threshold);
    }

    public ThresholdCondition(String triggerId, Mode triggerMode, int conditionSetSize, int conditionSetIndex,
            String dataId, Operator operator, Double threshold) {
        super(triggerId, triggerMode, conditionSetSize, conditionSetIndex);
        this.dataId = dataId;
        this.operator = operator;
        this.threshold = threshold;
    }

    public String getDataId() {
        return dataId;
    }

    public void setDataId(String dataId) {
        this.dataId = dataId;
    }

    public Operator getOperator() {
        return operator;
    }

    public void setOperator(Operator operator) {
        this.operator = operator;
    }

    public Double getThreshold() {
        return threshold;
    }

    public void setThreshold(Double threshold) {
        this.threshold = threshold;
    }

    public String getLog(double value) {
        return triggerId + " : " + value + " " + operator.name() + " " + threshold;
    }

    public boolean match(double value) {
        switch (operator) {
            case LT:
                return value < threshold;
            case GT:
                return value > threshold;
            case LTE:
                return value <= threshold;
            case GTE:
                return value >= threshold;
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

        ThresholdCondition that = (ThresholdCondition) o;

        if (dataId != null ? !dataId.equals(that.dataId) : that.dataId != null)
            return false;
        if (operator != that.operator)
            return false;
        if (threshold != null ? !threshold.equals(that.threshold) : that.threshold != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (dataId != null ? dataId.hashCode() : 0);
        result = 31 * result + (operator != null ? operator.hashCode() : 0);
        result = 31 * result + (threshold != null ? threshold.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ThresholdCondition [dataId=" + dataId + ", operator=" + operator + ", threshold=" + threshold
                + ", toString()=" + super.toString() + "]";
    }

}
