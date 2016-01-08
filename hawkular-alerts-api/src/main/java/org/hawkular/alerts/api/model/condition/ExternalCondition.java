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
public class ExternalCondition extends Condition {

    private static final long serialVersionUID = 1L;

    /**
     * An identifier assigned by the external system to identify this condition as being handled by that
     * external system. It should be unique enough such that externalSystemIds are unique.
     */
    @JsonInclude(Include.NON_NULL)
    private String systemId;

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
    public ExternalCondition(String triggerId, Mode triggerMode, String dataId, String systemId,
            String expression) {
        this("", triggerId, triggerMode, 1, 1, dataId, systemId, expression);
    }

    public ExternalCondition(String tenantId, String triggerId, Mode triggerMode, String dataId, String systemId,
            String expression) {
        this(tenantId, triggerId, triggerMode, 1, 1, dataId, systemId, expression);
    }

    /**
     * This constructor requires the tenantId be assigned prior to persistence. It can be used when
     * creating triggers via Rest, as the tenant will be assigned automatically.
     */
    public ExternalCondition(String triggerId, Mode triggerMode, int conditionSetSize,
            int conditionSetIndex, String dataId, String systemId, String expression) {
        this("", triggerId, triggerMode, conditionSetSize, conditionSetIndex, dataId, systemId, expression);
    }

    public ExternalCondition(String tenantId, String triggerId, Mode triggerMode, int conditionSetSize,
            int conditionSetIndex, String dataId, String systemId, String expression) {
        super(tenantId, triggerId, triggerMode, conditionSetSize, conditionSetIndex, Type.EXTERNAL);
        this.systemId = systemId;
        this.dataId = dataId;
        this.expression = expression;
    }

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

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

    public String getLog(String value) {
        return triggerId + " : " + value + " " + expression;
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
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((dataId == null) ? 0 : dataId.hashCode());
        result = prime * result + ((expression == null) ? 0 : expression.hashCode());
        result = prime * result + ((systemId == null) ? 0 : systemId.hashCode());
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
        if (systemId == null) {
            if (other.systemId != null)
                return false;
        } else if (!systemId.equals(other.systemId))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "ExternalCondition [systemId=" + systemId + ", dataId=" + dataId + ", expression=" + expression + "]";
    }

}
