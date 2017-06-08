package org.hawkular.alerts.engine.impl.ispn.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.event.Event;
import org.hibernate.search.annotations.Analyze;
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
    private Map<String, String> tags;

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
        } else {
            this.event = new Event(event);
        }
        this.id = event.getId();
        this.eventType = event.getEventType();
        this.tenantId = event.getTenantId();
        this.tags = this.event.getTags();
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IspnEvent ispnEvent = (IspnEvent) o;

        if (eventType != null ? !eventType.equals(ispnEvent.eventType) : ispnEvent.eventType != null) return false;
        if (tenantId != null ? !tenantId.equals(ispnEvent.tenantId) : ispnEvent.tenantId != null) return false;
        if (id != null ? !id.equals(ispnEvent.id) : ispnEvent.id != null) return false;
        if (tags != null ? !tags.equals(ispnEvent.tags) : ispnEvent.tags != null) return false;
        return event != null ? event.equals(ispnEvent.event) : ispnEvent.event == null;
    }

    @Override
    public int hashCode() {
        int result = eventType != null ? eventType.hashCode() : 0;
        result = 31 * result + (tenantId != null ? tenantId.hashCode() : 0);
        result = 31 * result + (id != null ? id.hashCode() : 0);
        result = 31 * result + (tags != null ? tags.hashCode() : 0);
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
                ", event=" + event +
                '}';
    }
}
