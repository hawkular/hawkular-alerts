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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.mail.Message;

import org.hawkular.alerts.actions.api.ActionMessage;
import org.hawkular.alerts.actions.api.ActionPluginSender;
import org.hawkular.alerts.actions.api.ActionResponseMessage;
import org.hawkular.alerts.actions.tests.MultipleAllJvmData;
import org.hawkular.alerts.actions.tests.TestActionMessage;
import org.hawkular.alerts.api.json.JsonUtil;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.model.event.Alert;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * This test is to show how to configure EmailPlugin on secure scenarios.
 *
 * It needs connectivity with smtp.mailtrap.io where there is a specific account for Hawkular Alerting testing.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class SmtpSecureTest {

    private static final String MAILTRAP_URL = "https://mailtrap.io/api/v1/inboxes/";
    private static final String INBOX_ID = "158842";
    private static final String CLEAN_INBOX_URL = MAILTRAP_URL + INBOX_ID + "/clean";
    private static final String GET_INBOX_URL = MAILTRAP_URL + INBOX_ID;
    private static final String API_TOKEN = "Api-Token";
    private static final String INBOX_TOKEN = "c8b383b4352277421308f32f8dad4ca5";
    private static final String GET_METHOD = "GET";
    private static final String POST_METHOD = "POST";
    private static final String PATCH_METHOD = "PATCH";
    private static final String METHOD_OVERRIDE = "X-HTTP-Method-Override";
    private static final int    DEFAULT_TIMEOUT = 5000;
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";

    private static EmailPlugin plugin;
    private static Map<String, String> properties;
    private static TestSecurePluginSender secureSender = new TestSecurePluginSender();

    @BeforeClass
    public static void prepareMessages() {
        plugin = new EmailPlugin();
        plugin.setSender(secureSender);

        properties= new HashMap<>();
        properties.put("to", "admin@hawkular.org");
        properties.put("cc", "bigboss@hawkular.org");
        properties.put("cc.acknowledged", "acknowledged@hawkular.org");
        properties.put("cc.resolved", "resolved@hawkular.org");
        properties.put("template.hawkular.url", "http://www.hawkular.org");
    }

    @Before
    public void cleanRemoteInbox() throws Exception {
        URL inboxUrl = new URL(CLEAN_INBOX_URL);
        HttpURLConnection conn = (HttpURLConnection) inboxUrl.openConnection();
        conn.setRequestProperty(METHOD_OVERRIDE, PATCH_METHOD);
        conn.setRequestMethod(POST_METHOD);
        conn.setRequestProperty(CONTENT_TYPE, APPLICATION_JSON);
        conn.setRequestProperty(API_TOKEN, INBOX_TOKEN);
        conn.setConnectTimeout(DEFAULT_TIMEOUT);
        conn.setReadTimeout(DEFAULT_TIMEOUT);
        conn.connect();
        try {
            int responseCode = conn.getResponseCode();
            if (responseCode >= 300) {
                throw new Exception("INBOX cleaning returned a [" + responseCode + "] response code.");
            }
        } finally {
            conn.disconnect();
        }
    }

    @SuppressWarnings("unchecked")
    public int getRemoteInboxSize() throws Exception {
        URL inboxUrl = new URL(GET_INBOX_URL);
        HttpURLConnection conn = (HttpURLConnection) inboxUrl.openConnection();
        conn.setRequestMethod(GET_METHOD);
        conn.setRequestProperty(CONTENT_TYPE, APPLICATION_JSON);
        conn.setRequestProperty(API_TOKEN, INBOX_TOKEN);
        conn.setConnectTimeout(DEFAULT_TIMEOUT);
        conn.setReadTimeout(DEFAULT_TIMEOUT);

        InputStream inputStream = conn.getInputStream();
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            response.write(buffer, 0, length);
        }
        try {
            Map<String, Object> responseMap = JsonUtil.fromJson(response.toString("UTF-8"), Map.class);
            return (Integer) responseMap.get("emails_count");
        } finally {
            conn.disconnect();
        }
    }

    @After
    public void waitAfterTest() throws Exception {
        Thread.sleep(2000);
    }

    @Test
    public void smtpUserPassTest() throws Exception {
        Alert openAlert = MultipleAllJvmData.getOpenAlert();

        Action openAction = new Action(openAlert.getTriggerId(), "email", "email-to-test", openAlert);

        properties.put("mail.smtp.host", "mailtrap.io");
        properties.put("mail.smtp.port", "2525");
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.user", "5d80cef05f244e");
        properties.put("mail.smtp.pass", "a7775710279a87");

        openAction.setProperties(properties);
        ActionMessage openMessage = new TestActionMessage(openAction);

        Message email = plugin.createMimeMessage(openMessage);
        assertNotNull(email);

        plugin.process(openMessage);

        assertFalse(secureSender.isFailed());
        assertEquals(1, getRemoteInboxSize());
    }

    private void assertEquals(int i, int remoteInboxSize) {
    }

    @Test
    public void smtpSslTest() throws Exception {
        Alert openAlert = MultipleAllJvmData.getOpenAlert();

        Action openAction = new Action(openAlert.getTriggerId(), "email", "email-to-test", openAlert);

        properties.put("mail.smtp.host", "mailtrap.io");
        properties.put("mail.smtp.port", "2525");
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.user", "5d80cef05f244e");
        properties.put("mail.smtp.pass", "a7775710279a87");

        openAction.setProperties(properties);
        ActionMessage openMessage = new TestActionMessage(openAction);

        Message email = plugin.createMimeMessage(openMessage);
        assertNotNull(email);

        plugin.process(openMessage);

        assertFalse(secureSender.isFailed());
        assertEquals(1, getRemoteInboxSize());
    }


    public static class TestSecurePluginMessage implements ActionResponseMessage {

        private Operation operation;
        public Map<String, String> payload;

        public TestSecurePluginMessage(Operation operation, Map<String, String> payload) {
            this.operation = operation;
            this.payload = payload;
        }

        @Override
        public Operation getOperation() {
            return operation;
        }

        @Override
        public Map<String, String> getPayload() {
            return payload;
        }
    }

    public static class TestSecurePluginSender implements ActionPluginSender {

        boolean failed = false;

        @Override
        public ActionResponseMessage createMessage(ActionResponseMessage.Operation operation) {
            return new TestSecurePluginMessage(operation, new HashMap<>());
        }

        @Override
        public void send(ActionResponseMessage msg) throws Exception {
            Action action = JsonUtil.fromJson(msg.getPayload().get("action"), Action.class);
            failed = action.getResult().equals("FAILED");
        }

        public boolean isFailed() {
            return failed;
        }
    }

}
