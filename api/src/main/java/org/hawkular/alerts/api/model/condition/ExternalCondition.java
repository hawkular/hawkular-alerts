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
import org.hawkular.alerts.api.model.trigger.Mode;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * An <code>ExternalCondition</code> is used for condition evaluations performed outside of the Alerts engine.
 * The external engine will send <code>StringData</code> providing the data for which the external evaluation
 * <b>has already evaluated to <code>True</code></b>. The Alerts engine assumes a true evaluation for the data being
 * sent in from the external engine. In other words, every <code>ExternalConditionEvaluation</code> will have
 * a true evaluation and therefore, for triggers with only a single external condition, and with default dampening,
 * an alert will be fired for each data submission.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@DocModel(description = "An ExternalCondition is used for condition evaluations performed outside of the " +
        "Alerting engine. + \n" +
        " + \n" +
        "The external engine will send StringData providing the data for which the external evaluation " +
        " + \n" +
        "has already evaluated to true. + \n" +
        " + \n" +
        "The Alerting engine assumes a true evaluation for the data being sent in from the external engine. " +
        "In other words, every <<ExternalConditionEval>> will have a true evaluation and therefore, for triggers with " +
        "only a single external condition, and with default dampening, an alert will be fired for each " +
        "data submission.")
public class ExternalCondition extends Condition {

    private static final long serialVersionUID = 1L;

    /**
     * An identifier assigned by the external alerter to identify this condition as being handled by that
     * alerter. It should be unique enough such that external AlerterIds are unique.
     */
    @DocModelProperty(description = "An identifier assigned by the external alerter to identify this condition as being " +
            "handled by that. It should be unique enough such that external AlerterIds are unique.",
            position = 0,
            required = true)
    @JsonInclude(Include.NON_NULL)
    private String alerterId;

    /**
     * The ID of a StringData.  StringData is used with an ExternalCondition to provide any free-form value
     * justifying the implicit "true" ConditionEvaluation.
     */
    @JsonInclude(Include.NON_NULL)
    private String dataId;

    /**
     * The operator/pattern/expression/description of the external condition. The use of this field is up to
     * the external engine, It may be a pattern, expression or operator used to configure/drive an
     * external evaluation engine or may just be a static description of the externally defined event.
     */
    @DocModelProperty(description = "The operator/pattern/expression/description of the external condition. The use of " +
            "this field is up to the external engine, It may be a pattern, expression or operator used to " +
            "configure/drive an external evaluation engine or may just be a static description of the externally " +
            "defined event.",
            position = 1,
            required = true)
    @JsonInclude(Include.NON_NULL)
    private String expression;

    public ExternalCondition() {
        /*
            Default constructor is needed for JSON libraries in JAX-RS context.
         */
        this("", "", Mode.FIRING, 1, 1, null, null, null);
    }

    /**
     * This constructor requires the tenantId be assigned prior to persistence. It can be used when
     * creating triggers via Rest, as the tenant will be assigned automatically.
     */
    public ExternalCondition(String triggerId, Mode triggerMode, String dataId, String alerterId,
            String expression) {
        this("", triggerId, triggerMode, 1, 1, dataId, alerterId, expression);
    }

    public ExternalCondition(String tenantId, String triggerId, Mode triggerMode, String dataId, String alerterId,
            String expression) {
        this(tenantId, triggerId, triggerMode, 1, 1, dataId, alerterId, expression);
    }

    /**
     * This constructor requires the tenantId be assigned prior to persistence. It can be used when
     * creating triggers via Rest, as the tenant will be assigned automatically.
     */
    public ExternalCondition(String triggerId, Mode triggerMode, int conditionSetSize,
            int conditionSetIndex, String dataId, String alerterId, String expression) {
        this("", triggerId, triggerMode, conditionSetSize, conditionSetIndex, dataId, alerterId, expression);
    }

    public ExternalCondition(String tenantId, String triggerId, Mode triggerMode, int conditionSetSize,
            int conditionSetIndex, String dataId, String alerterId, String expression) {
        super(tenantId, triggerId, triggerMode, conditionSetSize, conditionSetIndex, Type.EXTERNAL);
        this.alerterId = alerterId;
        this.dataId = dataId;
        this.expression = expression;
        updateDisplayString();
    }

    public ExternalCondition(ExternalCondition condition) {
        super(condition);

        this.alerterId = condition.getAlerterId();
        this.dataId = condition.getDataId();
        this.expression = condition.getExpression();
    }

    public String getAlerterId() {
        return alerterId;
    }

    public void setAlerterId(String alerterId) {
        this.alerterId = alerterId;
    }

    @Override
    public String getDataId() {
        return dataId;
    }

    public void setDataId(String dataId) {
        this.dataId = dataId;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    /**
     * @param value the value, format is defined by the external engine
     * @return true in all cases because it is expected that the provided value was already determined to
     * match the external condition in the external engine's evaluation.
     */
    public boolean match(String value) {

        return true;
    }

    @Override
    public void updateDisplayString() {
        String s = String.format("%s: %s matches [%s]", this.alerterId, this.dataId, this.expression);
        setDisplayString(s);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((dataId == null) ? 0 : dataId.hashCode());
        result = prime * result + ((expression == null) ? 0 : expression.hashCode());
        result = prime * result + ((alerterId == null) ? 0 : alerterId.hashCode());
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
        ExternalCondition other = (ExternalCondition) obj;
        if (dataId == null) {
            if (other.dataId != null)
                return false;
        } else if (!dataId.equals(other.dataId))
            return false;
        if (expression == null) {
            if (other.expression != null)
                return false;
        } else if (!expression.equals(other.expression))
            return false;
        if (alerterId == null) {
            if (other.alerterId != null)
                return false;
        } else if (!alerterId.equals(other.alerterId))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "ExternalCondition [alerterId=" + alerterId + ", dataId=" + dataId + ", expression=" + expression + "]";
    }

}
