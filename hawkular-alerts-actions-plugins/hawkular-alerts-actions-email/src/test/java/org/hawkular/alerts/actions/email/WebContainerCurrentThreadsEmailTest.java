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
package org.hawkular.alerts.actions.email;

import static org.junit.Assert.assertNotNull;

import java.util.HashMap;
import java.util.Map;

import javax.mail.Message;

import org.hawkular.alerts.actions.api.PluginMessage;
import org.hawkular.alerts.actions.tests.TestPluginMessage;
import org.hawkular.alerts.actions.tests.WebContainerCurrentThreadsData;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.model.condition.Alert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class WebContainerCurrentThreadsEmailTest extends CommonTest {

    private static EmailPlugin plugin;
    private static Map<String, String> properties;

    @BeforeClass
    public static void prepareMessages() {
        plugin = new EmailPlugin();

        properties= new HashMap<>();
        properties.put("to", "admin@hawkular.org");
        properties.put("cc", "bigboss@hawkular.org");
        properties.put("cc.acknowledged", "acknowledged@hawkular.org");
        properties.put("cc.resolved", "resolved@hawkular.org");
        properties.put("template.hawkular.url", "http://www.hawkular.org");
    }

    @Test
    public void writeOpenEmailTest() throws Exception {
        Alert openAlert = WebContainerCurrentThreadsData.getOpenAlert();

        Action openAction = new Action(openAlert.getTriggerId(), "email", "email-to-test", openAlert);

        PluginMessage openMessage = new TestPluginMessage(openAction, properties);

        Message email = plugin.createMimeMessage(openMessage);
        assertNotNull(email);
        writeEmailFile(email, this.getClass().getSimpleName() + "-1-open.eml");
    }

    @Test
    public void writeAcknowledgeEmailTest() throws Exception {
        Alert openAlert = WebContainerCurrentThreadsData.getOpenAlert();
        Alert ackAlert = WebContainerCurrentThreadsData.ackAlert(openAlert);

        Action ackAction = new Action(ackAlert.getTriggerId(), "email", "email-to-test", ackAlert);

        PluginMessage ackMessage = new TestPluginMessage(ackAction, properties);

        Message email = plugin.createMimeMessage(ackMessage);
        assertNotNull(email);
        writeEmailFile(email, this.getClass().getSimpleName() + "-2-ack.eml");
    }

    @Test
    public void writeResolvedEmailTest() throws Exception {
        Alert openAlert = WebContainerCurrentThreadsData.getOpenAlert();
        Alert ackAlert = WebContainerCurrentThreadsData.ackAlert(openAlert);
        Alert resolvedAlert = WebContainerCurrentThreadsData.resolveAlert(ackAlert);

        Action resolvedAction = new Action(resolvedAlert.getTriggerId(), "email", "email-to-test", resolvedAlert);

        PluginMessage resolvedMessage = new TestPluginMessage(resolvedAction, properties);

        Message email = plugin.createMimeMessage(resolvedMessage);
        assertNotNull(email);
        writeEmailFile(email, this.getClass().getSimpleName() + "-3-resolved.eml");
    }

}
