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
package org.hawkular.alerts.api.model.action;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * An action is the abstract concept of a consequence of an event.
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
@ApiModel(description = "An action represents a consequence of an event. + \n" +
        " + \n" +
        "Actions are processed by plugins, and plugins offer a map of properties to personalize an action. + \n" +
        "An ActionDefinition stores which properties will be used for a specific action in a specific plugin. + \n" +
        " + \n" +
        "A Trigger definition can be assigned a list of action definitions. + \n" +
        " + \n" +
        "The alert engine will instantiate a specific Action based on its ActionDefinition. + \n" +
        " + \n" +
        "An ActionDefinition can add default constraints to determine when an action will be performed. + \n" +
        " + \n" +
        "<<TriggerAction>> can override the default constraints. + \n" +
        "-- States constraint: a set of Alert.Status (represented by its string value). + \n" +
        "The action is limited to the specified states.  By default the action applies to all Alert states. + \n" +
        "Unlike Alerts, Events don't have lifecycle. All TriggerActions are applied at Event creation time. + \n" +
        " + \n" +
        "-- Calendar constraint: A <<TimeConstraint>>. + \n" +
        "The action is applied only when the event create time occurs during the specified time intervals, + \n" +
        "absolute or relative, as defined. By default the action can be performed at any time. + \n" +
        " + \n" +
        "If a <<TriggerAction>> defines any constraints the <<ActionDefinition>> constraints will be ignored. + \n" +
        "If a <<TriggerAction>> defines no constraints the <<ActionDefinition>> constraints will be used. + \n")
public class ActionDefinition implements Serializable {

    @ApiModelProperty(value = "Tenant id owner of this trigger.",
            position = 0,
            allowableValues = "Tenant is overwritten from Hawkular-Tenant HTTP header parameter request")
    @JsonInclude
    private String tenantId;

    @ApiModelProperty(value = "Action plugin identifier.",
            position = 1,
            required = true,
            allowableValues = "Only plugins deployed on the system are valid.")
    @JsonInclude
    private String actionPlugin;

    @ApiModelProperty(value = "Action definition identifier.",
            position = 2,
            required = true)
    @JsonInclude
    private String actionId;

    @ApiModelProperty(value = "Flag to indicate this is a global action.",
            position = 3,
            required = false)
    @JsonInclude
    private boolean global;

    @ApiModelProperty(value = "Plugin properties. Each plugin defines its own specific properties that can be " +
            "supplied at action definition level.",
            position = 4,
            required = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, String> properties;

    @ApiModelProperty(value = "A list of Alert.Status restricting active states for this action. <<TriggerAction>> " +
            "constraints take precedence, if defined",
            position = 5,
            allowableValues = "OPEN, ACKNOWLEDGED, RESOLVED")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Set<String> states;

    @ApiModelProperty(value = "A TimeConstraint restricting active times for this action. <<TriggerAction>> " +
            "constraints take precedence, if defined.",
            position = 6)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private TimeConstraint calendar;

    public ActionDefinition() {
        this(null, null, null, false, new HashMap<>(), null, null);
    }

    public ActionDefinition(String tenantId, String actionPlugin, String actionId) {
        this(tenantId, actionPlugin, actionId, false, new HashMap<>(), null, null);
    }

    public ActionDefinition(String tenantId, String actionPlugin, String actionId,
                            Map<String, String> properties) {
        this(tenantId, actionPlugin, actionId, false, properties, null, null);
    }

    public ActionDefinition(ActionDefinition actionDefinition) {
        if (actionDefinition == null) {
            throw new IllegalArgumentException("actionDefinition must be not null");
        }
        this.tenantId = actionDefinition.getTenantId();
        this.actionPlugin = actionDefinition.getActionPlugin();
        this.actionId = actionDefinition.getActionId();
        this.global = actionDefinition.isGlobal();
        this.properties = actionDefinition.getProperties() != null ? new HashMap<>(actionDefinition.getProperties())
                : new HashMap<>();
        this.states = new HashSet<>(actionDefinition.getStates());
        this.calendar = actionDefinition.getCalendar() != null ? new TimeConstraint(actionDefinition.getCalendar())
                : null;
    }

    public ActionDefinition(String tenantId, String actionPlugin, String actionId, boolean global,
                            Map<String, String> properties, Set<String> states, TimeConstraint calendar) {
        this.tenantId = tenantId;
        this.actionPlugin = actionPlugin;
        this.actionId = actionId;
        this.global = global;
        this.properties = properties != null ? new HashMap<>(properties) : new HashMap<>();
        this.states = states != null ? new HashSet<>(states) : new HashSet<>();
        this.calendar = calendar;
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

    public boolean isGlobal() {
        return global;
    }

    public void setGlobal(boolean global) {
        this.global = global;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public Set<String> getStates() {
        if (states == null) {
            states = new HashSet<>();
        }
        return states;
    }

    public void setStates(Set<String> states) {
        this.states = states;
    }

    public void addState(String state) {
        getStates().add(state);
    }

    public TimeConstraint getCalendar() {
        return calendar;
    }

    public void setCalendar(TimeConstraint calendar) {
        this.calendar = calendar;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ActionDefinition that = (ActionDefinition) o;

        if (global != that.global) return false;
        if (tenantId != null ? !tenantId.equals(that.tenantId) : that.tenantId != null) return false;
        if (actionPlugin != null ? !actionPlugin.equals(that.actionPlugin) : that.actionPlugin != null) return false;
        if (actionId != null ? !actionId.equals(that.actionId) : that.actionId != null) return false;
        if (properties != null ? !properties.equals(that.properties) : that.properties != null) return false;
        if (states != null ? !states.equals(that.states) : that.states != null) return false;
        return calendar != null ? calendar.equals(that.calendar) : that.calendar == null;
    }

    @Override
    public int hashCode() {
        int result = tenantId != null ? tenantId.hashCode() : 0;
        result = 31 * result + (actionPlugin != null ? actionPlugin.hashCode() : 0);
        result = 31 * result + (actionId != null ? actionId.hashCode() : 0);
        result = 31 * result + (global ? 1 : 0);
        result = 31 * result + (properties != null ? properties.hashCode() : 0);
        result = 31 * result + (states != null ? states.hashCode() : 0);
        result = 31 * result + (calendar != null ? calendar.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ActionDefinition{" +
                "tenantId='" + tenantId + '\'' +
                ", actionPlugin='" + actionPlugin + '\'' +
                ", actionId='" + actionId + '\'' +
                ", global=" + global +
                ", properties=" + properties +
                ", states=" + states +
                ", calendar=" + calendar +
                '}';
    }
}
