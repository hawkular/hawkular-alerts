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
import org.hawkular.alerts.api.model.data.Availability.AvailabilityType;
import org.hawkular.alerts.api.model.trigger.Trigger.Mode;

/**
 * An availability condition definition.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class AvailabilityCondition extends Condition {
    private static final MsgLogger msgLog = MsgLogger.LOGGER;

    public enum Operator {
        DOWN, NOT_UP, UP
    }

    private String dataId;
    private Operator operator;

    public AvailabilityCondition() {
        /*
            Default constructor is needed for JSON libraries in JAX-RS context.
         */
        this("DefaultId", 1, 1, null, null);
    }

    public AvailabilityCondition(String triggerId, int conditionSetSize, int conditionSetIndex,
            String dataId, Operator operator) {
        this(triggerId, FIRE, conditionSetSize, conditionSetIndex, dataId, operator);
    }

    public AvailabilityCondition(String triggerId, Mode triggerMode, int conditionSetSize, int conditionSetIndex,
            String dataId, Operator operator) {
        super(triggerId, triggerMode, conditionSetSize, conditionSetIndex);
        this.dataId = dataId;
        this.operator = operator;
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

    public String getLog(AvailabilityType value) {
        return triggerId + " : " + value + " " + operator.name();
    }

    public boolean match(AvailabilityType value) {
        switch (operator) {
            case DOWN:
                return value == AvailabilityType.DOWN;
            case UP:
                return value == AvailabilityType.UP;
            case NOT_UP:
                return value != AvailabilityType.UP;
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

        AvailabilityCondition that = (AvailabilityCondition) o;

        if (dataId != null ? !dataId.equals(that.dataId) : that.dataId != null)
            return false;
        if (operator != that.operator)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (dataId != null ? dataId.hashCode() : 0);
        result = 31 * result + (operator != null ? operator.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "AvailabilityCondition [dataId=" + dataId + ", operator=" + operator + ", toString()="
                + super.toString() + "]";
    }

}
