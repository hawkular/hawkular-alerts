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
package org.hawkular.alerts.engine.impl.ispn;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hawkular.alerts.api.model.Severity;
import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.AvailabilityConditionEval;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.data.AvailabilityType;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.services.AlertsCriteria;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class IspnAlertsServiceImplTest {
    static final MsgLogger log = MsgLogging.getMsgLogger(IspnAlertsServiceImplTest.class);

    static IspnAlertsServiceImpl alerts;

    @BeforeClass
    public static void init() {
        System.setProperty("hawkular.data", "./target/ispn");
        alerts = new IspnAlertsServiceImpl();
        alerts.init();
    }

    void createTestAlerts(int numTenants, int numTriggers, int numAlerts) throws Exception {
        List<Alert> newAlerts = new ArrayList<>();
        for (int tenant = 0; tenant < numTenants; tenant++) {
            String tenantId = "tenant" + tenant;
            for (int trigger = 0; trigger < numTriggers; trigger++) {
                String triggerId = "trigger" + trigger;
                Trigger triggerX = new Trigger(tenantId, triggerId, "Trigger " + triggerId);
                AvailabilityCondition availability = new AvailabilityCondition(tenantId, triggerId, "Availability-" + trigger, AvailabilityCondition.Operator.DOWN);
                for (int alert = 0; alert < numAlerts; alert++) {
                    long dataTime = alert;
                    Data data = Data.forAvailability(tenantId, "Availability-" + trigger, dataTime, AvailabilityType.DOWN);
                    AvailabilityConditionEval eval = new AvailabilityConditionEval(availability, data);
                    Set<ConditionEval> evalSet = new HashSet<>();
                    evalSet.add(eval);
                    List<Set<ConditionEval>> evals = new ArrayList<>();
                    evals.add(evalSet);
                    Alert alertX = new Alert(tenantId, triggerX, evals);
                    switch (alert % 3) {
                        case 2:
                            alertX.setStatus(Alert.Status.OPEN);
                            alertX.setSeverity(Severity.CRITICAL);
                            break;
                        case 1:
                            alertX.setStatus(Alert.Status.ACKNOWLEDGED);
                            alertX.setSeverity(Severity.LOW);
                            break;
                        case 0:
                            alertX.setStatus(Alert.Status.RESOLVED);
                            alertX.setSeverity(Severity.MEDIUM);
                    }
                    newAlerts.add(alertX);
                }
            }
        }
        alerts.addAlerts(newAlerts);
    }

    void removeAllAlerts() {

    }

    @Test
    public void addAlerts() throws Exception {
        int numTenants = 2;
        int numTriggers = 5;
        int numAlerts = 100;
        createTestAlerts(numTenants, numTriggers, numAlerts);

        Set<String> tenantIds = new HashSet<>();
        tenantIds.add("tenant0");
        tenantIds.add("tenant1");

        assertEquals(2 * 5 * 100, alerts.getAlerts(tenantIds, null, null).size());

        tenantIds.remove("tenant0");
        assertEquals(1 * 5 * 100, alerts.getAlerts(tenantIds, null, null).size());

        List<Alert> testAlerts = alerts.getAlerts(tenantIds, null, null);
        tenantIds.clear();
        Set<String> alertIds = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            Alert alertX = testAlerts.get(i);
            tenantIds.add(alertX.getTenantId());
            alertIds.add(alertX.getAlertId());
        }

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setAlertIds(alertIds);

        assertEquals(3, alerts.getAlerts(tenantIds, criteria, null).size());

        removeAllAlerts();
    }
}
