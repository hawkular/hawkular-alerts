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

import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hibernate.search.annotations.Analyze;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.FieldBridge;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.Store;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Indexed(index = "trigger")
public class IspnTrigger implements Serializable {

    @Field(store = Store.YES, analyze = Analyze.NO)
    private String tenantId;

    @Field(store = Store.YES, analyze = Analyze.NO)
    private String triggerId;

    @Field(store = Store.YES, analyze = Analyze.NO)
    @FieldBridge(impl = TagsBridge.class)
    private Map<String, String> tags;

    @Field(store = Store.YES, analyze = Analyze.NO)
    private String memberOf;

    private Trigger trigger;

    public IspnTrigger() {
    }

    public IspnTrigger(Trigger trigger) {
        updateTrigger(trigger);
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

    public Trigger getTrigger() {
        return new Trigger(this.trigger);
    }

    public void setTrigger(Trigger trigger) {
        updateTrigger(trigger);
    }

    public String getTriggerId() {
        return triggerId;
    }

    public void setTriggerId(String triggerId) {
        this.triggerId = triggerId;
    }

    private void updateTrigger(Trigger trigger) {
        if (trigger == null) {
            throw new IllegalArgumentException("trigger must be not null");
        }
        this.trigger = new Trigger(trigger);
        this.tenantId = trigger.getTenantId();
        this.triggerId = trigger.getId();
        this.memberOf = trigger.getMemberOf();

        this.tags = this.trigger.getTags();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IspnTrigger that = (IspnTrigger) o;

        if (tenantId != null ? !tenantId.equals(that.tenantId) : that.tenantId != null) return false;
        if (triggerId != null ? !triggerId.equals(that.triggerId) : that.triggerId != null) return false;
        if (memberOf != null ? !memberOf.equals(that.memberOf) : that.memberOf != null) return false;
        if (tags != null ? !tags.equals(that.tags) : that.tags != null) return false;
        return trigger != null ? trigger.equals(that.trigger) : that.trigger == null;
    }

    @Override
    public int hashCode() {
        int result = tenantId != null ? tenantId.hashCode() : 0;
        result = 31 * result + (triggerId != null ? triggerId.hashCode() : 0);
        result = 31 * result + (memberOf != null ? memberOf.hashCode() : 0);
        result = 31 * result + (tags != null ? tags.hashCode() : 0);
        result = 31 * result + (trigger != null ? trigger.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "IspnTrigger{" +
                "tenantId='" + tenantId + '\'' +
                ", triggerId='" + triggerId + '\'' +
                ", tags=" + tags +
                ", trigger=" + trigger +
                '}';
    }
}
