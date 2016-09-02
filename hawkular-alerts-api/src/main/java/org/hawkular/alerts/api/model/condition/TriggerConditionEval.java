/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.hawkular.alerts.api.model.trigger.Mode;

/**
 * A class to accumulate one or more ConditionEval per Trigger.
 * This helper class will help to process all ConditionEval in one step inside rules engine.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class TriggerConditionEval {

    private String tenantId;

    private String triggerId;

    private Mode mode;

    private Set<ConditionEval> conditionEvals;

    public TriggerConditionEval(String tenantId, String triggerId, Mode mode) {
        this.tenantId = tenantId;
        this.triggerId = triggerId;
        this.mode = mode;
        conditionEvals = new HashSet<>();
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getTriggerId() {
        return triggerId;
    }

    public void setTriggerId(String triggerId) {
        this.triggerId = triggerId;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public Set<ConditionEval> getConditionEvals() {
        return conditionEvals;
    }

    public void setConditionEvals(Set<ConditionEval> conditionEvals) {
        this.conditionEvals = conditionEvals;
    }

    public void addConditionEval(ConditionEval ce) {
        conditionEvals.add(ce);
    }

    public void addConditionEval(Collection<ConditionEval> ces) {
        conditionEvals.addAll(ces);
    }

    public int getSize() {
        return conditionEvals.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TriggerConditionEval that = (TriggerConditionEval) o;

        if (tenantId != null ? !tenantId.equals(that.tenantId) : that.tenantId != null) return false;
        if (triggerId != null ? !triggerId.equals(that.triggerId) : that.triggerId != null) return false;
        if (mode != that.mode) return false;
        return conditionEvals != null ? conditionEvals.equals(that.conditionEvals) : that.conditionEvals == null;

    }

    @Override
    public int hashCode() {
        int result = tenantId != null ? tenantId.hashCode() : 0;
        result = 31 * result + (triggerId != null ? triggerId.hashCode() : 0);
        result = 31 * result + (mode != null ? mode.hashCode() : 0);
        result = 31 * result + (conditionEvals != null ? conditionEvals.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "TriggerConditionEval{" +
                "tenantId='" + tenantId + '\'' +
                ", triggerId='" + triggerId + '\'' +
                ", mode=" + mode +
                ", conditionEvals=" + conditionEvals +
                '}';
    }
}
