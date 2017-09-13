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

import java.util.HashMap;

import org.hawkular.alerts.api.doc.DocModel;
import org.hawkular.alerts.api.doc.DocModelProperty;
import org.hawkular.alerts.api.model.condition.Condition.Type;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * An evaluation state for MissingCondition
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@DocModel(description = "An evaluation state for missing condition.")
public class MissingConditionEval extends ConditionEval {

    private static final long serialVersionUID = 1L;

    @DocModelProperty(description = "Missing condition linked with this state.",
            position = 0)
    @JsonInclude(Include.NON_NULL)
    private MissingCondition condition;

    @DocModelProperty(description = "Time when trigger was enabled or last time a data/event was received.",
            position = 1)
    @JsonInclude
    private long previousTime;

    @DocModelProperty(description = "Time when most recently evaluation of missing condition.",
            position = 2)
    @JsonInclude
    private long time;

    /**
     * Used for JSON deserialization, not for general use.
     */
    public MissingConditionEval() {
        super(Type.MISSING, false, 0, null);
        this.previousTime = 0L;
        this.time = 0L;
    }

    public MissingConditionEval(MissingCondition condition, long previousTime, long time) {
        super(Type.MISSING, condition.match(previousTime, time), time, new HashMap<>());
        this.condition = condition;
        this.previousTime = previousTime;
        this.time = time;
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
        String s = String.format("Missing: %s[%tc] %dms GTE %dms", condition.getDataId(), time,
                (time - previousTime), condition.getInterval());
        setDisplayString(s);
    }

    public MissingCondition getCondition() {
        return condition;
    }

    public void setCondition(MissingCondition condition) {
        this.condition = condition;
    }

    public long getPreviousTime() {
        return previousTime;
    }

    public void setPreviousTime(long previousTime) {
        this.previousTime = previousTime;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        MissingConditionEval that = (MissingConditionEval) o;

        if (previousTime != that.previousTime) return false;
        if (time != that.time) return false;
        return condition != null ? condition.equals(that.condition) : that.condition == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (condition != null ? condition.hashCode() : 0);
        result = 31 * result + (int) (previousTime ^ (previousTime >>> 32));
        result = 31 * result + (int) (time ^ (time >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "MissingConditionEval{" +
                "condition=" + condition +
                ", previousTime=" + previousTime +
                ", time=" + time +
                '}';
    }
}
