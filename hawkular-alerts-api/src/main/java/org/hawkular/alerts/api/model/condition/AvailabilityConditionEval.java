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

import org.hawkular.alerts.api.model.data.Availability;
import org.hawkular.alerts.api.model.data.Availability.AvailabilityType;

/**
 * An evaluation state for availability condition.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class AvailabilityConditionEval extends ConditionEval {

    private AvailabilityCondition condition;
    private AvailabilityType value;

    public AvailabilityConditionEval() {
        super(false, 0);
        this.condition = null;
        this.value = null;
    }

    public AvailabilityConditionEval(AvailabilityCondition condition, Availability avail) {
        super(condition.match(avail.getValue()), avail.getTimestamp());
        this.condition = condition;
        this.value = avail.getValue();
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
        return condition.getLog(value) + ", evalTimestamp=" + evalTimestamp + ", dataTimestamp=" + dataTimestamp;
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
        return "AvailabilityConditionEval [condition=" + condition + ", value=" + value + ", toString()="
                + super.toString() + "]";
    }

}
