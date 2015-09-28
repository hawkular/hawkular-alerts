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
package org.hawkular.alerts.api.services;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.hawkular.alerts.api.model.Severity;
import org.hawkular.alerts.api.model.condition.Alert;

/**
 * Query criteria for fetching Alerts.
 * @author jay shaughnessy
 * @author lucas ponce
 */
public class AlertsCriteria {
    Long startTime = null;
    Long endTime = null;
    String alertId = null;
    Collection<String> alertIds = null;
    Alert.Status status = null;
    Collection<Alert.Status> statusSet = null;
    Severity severity = null;
    Collection<Severity> severities = null;
    String triggerId = null;
    Collection<String> triggerIds = null;
    Map<String, String> tags = null;
    boolean thin = false;

    public AlertsCriteria() {
        super();
    }

    public Long getStartTime() {
        return startTime;
    }

    /**
     * @param startTime fetched Alerts must have cTime greater than or equal to startTime
     */
    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Long getEndTime() {
        return endTime;
    }

    /**
     * @param endTime fetched Alerts must have cTime less than or equal to endTime
     */
    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    public String getAlertId() {
        return alertId;
    }

    public void setAlertId(String alertId) {
        this.alertId = alertId;
    }

    public Collection<String> getAlertIds() {
        return alertIds;
    }

    public void setAlertIds(Collection<String> alertIds) {
        this.alertIds = alertIds;
    }

    public Alert.Status getStatus() {
        return status;
    }

    public void setStatus(Alert.Status status) {
        this.status = status;
    }

    public Collection<Alert.Status> getStatusSet() {
        return statusSet;
    }

    public void setStatusSet(Collection<Alert.Status> statusSet) {
        this.statusSet = statusSet;
    }

    public String getTriggerId() {
        return triggerId;
    }

    /**
     * @param triggerId fetched Alerts must be for the specified trigger. Ignored if triggerIds is not empty.
     */
    public void setTriggerId(String triggerId) {
        this.triggerId = triggerId;
    }

    public Collection<String> getTriggerIds() {
        return triggerIds;
    }

    /**
     * @param triggerIds fetched alerts must be for one of the specified triggers.
     */
    public void setTriggerIds(Collection<String> triggerIds) {
        this.triggerIds = triggerIds;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }

    public void addTag(String name, String value) {
        if (null == tags) {
            tags = new HashMap<>();
        }
        tags.put(name, value);
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public Collection<Severity> getSeverities() {
        return severities;
    }

    public void setSeverities(Collection<Severity> severities) {
        this.severities = severities;
    }

    public boolean isThin() {
        return thin;
    }

    public void setThin(boolean thin) {
        this.thin = thin;
    }

    public boolean hasCriteria() {
        return null != startTime //
                || null != endTime
                || null != status
                || null != severity
                || null != triggerId
                || null != alertId
                || (null != statusSet && !statusSet.isEmpty())
                || (null != severities && !severities.isEmpty())
                || (null != triggerIds && !triggerIds.isEmpty())
                || (null != alertIds && !alertIds.isEmpty())
                || (null != tags && !tags.isEmpty());
    }

    @Override
    public String toString() {
        return "AlertsCriteria [startTime=" + startTime + ", endTime=" + endTime + ", alertId=" + alertId
                + ", alertIds=" + alertIds + ", status=" + status + ", statusSet=" + statusSet + ", severity="
                + severity + ", severities=" + severities + ", triggerId=" + triggerId + ", triggerIds=" + triggerIds
                + ", tags=" + tags + ", thin=" + thin + "]";
    }
}
