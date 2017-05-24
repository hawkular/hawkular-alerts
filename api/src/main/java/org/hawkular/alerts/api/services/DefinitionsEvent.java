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
package org.hawkular.alerts.api.services;

import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.api.model.action.ActionDefinition;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.trigger.Trigger;

/**
 * A DefinitionEvent represents a change on the Alerting backend.
 * It targets triggers, conditions, dampenings and actions granularity.
 *
 * {@code DefinitionEvent} are invoked via {@code DefinitionsListener} which are registered
 * via {@code DefinitionsService}.
 *
 * {@code DefinitionsListener} are invoked locally on the node which performs the definitions operation,
 * in distributed scenarios these events are not propagated and others nodes are not aware of the changes.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class DefinitionsEvent {

    public enum Type {
        ACTION_DEFINITION_CREATE,
        ACTION_DEFINITION_REMOVE,
        ACTION_DEFINITION_UPDATE,
        DAMPENING_CHANGE,
        TRIGGER_CONDITION_CHANGE,
        TRIGGER_CREATE,
        TRIGGER_REMOVE,
        TRIGGER_UPDATE
    };

    private Type type;
    private String targetTenantId;
    private String targetId;
    private Set<String> dataIds;
    private String actionPlugin;
    private ActionDefinition actionDefinition;
    private Map<String, String> tags;

    public DefinitionsEvent(Type type, ActionDefinition actionDefinition) {
        this(type, actionDefinition.getTenantId(), actionDefinition.getActionId(), null,
                actionDefinition.getActionPlugin(), actionDefinition, null);
    }

    public DefinitionsEvent(Type type, String targetTenantId, String targetActionPlugin, String targetActionId) {
        this(type, targetTenantId, targetActionId, null, targetActionPlugin, null, null);
    }

    public DefinitionsEvent(Type type, Dampening dampening) {
        this(type, dampening.getTenantId(), dampening.getDampeningId(), null, null, null, null);
    }

    public DefinitionsEvent(Type type, Trigger trigger) {
        this(type, trigger.getTenantId(), trigger.getId(), null, null, null, trigger.getTags());
    }

    public DefinitionsEvent(Type type, String targetTenantId, String targetId) {
        this(type, targetTenantId, targetId, null, null, null, null);
    }

    public DefinitionsEvent(Type type, String targetTenantId, String targetId, Map<String, String> tags) {
        this(type, targetTenantId, targetId, null, null, null, tags);
    }

    public DefinitionsEvent(Type type, String targetTenantId, String targetId, Set<String> dataIds) {
        this(type, targetTenantId, targetId, dataIds, null, null, null);
    }

    public DefinitionsEvent(Type type, String targetTenantId, String targetId, Set<String> dataIds,
                            String actionPlugin, ActionDefinition actionDefinition, Map<String, String> tags) {
        super();
        this.type = type;
        this.targetTenantId = targetTenantId;
        this.targetId = targetId;
        this.dataIds = dataIds;
        this.actionPlugin = actionPlugin;
        this.actionDefinition = actionDefinition;
        this.tags = tags;
    }

    public Type getType() {
        return type;
    }

    public String getTargetTenantId() {
        return targetTenantId;
    }

    public String getTargetId() {
        return targetId;
    }

    public Set<String> getDataIds() {
        return dataIds;
    }

    public String getActionPlugin() {
        return actionPlugin;
    }

    public void setActionPlugin(String actionPlugin) {
        this.actionPlugin = actionPlugin;
    }

    public ActionDefinition getActionDefinition() {
        return actionDefinition;
    }

    public void setActionDefinition(ActionDefinition actionDefinition) {
        this.actionDefinition = actionDefinition;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DefinitionsEvent that = (DefinitionsEvent) o;

        if (type != that.type) return false;
        if (targetTenantId != null ? !targetTenantId.equals(that.targetTenantId) : that.targetTenantId != null)
            return false;
        if (targetId != null ? !targetId.equals(that.targetId) : that.targetId != null) return false;
        if (dataIds != null ? !dataIds.equals(that.dataIds) : that.dataIds != null) return false;
        if (actionPlugin != null ? !actionPlugin.equals(that.actionPlugin) : that.actionPlugin != null) return false;
        if (actionDefinition != null ? !actionDefinition.equals(that.actionDefinition) : that.actionDefinition != null)
            return false;
        return tags != null ? tags.equals(that.tags) : that.tags == null;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (targetTenantId != null ? targetTenantId.hashCode() : 0);
        result = 31 * result + (targetId != null ? targetId.hashCode() : 0);
        result = 31 * result + (dataIds != null ? dataIds.hashCode() : 0);
        result = 31 * result + (actionPlugin != null ? actionPlugin.hashCode() : 0);
        result = 31 * result + (actionDefinition != null ? actionDefinition.hashCode() : 0);
        result = 31 * result + (tags != null ? tags.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DefinitionsEvent{" +
                "type=" + type +
                ", targetTenantId='" + targetTenantId + '\'' +
                ", targetId='" + targetId + '\'' +
                ", dataIds=" + dataIds +
                ", actionPlugin='" + actionPlugin + '\'' +
                ", actionDefinition=" + actionDefinition +
                ", tags=" + tags +
                '}';
    }
}
