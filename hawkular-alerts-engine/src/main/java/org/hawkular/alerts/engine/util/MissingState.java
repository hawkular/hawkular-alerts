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
package org.hawkular.alerts.engine.util;

import org.hawkular.alerts.api.model.condition.MissingCondition;
import org.hawkular.alerts.api.model.trigger.Mode;
import org.hawkular.alerts.api.model.trigger.Trigger;

/**
 * MissingConditions detect missing data or events within a defined time interval.  Each MissingCondition
 * has an associated MissingState object to track last-seen/evaluation times for the relevant data or event.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class MissingState {

    // Fields accessed in the rulebase
    private String tenantId;
    private String triggerId;
    private Mode triggerMode; // This is the MissingCondition trigger mode
    private String source;
    private String dataId;
    private long previousTime;
    private long time;

    // Need to ensure the current trigger mode matches the condition's trigger mode
    private Trigger trigger;

    // Given the 1:1 relationship we use the [immutable] conditionId as the unique id for this as well.
    private String conditionId;
    private MissingCondition condition;

    public MissingState(Trigger trigger, MissingCondition condition) {
        this.trigger = trigger;
        this.tenantId = trigger.getTenantId();
        this.triggerId = trigger.getId();
        this.source = trigger.getSource();

        this.condition = condition;
        this.conditionId = condition.getConditionId();
        this.triggerMode = condition.getTriggerMode();
        this.dataId = condition.getDataId();

        long now = System.currentTimeMillis();
        this.previousTime = now;
        this.time = now;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getTriggerId() {
        return triggerId;
    }

    public Mode getTriggerMode() {
        return triggerMode;
    }

    public String getSource() {
        return source;
    }

    public String getDataId() {
        return dataId;
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

    public Trigger getTrigger() {
        return trigger;
    }

    public MissingCondition getCondition() {
        return condition;
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
        MissingState other = (MissingState) obj;
        if (conditionId == null) {
            if (other.conditionId != null)
                return false;
        } else if (!conditionId.equals(other.conditionId))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "MissingState [previousTime=" + previousTime + ", time=" + time + ", condition=" + condition + "]";
    }


}
