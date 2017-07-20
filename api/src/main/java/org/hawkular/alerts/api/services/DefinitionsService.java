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

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.api.exception.NotFoundException;
import org.hawkular.alerts.api.model.action.ActionDefinition;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.export.Definitions;
import org.hawkular.alerts.api.model.export.ImportType;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.model.trigger.FullTrigger;
import org.hawkular.alerts.api.model.trigger.Mode;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.services.DefinitionsEvent.Type;

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
     * <p>
     * The preferred mechanism for creating a standard or group <code>Trigger</code>.
     * </p>
     * Create a new <code>FullTrigger</code>. A <code>FullTrigger</code> includes <code>Trigger, Conditions</code> and
     * <code>Dampenings</code>.  Set <code>FullTrigger.trigger.type</code> to <code>STANDARD</code> or
     * <code>GROUP</code> depending on the desired trigger type.  The new <code>Trigger</code> will be persisted.
     * If not set the triggerId will be set to a UUID.
     *
     * @param tenantId Tenant where trigger is created
     * @param fullTrigger New full trigger definition to be created
     * @throws Exception If the <code>Trigger</code> already exists.
     *
     */
    void createFullTrigger(String tenantId, FullTrigger fullTrigger) throws Exception;

    /**
     * Get a stored FullTrigger for a specific Tenant.
     * @param tenantId Tenant where trigger is stored
     * @param triggerId Given trigger to be retrieved
     * @throws NotFoundException if not found
     * @throws Exception on any problem
     */
    FullTrigger getFullTrigger(String tenantId, String triggerId) throws Exception;

    /**
     * <p>
     * The preferred mechanism for updating a standard or group <code>Trigger</code>.
     * </p>
     * Update a new <code>FullTrigger</code>. Unchanged parts of the <code>FullTrigger</code> are ignored.
     * <p>
     * Note: This service can not be uses to update a group trigger when introducing new dataIds into the condition
     * set.  That requires a new dataIdMap.  See {@link #setGroupConditions(String, String, Mode, Collection, Map)}.
     *
     * @param tenantId Tenant where trigger is created
     * @param fullTrigger New full trigger definition to be created
     * @throws Exception If the <code>Trigger</code> does not exist.
     */
    void updateFullTrigger(String tenantId, FullTrigger fullTrigger) throws Exception;

    /**
     * <p>
     * <code>createFullTrigger(String, Trigger)</code> is the preferred way to create a trigger.
     * </p>
     * Create a new <code>Trigger</code>. <code>Conditions</code> and <code>Dampening</code> are
     * manipulated in separate calls. The new <code>Trigger</code> will be persisted.  When fully defined a call to
     * {@link #updateTrigger(String, Trigger)} may be needed to enable the <code>Trigger</code>.
     *
     * @param tenantId Tenant where trigger is created
     * @param trigger New trigger definition to be added
     * @throws Exception If the <code>Trigger</code> already exists.
     * @see {@link #createFullTrigger(String, Trigger)} for the preferred way to create a trigger.
     * @see {@link #addGroupTrigger(String, Trigger)} for adding a group trigger.
     */
    void addTrigger(String tenantId, Trigger trigger) throws Exception;

    /**
     * <p>
     * <code>createFullTrigger(String, Trigger)</code> is the preferred way to create a trigger.
     * </p>
     * Create a new Group <code>Trigger</code>. <code>Conditions</code> and <code>Dampening</code> are
     * manipulated in separate calls. The new <code>Group Trigger</code> will be persisted.  When fully
     * defined a call to {@link #updateGroupTrigger(String, Trigger)} is needed to enable the <code>Trigger</code>.
     *
     * @param tenantId Tenant where trigger is created
     * @param groupTrigger New trigger definition to be added
     * @throws Exception If the <code>Trigger</code> already exists.
     * @see {@link #createFullTrigger(String, Trigger)} for the preferred way to create a trigger.
     * @see {@link #addTrigger(String, Trigger)} for adding a non-group trigger.
     */
    void addGroupTrigger(String tenantId, Trigger groupTrigger) throws Exception;

    /**
     * Generate a member trigger for the specified group trigger. See
     * {@link org.hawkular.alerts.api.json.MemberTrigger#setDataIdMap(Map)} for an example of the
     * <code>dataIdMap</code> parameter. The member trigger gets the enabled state of the group.
     * @param tenantId Tenant where trigger is stored
     * @param groupId Group triggerId from which to spawn the member trigger
     * @param memberId The member triggerId, unique id within the tenant, if null an Id will be generated
     * @param memberName The member triggerName, if null defaults to group trigger name
     * @param memberDescription The member description, if null defaults to group trigger description
     * @param memberContext Members inherit the group trigger context. If not null this adds additional, or
     *                      overrides existing, context entries.
     * @param memberTags Members inherit the group trigger tags. If not null this adds additional, or
     *                      overrides existing, tags.
     * @param dataIdMap Tokens to be replaced in the new trigger.
     * @return the member trigger
     * @throws Exception on any problem
     * @see {@link #addTrigger(String, Trigger)} for adding a non-group trigger.
     * @see {@link #addGroupTrigger(String, Trigger)} for adding a group trigger.
     * @see {@link org.hawkular.alerts.api.json.MemberTrigger#setDataIdMap(Map)} for an example of the
     * <code>dataIdMap</code> parameter.
     */
    Trigger addMemberTrigger(String tenantId, String groupId, String memberId, String memberName,
            String memberDescription, Map<String, String> memberContext, Map<String, String> memberTags,
            Map<String, String> dataIdMap)
            throws Exception;

    /**
     * Generate a member trigger for the specified data-driven group trigger.
     * @param tenantId Tenant where trigger is stored
     * @param groupId Group triggerId from which to spawn the member trigger
     * @param source the source for this member, no member should exist for this source already
     * @return the member trigger
     * @throws Exception on any problem
     */
    Trigger addDataDrivenMemberTrigger(String tenantId, String groupId, String source) throws Exception;

    /**
     * The <code>Trigger</code> will be removed from the Alerts engine, as needed, and will no longer be persisted.
     * This can be used to remove both standard or group member triggers but not a group trigger.
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
     * <code>keepNonOrphans</code> and <code>keepOrphans</code>. Note that any member triggers not removed will
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
     * <p>
     * A non-group trigger can never be made into a group trigger, and vice-versa.
     * </p>
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
     * </p><p>
     * A non-group trigger can never be made into a group trigger, and vice-versa.
     * </p>
     * @param tenantId Tenant where trigger is updated
     * @param groupTrigger Existing trigger to be updated
     * @throws NotFoundException if trigger is not found
     * @throws Exception on any problem
     * @see {@link #updateTrigger(String, Trigger)} for updating a non-group trigger.
     */
    Trigger updateGroupTrigger(String tenantId, Trigger groupTrigger) throws Exception;

    /**
     * Update and persist Group <code>Trigger</code> enablement state. The Alerts engine will be updated
     * with any changes to member <code>Trigger</code> enablement.
     * @param tenantId Tenant where trigger is updated
     * @param groupTriggerIds Comma-separated list of existing group triggerIds to be updated.
     * @param enabled The desired enablement state
     * @throws NotFoundException if a trigger is not found
     * @throws Exception on any problem
     * @see {@link #updateGroupTriggerEnablement(String, Trigger)} for updating a group trigger
     */
    void updateGroupTriggerEnablement(String tenantId, String groupTriggerIds, boolean enabled) throws Exception;

    /**
     * Update and persist <code>Trigger</code> enablement state. The Alerts engine will be updated
     * with any changes to <code>Trigger</code> enablement.
     * @param tenantId Tenant where trigger is updated
     * @param triggerIds Comma-separated list of existing triggerIds to be updated.
     * @param enabled The desired enablement state
     * @throws NotFoundException if a trigger is not found
     * @throws Exception on any problem
     * @see {@link #updateGroupTriggerEnablement(String, Trigger)} for updating a group trigger
     */
    void updateTriggerEnablement(String tenantId, String triggerIds, boolean enabled) throws Exception;

    /**
     * Get a stored Trigger for a specific Tenant.
     * @param tenantId Tenant where trigger is stored
     * @param triggerId Given trigger to be retrieved
     * @throws NotFoundException if not found
     * @throws Exception on any problem
     */
    Trigger getTrigger(String tenantId, String triggerId) throws Exception;

    /**
     * @param tenantId Tenant where triggers are stored
     * @param criteria If null returns all triggers for the tenant (not recommended)
     * @param pager Paging requirement for fetching triggers. Optional. Return all if null.
     * @return NotNull, can be empty.
     * @throws Exception any problem
     */
    Page<Trigger> getTriggers(String tenantId, TriggersCriteria criteria, Pager pager) throws Exception;

    /**
     * Get the member triggers for the specified group trigger.
     * @param tenantId Tenant for the group trigger
     * @param groupId Group triggerId
     * @param includeOrphans if true, include orphan triggers for the group
     * @throws Exception on any problem
     */
    Collection<Trigger> getMemberTriggers(String tenantId, String groupId, boolean includeOrphans) throws Exception;

    /**
     * Get all stored Triggers for all Tenants. Be careful.
     * @throws Exception on any problem
     */
    Collection<Trigger> getAllTriggers() throws Exception;

    /**
     * Get all stored Triggers for all Tenants with a specific Tag. This can be inefficient.
     * @param name The tag name, not null.
     * @param value The tag value, not null. Set to '*' to match all values for the name.
     * @throws Exception on any problem
     */
    Collection<Trigger> getAllTriggersByTag(String name, String value) throws Exception;

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
     * it may have been altered in various ways. So, as when spawning a new member trigger, the context, tags and
     * dataIdMap are specified. See {@link org.hawkular.alerts.api.json.MemberTrigger#setDataIdMap(Map)} for an example
     * of setting the <code>dataIdMap</code>.
     * <p>
     * This is basically a convenience method that first performs a {@link #removeTrigger(String, String)} and
     * then an {@link #addMemberTrigger(String, String, String, String, Map, Map)}. But the member trigger must
     * already exist for this call to succeed. The trigger will maintain the same group, id, name and description.
     * </p>
     * @param tenantId Tenant where trigger is stored
     * @param memberId The member triggerId
     * @param memberContext Context is reset to the group trigger context. If not null this adds additional, or
     *                      overrides existing, context entries.
     * @param memberTags Tags are reset to the group trigger tags. If not null this adds additional, or
     *                      overrides existing, tags.
     * @param dataIdMap Tokens to be replaced in the new trigger
     * @return the member trigger
     * @throws NotFoundException if trigger is not found
     * @throws Exception
     * @see {@link #orphanMemberTrigger(String, String)} for setting a member to be an orphan.
     * @see {@link org.hawkular.alerts.api.json.MemberTrigger#setDataIdMap(Map)} for an example
     * of setting the <code>dataIdMap</code>.
     */
    Trigger unorphanMemberTrigger(String tenantId, String memberId, Map<String, String> memberContext,
            Map<String, String> memberTags, Map<String, String> dataIdMap) throws Exception;

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
     * @return get all dampenings for all tenants. Be careful.
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
     * The condition set for a trigger's trigger mode is treated as a whole.  When making any change to the
     * conditions just [re-]set all of the conditions.  This method replaces all existing conditions (regardless
     * of trigger mode) with the provided set of new conditions. triggerMode is required to be set for each
     * provided condition.
     * <p>
     * IMPORTANT! The new condition set is returned. Clients code should then use the new condition set as
     * ConditionIds may have changed!
     * </p>
     * The following Condition fields are ignored for the incoming conditions, and set in the returned collection:
     * <pre>
     *   conditionId
     *   triggerId
     *   conditionSetSize
     *   conditionSetIndex
     * </pre>
     * @param tenantId Tenant where trigger and his conditions are stored
     * @param triggerId Trigger where conditions will be stored
     * @param conditions Not null, Not Empty
     * @return The persisted condition set
     * @throws Exception on any problem
     * @see {@link #removeGroupCondition(String, String)} to remove a group condition.
     * @see {@link #addGroupCondition(String, String, Mode, Condition, Map)} to add a group condition.
     */
    Collection<Condition> setAllConditions(String tenantId, String triggerId,
            Collection<Condition> conditions) throws Exception;

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

    /**
     * The condition set for the specified Group Trigger and trigger mode.  The conditionSet is ordered
     * using the Collection ordering (assuming an ordered Collection implementation is supplied).  Any existing
     * conditions are replaced. The non-orphan member triggers will have the new condition set applied, using the
     * provided dataIdMemberMap. See {@link org.hawkular.alerts.api.json.GroupConditionsInfo#setDataIdMemberMap(Map)}
     * for an example of the <code>dataIdMemberMap</code> parameter.
     * <p>
     * The <code>dataIdMemberMap</code> should be null if the group has no members.
     * </p>
     * <p>
     * The <code>dataIdMemberMap</code> should be null if this is a DataDriven group trigger.  In this
     * case the member triggers are removed and will be re-populated as incoming data demands.
     * </p>
     * <p>
     * For [non-data-driven] group triggers with existing members the <code>dataIdMemberMap</code> is handled
     * as follows. For members not included in the <code>dataIdMemberMap</code> their most recently supplied
     * dataIdMap will be used. This means that it is not necessary to supply mappings if the new condition set
     * uses only dataIds found in the old condition set. If the new conditions introduce new dataIds a full
     * <code>dataIdMemberMap</code> must be supplied.
     * </p>
     * <p>
     * The following Condition fields are ignored for the incoming conditions, and set in the returned collection set:
     * <pre>
     *   conditionId
     *   triggerId
     *   triggerMode
     *   conditionSetSize
     *   conditionSetIndex
     * </pre>
     * </p>
     * @param tenantId Tenant where trigger is stored
     * @param groupId Group Trigger adding the condition and whose non-orphan members will also have it added.
     * @param triggerMode Mode where condition is applied
     * @param groupCondition Not null, the condition to add
     * @param dataIdMemberMap see above for details. Null if the group trigger has no members.
     * @return The updated, persisted condition set for the group
     * @throws NotFoundException if trigger is not found
     * @throws Exception on any problem
     * @see {@link #addCondition(String, String, Mode, Condition)} for non-group conditions.
     * @see {@link org.hawkular.alerts.api.json.MemberCondition#setDataIdMemberMap(Map)} for an example of the
     * <code>dataIdMemberMap</code> parameter.
     */
    Collection<Condition> setGroupConditions(String tenantId, String groupId, Mode triggerMode,
            Collection<Condition> groupConditions, Map<String, Map<String, String>> dataIdMemberMap) throws Exception;


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

    /**
     * @return returns all conditions for all tenants. Be careful.
     * @throws Exception on any problem
     */
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
     * @throws Exception on any problem
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
     * Create a new ActionDefinition
     *
     * @param tenantId Tenant where actions are stored
     * @param actionDefinition the ActionDefinition object to add
     * @throws Exception on any problem
     */
    void addActionDefinition(String tenantId, ActionDefinition actionDefinition) throws Exception;

    void removeActionDefinition(String tenantId, String actionPlugin, String actionId) throws Exception;

    void updateActionDefinition(String tenantId, ActionDefinition actionDefinition) throws Exception;

    /**
     * @return Map where key is a tenantId and value is a Map with actionPlugin as key and a set of actionsId as value
     * @throws Exception on any problem
     */
    Map<String, Map<String, Set<String>>> getAllActionDefinitionIds() throws Exception;

    /**
     * Get all action definitions configured in the system.
     *
     * @return The existing action definitions stored in the system. Not null.
     * @throws Exception on any problem
     */
    Collection<ActionDefinition> getAllActionDefinitions() throws Exception;

    /**
     * @param tenantId Tenant where actions are stored.
     * @return Map where key represents an actionPlugin and value a Set of actionsId per actionPlugin
     * @throws Exception on any problem
     */
    Map<String, Set<String>> getActionDefinitionIds(String tenantId) throws Exception;

    Collection<String> getActionDefinitionIds(String tenantId, String actionPlugin) throws Exception;

    ActionDefinition getActionDefinition(String tenantId, String actionPlugin, String actionId) throws Exception;

    void registerListener(DefinitionsListener listener, Type eventType, Type... eventTypes);

    /**
     * Export alert definitions per a specific Tenant.
     * Alert definitions are wrapped in a Definitions object, which is a collection of FullTrigger (a Trigger
     * with Dampenings and Conditions) objects and a collection of ActionDefinition objects.
     *
     * @param tenantId Tenant where definitions are stored
     * @return Definitions object with full triggers and actions definitions specified by tenantId
     * @throws Exception on any problem
     */
    Definitions exportDefinitions(String tenantId) throws Exception;

    /**
     * Import alert definitions per a specific Tenant.
     * Alert definitions are wrapped in an Definitions object, which contains a collection of FullTrigger (a
     * Trigger with Dampenings and Conditions) objects and a collection of ActionDefinition objects.
     * An ImportType stragy must be defined to resolve conflicts with existing data.
     *
     * @param tenantId Tenant where definitions will be imported
     * @param definitions Definitions with the collections of FullTrigger and ActionDefinition to import
     * @param strategy the ImportType strategy to apply
     * @return an ActionDefinition object updated with the effective FullTrigger and ActionDefinition imported
     * @throws Exception on any problem
     */
    Definitions importDefinitions(String tenantId, Definitions definitions, ImportType strategy)
            throws Exception;

    void registerDistributedListener(DistributedListener listener);
}
