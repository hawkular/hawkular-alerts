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

import org.hawkular.alerts.api.model.trigger.Mode;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * A numeric comparison condition. Examples:
 * <code>"X GT 80% of Y"</code>,  <code>"FreeSpace LT 20% of TotalSpace"</code>
 * Note that when constructing a <code>CompareCondition</code>, or calling {@link #setData2Multiplier(Double)}
 * that <code>data2Multiplier</code> is a straight multiplier. So, for <code>"X GT 80% of Y"</code> you would
 * set <code>data2Multiplier=0.80</code>.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@ApiModel(description = "A numeric comparison condition. + \n" +
        " + \n" +
        "Examples: + \n" +
        "X > 80% of Y, FreeSpace < 20% of TotalSpace + \n")
public class CompareCondition extends Condition {

    private static final long serialVersionUID = 1L;

    public enum Operator {
        LT, GT, LTE, GTE
    }

    @JsonInclude(Include.NON_NULL)
    private String dataId;

    @ApiModelProperty(value = "Compare operator [LT (<), GT (>), LTE (<=), GTE (>=)].",
            position = 0,
            required = true)
    @JsonInclude(Include.NON_NULL)
    private Operator operator;

    @ApiModelProperty(value = "Data identifier of the metric used for comparison.",
            position = 1,
            required = true)
    @JsonInclude(Include.NON_NULL)
    private String data2Id;

    @ApiModelProperty(value = "Straight multiplier to be applied to data2Id on the comparison. " +
            "Final comparison expression can be read as \"dataId <operator> data2Multiplier*data2Id\".",
            position = 2,
            required = true)
    @JsonInclude(Include.NON_NULL)
    private Double data2Multiplier;

    public CompareCondition() {
        /*
            Default constructor is needed for JSON libraries in JAX-RS context.
         */
        this("", "", 1, 1, null, null, null, null);
    }

    public CompareCondition(String tenantId, String triggerId,
            String dataId, Operator operator, Double data2Multiplier, String data2Id) {
        this(tenantId, triggerId, FIRING, 1, 1, dataId, operator, data2Multiplier, data2Id);
    }

    /**
     * This constructor requires the tenantId be assigned prior to persistence. It can be used when
     * creating triggers via Rest, as the tenant will be assigned automatically.
     */
    public CompareCondition(String triggerId, Mode triggerMode,
            String dataId, Operator operator, Double data2Multiplier, String data2Id) {
        this("", triggerId, triggerMode, 1, 1, dataId, operator, data2Multiplier, data2Id);
    }

    public CompareCondition(String tenantId, String triggerId, Mode triggerMode,
            String dataId, Operator operator, Double data2Multiplier, String data2Id) {
        this(tenantId, triggerId, triggerMode, 1, 1, dataId, operator, data2Multiplier, data2Id);
    }

    public CompareCondition(String tenantId, String triggerId, int conditionSetSize, int conditionSetIndex,
            String dataId, Operator operator, Double data2Multiplier, String data2Id) {
        this(tenantId, triggerId, FIRING, conditionSetSize, conditionSetIndex, dataId, operator, data2Multiplier,
                data2Id);
    }

    /**
     * This constructor requires the tenantId be assigned prior to persistence. It can be used when
     * creating triggers via Rest, as the tenant will be assigned automatically.
     */
    public CompareCondition(String triggerId, Mode triggerMode, int conditionSetSize,
            int conditionSetIndex, String dataId, Operator operator, Double data2Multiplier, String data2Id) {
        this("", triggerId, triggerMode, conditionSetSize, conditionSetIndex, dataId, operator, data2Multiplier,
                data2Id);
    }

    public CompareCondition(String tenantId, String triggerId, Mode triggerMode, int conditionSetSize,
            int conditionSetIndex, String dataId, Operator operator, Double data2Multiplier, String data2Id) {
        super(tenantId, triggerId, triggerMode, conditionSetSize, conditionSetIndex, Type.COMPARE);
        this.dataId = dataId;
        this.operator = operator;
        this.data2Id = data2Id;
        this.data2Multiplier = data2Multiplier;
        updateDisplayString();
    }

    public CompareCondition(CompareCondition condition) {
        super(condition);

        this.dataId = condition.getDataId();
        this.data2Id = condition.getData2Id();
        this.data2Multiplier = condition.getData2Multiplier();
        this.operator = condition.getOperator();
    }

    @Override
    public String getDataId() {
        return dataId;
    }

    public void setDataId(String dataId) {
        this.dataId = dataId;
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

    public boolean match(double dataValue, double data2Value) {
        double threshold = (data2Multiplier * data2Value);
        switch (operator) {
            case LT:
                return dataValue < threshold;
            case GT:
                return dataValue > threshold;
            case LTE:
                return dataValue <= threshold;
            case GTE:
                return dataValue >= threshold;
            default:
                throw new IllegalStateException("Unknown operator: " + operator.name());
        }
    }

    @Override
    public void updateDisplayString() {
        String operator = null == this.operator ? null : this.operator.name();
        Double data2Multiplier = (null == this.data2Multiplier) ? 0.0 : this.data2Multiplier;
        String s = String.format("%s %s %.2f%% %s", this.dataId, operator, (100 * data2Multiplier), this.data2Id);
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

        CompareCondition that = (CompareCondition) o;

        if (dataId != null ? !dataId.equals(that.dataId) : that.dataId != null)
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
        result = 31 * result + (dataId != null ? dataId.hashCode() : 0);
        result = 31 * result + (operator != null ? operator.hashCode() : 0);
        result = 31 * result + (data2Id != null ? data2Id.hashCode() : 0);
        result = 31 * result + (data2Multiplier != null ? data2Multiplier.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "CompareCondition [triggerId='" + triggerId + "', " +
                "triggerMode=" + triggerMode + ", " +
                "dataId=" + (dataId == null ? null : '\'' + dataId + '\'') + ", " +
                "operator=" + (operator == null ? null : '\'' + operator.toString() + '\'') + ", " +
                "data2Id=" + (data2Id == null ? null : '\'' + data2Id + '\'') + ", " +
                "data2Multiplier=" + data2Multiplier + "]";
    }

}
