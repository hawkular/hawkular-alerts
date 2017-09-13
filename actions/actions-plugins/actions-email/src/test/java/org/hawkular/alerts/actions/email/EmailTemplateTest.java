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
package org.hawkular.alerts.actions.email;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.hawkular.alerts.actions.api.ActionMessage;
import org.hawkular.alerts.actions.tests.JvmGarbageCollectionData;
import org.hawkular.alerts.actions.tests.TestActionMessage;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.model.event.Alert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Basic tests with freemarker templates
 *
 * @author Lucas Ponce
 */
public class EmailTemplateTest {

    private static Map<String, String> properties;

    @BeforeClass
    public static void prepareMessages() {
        properties= new HashMap<>();
        properties.put("to", "admin@hawkular.org");
        properties.put("cc", "bigboss@hawkular.org");
        properties.put("cc.acknowledged", "acknowledged@hawkular.org");
        properties.put("cc.resolved", "resolved@hawkular.org");
        properties.put("template.hawkular.url", "http://www.hawkular.org");
    }


    @Test
    public void loadTemplatesFromDiskTest() throws Exception {
        String testPath = EmailTemplateTest.class
                .getClassLoader().getResource("template.plain.default_en_US.ftl").getPath();
        File testFile = new File(testPath);
        String templatesTestDir = testFile.getParent();
        System.setProperty(EmailPlugin.HAWKULAR_ALERTS_TEMPLATES_PROPERY, templatesTestDir);
        new EmailTemplate();
    }

    @Test
    public void loadTemplatesFromPropertiesTest() throws Exception {
        Alert openAlert = JvmGarbageCollectionData.getOpenAlert();
        Action openAction = new Action(openAlert.getTriggerId(), "email", "email-to-test", openAlert);
        openAction.setProperties(properties);
        ActionMessage openMessage = new TestActionMessage(openAction);

        properties.put("template.plain", "Tiny template: ${emailSubject}");

        EmailTemplate template = new EmailTemplate();
        Map<String, String> processed = template.processTemplate(openMessage);

        assertNotNull(processed.get("emailBodyPlain"));
        assertEquals("Processed template", processed.get("emailBodyPlain"), "Tiny template: Alert [open] message: GC " +
                "Duration greater than 1000.0 ms for App Server thevault~Local");

        properties.remove("template.plain");
    }

    @Test
    public void loadTemplatesFromPropertiesWithLocaleTest() throws Exception {
        Alert openAlert = JvmGarbageCollectionData.getOpenAlert();
        Action openAction = new Action(openAlert.getTriggerId(), "email", "email-to-test", openAlert);
        openAction.setProperties(properties);
        ActionMessage openMessage = new TestActionMessage(openAction);

        // This template can be defined as default at plugin level
        properties.put("template.plain", "Tiny template: ${emailSubject}");

        // These template can be defined per action level
        properties.put("template.plain.es", "ES Tiny template: ${emailSubject}");
        properties.put("template.plain.fr", "FR Tiny template: ${emailSubject}");

        properties.put("template.locale", "es");

        EmailTemplate template = new EmailTemplate();
        Map<String, String> processed = template.processTemplate(openMessage);

        assertNotNull(processed.get("emailBodyPlain"));
        assertEquals("Processed template", processed.get("emailBodyPlain"), "ES Tiny template: Alert [open] message: " +
                "GC Duration greater than 1000.0 ms for App Server thevault~Local");


        properties.put("template.locale", "fr");
        processed = template.processTemplate(openMessage);

        assertNotNull(processed.get("emailBodyPlain"));
        assertEquals("Processed template", processed.get("emailBodyPlain"), "FR Tiny template: Alert [open] message: " +
                "GC Duration greater than 1000.0 ms for App Server thevault~Local");

        properties.remove("template.plain");
        properties.remove("template.locale");
    }

}
