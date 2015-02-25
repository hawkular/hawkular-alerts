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

import org.hawkular.alerts.api.model.condition.Alert;
import org.hawkular.alerts.api.model.data.Data;

/**
 * Interface that allows to send data to the alerts engine and check resulting state.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public interface AlertsService {

    void sendData(Data data);

    void sendData(Collection<Data> data);

    Collection<Alert> checkAlerts();

    /**
     * Reset session state.
     */
    void clear();

    /**
     * Reload all Triggers.
     */
    void reload();

    /**
     * Reload the specified Trigger.  Removes any existing definition from the engine.  If enabled then loads the firing
     * condition set and dampening.  If safetyEnabled then also loads the safety condition set and dampening.
     * @param triggerId
     */
    void reloadTrigger(String triggerId);
}
