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
package org.hawkular.alerts.actions.email.templates;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hawkular.alerts.actions.email.EmailPlugin;
import org.hawkular.alerts.actions.email.template.EmailTemplate;
import org.hawkular.alerts.api.model.condition.Alert;
import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.AvailabilityConditionEval;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.condition.ThresholdCondition;
import org.hawkular.alerts.api.model.condition.ThresholdConditionEval;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.data.Availability;
import org.hawkular.alerts.api.model.data.NumericData;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.junit.Before;
import org.junit.Test;

/**
 * Basic tests with mustache templates
 * @author Lucas Ponce
 */
public class EmailTemplateTest {

    public static final String TEST_TENANT = "test-tenant";

    public static final String TEST_TRIGGER_ID_RESPONSE_TIME = "test-trigger-response-time";
    public static final String TEST_TRIGGER_NAME_RESPONSE_TIME = "http://www.mydemourl.com";
    public static final String TEST_DATA_ID_RESPONSE_TIME = "test-data-response-time";

    public static final String TEST_TRIGGER_ID_AVAILABILITY = "test-trigger-availability";
    public static final String TEST_TRIGGER_NAME_AVAILABILITY = "http://www.mydemourl.com";
    public static final String TEST_DATA_ID_AVAILABILITY = "test-data-availability";

    protected Alert alertThreshold;
    protected Alert alertAvailability;
    protected EmailTemplate template;

    @Before
    public void createFullAlert() {
        /*
            Response Time definition
         */
        Trigger tRT = new Trigger(TEST_TRIGGER_ID_RESPONSE_TIME, TEST_TRIGGER_NAME_RESPONSE_TIME);
        tRT.setTenantId(TEST_TENANT);

        Dampening dRT = Dampening.forStrictTime(TEST_TRIGGER_ID_RESPONSE_TIME, tRT.getMode(), 7000);

        ThresholdCondition tCondition = new ThresholdCondition(TEST_TRIGGER_ID_RESPONSE_TIME,
                TEST_DATA_ID_RESPONSE_TIME, ThresholdCondition.Operator.GT, 10.0);
        tCondition.setTenantId(TEST_TENANT);

        NumericData nData = new NumericData(TEST_DATA_ID_RESPONSE_TIME, System.currentTimeMillis(), 12.5);
        ThresholdConditionEval tEval = new ThresholdConditionEval(tCondition, nData);
        Set<ConditionEval> tEvalsSet = new HashSet<>();
        tEvalsSet.add(tEval);
        List<Set<ConditionEval>> tEvalsList = new ArrayList<>();
        tEvalsList.add(tEvalsSet);

        /*
            Response Time Alert
         */
        alertThreshold = new Alert(tRT.getTenantId(), tRT.getId(), tRT.getSeverity(), tEvalsList);
        alertThreshold.setTrigger(tRT);
        alertThreshold.setDampening(dRT);

        /*
            Availability definition
         */
        Trigger tAV = new Trigger(TEST_TRIGGER_ID_AVAILABILITY, TEST_TRIGGER_NAME_AVAILABILITY);
        tAV.setTenantId(TEST_TENANT);

        Dampening dAV = Dampening.forStrictTime(TEST_TRIGGER_ID_AVAILABILITY, tAV.getMode(), 7000);

        AvailabilityCondition aCondition = new AvailabilityCondition(TEST_TRIGGER_ID_AVAILABILITY,
                TEST_DATA_ID_AVAILABILITY, AvailabilityCondition.Operator.NOT_UP);
        aCondition.setTenantId(TEST_TENANT);

        Availability aData = new Availability(TEST_DATA_ID_AVAILABILITY, System.currentTimeMillis(),
                Availability.AvailabilityType.DOWN);
        AvailabilityConditionEval aEval = new AvailabilityConditionEval(aCondition, aData);
        Set<ConditionEval> aEvalsSet = new HashSet<>();
        aEvalsSet.add(aEval);
        List<Set<ConditionEval>> aEvalsList = new ArrayList<>();
        aEvalsList.add(aEvalsSet);

        /*
            Availability Alert
         */
        alertAvailability = new Alert(tAV.getTenantId(), tAV.getId(), tAV.getSeverity(), aEvalsList);
        alertAvailability.setTrigger(tAV);
        alertAvailability.setDampening(dAV);

        /*
            Create a test class
         */
        template = new EmailTemplate();
    }

    @Test
    public void defaultTemplate() throws Exception {
        String message = "This is a dummy message";

        Map<String, String> content = template.body(null, null, message, null);

        assertNotNull(content.get("plain"));
        assertNotNull(content.get("html"));
        assertFalse(content.get("plain").contains("html"));
        assertTrue(content.get("html").contains("html"));
    }

    @Test
    public void templateForLocale() throws Exception {
        String enTemplate = "ENGLISH TEMPLATE";
        String esTemplate = "PLANTILLA EN ESPAÑOL";

        Map<String, String> props = new HashMap<>();
        props.put(EmailPlugin.PROP_TEMPLATE_LOCALE, "en");
        props.put(EmailPlugin.PROP_TEMPLATE_PLAIN + ".en", enTemplate);

        String message = "This is a dummy message";

        Map<String, String> content = template.body(props, null, message, null);
        assertEquals(enTemplate, content.get("plain"));

        props.put(EmailPlugin.PROP_TEMPLATE_LOCALE, "es");
        content = template.body(props, null, message, null);
        assertNotEquals(esTemplate, content.get("plain"));

        props.put(EmailPlugin.PROP_TEMPLATE_PLAIN + ".es", esTemplate);
        content = template.body(props, null, message, null);
        assertEquals(esTemplate, content.get("plain"));

        content = template.body(null, props, message, null);
        assertEquals(esTemplate, content.get("plain"));
    }

    @Test
    public void testSubject() throws Exception {
        String subject = template.subject(alertAvailability);
        assertEquals("Alert [open] message: http://www.mydemourl.com is not up", subject);

        subject = template.subject(alertThreshold);
        assertEquals("Alert [open] message: http://www.mydemourl.com has response time greater than threshold",
                subject);
    }

    @Test
    public void testBody() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(EmailPlugin.PROP_TEMPLATE_HAWKULAR_URL, "http://localhost:8080/");

        Map<String, String> body = template.body(null, props, null, alertAvailability);
        assertNotNull(body);
        assertNotNull(body.get("plain"));
        assertNotNull(body.get("html"));

        System.out.println(body.get("plain"));

        body = template.body(null, props, null, alertThreshold);
        assertNotNull(body);
        assertNotNull(body.get("plain"));
        assertNotNull(body.get("html"));

        System.out.println(body.get("plain"));
    }

    @Test
    public void testPropertyFolder() throws Exception {
        String testPath = EmailTemplateTest.class
                .getClassLoader().getResource("template.plain.default_en_US.ftl").getPath();
        File testFile = new File(testPath);
        String templatesTestDir = testFile.getParent();
        System.setProperty(EmailPlugin.HAWKULAR_ALERTS_TEMPLATES_PROPERY, templatesTestDir);
        EmailTemplate templatesFromDisk = new EmailTemplate();
    }


}
