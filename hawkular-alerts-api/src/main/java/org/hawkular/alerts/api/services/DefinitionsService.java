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
package org.hawkular.alerts.api.services;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.trigger.Mode;
import org.hawkular.alerts.api.model.trigger.Tag;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.services.DefinitionsEvent.EventType;

/**
 * A interface used to create new triggers, conditions and init new notifiers.
 *
 * Implementation should manage the persistence of the definitions.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public interface DefinitionsService {

    /*
        CRUD interface for Trigger
     */

    /**
     * Create a new <code>Trigger</code>. <code>Conditions</code> and <code>Dampening</code> are
     * manipulated in separate calls. The new <code>Trigger</code> will be persisted.  When fully defined a call to
     * {@link #updateTrigger(String, Trigger)} is needed to enable the <code>Trigger</code>.
     * <p>
     * Parent triggers must have <code>parent=true</code> at create time. A parent trigger can never be
     * made a non-parent, and vice-versa.
     * </p>
     * @param tenantId Tenant where trigger is created
     * @param trigger New trigger definition to be added
     * @throws Exception If the <code>Trigger</code> already exists.
     */
    void addTrigger(String tenantId, Trigger trigger) throws Exception;

    /**
     * The <code>Trigger</code> will be removed from the Alerts engine, as needed, and will no longer be persisted.
     * <p>
     * Parent triggers will also have all child triggers removed (including orphans).  To remove a parent trigger
     * while leaving behind child triggers use {@link #removeParentTrigger(String, String, boolean, boolean)}.
     * </p>
     * @param tenantId Tenant where trigger is stored
     * @param triggerId Trigger to be removed
     * @throws Exception on any problem
     */
    void removeTrigger(String tenantId, String triggerId) throws Exception;

    /**
     * The parent <code>Trigger</code> will be removed from the Alerts engine, as needed, and will no longer be
     * persisted. The child triggers will be removed as well, depending on the settings for
     * <code>leaveChildren</code> and <code>leaveOrphans</code>. Note that any child triggers not removed will
     * no longer have a parent trigger associated and will then need to be managed independently.
     * @param tenantId Tenant where trigger is stored
     * @param triggerId Parent Trigger to be removed.
     * @param keepChildren If true the non-orphan child triggers for the parent are saved.
     * @param keepOrphans If true the orphan child triggers for the parent are saved.
     * @throws Exception on any problem
     */
    void removeParentTrigger(String tenantId, String parentId, boolean keepChildren, boolean keepOrphans)
            throws Exception;

    /**
     * Update the <code>Trigger</code>. <code>Conditions</code> and <code>Dampening</code> are
     * manipulated in separate calls. The updated <code>Trigger</code> will be persisted.  If enabled the
     * <code>Trigger</code> will be [re-]inserted into the Alerts engine and any prior dampening will be reset.
     * <p>
     * Parent triggers will also have all non-orphan child triggers similarly updated.
     * </p>
     * @param tenantId Tenant where trigger is updated
     * @param trigger Existing trigger to be updated
     * @throws Exception If the <code>Trigger</code> does not exist.
     */
    Trigger updateTrigger(String tenantId, Trigger trigger) throws Exception;

    /**
     * Get a stored Trigger for a specific Tenant.
     * @param tenantId Tenant where trigger is stored
     * @param triggerId Given trigger to be retrieved
     * @throws Exception on any problem
     */
    Trigger getTrigger(String tenantId, String triggerId) throws Exception;

    /**
     * Get all stored Triggers for a specific Tenant.
     * @param tenantId Tenant where triggers are stored
     * @throws Exception on any problem
     */
    Collection<Trigger> getTriggers(String tenantId) throws Exception;

    /**
     * Get all stored Triggers with a specific Tag. Category and Name can not both be null.
     * @param tenantId Tenant where trigger is stored
     * @param category The tag category, if null or empty fetch only by tag name
     * @param name The tag name, if null or empty fetch only by tag category
     * @throws Exception on any problem
     */
    Collection<Trigger> getTriggersByTag(String tenantId, String category, String name) throws Exception;

    /**
     * Get the child triggers for the specified parent trigger.
     * @param tenantId Tenant for the parent trigger
     * @param parentId Parent triggerId
     * @param includeOrphans if true, include orphan child triggers for the parent
     * @throws Exception on any problem
     */
    Collection<Trigger> getChildTriggers(String tenantId, String parentId, boolean includeOrphans) throws Exception;

    /**
     * Get all stored Triggers for all Tenants
     * @throws Exception on any problem
     */
    Collection<Trigger> getAllTriggers() throws Exception;

    /**
     * Get all stored Triggers for all Tenants with a specific Tag. This can be inefficient, especially if
     * querying by name only.
     * @param category The tag category, if null or empty fetch only by tag name
     * @param name The tag name, required
     * @throws Exception on any problem
     */
    Collection<Trigger> getAllTriggersByTag(String category, String name) throws Exception;

    /**
     * Generate a child trigger for the specified parent trigger. The dataIdMap replaces the tokens in the
     * parent trigger's conditions with actual dataIds. The child trigger gets the enabled state of the parent.
     * @param tenantId Tenant where trigger is stored
     * @param parentId Parent triggerId from which to spawn the child trigger
     * @param childId The child triggerId, unique id within the tenant, if null an Id will be generated
     * @param childName The child triggerName, not null, unique name within the tenant
     * @param childContext The child triggerContext. If null the context is inherited from the parent trigger
     * @param dataIdMap Tokens to be replaced in the new trigger
     * @return the child trigger
     * @throws Exception on any problem
     */
    Trigger addChildTrigger(String tenantId, String parentId, String childId, String childName,
            Map<String, String> childContext, Map<String, String> dataIdMap) throws Exception;

    /**
     * Orphan a child trigger.  The child trigger will no longer inherit parent updates.  It will be allowed
     * to be independently updated.  It does maintain its parent reference and can again be tied to the
     * parent via a call to {@link #unorphanChildTrigger(String, String, Map, Map)}.
     * @param tenantId Tenant where trigger is stored
     * @param childId The child triggerId
     * @param childContext The child triggerContext. If null the context is inherited from the parent trigger
     * @param dataIdMap Tokens to be replaced in the new trigger
     * @return the child trigger
     * @throws Exception
     */
    Trigger orphanChildTrigger(String tenantId, String childId) throws Exception;

    /**
     * Un-orphan a child trigger.  The child trigger is again synchronized with the parent definition. As an orphan
     * it may have been altered in various ways. So, as when spawning a new child trigger, the context and dataIdMap
     * are specified.
     * <p>
     * This is basically a convenience method that first performs a {@link #removeTrigger(String, String)} and
     * then an {@link #addChildTrigger(String, String, String, String, Map, Map)}. But the child trigger must
     * already exist for this call to succeed. The trigger will maintain the same parent, id, and name.
     * </p>
     * @param tenantId Tenant where trigger is stored
     * @param childId The child triggerId
     * @param childContext The child triggerContext. If null the context is inherited from the parent trigger
     * @param dataIdMap Tokens to be replaced in the new trigger
     * @return the child trigger
     * @throws Exception
     */
    Trigger unorphanChildTrigger(String tenantId, String childId, Map<String, String> childContext,
            Map<String, String> dataIdMap) throws Exception;



    /*
        CRUD interface for Dampening
     */

    /**
     * Add the <code>Dampening</code>. The relevant triggerId is specified in the Dampening object.
     * <p>
     * Parent triggers will apply the dampening to their spawned children.
     * </p>
     * @param tenantId the owning tenant
     * @param dampening the Dampening definition, which should be tied to a trigger
     * @return the new Dampening
     * @throws Exception
     */
    Dampening addDampening(String tenantId, Dampening dampening) throws Exception;

    /**
     * Remove the specified <code>Dampening</code> from the relevant trigger.
     * <p>
     * Parent triggers will remove the dampening from their non-orphan children.
     * </p>
     * @param tenantId the owning tenant
     * @param dampeningId the doomed dampening  record
     * @throws Exception
     */
    void removeDampening(String tenantId, String dampeningId) throws Exception;

    /**
     * Update the <code>Dampening</code> on the relevant trigger.
     * <p>
     * Parent triggers will update the dampening on their non-orphan children.
     * </p>
     * @param tenantId the owning tenant
     * @param dampening the Dampening definition, which should be tied to a trigger
     * @return the new Dampening
     * @throws Exception
     */
    Dampening updateDampening(String tenantId, Dampening dampening) throws Exception;

    Dampening getDampening(String tenantId, String dampeningId) throws Exception;

    /**
     * @param tenantId Tenant where trigger is stored
     * @param triggerId Given trigger
     * @param triggerMode Return only dampenings for the given trigger mode. Return all if null.
     * @return The existing dampenings for the trigger. Not null.
     * @throws Exception on any problem
     */
    Collection<Dampening> getTriggerDampenings(String tenantId, String triggerId, Mode triggerMode)
            throws Exception;

    /**
     * @return The existing dampenings stored under a tenant
     * @throws Exception on any problem
     */
    Collection<Dampening> getAllDampenings() throws Exception;

    /**
     * @param tenantId Tenant where dampening are stored
     * @return The existing dampenings stored under a tenant
     * @throws Exception on any problem
     */
    Collection<Dampening> getDampenings(String tenantId) throws Exception;

    /*
        CRUD interface for Condition
     */

    /**
     * A convenience method that adds a new Condition to the existing condition set for the specified
     * Trigger and trigger mode.  The new condition will be assigned the highest conditionSetIndex for the
     * updated conditionSet.
     * <p>
     * IMPORTANT! Add/Delete/Update of a condition effectively replaces the condition set for the trigger.  The new
     * condition set is returned. Clients code should then use the new condition set as ConditionIds may have changed!
     * </p>
     * The following Condition fields are ignored for the incoming condition, and set in the returned collection set:
     * <pre>
     *   conditionId
     *   triggerId
     *   triggerMode
     *   conditionSetSize
     *   conditionSetIndex
     * </pre>
     * <p>
     * Parent triggers will add the condition to their non-orphan children.
     * </p>
     * @param tenantId Tenant where trigger is stored
     * @param triggerId Trigger where condition will be stored
     * @param triggerMode Mode where condition is applied
     * @param condition Not null
     * @return The updated, persisted condition set
     * @throws Exception on any problem
     */
    Collection<Condition> addCondition(String tenantId, String triggerId, Mode triggerMode, Condition condition)
            throws Exception;

    /**
     * A convenience method that removes a Condition from an existing condition set.
     * <p>
     * IMPORTANT! Add/Delete/Update of a condition effectively replaces the condition set for the trigger.  The new
     * condition set is returned. Clients code should then use the new condition set as ConditionIds may have changed!
     * </p>
     * <p>
     * Parent triggers will remove the condition from their non-orphan children.
     * </p>
     * @param tenantId Tenant where trigger and his conditions are stored
     * @param conditionId Condition id to be removed
     * @return The updated, persisted condition set. Not null. Can be empty.
     * @throws Exception on any problem
     */
    Collection<Condition> removeCondition(String tenantId, String conditionId) throws Exception;

    /**
     * A convenience method that updates an existing Condition from an existing condition set.
     * <p>
     * IMPORTANT! Add/Delete/Update of a condition effectively replaces the condition set for the trigger.  The new
     * condition set is returned. Clients code should then use the new condition set as ConditionIds may have changed!
     * </p>
     * <p>
     * Parent triggers will update the condition on their non-orphan children.
     * </p>
     * @param tenantId
     * @param condition Not null. conditionId must be for an existing condition.
     * @return The updated, persisted condition set. Not null. Can be empty.
     * @throws Exception on any problem
     */
    Collection<Condition> updateCondition(String tenantId, Condition condition) throws Exception;

    /**
     * The condition set for a trigger's trigger mode is treated as a whole.  When making any change to the
     * conditions just [re-]set all of the conditions.
     * <p>
     * IMPORTANT! Add/Delete/Update of a condition effectively replaces the condition set for the trigger.  The new
     * condition set is returned. Clients code should then use the new condition set as ConditionIds may have changed!
     * </p>
     * The following Condition fields are ignored for the incoming conditions, and set in the returned collection:
     * <pre>
     *   conditionId
     *   triggerId
     *   triggerMode
     *   conditionSetSize
     *   conditionSetIndex
     * </pre>
     * <p>
     * Parent triggers will set the conditions on their non-orphan children.
     * </p>
     * @param tenantId Tenant where trigger and his conditions are stored
     * @param triggerId Trigger where conditions will be stored
     * @param triggerMode Mode where conditions are applied
     * @param conditions Not null, Not Empty
     * @return The persisted condition set
     * @throws Exception on any problem
     */
    Collection<Condition> setConditions(String tenantId, String triggerId, Mode triggerMode,
            Collection<Condition> conditions) throws Exception;

    Condition getCondition(String tenantId, String conditionId) throws Exception;

    /**
     * @param tenantId Tenant where trigger and his conditions are stored
     * @param triggerId Trigger where conditions are stored
     * @param triggerMode Return only conditions for the given trigger mode. Return all if null.
     * @return The existing conditions for the trigger. Not null.
     * @throws Exception on any problem
     */
    Collection<Condition> getTriggerConditions(String tenantId, String triggerId, Mode triggerMode)
            throws Exception;

    Collection<Condition> getConditions(String tenantId) throws Exception;

    Collection<Condition> getAllConditions() throws Exception;

    /*
        An action plugin is a representation of an action capability.
        i.e. email, snmp or sms.
        It will have a set of specific properties to fill per a specific action definition.

        Action plugin should be responsible to register an action type before to send actions.

        ActionPlugin API will be useful in the UI to help to define new actions.
        i.e. querying for properties to fill for a specific action type.
     */

    /**
     * Add a new Plugin into the definitions service.
     * This operation is invoked mainly on a registration plugin phase.
     * A new plugin is deployed into hawkular and it registers his name and properties name.
     * Properties have to be filled when a new action is created.
     *
     * @param actionPlugin name of the plugin
     * @param properties list of properties to be configured in actions of this plugins type
     * @throws Exception on any problem
     */
    void addActionPlugin(String actionPlugin, Set<String> properties) throws Exception;

    /*
        An action plugin can define default properties at plugin level.
     */

    /**
     * Add a new Plugin into definitions service.
     * This operation is invoked mainly on a registration plugin phase.
     * A new plugin is deployed into hawkular and it register his name, his properties and also defines default
     * values for those properties.
     * Properties are overwritten per action but default values are used in case some property is not present.
     *
     * @param actionPlugin name of the plugin
     * @param defaultProperties map of properties with its default values
     * @throws Exception on any problem
     */
    void addActionPlugin(String actionPlugin, Map<String, String> defaultProperties)
        throws Exception;

    /**
     * Remove an existing Plugin from the definitions service.
     *
     * @param actionPlugin name of the plugin
     * @throws Exception on any problem
     */
    void removeActionPlugin(String actionPlugin) throws Exception;

    /**
     * Update an existing plugin.
     * This operation changes the properties needed for a plugin.
     *
     * @param actionPlugin name of the plugin
     * @param properties list of properties to be configured in actions of this plugins type
     * @throws Exception on any problem
     */
    void updateActionPlugin(String actionPlugin, Set<String> properties) throws Exception;

    /**
     * Update an existing plugin.
     * This operation changes the properties needed for a plugin and its default values.
     *
     * @param actionPlugin name of the plugin
     * @param defaultProperties map of properties with its default values
     * @throws Exception on any problem
     */
    void updateActionPlugin(String actionPlugin, Map<String, String> defaultProperties)
        throws Exception;

    /**
     * Get all list of plugins configured on the system.
     *
     * @return List of plugins configured on the definitions service
     * @throws Exception on an problem
     */
    Collection<String> getActionPlugins() throws Exception;

    /**
     * Get list of properties needed to configure an action for a specific plugin
     *
     * @param actionPlugin name of the plugin
     * @return list of properties to be configured
     * @throws Exception on any problem
     */
    Set<String> getActionPlugin(String actionPlugin) throws Exception;

    /**
     * Get a map with the properties needed to configure an action with its default values
     *
     * @param actionPlugin name of the plugin
     * @return map of properties with its default values
     * @throws Exception on any problem
     */
    Map<String, String> getDefaultActionPlugin(String actionPlugin) throws Exception;

    /*
        An action is a representation of specific tasks to be executed by action plugins.
        i.e. email to admin@mycompany.com.
             send a specific TRAP with specific details.
             send a SMS mobile to an admin number.
     */

    /**
     * Create a new Action
     *
     * @param tenantId Tenant where actions are stored
     * @param actionPlugin Action plugin where this action is stored
     * @param actionId Id of new action
     * @param properties the properties of the action
     * @throws Exception on any problem
     */
    void addAction(String tenantId, String actionPlugin, String actionId, Map<String, String> properties)
            throws Exception;

    void removeAction(String tenantId, String actionPlugin, String actionId) throws Exception;

    void updateAction(String tenantId, String actionPlugin, String actionId, Map<String, String> properties)
            throws Exception;

    /**
     * @return Map where key is a tenantId and value is a Map with actionPlugin as key and a set of actionsId as value
     * @throws Exception on any problem
     */
    Map<String, Map<String, Set<String>>> getAllActions() throws Exception;

    /**
     * @param tenantId Tenant where actions are stored.
     * @return Map where key represents an actionPlugin and value a Set of actionsId per actionPlugin
     * @throws Exception on any problem
     */
    Map<String, Set<String>> getActions(String tenantId) throws Exception;

    Collection<String> getActions(String tenantId, String actionPlugin) throws Exception;

    Map<String, String> getAction(String tenantId, String actionPlugin, String actionId) throws Exception;

    /*
    CRUD interface for Tag
    */

    /**
     * Add Tag to the specified Trigger (tenantId+triggerid). Category is optional but highly recommended for
     * efficiency and to avoid unwanted name collisions. If the Tag exists the call returns successfully but
     * has no effect.
     * @param tenantId Tenant where tag is created
     * @param tag New tag to be created
     * @throws Exception on any problem
     */
    void addTag(String tenantId, Tag tag) throws Exception;

    /**
     * Delete tag(s) for the specified trigger, optionally filtered by category and/or name.
     * @param triggerId NotEmpty
     * @param category Nullable
     * @param name Nullable
     * @throws Exception on any problem
     */
    void removeTags(String tenantId, String triggerId, String category, String name) throws Exception;

    /**
     * @param tenantId NotEmpty, must be the proper tenant for the specified trigger.
     * @param triggerId NotEmpty
     * @param category Nullable.
     * @return The existing Tags for the trigger, optionally filtered by category. Sorted by category, name.
     * @throws Exception on any problem
     */
    List<Tag> getTriggerTags(String tenantId, String triggerId, String category) throws Exception;

    void registerListener(DefinitionsListener listener, EventType eventType, EventType... eventTypes);
}
