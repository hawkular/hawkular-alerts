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

import static org.hawkular.alerts.api.model.trigger.Mode.FIRING;

import org.hawkular.alerts.api.model.data.AvailabilityType;
import org.hawkular.alerts.api.model.trigger.Mode;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * An availability condition definition.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@ApiModel(description = "An availability condition definition. + \n" +
        " + \n" +
        "Examples: + \n" +
        "X is DOWN")
public class AvailabilityCondition extends Condition {

    private static final long serialVersionUID = 1L;

    public enum Operator {
        DOWN, NOT_UP, UP
    }

    @JsonInclude(Include.NON_NULL)
    private String dataId;

    @ApiModelProperty(value = "Availability operator.",
            position = 0,
            required = true)
    @JsonInclude(Include.NON_NULL)
    private Operator operator;

    public AvailabilityCondition() {
        /*
            Default constructor is needed for JSON libraries in JAX-RS context.
         */
        this("", "", 1, 1, null, null);
    }

    public AvailabilityCondition(String tenantId, String triggerId, String dataId, Operator operator) {
        this(tenantId, triggerId, FIRING, 1, 1, dataId, operator);
    }

    /**
     * This constructor requires the tenantId be assigned prior to persistence. It can be used when
     * creating triggers via Rest, as the tenant will be assigned automatically.
     */
    public AvailabilityCondition(String triggerId, Mode triggerMode, String dataId, Operator operator) {
        this("", triggerId, triggerMode, 1, 1, dataId, operator);
    }

    public AvailabilityCondition(String tenantId, String triggerId, Mode triggerMode, String dataId,
            Operator operator) {
        this(tenantId, triggerId, triggerMode, 1, 1, dataId, operator);
    }

    public AvailabilityCondition(String tenantId, String triggerId, int conditionSetSize, int conditionSetIndex,
            String dataId, Operator operator) {
        this(tenantId, triggerId, FIRING, conditionSetSize, conditionSetIndex, dataId, operator);
    }

    /**
     * This constructor requires the tenantId be assigned prior to persistence. It can be used when
     * creating triggers via Rest, as the tenant will be assigned automatically.
     */
    public AvailabilityCondition(String triggerId, Mode triggerMode, int conditionSetSize, int conditionSetIndex,
            String dataId, Operator operator) {
        this("", triggerId, triggerMode, conditionSetSize, conditionSetIndex, dataId, operator);
    }

    public AvailabilityCondition(String tenantId, String triggerId, Mode triggerMode, int conditionSetSize, int
            conditionSetIndex, String dataId, Operator operator) {
        super(tenantId, triggerId, triggerMode, conditionSetSize, conditionSetIndex, Type.AVAILABILITY);
        this.dataId = dataId;
        this.operator = operator;
        updateDisplayString();
    }

    public AvailabilityCondition(AvailabilityCondition condition) {
        super(condition);

        this.dataId = condition.getDataId();
        this.operator = condition.getOperator();
    }

    @Override
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

    public boolean match(AvailabilityType value) {
        switch (operator) {
            case DOWN:
                return value == AvailabilityType.DOWN;
            case UP:
                return value == AvailabilityType.UP;
            case NOT_UP:
                return value != AvailabilityType.UP;
            default:
                throw new IllegalStateException("Unknown operator: " + operator.name());
        }
    }

    @Override
    public void updateDisplayString() {
        String operator = null == this.operator ? null : this.getOperator().name();
        String s = String.format("%s is %s", this.dataId, operator);
        setDisplayString(s);
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
        return "AvailabilityCondition [tenantId='" + tenantId + "', triggerId='" + triggerId + "', " +
                "triggerMode=" + triggerMode + ", " +
                "dataId=" + (dataId == null ? null : '\'' + dataId + '\'') + ", " +
                "operator=" + (operator == null ? null : '\'' + operator.toString() + '\'') + "]";
    }

}
