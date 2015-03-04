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
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.dampening.Dampening;
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
     * calls. The new </code>Trigger</code> will be persisted.  When fully defined a call to
     * {@link #updateTrigger(Trigger)}
     * is needed to enable the </code>Trigger</code>.
     * @param trigger
     * @throws Exception If the </code>Trigger</code> already exists.
     */
    void addTrigger(Trigger trigger) throws Exception;

    /**
     * The <code>Trigger</code> will be removed from the Alerts engine, as needed, and will no longer be persisted.
     * @param triggerId
     * @throws Exception
     */
    void removeTrigger(String triggerId) throws Exception;

    /**
     * Update the <code>Trigger</code>. <code>Conditions</code> and <code>Actions</code> are manipulated in separate
     * calls. The updated </code>Trigger</code> will be persisted.  If enabled the </code>Trigger</code>
     * will be [re-]inserted into the Alerts engine and any prior dampening will be reset.
     * @param triggerId
     * @throws Exception If the </code>Trigger</code> does not exist.
     */
    Trigger updateTrigger(Trigger trigger) throws Exception;

    Trigger getTrigger(String triggerId) throws Exception;

    Collection<Trigger> getAllTriggers() throws Exception;

    /**
     * Used to generate an explicit Trigger from a Tokenized Trigger.  The dataIdMap replaces the tokens in the
     * Conditions with actual dataIds.
     * @param triggerId
     * @param dataIdMap
     * @return
     * @throws Exception
     */
    Trigger copyTrigger(String triggerId, Map<String, String> dataIdMap) throws Exception;

    /*
        CRUD interface for Dampening
     */

    Dampening addDampening(Dampening dampening) throws Exception;

    void removeDampening(String dampeningId) throws Exception;

    Dampening updateDampening(Dampening dampening) throws Exception;

    Dampening getDampening(String dampeningId) throws Exception;

    /**
     * @param triggerId
     * @param triggerMode Return only dampenings for the given trigger mode. Return all if null.
     * @return The existing dampenings for the trigger. Not null.
     * @throws Exception
     */
    Collection<Dampening> getTriggerDampenings(String triggerId, Trigger.Mode triggerMode) throws Exception;

    Collection<Dampening> getAllDampenings() throws Exception;

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
     * @param triggerId
     * @param triggerMode
     * @param condition Not null
     * @return The updated, persisted condition set
     * @throws Exception
     */
    Collection<Condition> addCondition(String triggerId, Trigger.Mode triggerMode, Condition condition)
            throws Exception;

    /**
     * A convenience method that removes a Condition from an existing condition set. This will update the
     * conditionSetSize and possibly the conditionSetIndex for any remaining conditions.
     * @param conditionId
     * @return The updated, persisted condition set. Not null. Can be empty.
     * @throws Exception
     */
    Collection<Condition> removeCondition(String conditionId)
            throws Exception;

    /**
     * A convenience method that updates an existing Condition from an existing condition set.
     * @param condition Not null. conditionId must be for an existing condition.
     * @return The updated, persisted condition set. Not null. Can be empty.
     * @throws Exception
     */
    Collection<Condition> updateCondition(Condition condition)
            throws Exception;

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
     * @param triggerId
     * @param triggerMode
     * @param conditions Not null, Not Empty
     * @return The persisted condition set
     * @throws Exception
     */
    Collection<Condition> setConditions(String triggerId, Trigger.Mode triggerMode, Collection<Condition> conditions)
            throws Exception;

    Condition getCondition(String conditionId) throws Exception;

    /**
     * @param triggerId
     * @param triggerMode Return only conditions for the given trigger mode. Return all if null.
     * @return The existing conditions for the trigger. Not null.
     * @throws Exception
     */
    Collection<Condition> getTriggerConditions(String triggerId, Trigger.Mode triggerMode) throws Exception;

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
    void addAction(String actionId, Map<String, String> properties) throws Exception;

    void removeAction(String actionId) throws Exception;

    void updateAction(String actionId, Map<String, String> properties) throws Exception;

    Collection<String> getAllActions() throws Exception;

    Collection<String> getActions(String actionPlugin) throws Exception;

    Map<String, String> getAction(String actionId) throws Exception;

}
