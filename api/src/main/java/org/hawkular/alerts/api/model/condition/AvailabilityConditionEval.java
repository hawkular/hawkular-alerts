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
import org.hawkular.alerts.api.model.condition.Condition.Type;
import org.hawkular.alerts.api.model.data.AvailabilityType;
import org.hawkular.alerts.api.model.data.Data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * An evaluation state for availability condition.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@DocModel(description = "An evaluation state for availability condition.")
public class AvailabilityConditionEval extends ConditionEval {

    private static final long serialVersionUID = 1L;

    @DocModelProperty(description = "Availability condition linked with this state.",
            position = 0)
    @JsonInclude(Include.NON_NULL)
    private AvailabilityCondition condition;

    @DocModelProperty(description = "Availability value used for dataId.",
            position = 1)
    @JsonInclude(Include.NON_NULL)
    private AvailabilityType value;

    public AvailabilityConditionEval() {
        super(Type.AVAILABILITY, false, 0, null);
        this.condition = null;
        this.value = null;
    }

    public AvailabilityConditionEval(AvailabilityCondition condition, Data avail) {
        super(Type.AVAILABILITY, condition.match(AvailabilityType.valueOf(avail.getValue())), avail.getTimestamp(),
                avail.getContext());
        this.condition = condition;
        this.value = AvailabilityType.valueOf(avail.getValue());
    }

    public AvailabilityCondition getCondition() {
        return condition;
    }

    public void setCondition(AvailabilityCondition condition) {
        this.condition = condition;
    }

    public AvailabilityType getValue() {
        return value;
    }

    public void setValue(AvailabilityType value) {
        this.value = value;
    }

    @Override
    public String getTenantId() {
        return condition.getTenantId();
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
    public void updateDisplayString() {
        String s = String.format("Avail: %s[%s] is %s", condition.getDataId(), value.name(),
                condition.getOperator().name());
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

        AvailabilityConditionEval that = (AvailabilityConditionEval) o;

        if (condition != null ? !condition.equals(that.condition) : that.condition != null)
            return false;
        if (value != that.value)
            return false;

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
        return "AvailabilityConditionEval [condition=" + condition + ", value=" + value + ", match=" + match
                + ", evalTimestamp=" + evalTimestamp + ", dataTimestamp=" + dataTimestamp + "]";
    }

}
