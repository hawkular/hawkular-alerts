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
package org.hawkular.alerts.api.model.event;

import java.util.List;
import java.util.Set;

import org.hawkular.alerts.api.model.Severity;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.trigger.Trigger;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * A status of an alert thrown by several matched conditions.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class Alert extends Event {

    public enum Status {
        OPEN, ACKNOWLEDGED, RESOLVED
    };

    @JsonInclude
    private Severity severity;

    @JsonInclude
    private Status status;

    @JsonInclude
    private long ackTime;

    @JsonInclude
    private String ackBy;

    @JsonInclude
    private String ackNotes;

    @JsonInclude
    private long resolvedTime;

    @JsonInclude
    private String resolvedBy;

    @JsonInclude
    private String resolvedNotes;

    @JsonInclude(Include.NON_EMPTY)
    @Thin
    private List<Set<ConditionEval>> resolvedEvalSets;


    public Alert() {
        // for json assembly
    }

    /**
     * Assumes default dampening.
     */
    public Alert(String tenantId, Trigger trigger, List<Set<ConditionEval>> evalSets) {
        this(tenantId, trigger, null, evalSets);
    }

    public Alert(String tenantId, Trigger trigger, Dampening dampening, List<Set<ConditionEval>> evalSets) {
        super(tenantId, trigger, dampening, evalSets, null);

        this.status = Status.OPEN;
        this.severity = trigger.getSeverity();
    }

    @JsonIgnore
    public String getAlertId() {
        return id;
    }

    public void setAlertId(String alertId) {
        this.id = alertId;
    }

    @JsonIgnore
    public String getTriggerId() {
        return getTrigger().getId();
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public long getAckTime() {
        return ackTime;
    }

    public void setAckTime(long ackTime) {
        this.ackTime = ackTime;
    }

    public String getAckBy() {
        return ackBy;
    }

    public void setAckBy(String ackBy) {
        this.ackBy = ackBy;
    }

    public String getAckNotes() {
        return ackNotes;
    }

    public void setAckNotes(String ackNotes) {
        this.ackNotes = ackNotes;
    }

    public long getResolvedTime() {
        return resolvedTime;
    }

    public void setResolvedTime(long resolvedTime) {
        this.resolvedTime = resolvedTime;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public String getResolvedNotes() {
        return resolvedNotes;
    }

    public void setResolvedNotes(String resolvedNotes) {
        this.resolvedNotes = resolvedNotes;
    }

    public List<Set<ConditionEval>> getResolvedEvalSets() {
        return resolvedEvalSets;
    }

    public void setResolvedEvalSets(List<Set<ConditionEval>> resolvedEvalSets) {
        this.resolvedEvalSets = resolvedEvalSets;
    }

    @Override
    public String toString() {
        return "Alert [alertId=" + id + ", status=" + status + ", ackTime=" + ackTime
                + ", ackBy=" + ackBy + ", resolvedTime=" + resolvedTime + ", resolvedBy=" + resolvedBy + ", context="
                + getContext() + "]";
    }

}