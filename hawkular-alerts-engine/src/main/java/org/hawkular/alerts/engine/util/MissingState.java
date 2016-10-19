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
package org.hawkular.alerts.engine.util;

import org.hawkular.alerts.api.model.condition.MissingCondition;
import org.hawkular.alerts.api.model.trigger.Mode;
import org.hawkular.alerts.api.model.trigger.Trigger;

/**
 * MissingConditions detects when there is a missing data or event within a defined time interval.
 * This class stores a last evaluation time of a missing data.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class MissingState {

    private String tenantId;

    private String triggerId;

    private Mode triggerMode;

    private String source;

    private String dataId;

    private long previousTime;

    private long time;

    private String conditionId;

    private MissingCondition condition;

    public MissingState(Trigger trigger, MissingCondition condition) {
        this.tenantId = trigger.getTenantId();
        this.triggerId = trigger.getId();
        this.triggerMode = condition.getTriggerMode();
        this.source = trigger.getSource();
        this.dataId = condition.getDataId();
        this.previousTime = System.currentTimeMillis();
        this.time = System.currentTimeMillis();
        this.conditionId = condition.getConditionId();
        this.condition = condition;
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

    public Mode getTriggerMode() {
        return triggerMode;
    }

    public void setTriggerMode(Mode triggerMode) {
        this.triggerMode = triggerMode;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDataId() {
        return dataId;
    }

    public void setDataId(String dataId) {
        this.dataId = dataId;
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

    public String getConditionId() {
        return conditionId;
    }

    public void setConditionId(String conditionId) {
        this.conditionId = conditionId;
    }

    public MissingCondition getCondition() {
        return condition;
    }

    public void setCondition(MissingCondition condition) {
        this.condition = condition;
    }

    /*
            Due performance reasons, conditionId will be used for equals()/hashCode() instead of condition
         */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MissingState that = (MissingState) o;

        if (previousTime != that.previousTime) return false;
        if (time != that.time) return false;
        if (tenantId != null ? !tenantId.equals(that.tenantId) : that.tenantId != null) return false;
        if (triggerId != null ? !triggerId.equals(that.triggerId) : that.triggerId != null) return false;
        if (triggerMode != that.triggerMode) return false;
        if (source != null ? !source.equals(that.source) : that.source != null) return false;
        if (dataId != null ? !dataId.equals(that.dataId) : that.dataId != null) return false;
        return conditionId != null ? conditionId.equals(that.conditionId) : that.conditionId == null;

    }

    @Override
    public int hashCode() {
        int result = tenantId != null ? tenantId.hashCode() : 0;
        result = 31 * result + (triggerId != null ? triggerId.hashCode() : 0);
        result = 31 * result + (triggerMode != null ? triggerMode.hashCode() : 0);
        result = 31 * result + (source != null ? source.hashCode() : 0);
        result = 31 * result + (dataId != null ? dataId.hashCode() : 0);
        result = 31 * result + (int) (previousTime ^ (previousTime >>> 32));
        result = 31 * result + (int) (time ^ (time >>> 32));
        result = 31 * result + (conditionId != null ? conditionId.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "MissingState{" +
                "tenantId='" + tenantId + '\'' +
                ", triggerId='" + triggerId + '\'' +
                ", triggerMode=" + triggerMode +
                ", source='" + source + '\'' +
                ", dataId='" + dataId + '\'' +
                ", previousTime=" + previousTime +
                ", time=" + time +
                ", conditionId='" + conditionId + '\'' +
                ", condition=" + condition +
                '}';
    }
}
