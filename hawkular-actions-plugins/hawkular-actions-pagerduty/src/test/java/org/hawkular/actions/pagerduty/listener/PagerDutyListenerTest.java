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
package org.hawkular.actions.pagerduty.listener;

import static org.hawkular.actions.pagerduty.listener.PagerDutyListener.API_KEY_PROPERTY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

import com.google.gson.InstanceCreator;
import com.squareup.pagerduty.incidents.FakePagerDuty;
import com.squareup.pagerduty.incidents.NotifyResult;

import org.hawkular.actions.api.model.ActionMessage;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Thomas Segismont
 */
public class PagerDutyListenerTest {

    @BeforeClass
    public static void configureListener() {
        System.setProperty(API_KEY_PROPERTY, "test");
    }

    private FakePagerDuty fakePagerDuty;
    private PagerDutyListener pagerDutyListener;

    @Before
    public void setup() {
        pagerDutyListener = new PagerDutyListener();
        fakePagerDuty = new FakePagerDuty();
        pagerDutyListener.pagerDuty = fakePagerDuty;
    }

    @Test
    public void testSend() throws Exception {
        ActionMessage actionMessage = new ActionMessage();
        actionMessage.setMessage("GRAVE");

        pagerDutyListener.onBasicMessage(actionMessage);

        assertEquals("Expected PagerDuty incident to be created", 1, fakePagerDuty.openIncidents().size());
        assertEquals("GRAVE", fakePagerDuty.openIncidents().values().iterator().next());
    }

    @Test
    public void testNotifyResultCreator() throws Exception {
        // Expecting no exception
        InstanceCreator<NotifyResult> instanceCreator = pagerDutyListener.buildNotifyResultCreator();
        assertNotNull("Should not return null", instanceCreator.createInstance(null));

        NotifyResult instance1 = instanceCreator.createInstance(null);
        NotifyResult instance2 = instanceCreator.createInstance(null);

        assertNotSame(instance1, instance2);
    }
}
