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
package org.hawkular.alerts.engine.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import org.hawkular.alerts.api.model.action.ActionDefinition;
import org.hawkular.alerts.api.model.condition.CompareCondition;
import org.hawkular.alerts.api.model.condition.RateCondition;
import org.hawkular.alerts.api.model.condition.ThresholdCondition;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.trigger.FullTrigger;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class AlertsImportManagerTest {

    AlertsImportManager importManager;

    @Before
    public void checkTestFile() throws Exception {
        String testFolder = AlertsImportManagerTest.class.getResource("/").toURI().getPath();
        File fAlerts = new File(new File(testFolder, "hawkular-alerts"), "alerts-data.json");
        importManager = new AlertsImportManager(fAlerts);
    }

    @Test
    public void detailedCheck() throws Exception {
        List<FullTrigger> fullTriggers = importManager.getFullTriggers();
        List<ActionDefinition> actionDefinitions = importManager.getActionDefinitions();

        assertTrue(fullTriggers.size() > 0);
        assertTrue(actionDefinitions.size() > 0);

        // Check trigger-1
        assertEquals("value1", fullTriggers.get(0).getTrigger().getContext().get("name1"));
        assertEquals("tvalue2", fullTriggers.get(0).getTrigger().getTags().get("tname2"));
        assertEquals(Dampening.Type.STRICT, fullTriggers.get(0).getDampenings().get(0).getType());
        assertTrue(fullTriggers.get(0).getConditions().get(0) instanceof ThresholdCondition);

        // Check trigger-2
        assertEquals(2, fullTriggers.get(1).getConditions().size());
        assertEquals("NumericData-01", fullTriggers.get(1).getConditions().get(0).getDataId());
        assertEquals("NumericData-02", fullTriggers.get(1).getConditions().get(1).getDataId());

        // Check trigger-3
        assertEquals("NumericData-03", fullTriggers.get(2).getConditions().get(0).getDataId());

        // Check trigger-4
        assertTrue(fullTriggers.get(3).getConditions().get(0) instanceof CompareCondition);

        // Check trigger-5
        assertEquals("StringData-01", fullTriggers.get(4).getConditions().get(0).getDataId());

        // Check trigger-6
        assertEquals("Availability-01", fullTriggers.get(5).getConditions().get(0).getDataId());

        // Check trigger-7
        assertTrue(fullTriggers.get(6).getTrigger().isGroup());

        // Check trigger-8
        assertEquals("NumericData-01", fullTriggers.get(7).getConditions().get(0).getDataId());

        // Check trigger-9
        assertTrue(fullTriggers.get(8).getConditions().get(0) instanceof RateCondition);
    }

}
