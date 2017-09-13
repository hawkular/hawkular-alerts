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
package org.hawkular.alerts.api.model.trigger;

import static org.hawkular.alerts.api.util.Util.isEmpty;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.hawkular.alerts.api.doc.DocModel;
import org.hawkular.alerts.api.doc.DocModelProperty;
import org.hawkular.alerts.api.model.Severity;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.event.EventType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * A Trigger definition.  A Trigger can fire an Alert or an Event.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@DocModel(description = "A Trigger definition. + \n" +
        " + \n" +
        "A Trigger can fire an Alert or an Event. + \n" +
        " + \n" +
        "Triggers always start in FIRING mode. + \n" +
        " + \n" +
        "If the auto-resolve feature is enabled for the Trigger, then it will switch to AUTORESOLVE " +
        "mode after firing. + \n" +
        " + \n" +
        "When the auto-resolve condition set is satisfied, or if the Trigger is reloaded (manually, via edit, " +
        "or at startup), the trigger returns to FIRING mode. + \n" +
        " + \n" +
        "The mode is also needed when defining a trigger, to indicate the relevant mode for a conditions or " +
        "dampening definition.")
public class Trigger implements Serializable {

    private static final long serialVersionUID = 1L;

    @DocModelProperty(description = "Tenant id owner of this trigger.", position = 0, required = true, allowableValues = "Tenant is overwritten from Hawkular-Tenant HTTP header parameter request")
    @JsonInclude
    private String tenantId;

    /** Unique within the tenant */
    @DocModelProperty(description = "Trigger identifier. Unique within the tenant.", position = 1, required = true,
            defaultValue = "Auto-generated UUID if not explicitly defined.")
    @JsonInclude
    private String id;

    /** For display */
    @DocModelProperty(description = "Trigger name. Used for display.", position = 2, required = true)
    @JsonInclude
    private String name;

    @DocModelProperty(description = "Trigger description. Used for display.", position = 3)
    @JsonInclude(Include.NON_EMPTY)
    private String description;

    /** The type of trigger, standard, group, etc.. Defaults to TriggerType.STANDARD */
    @DocModelProperty(description = "The type of the trigger.", position = 4, defaultValue = "STANDARD")
    @JsonInclude
    private TriggerType type;

    /** The type of event produced by the trigger. Defaults to EventType.ALERT */
    @DocModelProperty(description = "The type of event produced by the trigger.", position = 5, defaultValue = "ALERT")
    @JsonInclude
    private EventType eventType;

    @DocModelProperty(description = "The category of the event produced by the trigger.", position = 6)
    @JsonInclude
    private String eventCategory;

    /** Defaults to the Trigger Description if not null, otherwise the trigger name. */
    @JsonInclude
    @DocModelProperty(description = "The text of the event produced by the trigger.", position = 7, defaultValue = "If not eventText defined. Description will be used. If not description defined, trigger name "
            +
            "will be used.")
    private String eventText;

    // Ignored for Event Triggers
    @DocModelProperty(description = "Severity of a trigger.", position = 8, defaultValue = "MEDIUM")
    @JsonInclude
    private Severity severity;

    @DocModelProperty(description = "Properties defined by the user for this trigger. Context is propagated " +
            "on generated Events/Alerts. Context cannot be used as criteria on finder methods.", position = 9)
    @JsonInclude(Include.NON_EMPTY)
    protected Map<String, String> context;

    @DocModelProperty(description = "Tags defined by the user for this trigger. A tag is a [name, value] pair." +
            "Tags can be used as criteria on finder methods. + \n" +
            "Tag value cannot be null.", position = 10)
    @JsonInclude(Include.NON_EMPTY)
    protected Map<String, String> tags;

    /** A list of links to actions represented by TriggerAction*/
    @DocModelProperty(description = "A list of links to actions.", position = 11)
    @JsonInclude(Include.NON_EMPTY)
    private Set<TriggerAction> actions;

    /** Disable automatically after firing */
    @DocModelProperty(description = "Disable automatically after firing.", position = 12, defaultValue = "false")
    @JsonInclude
    private boolean autoDisable;

    /** Enable automatically if disabled and resolved manually */
    @DocModelProperty(description = "Enable automatically if disabled and resolved manually.", position = 13, defaultValue = "false")
    @JsonInclude
    private boolean autoEnable;

    /** Switch to auto-resolve mode after firing */
    @DocModelProperty(description = "Switch to auto-resolve mode after firing.", position = 14, defaultValue = "false")
    @JsonInclude
    private boolean autoResolve;

    /** Resolve all unresolved alerts when auto-resolve condition-set is satisfied */
    @DocModelProperty(description = "Resolve all unresolved alerts when auto-resolve condition-set is satisfied.", position = 15, defaultValue = "false")
    @JsonInclude
    private boolean autoResolveAlerts;

    @DocModelProperty(description = "The policy used for deciding whether the trigger auto-resolved condition-set is " +
            "satisfied. ALL conditions must evaluate to true or ANY one condition must evaluate to true.", position = 16, defaultValue = "ALL")
    @JsonInclude
    private Match autoResolveMatch;

    // Only set for MEMBER triggers, the dataIdMap used when adding the member. Is re-used
    // for group condition updates unless a new dataIdMap is provided.
    @DocModelProperty(description = "Only set for MEMBER triggers, the dataIdMap used when adding the member. " +
            "It is reused for group condition updates unless a new dataIdMap is provided.", position = 17)
    @JsonInclude(Include.NON_EMPTY)
    protected Map<String, String> dataIdMap;

    // Only set for MEMBER triggers, the group trigger for which this is a member.
    @DocModelProperty(description = "Only set for MEMBER triggers, the group trigger for which this is a member.", position = 18)
    @JsonInclude(Include.NON_EMPTY)
    private String memberOf;

    @DocModelProperty(description = "A enabled trigger is loaded into the engine for data evaluation.", position = 19, defaultValue = "false")
    @JsonInclude
    private boolean enabled;

    @DocModelProperty(description = "The policy used for deciding whether the trigger condition-set is satisfied. " +
            "ALL conditions must evaluate to true or ANY one condition must evaluate to true.", position = 20, defaultValue = "ALL")
    @JsonInclude
    private Match firingMatch;

    @DocModelProperty(description = "Extended mechanism to match trigger conditions against Data with [source, dataId] " +
            "identifiers. In this way it is possible to qualify triggers and data with a source such that a trigger " +
            "only evaluates data having the same source.", position = 21)
    @JsonInclude
    String source;

    /** Used internally by the rules engine. Indicates current mode of a trigger: FIRING or AUTORESOLVE. */
    @JsonIgnore
    private Mode mode;

    /** Used internally by the rules engine. Indicates current match of a trigger: ALL or ANY. */
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
        this(tenantId, id, name, null, null);
    }

    public Trigger(String tenantId, String id, String name, Map<String, String> context) {
        this(tenantId, id, name, context, null);
    }

    public Trigger(Trigger trigger) {
        if (trigger == null) {
            throw new IllegalArgumentException("trigger must be not null");
        }
        this.tenantId = trigger.getTenantId();
        this.id = trigger.getId();
        this.name = trigger.getName();
        this.context = new HashMap<>(trigger.getContext());
        this.tags = new HashMap<>(trigger.getTags());
        this.actions = new HashSet<>();
        for (TriggerAction action : trigger.getActions()) {
            this.actions.add(new TriggerAction(action));
        }
        this.autoDisable = trigger.isAutoDisable();
        this.autoEnable = trigger.isAutoEnable();
        this.autoResolve = trigger.isAutoResolve();
        this.autoResolveAlerts = trigger.isAutoResolveAlerts();
        this.autoResolveMatch = trigger.getAutoResolveMatch();
        this.dataIdMap = new HashMap<>(trigger.getDataIdMap() != null ? trigger.getDataIdMap() : new HashMap<>());
        this.eventCategory = trigger.getEventCategory();
        this.eventText = trigger.getEventText();
        this.eventType = trigger.getEventType();
        this.memberOf = trigger.getMemberOf();
        this.description = trigger.getDescription();
        this.enabled = trigger.isEnabled();
        this.firingMatch = trigger.getFiringMatch();
        this.type = trigger.getType();
        this.source = trigger.getSource();
        this.severity = trigger.getSeverity();

        this.mode = trigger.getMode() != null ? trigger.getMode() : Mode.FIRING;
        this.match = trigger.getMode() == Mode.FIRING ? trigger.getFiringMatch() : trigger.getAutoResolveMatch();
    }

    public Trigger(String tenantId, String id, String name, Map<String, String> context, Map<String, String> tags) {
        if (isEmpty(id)) {
            throw new IllegalArgumentException("Trigger id must be non-empty");
        }
        this.tenantId = tenantId;
        this.id = id;
        this.name = name;
        this.context = context;
        this.tags = tags != null ? tags : new HashMap<>();

        this.actions = new HashSet<>();
        this.autoDisable = false;
        this.autoEnable = false;
        this.autoResolve = false;
        this.autoResolveAlerts = true;
        this.autoResolveMatch = Match.ALL;
        this.dataIdMap = null;
        this.eventCategory = null;
        this.eventText = null;
        this.eventType = EventType.ALERT; // Is this an OK default, or should it be a constructor parameter?
        this.memberOf = null;
        this.description = null;
        this.enabled = false;
        this.firingMatch = Match.ALL;
        this.type = TriggerType.STANDARD;
        this.source = Data.SOURCE_NONE;
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
        if (isEmpty(name)) {
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

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        if (eventType == null) {
            eventType = EventType.ALERT;
        }
        this.eventType = eventType;
    }

    public String getEventCategory() {
        return eventCategory;
    }

    public void setEventCategory(String eventCategory) {
        this.eventCategory = eventCategory;
    }

    public String getEventText() {
        return eventText;
    }

    public void setEventText(String eventText) {
        this.eventText = eventText;
    }

    public Map<String, String> getContext() {
        if (null == context) {
            context = new HashMap<>();
        }
        return context;
    }

    public void setContext(Map<String, String> context) {
        if (null == context) {
            context = new HashMap<>();
        }
        this.context = context;
    }

    public void addContext(String name, String value) {
        if (null == name || null == value) {
            throw new IllegalArgumentException("Context must have non-null name and value");
        }
        getContext().put(name, value);
    }

    public Map<String, String> getTags() {
        if (null == tags) {
            tags = new HashMap<>();
        }
        return tags;
    }

    public void setTags(Map<String, String> tags) {
        if (null == tags) {
            tags = new HashMap<>();
        }
        this.tags = tags;
    }

    public void addTag(String name, String value) {
        if (null == name || null == value) {
            throw new IllegalArgumentException("Tag must have non-null name and value");
        }
        getTags().put(name, value);
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
        if (severity == null) {
            severity = Severity.MEDIUM;
        }
        this.severity = severity;
    }

    public Match getFiringMatch() {
        return firingMatch;
    }

    public Match getAutoResolveMatch() {
        return autoResolveMatch;
    }

    public Set<TriggerAction> getActions() {
        if (actions == null) {
            actions = new HashSet<>();
        }
        return actions;
    }

    public void setActions(Set<TriggerAction> actions) {
        if (actions == null) {
            actions = new HashSet<>();
        }
        this.actions = actions;
    }

    public Map<String, String> getDataIdMap() {
        return dataIdMap;
    }

    public void setDataIdMap(Map<String, String> dataIdMap) {
        this.dataIdMap = dataIdMap;
    }

    public void addAction(TriggerAction triggerAction) {
        triggerAction.setTenantId(this.getTenantId());
        getActions().add(triggerAction);
    }

    public String getMemberOf() {
        return memberOf;
    }

    /**
     * The group trigger in which this is a member trigger.  A trigger can be one of group-level, member-level, or
     * neither.
     * @param memberOf If set, the group-level triggerId.
     */
    public void setMemberOf(String memberOf) {
        this.memberOf = memberOf;
    }

    @JsonIgnore
    public boolean isGroup() {
        switch (type) {
            case GROUP:
            case DATA_DRIVEN_GROUP:
                return true;
            default:
                return false;
        }
    }

    public TriggerType getType() {
        return type;
    }

    public void setType(TriggerType type) {
        if (null == type) {
            type = TriggerType.STANDARD;
        }
        this.type = type;
    }

    public String getSource() {
        return source;
    }

    /**
     * Typically set for DataDriven group triggers but can be set on any trigger to signify that the
     * trigger only operates on data from the specified data source.
     */
    public void setSource(String source) {
        if (null == source) {
            source = Data.SOURCE_NONE;
        }
        this.source = source;
    }

    @JsonIgnore
    public boolean isMember() {
        switch (type) {
            case MEMBER:
            case ORPHAN:
                return true;
            default:
                return false;
        }
    }

    @JsonIgnore
    public boolean isOrphan() {
        return type == TriggerType.ORPHAN;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @JsonIgnore
    public boolean isLoadable() {
        return !isGroup() && enabled;
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
        if (firingMatch == null) {
            firingMatch = Match.ALL;
        }
        this.firingMatch = firingMatch;
        setMatch(this.mode == Mode.FIRING ? getFiringMatch() : getAutoResolveMatch());
    }

    public void setAutoResolveMatch(Match autoResolveMatch) {
        if (autoResolveMatch == null) {
            autoResolveMatch = Match.ALL;
        }
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

    public boolean isSame(Trigger t) {
        if (this.equals(t) &&
                same(actions, t.actions) &&
                autoDisable == t.autoDisable &&
                autoEnable == t.autoEnable &&
                autoResolve == t.autoResolve &&
                autoResolveAlerts == t.autoResolveAlerts &&
                autoResolveMatch == t.autoResolveMatch &&
                same(context, t.context) &&
                same(description, t.description) &&
                enabled == t.enabled &&
                same(eventCategory, t.eventCategory) &&
                same(eventText, t.eventText) &&
                eventType == t.eventType &&
                firingMatch == t.firingMatch &&
                same(memberOf, t.memberOf) &&
                same(name, t.name) &&
                severity == t.severity &&
                same(source, t.source) &&
                same(tags, t.tags) &&
                type == t.type) {
            return true;
        }
        return false;
    }

    private boolean same(Object s1, Object s2) {
        return null == s1 ? null == s2 : s1.equals(s2);
    }

    @Override
    public String toString() {
        return "Trigger [tenantId=" + tenantId + ", id=" + id + ", type=" + type.name()
                + ", eventType=" + eventType.name() + ", name=" + name + ", description=" + description
                + ", eventCategory=" + eventCategory + ", eventText=" + eventText + ", severity=" + severity
                + ", context=" + context + ", actions=" + actions + ", autoDisable=" + autoDisable
                + ", autoEnable=" + autoEnable + ", autoResolve=" + autoResolve + ", autoResolveAlerts="
                + autoResolveAlerts + ", autoResolveMatch=" + autoResolveMatch + ", memberOf=" + memberOf
                + ", dataIdMap=" + dataIdMap + ", enabled=" + enabled + ", firingMatch=" + firingMatch
                + ", mode=" + mode + ", tags=" + tags + "]";
    }

}
