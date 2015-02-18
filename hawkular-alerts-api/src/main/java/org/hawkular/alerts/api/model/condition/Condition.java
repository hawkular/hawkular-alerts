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

import org.hawkular.alerts.api.model.trigger.Trigger.Mode;

/**
 * A base class for condition definition.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public abstract class Condition {

    /**
     * The owning trigger
     */
    protected String triggerId;

    /**
     * The owning trigger's mode when this condition is active
     */
    protected Mode triggerMode;

    /**
     * Number of conditions associated with a particular trigger.
     * i.e. 2 [ conditions ]
     */
    protected int conditionSetSize;

    /**
     * Index of the current condition
     * i.e. 1 [ of 2 conditions ]
     */
    protected int conditionSetIndex;

    /**
     * A composed key for the condition
     */
    protected String conditionId;

    public Condition(String triggerId, Mode triggerMode, int conditionSetSize, int conditionSetIndex) {
        this.triggerId = triggerId;
        this.triggerMode = triggerMode;
        this.conditionSetSize = conditionSetSize;
        this.conditionSetIndex = conditionSetIndex;
        updateId();
    }

    public int getConditionSetIndex() {
        return conditionSetIndex;
    }

    public void setConditionSetIndex(int conditionSetIndex) {
        this.conditionSetIndex = conditionSetIndex;
        updateId();
    }

    public int getConditionSetSize() {
        return conditionSetSize;
    }

    public void setConditionSetSize(int conditionSetSize) {
        this.conditionSetSize = conditionSetSize;
        updateId();
    }

    public String getTriggerId() {
        return triggerId;
    }

    public void setTriggerId(String triggerId) {
        this.triggerId = triggerId;
        updateId();
    }

    public Mode getTriggerMode() {
        return triggerMode;
    }

    public void setTriggerMode(Mode triggerMode) {
        this.triggerMode = triggerMode;
        updateId();
    }

    public String getConditionId() {
        return conditionId;
    }

    private void updateId() {
        StringBuilder sb = new StringBuilder(triggerId);
        sb.append("-").append(triggerMode.ordinal());
        sb.append("-").append(conditionSetSize);
        sb.append("-").append(conditionSetIndex);
        this.conditionId = sb.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((conditionId == null) ? 0 : conditionId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Condition other = (Condition) obj;
        if (conditionId == null) {
            if (other.conditionId != null)
                return false;
        } else if (!conditionId.equals(other.conditionId))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "Condition [triggerId=" + triggerId + ", triggerMode=" + triggerMode + ", conditionSetSize="
                + conditionSetSize + ", conditionSetIndex=" + conditionSetIndex + "]";
    }

}
