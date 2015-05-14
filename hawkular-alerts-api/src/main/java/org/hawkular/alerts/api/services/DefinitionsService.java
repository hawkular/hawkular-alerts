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
import org.hawkular.alerts.api.model.trigger.Tag;
import org.hawkular.alerts.api.model.trigger.Trigger;

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
     * Create a new <code>Trigger</code>.  <code>Conditions</code> and <code>Actions</code> are manipulated in separate
     * calls. The new <code>Trigger</code> will be persisted.  When fully defined a call to
     * {@link #updateTrigger(String, Trigger)}
     * is needed to enable the <code>Trigger</code>.
     * @param tenantId Tenant where trigger is created
     * @param trigger New trigger definition to be added
     * @throws Exception If the <code>Trigger</code> already exists.
     */
    void addTrigger(String tenantId, Trigger trigger) throws Exception;

    /**
     * The <code>Trigger</code> will be removed from the Alerts engine, as needed, and will no longer be persisted.
     * @param tenantId Tenant where trigger is stored
     * @param triggerId Trigger to be removed
     * @throws Exception on any problem
     */
    void removeTrigger(String tenantId, String triggerId) throws Exception;

    /**
     * Update the <code>Trigger</code>. <code>Conditions</code> and <code>Actions</code> are manipulated in separate
     * calls. The updated <code>Trigger</code> will be persisted.  If enabled the <code>Trigger</code>
     * will be [re-]inserted into the Alerts engine and any prior dampening will be reset.
     * @param tenantId Tenant where trigger is updated
     * @param trigger Existing trigger to be updated
     * @throws Exception If the <code>Trigger</code> does not exist.
     */
    Trigger updateTrigger(String tenantId, Trigger trigger) throws Exception;

    /**
     * Get a stored Trigger for a specific Tenant.
     * @param tenantId Tenant where trigger is stored
     * @param triggerId Given trigger to be retrieved
     * @throws Exception
     */
    Trigger getTrigger(String tenantId, String triggerId) throws Exception;

    /**
     * Get all stored Triggers for a specific Tenant.
     * @param tenantId Tenant where triggers are stored
     * @throws Exception
     */
    Collection<Trigger> getTriggers(String tenantId) throws Exception;

    /**
     * Get all stored Triggers for all Tenants
     * @throws Exception
     */
    Collection<Trigger> getAllTriggers() throws Exception;


    /**
     * Used to generate an explicit Trigger from a Tokenized Trigger.  The dataIdMap replaces the tokens in the
     * Conditions with actual dataIds.
     * @param tenantId Tenant where trigger is stored
     * @param triggerId Trigger to be copied
     * @param dataIdMap Tokens to be replaced in the new trigger
     * @return a copy of original trigger
     * @throws Exception on any problem
     */
    Trigger copyTrigger(String tenantId, String triggerId, Map<String, String> dataIdMap) throws Exception;

    /*
        CRUD interface for Dampening
     */

    Dampening addDampening(String tenantId, Dampening dampening) throws Exception;

    void removeDampening(String tenantId, String dampeningId) throws Exception;

    Dampening updateDampening(String tenantId, Dampening dampening) throws Exception;

    Dampening getDampening(String tenantId, String dampeningId) throws Exception;

    /**
     * @param tenantId Tenant where trigger is stored
     * @param triggerId Given trigger
     * @param triggerMode Return only dampenings for the given trigger mode. Return all if null.
     * @return The existing dampenings for the trigger. Not null.
     * @throws Exception on any problem
     */
    Collection<Dampening> getTriggerDampenings(String tenantId, String triggerId, Trigger.Mode triggerMode)
            throws Exception;

    /**
     * @return The existing dampenings stored under a tenant
     * @throws Exception
     */
    Collection<Dampening> getAllDampenings() throws Exception;

    /**
     * @param tenantId Tenant where dampening are stored
     * @return The existing dampenings stored under a tenant
     * @throws Exception
     */
    Collection<Dampening> getDampenings(String tenantId) throws Exception;


    /*
        CRUD interface for Condition
     */

    /**
     * A convenience method that adds a new Condition to the existing condition set for the specified
     * Trigger and trigger mode.  The new condition will be assigned the highest conditionSetIndex for the
     * updated conditionSet. The following Condition fields are ignored for the
     * incoming condition, and set in the returned collection set:<pre>
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
     * @throws Exception on any problem
     */
    Collection<Condition> addCondition(String tenantId, String triggerId, Trigger.Mode triggerMode, Condition condition)
            throws Exception;

    /**
     * A convenience method that removes a Condition from an existing condition set. This will update the
     * conditionSetSize and possibly the conditionSetIndex for any remaining conditions.
     * @param tenantId Tenant where trigger and his conditions are stored
     * @param conditionId Condition id to be removed
     * @return The updated, persisted condition set. Not null. Can be empty.
     * @throws Exception on any problem
     */
    Collection<Condition> removeCondition(String tenantId, String conditionId) throws Exception;

    /**
     * A convenience method that updates an existing Condition from an existing condition set.
     * @param tenantId
     * @param condition Not null. conditionId must be for an existing condition.
     * @return The updated, persisted condition set. Not null. Can be empty.
     * @throws Exception on any problem
     */
    Collection<Condition> updateCondition(String tenantId, Condition condition) throws Exception;

    /**
     * The condition set for a trigger's trigger mode is treated as a whole.  When making any change to the
     * conditions just [re-]set all of the conditions. The following Condition fields are ignored for the
     * incoming conditions, and set in the returned collection:<pre>
     *   conditionId
     *   triggerId
     *   triggerMode
     *   conditionSetSize
     *   conditionSetIndex
     * </pre>
     * @param tenantId Tenant where trigger and his conditions are stored
     * @param triggerId Trigger where conditions will be stored
     * @param triggerMode Mode where conditions are applied
     * @param conditions Not null, Not Empty
     * @return The persisted condition set
     * @throws Exception on any problem
     */
    Collection<Condition> setConditions(String tenantId, String triggerId, Trigger.Mode triggerMode,
            Collection<Condition> conditions) throws Exception;

    Condition getCondition(String tenantId, String conditionId) throws Exception;

    /**
     * @param tenantId Tenant where trigger and his conditions are stored
     * @param triggerId Trigger where conditions are stored
     * @param triggerMode Return only conditions for the given trigger mode. Return all if null.
     * @return The existing conditions for the trigger. Not null.
     * @throws Exception on any problem
     */
    Collection<Condition> getTriggerConditions(String tenantId, String triggerId, Trigger.Mode triggerMode)
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
    void addActionPlugin(String actionPlugin, Set<String> properties) throws Exception;

    void removeActionPlugin(String actionPlugin) throws Exception;

    void updateActionPlugin(String actionPlugin, Set<String> properties) throws Exception;

    Collection<String> getActionPlugins() throws Exception;

    Set<String> getActionPlugin(String actionPlugin) throws Exception;

    /*
        An action is a representation of specific tasks to be executed by action plugins.
        i.e. email to admin@mycompany.com.
             send a specific TRAP with specific details.
             send a SMS mobile to an admin number.
     */

    /**
     * Create a new Action.
     *
     * @param tenantId Tenant where actions are stored
     * @param actionPlugin Action plugin where this action is stored
     * @param actionId Id of new action
     * @param properties the properties of the action
     * @throws Exception
     */
    void addAction(String tenantId, String actionPlugin, String actionId, Map<String, String> properties)
            throws Exception;

    void removeAction(String tenantId, String actionPlugin, String actionId) throws Exception;

    void updateAction(String tenantId, String actionPlugin, String actionId, Map<String, String> properties)
            throws Exception;

    /**
     * @return Map where key is a tenantId and value is a Map with actionPlugin as key and a set of actionsId as value
     * @throws Exception
     */
    Map<String, Map<String, Set<String>>> getAllActions() throws Exception;

    /**
     * @param tenantId Tenant where actions are stored.
     * @return Map where key represents an actionPlugin and value a Set of actionsId per actionPlugin
     * @throws Exception
     */
    Map<String, Set<String>> getActions(String tenantId) throws Exception;

    Collection<String> getActions(String tenantId, String actionPlugin) throws Exception;

    Map<String, String> getAction(String tenantId, String actionPlugin, String actionId) throws Exception;

    /*
    CRUD interface for Tag
    */

    /**
     * Add Tag with the specified name to the specified Trigger. Category is optional. If the Tag exists the
     * call returns successfully but has no effect.
     * @param tenantId Tenant where tag is stored
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
     * @param triggerId NotEmpty
     * @param category Nullable.
     * @return The existing Tags for the trigger, optionally filtered by category. Sorted by category, name.
     * @throws Exception on any problem
     */
    List<Tag> getTriggerTags(String tenantId, String triggerId, String category) throws Exception;

    void registerListener(DefinitionsListener listener);
}
