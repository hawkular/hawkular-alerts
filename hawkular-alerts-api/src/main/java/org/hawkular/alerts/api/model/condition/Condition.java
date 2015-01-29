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
     * Number of conditions associated with a particular trigger.
     * i.e. 2 [ conditions ]
     */
    protected int conditionSetSize;

    /**
     * Index of the current condition
     * i.e. 1 [ of 2 conditions ]
     */
    protected int conditionSetIndex;

    public Condition(String triggerId, int conditionSetSize, int conditionSetIndex) {
        this.triggerId = triggerId;
        this.conditionSetSize = conditionSetSize;
        this.conditionSetIndex = conditionSetIndex;
    }

    public int getConditionSetIndex() {
        return conditionSetIndex;
    }

    public void setConditionSetIndex(int conditionSetIndex) {
        this.conditionSetIndex = conditionSetIndex;
    }

    public int getConditionSetSize() {
        return conditionSetSize;
    }

    public void setConditionSetSize(int conditionSetSize) {
        this.conditionSetSize = conditionSetSize;
    }

    public String getTriggerId() {
        return triggerId;
    }

    public void setTriggerId(String triggerId) {
        this.triggerId = triggerId;
    }

    public String getConditionId() {
        return triggerId + "/" + conditionSetSize + "/" + conditionSetIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Condition condition = (Condition) o;

        if (conditionSetIndex != condition.conditionSetIndex) return false;
        if (conditionSetSize != condition.conditionSetSize) return false;
        if (triggerId != null ? !triggerId.equals(condition.triggerId) : condition.triggerId != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = triggerId != null ? triggerId.hashCode() : 0;
        result = 31 * result + conditionSetSize;
        result = 31 * result + conditionSetIndex;
        return result;
    }

    @Override
    public String toString() {
        return "Condition{" +
                "conditionSetIndex=" + conditionSetIndex +
                ", triggerId='" + triggerId + '\'' +
                ", conditionSetSize=" + conditionSetSize +
                '}';
    }
}
