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
package org.hawkular.alerts.engine.impl.ispn.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.event.Event;
import org.hibernate.search.annotations.Analyze;
import org.hibernate.search.annotations.Analyzer;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.FieldBridge;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.Store;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Indexed(index = "event")
public class IspnEvent implements Serializable {

    @Field(store = Store.YES, analyze = Analyze.NO)
    private String eventType;

    @Field(store = Store.YES, analyze = Analyze.NO)
    private String tenantId;

    @Field(store = Store.YES, analyze = Analyze.NO)
    private String id;

    @Field(store = Store.YES, analyze = Analyze.YES)
    @FieldBridge(impl = TagsBridge.class)
    @Analyzer(impl = TagsBridge.TagsAnalyzer.class)
    private Map<String, String> tags;

    @Field(store = Store.YES, analyze = Analyze.NO)
    private String triggerId;

    @Field(store = Store.YES, analyze = Analyze.NO)
    private long ctime;

    @Field(store = Store.YES, analyze = Analyze.NO)
    private String status;

    @Field(store = Store.YES, analyze = Analyze.NO)
    private long stime;

    @Field(store = Store.YES, analyze = Analyze.NO)
    private String severity;

    @Field(store = Store.YES, analyze = Analyze.NO)
    private String category;

    private Event event;

    public IspnEvent() {
    }

    public IspnEvent(Event event) {
        updateEvent(event);
    }

    private void updateEvent(Event event) {
        if (event == null) {
            throw new IllegalArgumentException("event must be not null");
        }
        if (event instanceof Alert) {
            this.event = new Alert((Alert) event);
            this.status = ((Alert) event).getCurrentLifecycle().getStatus().name();
            this.stime = ((Alert) event).getCurrentLifecycle().getStime();
            this.severity = ((Alert) event).getSeverity().name();
        } else {
            this.event = new Event(event);
        }
        this.id = event.getId();
        this.eventType = event.getEventType();
        this.tenantId = event.getTenantId();
        this.tags = new HashMap<>(this.event.getTags());
        this.triggerId = event.getTrigger() != null ? event.getTrigger().getId() : null;
        this.ctime = event.getCtime();
        this.category = event.getCategory();
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Map<String, String> getTags() {
        return new HashMap<>(tags);
    }

    public void setTags(Map<String, String> tags) {
        this.tags = new HashMap<>(tags);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Event getEvent() {
        if (event instanceof Alert) {
            return new Alert((Alert) event);
        }
        return new Event(event);
    }

    public void setEvent(Event event) {
        updateEvent(event);
    }

    public String getTriggerId() {
        return triggerId;
    }

    public void setTriggerId(String triggerId) {
        this.triggerId = triggerId;
    }

    public long getCtime() {
        return ctime;
    }

    public void setCtime(long ctime) {
        this.ctime = ctime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getStime() {
        return stime;
    }

    public void setStime(long stime) {
        this.stime = stime;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IspnEvent ispnEvent = (IspnEvent) o;

        if (ctime != ispnEvent.ctime) return false;
        if (stime != ispnEvent.stime) return false;
        if (eventType != null ? !eventType.equals(ispnEvent.eventType) : ispnEvent.eventType != null) return false;
        if (tenantId != null ? !tenantId.equals(ispnEvent.tenantId) : ispnEvent.tenantId != null) return false;
        if (id != null ? !id.equals(ispnEvent.id) : ispnEvent.id != null) return false;
        if (tags != null ? !tags.equals(ispnEvent.tags) : ispnEvent.tags != null) return false;
        if (triggerId != null ? !triggerId.equals(ispnEvent.triggerId) : ispnEvent.triggerId != null) return false;
        if (status != null ? !status.equals(ispnEvent.status) : ispnEvent.status != null) return false;
        if (severity != null ? !severity.equals(ispnEvent.severity) : ispnEvent.severity != null) return false;
        if (category != null ? !category.equals(ispnEvent.category) : ispnEvent.category != null) return false;
        return event != null ? event.equals(ispnEvent.event) : ispnEvent.event == null;
    }

    @Override
    public int hashCode() {
        int result = eventType != null ? eventType.hashCode() : 0;
        result = 31 * result + (tenantId != null ? tenantId.hashCode() : 0);
        result = 31 * result + (id != null ? id.hashCode() : 0);
        result = 31 * result + (tags != null ? tags.hashCode() : 0);
        result = 31 * result + (triggerId != null ? triggerId.hashCode() : 0);
        result = 31 * result + (int) (ctime ^ (ctime >>> 32));
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (int) (stime ^ (stime >>> 32));
        result = 31 * result + (severity != null ? severity.hashCode() : 0);
        result = 31 * result + (category != null ? category.hashCode() : 0);
        result = 31 * result + (event != null ? event.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "IspnEvent{" +
                "eventType='" + eventType + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", id='" + id + '\'' +
                ", tags=" + tags +
                ", triggerId='" + triggerId + '\'' +
                ", ctime=" + ctime +
                ", status='" + status + '\'' +
                ", stime=" + stime +
                ", severity='" + severity + '\'' +
                ", category='" + category + '\'' +
                ", event=" + event +
                '}';
    }
}
