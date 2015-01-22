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

import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.trigger.Trigger;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

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
    void addTrigger(Trigger trigger);
    void removeTrigger(String triggerId);
    void updateTrigger(Trigger trigger);
    Collection<Trigger> getTriggers();
    Trigger getTrigger(String triggerId);

    /*
        CRUD interface for Trigger
     */
    void addDampening(Dampening dampening);
    void removeDampening(String triggerId);
    void updateDampening(Dampening dampening);
    Collection<Dampening> getDampenings();
    Dampening getDampening(String triggerId);

    /*
        CRUD interface for Condition
     */
    void addCondition(Condition condition);
    void removeCondition(String conditionId);
    void updateCondition(Condition condition);
    Collection<Condition> getConditions();
    Collection<Condition> getConditions(String triggerId);
    Condition getCondition(String conditionId);


    /*
        A notifier type is representation of a notifier capability.
        i.e. email, snmp or sms.
        It will have a set of specific properties to fill per a specific notifier.

        Notifier plugin should be responsible to init a notifier type before to send notifications.

        NotifierType API will be useful for future UI to help to define new notifiers.
        i.e. querying for properties to fill for a specific notifier type.
     */
    void addNotifierType(String notifierType, Set<String> properties);
    void removeNotifierType(String notifierType);
    void updateNotifierType(String notifierType, Set<String> properties);
    Collection<String> getNotifierTypes();
    Set<String> getNotifierType(String notifierType);

    /*
        A notifier is a specific instance of notification.
        i.e. email to admin@mycompany.com.
             send a specific TRAP with specific details.
             send a SMS mobile to an admin number.
     */
    void addNotifier(String notifierId, Map<String, String> properties);
    void removeNotifier(String notifierId);
    void updateNotifier(String notifierId, Map<String, String> properties);
    Collection<String> getNotifiers();
    Collection<String> getNotifiers(String notifierType);
    Map<String, String> getNotifier(String notifierId);

}
