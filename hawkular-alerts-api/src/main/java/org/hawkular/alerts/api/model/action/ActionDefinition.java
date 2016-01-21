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
package org.hawkular.alerts.api.model.action;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * An action is the abstract concept of ta consequence of an event.
 *
 * Actions are processed by plugins, and plugins offer a map of properties to personalize an action.
 * An ActionDefinition stores which properties will be used for a specific action in a specific plugin.
 *
 * A Trigger definition can be linked with a list of action definitions.
 *
 * Alert engine will instantiate a specific Action based on its ActionDefinition.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class ActionDefinition implements Serializable {

    @JsonInclude
    private String tenantId;

    @JsonInclude
    private String actionPlugin;

    @JsonInclude
    private String actionId;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, String> properties;

    public ActionDefinition() {
    }

    public ActionDefinition(String tenantId, String actionPlugin, String actionId) {
        this(tenantId, actionPlugin, actionId, Collections.EMPTY_MAP);
    }

    public ActionDefinition(String tenantId, String actionPlugin, String actionId,
                            Map<String, String> properties) {
        this.tenantId = tenantId;
        this.actionPlugin = actionPlugin;
        this.actionId = actionId;
        this.properties = new HashMap<>(properties);
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getActionPlugin() {
        return actionPlugin;
    }

    public void setActionPlugin(String actionPlugin) {
        this.actionPlugin = actionPlugin;
    }

    public String getActionId() {
        return actionId;
    }

    public void setActionId(String actionId) {
        this.actionId = actionId;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ActionDefinition that = (ActionDefinition) o;

        if (tenantId != null ? !tenantId.equals(that.tenantId) : that.tenantId != null) return false;
        if (actionPlugin != null ? !actionPlugin.equals(that.actionPlugin) : that.actionPlugin != null) return false;
        if (actionId != null ? !actionId.equals(that.actionId) : that.actionId != null) return false;
        return properties != null ? properties.equals(that.properties) : that.properties == null;

    }

    @Override
    public int hashCode() {
        int result = tenantId != null ? tenantId.hashCode() : 0;
        result = 31 * result + (actionPlugin != null ? actionPlugin.hashCode() : 0);
        result = 31 * result + (actionId != null ? actionId.hashCode() : 0);
        result = 31 * result + (properties != null ? properties.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ActionDefinition" + '[' +
                "tenantId='" + tenantId + '\'' +
                ", actionPlugin='" + actionPlugin + '\'' +
                ", actionId='" + actionId + '\'' +
                ", properties=" + properties +
                ']';
    }
}
