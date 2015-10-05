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
package org.hawkular.alerts.engine;

import org.hawkular.alerts.api.services.ActionsService;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.engine.impl.AlertsEngineImpl;
import org.hawkular.alerts.engine.impl.CassActionsServiceImpl;
import org.hawkular.alerts.engine.impl.CassAlertsServiceImpl;
import org.hawkular.alerts.engine.impl.CassDefinitionsServiceImpl;
import org.hawkular.alerts.engine.impl.DroolsRulesEngineImpl;

/**
 * Factory helper for standalone use cases.
 *
 * @author Lucas Ponce
 */
public class StandaloneAlerts {

    private static StandaloneAlerts instance = null;

    private CassActionsServiceImpl actions = null;
    private CassAlertsServiceImpl alerts = null;
    private CassDefinitionsServiceImpl definitions = null;
    private AlertsEngineImpl engine = null;
    private DroolsRulesEngineImpl rules = null;

    private StandaloneAlerts() {
        actions = new CassActionsServiceImpl();
        rules = new DroolsRulesEngineImpl();
        engine = new AlertsEngineImpl();
        definitions = new CassDefinitionsServiceImpl();
        alerts = new CassAlertsServiceImpl();

        definitions.setAlertsEngine(engine);
        engine.setDefinitions(definitions);
        engine.setActions(actions);
        engine.setRules(rules);

        definitions.init();
    }

    public static synchronized DefinitionsService getDefinitionsService() {
        if (instance == null) {
            instance = new StandaloneAlerts();
        }
        return instance.definitions;
    }

    public static synchronized AlertsService getAlertsService() {
        if (instance == null) {
            instance = new StandaloneAlerts();
        }
        return instance.alerts;
    }

    public static synchronized ActionsService getActionsService() {
        if (instance == null) {
            instance = new StandaloneAlerts();
        }
        return instance.actions;
    }
}
