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
        CRUD interface for Triggers
     */

    /**
     * Create a new <code>Trigger</code>. <code>Conditions</code> and <code>Dampening</code> are
     * manipulated in separate calls. The new <code>Trigger</code> will be persisted.  When fully defined a call to
     * {@link #updateTrigger(String, Trigger)} is needed to enable the <code>Trigger</code>.
     * <p>
     * A non-group trigger can never be made into a group trigger, and vice-versa.
     * </p>
     * @param tenantId Tenant where trigger is created
     * @param trigger New trigger definition to be added
     * @throws Exception If the <code>Trigger</code> already exists.
     * @see {@link #addGroupTrigger(String, Trigger)} for adding a group trigger.
     */
    void addTrigger(String tenantId, Trigger trigger) throws Exception;

    /**
     * Create a new Group <code>Trigger</code>. <code>Conditions</code> and <code>Dampening</code> are
     * manipulated in separate calls. The new <code>Group Trigger</code> will be persisted.  When fully
     * defined a call to {@link #updateGroupTrigger(String, Trigger)} is needed to enable the <code>Trigger</code>.
     * <p>
     * A non-group trigger can never be made into a group trigger, and vice-versa.
     * </p>
     * @param tenantId Tenant where trigger is created
     * @param groupTrigger New trigger definition to be added
     * @throws Exception If the <code>Trigger</code> already exists.
     * @see {@link #addTrigger(String, Trigger)} for adding a non-group trigger.
     */
    void addGroupTrigger(String tenantId, Trigger groupTrigger) throws Exception;

    /**
     * Generate a member trigger for the specified group trigger. The dataIdMap replaces the tokens in the
     * group trigger's conditions with actual dataIds. The member trigger gets the enabled state of the group.
     * @param tenantId Tenant where trigger is stored
     * @param groupId Group triggerId from which to spawn the member trigger
     * @param memberId The member triggerId, unique id within the tenant, if null an Id will be generated
     * @param memberName The member triggerName, not null, unique name within the tenant
     * @param memberContext The member triggerContext. If null the context is inherited from the group trigger
     * @param dataIdMap Tokens to be replaced in the new trigger
     * @return the member trigger
     * @throws Exception on any problem
     * @see {@link #addTrigger(String, Trigger)} for adding a non-group trigger.
     * @see {@link #addGroupTrigger(String, Trigger)} for adding a group trigger.
     */
    Trigger addMemberTrigger(String tenantId, String groupId, String memberId, String memberName,
            Map<String, String> memberContext, Map<String, String> dataIdMap) throws Exception;

    /**
     * The <code>Trigger</code> will be removed from the Alerts engine, as needed, and will no longer be persisted.
     * @param tenantId Tenant where trigger is stored
     * @param triggerId Trigger to be removed
     * @throws NotFoundException if trigger is not found
     * @throws Exception on any problem
     * @see {@link #removeGroupTrigger(String, String, boolean, boolean)} for removing a group trigger and its members.
     */
    void removeTrigger(String tenantId, String triggerId) throws Exception;

    /**
     * The group <code>Trigger</code> will be removed from the Alerts engine, as needed, and will no longer be
     * persisted. The member triggers will be removed as well, depending on the settings for
     * <code>leaveMember</code> and <code>leaveOrphans</code>. Note that any member triggers not removed will
     * no longer have a group trigger associated and will then need to be managed independently.
     * @param tenantId Tenant where trigger is stored
     * @param groupId Group Trigger to be removed.
     * @param keepNonOrphans If true the non-orphan member are maintained and made independent.
     * @param keepOrphans If true the orphan member triggers are maintained and made independent.
     * @throws NotFoundException if trigger is not found
     * @throws Exception on any problem
     * @see {@link #removeTrigger(String, String)} for removing a non-group trigger
     */
    void removeGroupTrigger(String tenantId, String groupId, boolean keepNonOrphans, boolean keepOrphans)
            throws Exception;

    /**
     * Update the <code>Trigger</code>. <code>Conditions</code> and <code>Dampening</code> are
     * manipulated in separate calls. The updated <code>Trigger</code> will be persisted.  If enabled the
     * <code>Trigger</code> will be [re-]inserted into the Alerts engine and any prior dampening will be reset.
     * @param tenantId Tenant where trigger is updated
     * @param trigger Existing trigger to be updated
     * @throws NotFoundException if trigger is not found
     * @throws Exception on any problem
     * @see {@link #updateGroupTrigger(String, Trigger)} for updating a group trigger
     */
    Trigger updateTrigger(String tenantId, Trigger trigger) throws Exception;

    /**
     * Update the group <code>Trigger</code>. <code>Conditions</code> and <code>Dampening</code> are
     * manipulated in separate calls. The updated <code>Trigger</code> will be persisted.  If enabled the
     * <code>Trigger</code> will be [re-]inserted into the Alerts engine and any prior dampening will be reset.
     * <p>
     * The group's non-orphan member triggers will be similarly updated.
     * </p>
     * @param tenantId Tenant where trigger is updated
     * @param groupTrigger Existing trigger to be updated
     * @throws NotFoundException if trigger is not found
     * @throws Exception on any problem
     * @see {@link #updateTrigger(String, Trigger)} for updating a non-group trigger.
     */
    Trigger updateGroupTrigger(String tenantId, Trigger groupTrigger) throws Exception;

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
     * Get the member triggers for the specified group trigger.
     * @param tenantId Tenant for the group trigger
     * @param groupId Group triggerId
     * @param includeOrphans if true, include orphan triggers for the group
     * @throws Exception on any problem
     */
    Collection<Trigger> getMemberTriggers(String tenantId, String groupId, boolean includeOrphans) throws Exception;

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
     * Orphan a member trigger.  The member trigger will no longer inherit group updates.  It will be allowed
     * to be independently updated.  It does maintain its group reference and can again be tied to the
     * group via a call to {@link #unorphanMemberTrigger(String, String, Map, Map)}.
     * @param tenantId Tenant where trigger is stored
     * @param memberId The member triggerId
     * @return the member trigger
     * @throws NotFoundException if trigger is not found
     * @throws Exception
     * @see {@link #unorphanMemberTrigger(String, String, Map, Map)} to again have the trigger be a full group member.
     */
    Trigger orphanMemberTrigger(String tenantId, String memberId) throws Exception;

    /**
     * Un-orphan a member trigger.  The member trigger is again synchronized with the group definition. As an orphan
     * it may have been altered in various ways. So, as when spawning a new member trigger, the context and dataIdMap
     * are specified.
     * <p>
     * This is basically a convenience method that first performs a {@link #removeTrigger(String, String)} and
     * then an {@link #addMemberTrigger(String, String, String, String, Map, Map)}. But the member trigger must
     * already exist for this call to succeed. The trigger will maintain the same group, id, and name.
     * </p>
     * @param tenantId Tenant where trigger is stored
     * @param memberId The member triggerId
     * @param memberContext The member triggerContext. If null the context is inherited from the member trigger
     * @param dataIdMap Tokens to be replaced in the new trigger
     * @return the member trigger
     * @throws NotFoundException if trigger is not found
     * @throws Exception
     * @see {@link #orphanMemberTrigger(String, String)} for setting a member to be an orphan.
     */
    Trigger unorphanMemberTrigger(String tenantId, String memberId, Map<String, String> memberContext,
            Map<String, String> dataIdMap) throws Exception;

    /*
        CRUD interface for Dampening
     */

    /**
     * Add the <code>Dampening</code>. The relevant triggerId is specified in the Dampening object.
     * @param tenantId the owning tenant
     * @param dampening the Dampening definition, which should be tied to a trigger
     * @return the new Dampening
     * @throws Exception
     * @see {@link #addGroupDampening(String, Dampening)} for adding group-level dampening
     */
    Dampening addDampening(String tenantId, Dampening dampening) throws Exception;

    /**
     * Add the <code>Dampening</code>. The relevant triggerId is specified in the Dampening object.
     * <p>
     * The group's non-orphan member triggers will be similarly updated.
     * </p>
     * @param tenantId the owning tenant
     * @param groupDampening the Dampening definition, which should be tied to a trigger
     * @return the new Dampening
     * @throws Exception
     * @see {@link #addDampening(String, Dampening)} for adding non-group dampening.
     */
    Dampening addGroupDampening(String tenantId, Dampening groupDampening) throws Exception;

    /**
     * Remove the specified <code>Dampening</code> from the relevant trigger.
     * @param tenantId the owning tenant
     * @param dampeningId the doomed dampening  record
     * @throws Exception
     * @see {@link #removeGroupDampening(String, String)} for removing group-level dampening.
     */
    void removeDampening(String tenantId, String dampeningId) throws Exception;

    /**
     * Remove the specified <code>Dampening</code> from the relevant group trigger.
     * <p>
     * The group's non-orphan member triggers will be similarly updated.
     * </p>
     * @param tenantId the owning tenant
     * @param groupDampeningId the doomed dampening record for the group trigger
     * @throws Exception
     * @see {@link #removeDampening(String, String)} for removing non-group dampening.
     */
    void removeGroupDampening(String tenantId, String groupDampeningId) throws Exception;

    /**
     * Update the <code>Dampening</code> on the relevant trigger.
     * @param tenantId the owning tenant
     * @param dampening the Dampening definition, which should be tied to a trigger
     * @return the new Dampening
     * @throws Exception
     * @see {@link #updateGroupDampening(String, Dampening)} for group-level dampening.
     */
    Dampening updateDampening(String tenantId, Dampening dampening) throws Exception;

    /**
     * Update the <code>Dampening</code> on the relevant group trigger.
     * <p>
     * The group's non-orphan member triggers will be similarly updated.
     * </p>
     * @param tenantId the owning tenant
     * @param groupDampening the Dampening definition, which should be tied to a group trigger
     * @return the new Dampening
     * @throws Exception
     * @see {@link #updateDampening(String, Dampening)} for non-group dampening.
     */
    Dampening updateGroupDampening(String tenantId, Dampening groupDampening) throws Exception;

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
     * @param tenantId Tenant where trigger is stored
     * @param triggerId Trigger where condition will be stored
     * @param triggerMode Mode where condition is applied
     * @param condition Not null
     * @return The updated, persisted condition set
     * @throws NotFoundException if trigger is not found
     * @throws Exception on any problem
     * @see {@link #addGroupCondition(String, String, Mode, Condition, Map)} for group-level conditions.
     */
    Collection<Condition> addCondition(String tenantId, String triggerId, Mode triggerMode, Condition condition)
            throws Exception;

    /**
     * A convenience method that adds a new Condition to the existing condition set for the specified
     * Group Trigger and trigger mode.  The new condition will be assigned the highest conditionSetIndex for the
     * updated conditionSet.  The non-orphan member triggers will have the new condition applied, using the
     * provided dataIdMemberMap.  The dataIdMemberMap has the form <code>Map&LTString, Map&LTString,String&GT&GT</code>.
     * The keys are the dataId tokens in the new condition, The values are themselves Maps with one entry
     * per member trigger.  The value Map key is a memberId. The value is the dataId to be used for that member, for
     * the relevant dataId token. This map will usually have 1 entry but because a condition could have multiple
     * dataIds (e.g CompareCondition), it may have multiple entries.
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
     * @param tenantId Tenant where trigger is stored
     * @param groupId Group Trigger adding the condition and whose non-orphan members will also have it added.
     * @param triggerMode Mode where condition is applied
     * @param groupCondition Not null, the condition to add
     * @param dataIdMemberMap see above for details.
     * @return The updated, persisted condition set for the group
     * @throws NotFoundException if trigger is not found
     * @throws Exception on any problem
     * @see {@link #addCondition(String, String, Mode, Condition)} for non-group conditions.
     */
    Collection<Condition> addGroupCondition(String tenantId, String groupId, Mode triggerMode,
            Condition groupCondition, Map<String, Map<String, String>> dataIdMemberMap) throws Exception;

    /**
     * A convenience method that removes a Condition from an existing condition set.
     * <p>
     * IMPORTANT! Add/Delete/Update of a condition effectively replaces the condition set for the trigger.  The new
     * condition set is returned. Clients code should then use the new condition set as ConditionIds may have changed!
     * </p>
     * @param tenantId Tenant where trigger and his conditions are stored
     * @param conditionId Condition id to be removed
     * @return The updated, persisted condition set. Not null. Can be empty.
     * @throws Exception on any problem
     * @see {@link #removeGroupCondition(String, String)} for group-level conditions.
     */
    Collection<Condition> removeCondition(String tenantId, String conditionId) throws Exception;

    /**
     * A convenience method that removes a Condition from an existing condition set.
     * <p>
     * The group's non-orphan member triggers will be similarly updated.
     * </p>
     * <p>
     * IMPORTANT! Add/Delete/Update of a condition effectively replaces the condition set for the trigger.  The new
     * condition set is returned. Clients code should then use the new condition set as ConditionIds may have changed!
     * </p>
     * @param tenantId Tenant where trigger and his conditions are stored
     * @param groupConditionId Condition id to be removed from the group trigger
     * @return The updated, persisted condition set. Not null. Can be empty.
     * @throws Exception on any problem
     * @see {@link #removeCondition(String, String)} for non-group conditions.
     */
    Collection<Condition> removeGroupCondition(String tenantId, String groupConditionId) throws Exception;

    /**
     * A convenience method that updates an existing condition.
     * <p>
     * IMPORTANT! Add/Delete/Update of a condition effectively replaces the condition set for the trigger.  The new
     * condition set is returned. Clients code should then use the new condition set as ConditionIds may have changed!
     * </p>
     * @param tenantId
     * @param condition Not null. conditionId must be for an existing condition.
     * @return The updated, persisted condition set. Not null. Can be empty.
     * @throws Exception on any problem
     * @see {@link #updateGroupCondition(String, Condition)} for group-level conditions.
     */
    Collection<Condition> updateCondition(String tenantId, Condition condition) throws Exception;

    /**
     * A convenience method that updates an existing group condition.
     * <p>
     * The group's non-orphan member triggers will be similarly updated.
     * </p>
     * <p>
     * IMPORTANT! Add/Delete/Update of a condition effectively replaces the condition set for the trigger.  The new
     * condition set is returned. Clients code should then use the new condition set as ConditionIds may have changed!
     * </p>
     * @param tenantId
     * @param groupCondition Not null. conditionId must be for an existing condition.
     * @return The updated, persisted condition set. Not null. Can be empty.
     * @throws Exception on any problem
     * @see {@link #updateCondition(String, Condition)} for non-group conditions.
     */
    Collection<Condition> updateGroupCondition(String tenantId, Condition groupCondition) throws Exception;

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
     * Note that due to the complexity of adding group-level conditions, it is only supported to
     * add or remove a single group condition at one time.  So there is no <code>setGroupConditions</code>.
     * Instead, use {@link #addGroupCondition(String, String, Mode, Condition, Map)} and
     * {@link #removeGroupCondition(String, String)} as needed.
     * @param tenantId Tenant where trigger and his conditions are stored
     * @param triggerId Trigger where conditions will be stored
     * @param triggerMode Mode where conditions are applied
     * @param conditions Not null, Not Empty
     * @return The persisted condition set
     * @throws Exception on any problem
     * @see {@link #removeGroupCondition(String, String)} to remove a group condition.
     * @see {@link #addGroupCondition(String, String, Mode, Condition, Map)} to add a group condition.
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
     * @see {@link #addGroupTag(String, Tag)} for group-level tags.
     */
    void addTag(String tenantId, Tag tag) throws Exception;

    /**
     * Add Tag to the group Trigger and its members. Category is optional but highly recommended for
     * efficiency and to avoid unwanted name collisions. If the Tag exists the call returns successfully but
     * has no effect.
     * @param tenantId Tenant where tag is created
     * @param groupTag New tag to be created
     * @throws Exception on any problem
     * @see {@link #addTag(String, Tag)} for non-group level tags.
     */
    void addGroupTag(String tenantId, Tag groupTag) throws Exception;

    /**
     * Delete tag(s) for the specified trigger, optionally filtered by category and/or name.
     * @param triggerId NotEmpty
     * @param category Nullable
     * @param name Nullable
     * @throws Exception on any problem
     * @see {@link #removeGroupTags(String, String, String, String)} for group-level tags.
     */
    void removeTags(String tenantId, String triggerId, String category, String name) throws Exception;

    /**
     * Delete tag(s) for the specified group trigger, optionally filtered by category and/or name.
     * @param groupId NotEmpty The group triggerid.
     * @param category Nullable
     * @param name Nullable
     * @throws Exception on any problem
     * @see {@link #removeTags(String, String, String, String)} for non-group-level tags.
     */
    void removeGroupTags(String tenantId, String groupId, String category, String name) throws Exception;

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
