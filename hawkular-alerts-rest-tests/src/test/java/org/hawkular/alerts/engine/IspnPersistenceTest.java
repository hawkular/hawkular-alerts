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
package org.hawkular.alerts.engine;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.hawkular.alerts.api.model.export.Definitions;
import org.hawkular.alerts.api.model.export.ImportType;
import org.hawkular.alerts.api.services.ActionsCriteria;
import org.hawkular.alerts.api.services.AlertsCriteria;
import org.hawkular.alerts.api.services.EventsCriteria;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class IspnPersistenceTest extends PersistenceTest {
    private static final MsgLogger log = MsgLogging.getMsgLogger(IspnPersistenceTest.class);
    private static ObjectMapper objectMapper;

    @BeforeClass
    public static void initSessionAndResetTestSchema() throws Exception {
        System.setProperty("hawkular-alerts.backend", "ispn");

        definitionsService = StandaloneAlerts.getDefinitionsService();
        alertsService = StandaloneAlerts.getAlertsService();
        actionsService = StandaloneAlerts.getActionsService();

        mockPluginsDeployments();

        objectMapper = new ObjectMapper();
        Definitions definitions = objectMapper.readValue(
                IspnPersistenceTest.class.getResourceAsStream("/hawkular-alerts/alerts-data.json"), Definitions.class);
        definitionsService.importDefinitions(TENANT, definitions, ImportType.DELETE);
    }

    private static void mockPluginsDeployments() throws Exception {
        Set<String> propsSnmp = new HashSet<>(Arrays.asList("host", "port", "oid", "description"));
        definitionsService.removeActionPlugin("snmp");
        definitionsService.addActionPlugin("snmp", propsSnmp);

        Set<String> propsEmail = new HashSet<>(Arrays.asList("to", "cc", "description"));
        definitionsService.removeActionPlugin("email");
        definitionsService.addActionPlugin("email", propsEmail);

        Set<String> propsSms = new HashSet<>(Arrays.asList("phone", "description"));
        definitionsService.removeActionPlugin("sms");
        definitionsService.addActionPlugin("sms", propsSms);

        Set<String> propsAerogear = new HashSet<>(Arrays.asList("alias", "description"));
        definitionsService.removeActionPlugin("aerogear");
        definitionsService.addActionPlugin("aerogear", propsAerogear);

        Set<String> propsFile = new HashSet<>(Arrays.asList("description"));
        definitionsService.removeActionPlugin("file");
        definitionsService.addActionPlugin("file", propsFile);
    }

    @Before
    public void cleanAlerts() throws Exception {
        AlertsCriteria criteria = new AlertsCriteria();
        log.info("Deleted " + alertsService.deleteAlerts(TENANT, criteria) + " Alerts before test.\n");
    }

    @Before
    public void cleanEvents() throws Exception {
        EventsCriteria criteria = new EventsCriteria();
        log.info("Deleted " + alertsService.deleteEvents(TENANT, criteria) + " Events before test.\n");
    }

    @Before
    public void cleanActions() throws Exception {
        ActionsCriteria criteria = new ActionsCriteria();
        log.info("Deleted " + actionsService.deleteActions(TENANT, criteria) + " Actions before test.\n");
    }

    @AfterClass
    public static void stop() throws Exception {
    }

}
