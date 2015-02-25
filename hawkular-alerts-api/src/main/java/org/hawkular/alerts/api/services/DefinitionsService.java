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
        A notifier type is representation of a notifier capability.
        i.e. email, snmp or sms.
        It will have a set of specific properties to fill per a specific notifier.

        Notifier plugin should be responsible to init a notifier type before to send notifications.

        NotifierType API will be useful for future UI to help to define new notifiers.
        i.e. querying for properties to fill for a specific notifier type.
     */
    void addNotifierType(String notifierType, Set<String> properties) throws Exception;

    void removeNotifierType(String notifierType) throws Exception;

    void updateNotifierType(String notifierType, Set<String> properties) throws Exception;

    Collection<String> getNotifierTypes() throws Exception;

    Set<String> getNotifierType(String notifierType) throws Exception;

    /*
        A notifier is a specific instance of notification.
        i.e. email to admin@mycompany.com.
             send a specific TRAP with specific details.
             send a SMS mobile to an admin number.
     */
    void addNotifier(String notifierId, Map<String, String> properties) throws Exception;

    void removeNotifier(String notifierId) throws Exception;

    void updateNotifier(String notifierId, Map<String, String> properties) throws Exception;

    Collection<String> getAllNotifiers() throws Exception;

    Collection<String> getNotifiers(String notifierType) throws Exception;

    Map<String, String> getNotifier(String notifierId) throws Exception;

}
