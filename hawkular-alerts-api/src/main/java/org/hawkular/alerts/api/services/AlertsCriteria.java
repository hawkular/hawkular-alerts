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
package org.hawkular.alerts.api.services;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.api.model.Severity;
import org.hawkular.alerts.api.model.event.Alert;

/**
 * Query criteria for fetching Alerts.
 * @author jay shaughnessy
 * @author lucas ponce
 */
public class AlertsCriteria {
    Long startTime = null;
    Long endTime = null;
    Long startResolvedTime = null;
    Long endResolvedTime = null;
    Long startAckTime = null;
    Long endAckTime = null;
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

    public AlertsCriteria(Long startTime, Long endTime, String alertIds, String triggerIds,
           String statuses, String severities, String tags, Long startResolvedTime, Long endResolvedTime,
           Long startAckTime, Long endAckTime, Boolean thin) {
        setStartTime(startTime);
        setEndTime(endTime);
        if (!isEmpty(alertIds)) {
            setAlertIds(Arrays.asList(alertIds.split(",")));
        }
        if (!isEmpty(triggerIds)) {
            setTriggerIds(Arrays.asList(triggerIds.split(",")));
        }
        if (!isEmpty(statuses)) {
            Set<Alert.Status> statusSet = new HashSet<>();
            for (String s : statuses.split(",")) {
                statusSet.add(Alert.Status.valueOf(s));
            }
            setStatusSet(statusSet);
        }
        if (null != severities && !severities.trim().isEmpty()) {
            Set<Severity> severitySet = new HashSet<>();
            for (String s : severities.split(",")) {
                severitySet.add(Severity.valueOf(s));
            }
            setSeverities(severitySet);
        }
        if (!isEmpty(tags)) {
            String[] tagTokens = tags.split(",");
            Map<String, String> tagsMap = new HashMap<>(tagTokens.length);
            for (String tagToken : tagTokens) {
                String[] fields = tagToken.split("\\|");
                if (fields.length == 2) {
                    tagsMap.put(fields[0], fields[1]);
                } else {
                    throw new IllegalArgumentException("Invalid Tag Criteria " + Arrays.toString(fields));
                }
            }
            setTags(tagsMap);
        }
        setStartResolvedTime(startResolvedTime);
        setEndResolvedTime(endResolvedTime);
        setStartAckTime(startAckTime);
        setEndAckTime(endAckTime);
        if (null != thin) {
            setThin(thin.booleanValue());
        }
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

    public Long getStartResolvedTime() {
        return startResolvedTime;
    }

    /**
     * @param startResolvedTime fetched Alerts must have at least one resolvedTime in the lifecycle greater than or
     *                          equal to startResolvedTime.
     *                          Alerts lifecycle might involve several transitions between ACKNOWLEDGE and RESOLVE
     *                          states.
     */
    public void setStartResolvedTime(Long startResolvedTime) {
        this.startResolvedTime = startResolvedTime;
    }

    public Long getEndResolvedTime() {
        return endResolvedTime;
    }

    /**
     * @param endResolvedTime fetched Alerts must have at least one resolvedTime in the lifecycle less than or equal to
     *                        endResolvedTime.
     *                        Alerts lifecycle might involve several transitions between ACKNOWLEDGE and RESOLVE states.
     */
    public void setEndResolvedTime(Long endResolvedTime) {
        this.endResolvedTime = endResolvedTime;
    }

    public Long getStartAckTime() {
        return startAckTime;
    }

    /**
     * @param startAckTime fetched Alerts must have at least one ackTime in the lifecycle greater than or equal to
     *                     startAckTime.
     *                     Alerts lifecycle might involve several transitions between ACKNOWLEDGE and RESOLVE states.
     */
    public void setStartAckTime(Long startAckTime) {
        this.startAckTime = startAckTime;
    }

    public Long getEndAckTime() {
        return endAckTime;
    }

    /**
     * @param endAckTime fetched Alerts must have at least one ackTime in the lifecycle less than or equal to
     *                   endAckTime.
     *                   Alerts lifecycle might involve several transitions between ACKNOWLEDGE and RESOLVE states.
     */
    public void setEndAckTime(Long endAckTime) {
        this.endAckTime = endAckTime;
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

    /**
     * @param tags return alerts with *any* of these tags, it does not have to have all of the tags). Specify '*' for
     * the value if you only want to match the name portion of the tag.
     */
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

    public boolean hasAlertIdCriteria() {
        return null != alertId
                || (null != alertIds && !alertIds.isEmpty());
    }

    public boolean hasSeverityCriteria() {
        return null != severity
                || (null != severities && !severities.isEmpty());
    }

    public boolean hasStatusCriteria() {
        return null != status
                || (null != statusSet && !statusSet.isEmpty());
    }

    public boolean hasTagCriteria() {
        return (null != tags && !tags.isEmpty());
    }

    public boolean hasCTimeCriteria() {
        return (null != startTime || null != endTime);
    }

    public boolean hasTriggerIdCriteria() {
        return null != triggerId
                || (null != triggerIds && !triggerIds.isEmpty());
    }

    public boolean hasResolvedTimeCriteria() {
        return (null != startResolvedTime || null != endResolvedTime);
    }

    public boolean hasAckTimeCriteria() {
        return (null != startAckTime || null != endAckTime);
    }

    public boolean hasCriteria() {
        return hasAlertIdCriteria()
                || hasStatusCriteria()
                || hasSeverityCriteria()
                || hasTagCriteria()
                || hasCTimeCriteria()
                || hasTriggerIdCriteria()
                || hasResolvedTimeCriteria()
                || hasAckTimeCriteria();
    }

    @Override
    public String toString() {
        return "AlertsCriteria [startTime=" + startTime + ", endTime=" + endTime + ", alertId=" + alertId
                + ", alertIds=" + alertIds + ", status=" + status + ", statusSet=" + statusSet + ", severity="
                + severity + ", severities=" + severities + ", triggerId=" + triggerId + ", triggerIds=" + triggerIds
                + ", tags=" + tags + ", startAckTime=" + startAckTime + ", endAckTime=" + endAckTime + ", " +
                "startResolvedTime=" + startResolvedTime + ", endResolvedTime=" + endResolvedTime + ", " +
                "thin=" + thin + "]";
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

}
