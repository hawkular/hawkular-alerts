/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.alerts.engine.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hawkular.alerts.api.json.JsonImport;
import org.hawkular.alerts.api.json.JsonImport.FullAction;
import org.hawkular.alerts.api.json.JsonImport.FullTrigger;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Read a json file with a list of full triggers and actions.
 * A full trigger is a Trigger with its Dampening and Conditions.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class AlertsImportManager {
    private static final Logger log = Logger.getLogger(AlertsImportManager.class);
    private ObjectMapper objectMapper = new ObjectMapper();
    private List<FullTrigger> fullTriggers = new ArrayList<>();
    private List<FullAction> fullActions = new ArrayList<>();


    /**
     * Read a json file and initialize the AlertsImportManager instance
     *
     * @param fAlerts json file to read
     * @throws Exception on any problem
     */
    public AlertsImportManager(File fAlerts) throws Exception {
        if (fAlerts == null) {
            throw new IllegalArgumentException("fAlerts must be not null");
        }
        if (!fAlerts.exists() || !fAlerts.isFile()) {
            throw new IllegalArgumentException(fAlerts.getName() + " file must exist");
        }
        Map<String, Object> rawImport = objectMapper.readValue(fAlerts, Map.class);
        List<Map<String, Object>> rawTriggers = (List<Map<String, Object>>) rawImport.get("triggers");
        for (Map<String, Object> rawTrigger: rawTriggers) {
            FullTrigger fTrigger = JsonImport.readFullTrigger(rawTrigger);
            if (log.isDebugEnabled()) {
                log.debug("Importing... " + fTrigger.toString());
            }
            fullTriggers.add(fTrigger);
        }

        List<Map<String, Object>> rawActions = (List<Map<String, Object>>) rawImport.get("actions");
        for (Map<String, Object> rawAction : rawActions) {
            FullAction fAction = JsonImport.readFullAction(rawAction);
            if (log.isDebugEnabled()) {
                log.debug("Importing... " + fAction.toString());
            }
            fullActions.add(fAction);
        }
    }

    public List<FullTrigger> getFullTriggers() {
        return fullTriggers;
    }

    public List<FullAction> getFullActions() {
        return fullActions;
    }

}
