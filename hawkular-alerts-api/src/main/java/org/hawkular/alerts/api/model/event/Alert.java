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

import java.util.ArrayList;
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
    private long resolvedTime;

    @JsonInclude
    private String resolvedBy;

    @JsonInclude(Include.NON_EMPTY)
    private List<Note> notes;

    @JsonInclude(Include.NON_EMPTY)
    @Thin
    private List<Set<ConditionEval>> resolvedEvalSets;

    public Alert() {
        // for json assembly
        this.eventType = EventType.ALERT.name();
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

    public List<Set<ConditionEval>> getResolvedEvalSets() {
        return resolvedEvalSets;
    }

    public void setResolvedEvalSets(List<Set<ConditionEval>> resolvedEvalSets) {
        this.resolvedEvalSets = resolvedEvalSets;
    }

    public List<Note> getNotes() {
        if (null == notes) {
            this.notes = new ArrayList<>();
        }
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

    @Override
    public String toString() {
        return "Alert [alertId=" + id + ", status=" + status + ", ackTime=" + ackTime
                + ", ackBy=" + ackBy + ", resolvedTime=" + resolvedTime + ", resolvedBy=" + resolvedBy + ", context="
                + getContext() + "]";
    }


    public static class Note {
        @JsonInclude(Include.NON_EMPTY)
        private String user;

        @JsonInclude(Include.NON_EMPTY)
        private long ctime;

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
}