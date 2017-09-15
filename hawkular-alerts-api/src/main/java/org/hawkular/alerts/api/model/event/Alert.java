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
package org.hawkular.alerts.api.model.event;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.hawkular.alerts.api.model.Severity;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.trigger.Trigger;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * A status of an alert thrown by several matched conditions.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@ApiModel(description = "Alerts are generated when an Alert Trigger fires, based on a set of defined conditions + \n" +
        "that have been matched, possibly more than once or have held true over a period of time. + \n" +
        " + \n" +
        "When fired the trigger can perform actions based on plugins (e-mail, sms, etc). + \n" +
        " + \n" +
        "Alerts then start moving through the Open, Acknowledged, Resolved life-cycle. + \n" +
        " + \n" +
        "- Open status represents an alert which has not been seen/taken yet by any user. + \n" +
        "- Acknowledge status represents an alert which has been seen/taken by any user and it is pending " +
        "resolution. + \n" +
        "- Resolved status represents an alert which problem has been resolved. + \n" +
        " + \n" +
        "Alerts can be resolved automatically using AUTORESOLVE <<Trigger>> conditions or manually via API. + \n" +
        " + \n" +
        "Alert can attach a list of notes defined by the user. + \n" +
        " + \n" +
        "There are many options on triggers to help ensure that alerts are not generated too frequently, + \n" +
        "including ways of automatically disabling and enabling the trigger. + \n")
public class Alert extends Event {

    public enum Status {
        OPEN, ACKNOWLEDGED, RESOLVED
    };

    @ApiModelProperty(value = "Severity set for a <<Trigger>> and assigned to an alert when it is generated.",
            position = 0,
            example = "MEDIUM")
    @JsonInclude
    private Severity severity;

    @ApiModelProperty(value = "Lifecycle current status.",
            position = 1)
    @JsonInclude
    private Status status;

    @ApiModelProperty(value = "Notes attached with this alert.",
            position = 2)
    @JsonInclude(Include.NON_EMPTY)
    private List<Note> notes = new ArrayList<>();;

    @ApiModelProperty(value = "List of lifecycle states that this alert has navigated.",
            position = 3)
    @JsonInclude(Include.NON_EMPTY)
    private List<LifeCycle> lifecycle = new ArrayList<>();

    @ApiModelProperty(value = "The Eval Sets that resolved the <<Trigger>> in AUTORESOLVE mode. + \n " +
            "Null for non AUTORESOLVE triggers.",
            position = 4)
    @JsonInclude(Include.NON_EMPTY)
    @Thin
    private List<Set<ConditionEval>> resolvedEvalSets;

    public Alert() {
        // for json assembly
        this.eventType = EventType.ALERT.name();
        this.status = Status.OPEN;
    }

    public Alert(Alert alert) {
        super((Event) alert);

        this.status = alert.getStatus();
        this.severity = alert.getSeverity();
        this.eventType = alert.getEventType();
        this.lifecycle = new ArrayList<>();
        for (LifeCycle item : alert.getLifecycle()) {
            this.lifecycle.add(new LifeCycle(item));
        }
        for (Note note : alert.getNotes()) {
            this.notes.add(note);
        }
        this.resolvedEvalSets = alert.getResolvedEvalSets();
    }

    /**
     * Assumes default dampening.
     */
    public Alert(String tenantId, Trigger trigger, List<Set<ConditionEval>> evalSets) {
        this(tenantId, trigger, null, evalSets);
    }

    public Alert(String tenantId, Trigger trigger, Dampening dampening, List<Set<ConditionEval>> evalSets) {
        super(tenantId, trigger, dampening, evalSets);

        this.status = Status.OPEN;
        this.severity = trigger.getSeverity();
        this.eventType = EventType.ALERT.name();
        addLifecycle(this.status, "system", this.ctime);
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

    public List<Set<ConditionEval>> getResolvedEvalSets() {
        return resolvedEvalSets;
    }

    public void setResolvedEvalSets(List<Set<ConditionEval>> resolvedEvalSets) {
        this.resolvedEvalSets = resolvedEvalSets;
    }

    public List<Note> getNotes() {
        return notes;
    }

    public void setNotes(List<Note> notes) {
        this.notes = notes;
    }

    /**
     * Add a note on this alert
     *
     * @param user author of the comment
     * @param text content of the note
     */
    public void addNote(String user, String text) {
        if (user == null || text == null) {
            throw new IllegalArgumentException("Note must have non-null user and text");
        }
        getNotes().add(new Note(user, text));
    }

    public List<LifeCycle> getLifecycle() {
        return lifecycle;
    }

    public void setLifecycle(List<LifeCycle> lifecycle) {
        this.lifecycle = lifecycle;
    }

    public void addLifecycle(Status status, String user, long stime) {
        if (status == null || user == null) {
            throw new IllegalArgumentException("Lifecycle must have non-null state and user");
        }
        setStatus(status);
        getLifecycle().add(new LifeCycle(status, user, stime));
    }

    @JsonIgnore
    public LifeCycle getCurrentLifecycle() {
        if (getLifecycle().isEmpty()) {
            return null;
        }
        return getLifecycle().get(getLifecycle().size() - 1);
    }

    @JsonIgnore
    public Long getLastStatusTime(Status status) {
        if (getLifecycle().isEmpty()) {
            return null;
        }
        Long statusTime = null;
        ListIterator<LifeCycle> iterator = getLifecycle().listIterator(getLifecycle().size());
        while (iterator.hasPrevious()) {
            LifeCycle lifeCycle = iterator.previous();
            if (lifeCycle.getStatus().equals(status)) {
                statusTime = lifeCycle.getStime();
                break;
            }
        }
        return statusTime;
    }

    @JsonIgnore
    public Long getLastOpenTime() {
        return getLastStatusTime(Status.OPEN);
    }

    @JsonIgnore
    public Long getLastAckTime() {
        return getLastStatusTime(Status.ACKNOWLEDGED);
    }

    @JsonIgnore
    public Long getLastResolvedTime() {
        return getLastStatusTime(Status.RESOLVED);
    }

    @Override
    public String toString() {
        return "Alert [tenantId=" + tenantId + ", triggerId=" + getTriggerId() + ", severity=" + severity
                + ", status=" + status + ", ctime=" + ctime + ", lifecycle=" + lifecycle
                + ", resolvedEvalSets=" + resolvedEvalSets + "]";
    }

    @ApiModel(description = "A simple note representation.")
    public static class Note implements Serializable {

        @ApiModelProperty(value = "The user who creates the note.",
                position = 0,
                required = true)
        @JsonInclude(Include.NON_EMPTY)
        private String user;

        @ApiModelProperty(value = "Note creation time.",
                position = 1,
                allowableValues = "Timestamp in milliseconds.")
        @JsonInclude(Include.NON_EMPTY)
        private long ctime;

        @ApiModelProperty(value = "The note text.",
                position = 2,
                required = true)
        @JsonInclude(Include.NON_EMPTY)
        private String text;

        public Note() {
            // for json assembly
        }

        public Note(String user, String text) {
            this(user, System.currentTimeMillis(), text);
        }

        public Note(String user, long ctime, String text) {
            this.user = user;
            this.ctime = ctime;
            this.text = text;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public long getCtime() {
            return ctime;
        }

        public void setCtime(long ctime) {
            this.ctime = ctime;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            Note note = (Note) o;

            if (ctime != note.ctime)
                return false;
            if (user != null ? !user.equals(note.user) : note.user != null)
                return false;
            return !(text != null ? !text.equals(note.text) : note.text != null);

        }

        @Override
        public int hashCode() {
            int result = user != null ? user.hashCode() : 0;
            result = 31 * result + (int) (ctime ^ (ctime >>> 32));
            result = 31 * result + (text != null ? text.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "Note{" +
                    "user='" + user + '\'' +
                    ", ctime=" + ctime +
                    ", text='" + text + '\'' +
                    '}';
        }
    }

    @ApiModel(description = "A lifecycle state representation.")
    public static class LifeCycle implements Serializable {

        @ApiModelProperty(value = "The status of this lifecycle.",
                position = 0,
                example = "OPEN")
        @JsonInclude(Include.NON_EMPTY)
        private Status status;

        @ApiModelProperty(value = "The user who creates the state + \n" +
                "Open statutes are created by 'system' + \n" +
                "In AUTORESOLVE triggers Resolved statutes are create by 'AutoResolve'.",
                position = 1)
        @JsonInclude(Include.NON_EMPTY)
        private String user;

        @ApiModelProperty(value = "Creation time for this state.",
                position = 2,
                allowableValues = "Timestamp in milliseconds.")
        @JsonInclude(Include.NON_EMPTY)
        private long stime;

        public LifeCycle() {
            // for json assembly
        }

        public LifeCycle(LifeCycle lifeCycle) {
            if (lifeCycle == null) {
                throw new IllegalArgumentException("lifeCycle must be not null");
            }
            this.status = lifeCycle.getStatus();
            this.user = lifeCycle.getUser();
            this.stime = lifeCycle.getStime();
        }

        public LifeCycle(Status status, String user, long stime) {
            this.status = status;
            this.user = user;
            this.stime = stime;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public Status getStatus() {
            return status;
        }

        public void setStatus(Status status) {
            this.status = status;
        }

        public long getStime() {
            return stime;
        }

        public void setStime(long stime) {
            this.stime = stime;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            LifeCycle lifeCycle = (LifeCycle) o;

            if (stime != lifeCycle.stime) return false;
            if (user != null ? !user.equals(lifeCycle.user) : lifeCycle.user != null) return false;
            return status == lifeCycle.status;

        }

        @Override
        public int hashCode() {
            int result = user != null ? user.hashCode() : 0;
            result = 31 * result + (status != null ? status.hashCode() : 0);
            result = 31 * result + (int) (stime ^ (stime >>> 32));
            return result;
        }

        @Override
        public String toString() {
            return "LifeCycle{" +
                    "user='" + user + '\'' +
                    ", status=" + status +
                    ", stime=" + stime +
                    '}';
        }
    }
}