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

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.hawkular.alerts.api.model.trigger.Mode;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * A base class for condition definition.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public abstract class Condition implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Type {
        AVAILABILITY, COMPARE, STRING, THRESHOLD, RANGE, EXTERNAL, EVENT, RATE
    }

    @JsonInclude
    protected String tenantId;

    /**
     * The owning trigger
     */
    @JsonInclude
    protected String triggerId;

    /**
     * The owning trigger's mode when this condition is active
     */
    @JsonInclude
    protected Mode triggerMode;

    @JsonInclude
    protected Type type;

    /**
     * Number of conditions associated with a particular trigger.
     * i.e. 2 [ conditions ]
     */
    @JsonInclude
    protected int conditionSetSize;

    /**
     * Index of the current condition
     * i.e. 1 [ of 2 conditions ]
     */
    @JsonInclude
    protected int conditionSetIndex;

    /**
     * A composed key for the condition
     */
    @JsonInclude
    protected String conditionId;

    @JsonInclude(Include.NON_EMPTY)
    protected Map<String, String> context;

    public Condition() {
        // for json assembly
    }

    public Condition(String triggerId, Mode triggerMode, int conditionSetSize, int conditionSetIndex, Type type) {
        this.triggerId = triggerId;
        this.triggerMode = triggerMode;
        this.conditionSetSize = conditionSetSize;
        this.conditionSetIndex = conditionSetIndex;
        this.type = type;
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

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Map<String, String> getContext() {
        if (null == context) {
            context = new HashMap<>();
        }
        return context;
    }

    public void setContext(Map<String, String> context) {
        this.context = context;
    }

    private void updateId() {
        StringBuilder sb = new StringBuilder(triggerId);
        sb.append("-").append(triggerMode.name());
        sb.append("-").append(conditionSetSize);
        sb.append("-").append(conditionSetIndex);
        this.conditionId = sb.toString();
    }

    public Type getType() {
        return type;
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

    /**
     * @return The dataId, can be null if the Condition has no relevant dataId.
     */
    public abstract String getDataId();
}
