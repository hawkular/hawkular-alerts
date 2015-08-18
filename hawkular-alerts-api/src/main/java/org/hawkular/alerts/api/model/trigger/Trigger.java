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
import java.util.UUID;

import org.hawkular.alerts.api.model.Severity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * A trigger definition.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class Trigger {

    @JsonInclude
    private String tenantId;

    /** Unique within the tenant */
    @JsonInclude
    private String id;

    /** For display */
    @JsonInclude
    private String name;

    @JsonInclude(Include.NON_EMPTY)
    protected Map<String, String> context;

    /** A map with key based on actionPlugin and value a set of action's ids */
    @JsonInclude(Include.NON_EMPTY)
    private Map<String, Set<String>> actions;

    /** Disable automatically after firing */
    @JsonInclude
    private boolean autoDisable;

    /** Enable automatically if disabled and resolved manually */
    @JsonInclude
    private boolean autoEnable;

    /** Switch to auto-resolve mode after firing */
    @JsonInclude
    private boolean autoResolve;

    /** Resolve all unresolved alerts when auto-resolve condition-set is satisfied */
    @JsonInclude
    private boolean autoResolveAlerts;

    @JsonInclude
    private Match autoResolveMatch;

    /** Is a child trigger of the specified triggerId */
    @JsonInclude(Include.NON_EMPTY)
    private String childOf;

    @JsonInclude(Include.NON_EMPTY)
    private String description;

    @JsonInclude
    private boolean enabled;

    @JsonInclude
    private Match firingMatch;

    /** Is a child trigger of the specified triggerId but currently orphaned (i.e. customized) */
    @JsonInclude
    private boolean orphan;

    /** Is a parent trigger, non-firing, used to manage a set of child triggers. */
    @JsonInclude
    private boolean parent;

    @JsonInclude
    private Severity severity;

    @JsonIgnore
    private Mode mode;

    @JsonIgnore
    private transient Match match;

    public Trigger() {
        /*
            Default constructor is needed for JSON libraries in JAX-RS context.
         */
        this("defaultTenant", "defaultName");
    }

    /**
     * This constructor requires the tenantId be assigned prior to persistence. It can be used when
     * creating triggers via Rest, as the tenant will be assigned automatically.
     * @param triggerId the triggerId, unique within the tenant.
     * @param name the trigger display name.
     */
    public Trigger(String triggerId, String name) {
        this(null, triggerId, name, null);
    }

    /**
     * This constructor requires the tenantId be assigned prior to persistence. It can be used when
     * creating triggers via Rest, as the tenant will be assigned automatically.
     * @param triggerId the triggerId, unique within the tenant.
     * @param name the trigger display name.
     * @param context optional context data to be stored with the trigger and assigned to its generated alerts
     */
    public Trigger(String triggerId, String name, Map<String, String> context) {
        this(null, triggerId, name, context);
    }

    public Trigger(String tenantId, String id, String name) {
        this(tenantId, id, name, null);
    }

    public Trigger(String tenantId, String id, String name, Map<String, String> context) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Trigger id must be non-empty");
        }
        this.tenantId = tenantId;
        this.id = id;
        this.name = name;
        this.context = context;

        this.actions = new HashMap<>();
        this.autoDisable = false;
        this.autoEnable = false;
        this.autoResolve = false;
        this.autoResolveAlerts = true;
        this.autoResolveMatch = Match.ALL;
        this.childOf = null;
        this.description = null;
        this.enabled = false;
        this.firingMatch = Match.ALL;
        this.orphan = false;
        this.parent = false;
        this.severity = Severity.MEDIUM;

        this.match = Match.ALL;
        this.mode = Mode.FIRING;
    }

    public static String generateId() {
        return UUID.randomUUID().toString();
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public Map<String, String> getContext() {
        return context;
    }

    public void setContext(Map<String, String> context) {
        this.context = context;
    }

    /**
     * Add context information.
     * @param name context key.
     * @param value context value.
     */
    public void addProperty(String name, String value) {
        if (null == name || null == value) {
            throw new IllegalArgumentException("Propety must have non-null name and value");
        }
        if (null == context) {
            context = new HashMap<>();
        }
        context.put(name, value);
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

    public Match getAutoResolveMatch() {
        return autoResolveMatch;
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

    public boolean isParent() {
        return parent;
    }

    public void setParent(boolean parent) {
        this.parent = parent;
    }

    public String getChildOf() {
        return childOf;
    }

    public void setChildOf(String childOf) {
        this.childOf = childOf;
    }

    @JsonIgnore
    public boolean isChild() {
        return null != childOf;
    }

    public boolean isOrphan() {
        return orphan;
    }

    public void setOrphan(boolean orphan) {
        this.orphan = orphan;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @JsonIgnore
    public boolean isLoadable() {
        return !parent && enabled;
    }

    @JsonIgnore
    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        setMatch(this.mode == Mode.FIRING ? getFiringMatch() : getAutoResolveMatch());
    }

    @JsonIgnore
    public Match getMatch() {
        return match;
    }

    public void setMatch(Match match) {
        this.match = match;
    }

    public void setFiringMatch(Match firingMatch) {
        this.firingMatch = firingMatch;
        setMatch(this.mode == Mode.FIRING ? getFiringMatch() : getAutoResolveMatch());
    }

    public void setAutoResolveMatch(Match autoResolveMatch) {
        this.autoResolveMatch = autoResolveMatch;
        setMatch(this.mode == Mode.FIRING ? getFiringMatch() : getAutoResolveMatch());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        Trigger trigger = (Trigger) o;

        if (id != null ? !id.equals(trigger.id) : trigger.id != null)
            return false;
        return !(tenantId != null ? !tenantId.equals(trigger.tenantId) : trigger.tenantId != null);

    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (tenantId != null ? tenantId.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Trigger [tenantId=" + tenantId + ", id=" + id + ", name=" + name + ", description=" + description
                + ", autoDisable=" + autoDisable + ", autoEnable=" + autoEnable + ", autoResolve=" + autoResolve
                + ", autoResolveAlerts=" + autoResolveAlerts + ", severity=" + severity + ", actions=" + actions
                + ", firingMatch=" + firingMatch + ", autoResolveMatch=" + autoResolveMatch + ", context=" + context
                + ", parent=" + parent + ", childOf=" + childOf + ", orphan=" + orphan + ", enabled=" + enabled
                + ", mode=" + mode + "]";
    }

}
