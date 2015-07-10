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
package org.hawkular.alerts.api.model.trigger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.api.model.Severity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * A base template for trigger definition.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public abstract class TriggerTemplate {

    public enum Match {
        ALL, ANY
    };

    @JsonInclude
    private String name;

    @JsonInclude
    private String description;

    @JsonInclude
    private boolean autoDisable;

    @JsonInclude
    private boolean autoEnable;

    @JsonInclude
    private boolean autoResolve;

    @JsonInclude
    private boolean autoResolveAlerts;

    @JsonInclude
    private Severity severity;

    /** A map with key based on actionPlugin and value a set of action's ids */
    @JsonInclude(Include.NON_EMPTY)
    private Map<String, Set<String>> actions;

    @JsonInclude
    private Match firingMatch;

    @JsonInclude
    private Match autoResolveMatch;

    public TriggerTemplate(String name) {
        this.name = name;

        this.autoDisable = false;
        this.autoEnable = false;
        this.autoResolve = false;
        this.autoResolveAlerts = true;
        this.severity = Severity.MEDIUM;
        this.firingMatch = Match.ALL;
        this.autoResolveMatch = Match.ALL;
        this.actions = new HashMap<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Trigger name must be non-empty.");
        }
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isAutoDisable() {
        return autoDisable;
    }

    public void setAutoDisable(boolean autoDisable) {
        this.autoDisable = autoDisable;
    }

    public boolean isAutoEnable() {
        return autoEnable;
    }

    public void setAutoEnable(boolean autoEnable) {
        this.autoEnable = autoEnable;
    }

    public boolean isAutoResolve() {
        return autoResolve;
    }

    public void setAutoResolve(boolean autoResolve) {
        this.autoResolve = autoResolve;
    }

    public boolean isAutoResolveAlerts() {
        return autoResolveAlerts;
    }

    public void setAutoResolveAlerts(boolean autoResolveAlerts) {
        this.autoResolveAlerts = autoResolveAlerts;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public Match getFiringMatch() {
        return firingMatch;
    }

    public void setFiringMatch(Match firingMatch) {
        this.firingMatch = firingMatch;
    }

    public Match getAutoResolveMatch() {
        return autoResolveMatch;
    }

    public void setAutoResolveMatch(Match autoResolveMatch) {
        this.autoResolveMatch = autoResolveMatch;
    }

    public Map<String, Set<String>> getActions() {
        return actions;
    }

    public void setActions(Map<String, Set<String>> actions) {
        this.actions = actions;
    }

    public void addAction(String actionPlugin, String actionId) {
        if (actionPlugin == null || actionPlugin.isEmpty()) {
            throw new IllegalArgumentException("ActionPlugin must be non-empty.");
        }
        if (actionId == null || actionId.isEmpty()) {
            throw new IllegalArgumentException("ActionId must be non-empty.");
        }
        if (actions.get(actionPlugin) == null) {
            actions.put(actionPlugin, new HashSet<>());
        }
        actions.get(actionPlugin).add(actionId);
    }

    public void addActions(String actionPlugin, Set<String> actionIds) {
        if (actionPlugin == null || actionPlugin.isEmpty()) {
            throw new IllegalArgumentException("ActionPlugin must be non-empty.");
        }
        if (actionIds == null) {
            throw new IllegalArgumentException("ActionIds must be non null");
        }
        if (actions.get(actionPlugin) == null) {
            actions.put(actionPlugin, new HashSet<>());
        }
        actions.get(actionPlugin).addAll(actionIds);
    }

    public void removeAction(String actionPlugin, String actionId) {
        if (actionPlugin == null || actionPlugin.isEmpty()) {
            throw new IllegalArgumentException("actionPlugin must be non-empty.");
        }
        if (actionId == null || actionId.isEmpty()) {
            throw new IllegalArgumentException("ActionId must be non-empty.");
        }
        if (actions.get(actionPlugin) != null) {
            actions.get(actionPlugin).remove(actionId);
        }
    }

    @Override
    public String toString() {
        return "TriggerTemplate [name=" + name + ", " +
                "description=" + description + ", " +
                "firingMatch=" + firingMatch + ", " +
                "safetyMatch=" + autoResolveMatch + "]";
    }

}
