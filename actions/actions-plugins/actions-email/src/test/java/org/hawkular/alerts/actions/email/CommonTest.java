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

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;

import javax.mail.Message;

import org.hawkular.alerts.actions.api.ActionPluginSender;
import org.hawkular.alerts.actions.api.ActionResponseMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;

/**
 * Helper methods for tests
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class CommonTest {

    protected static final String TEST_SMTP_HOST = "localhost";
    protected static int TEST_SMTP_PORT = 2525;

    protected GreenMail server;

    @BeforeClass
    public static void setUnitTest() {
        System.setProperty("mail.smtp.host", TEST_SMTP_HOST);
        System.setProperty("mail.smtp.port", String.valueOf(TEST_SMTP_PORT));
    }

    @Before
    public void initSmtpServer() {
        server = new GreenMail(new ServerSetup(TEST_SMTP_PORT, TEST_SMTP_HOST, "smtp"));
        server.start();
    }

    @After
    public void stopSmtpServer() {
        if (server != null) {
            server.stop();
        }
    }

    protected void writeEmailFile(Message msg, String fileName) throws Exception {
        File dir = new File("target/test-emails");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, fileName);
        FileOutputStream fos = new FileOutputStream(file);
        msg.writeTo(fos);
        fos.close();
    }

    public static class TestActionPluginSender implements ActionPluginSender {

        @Override
        public ActionResponseMessage createMessage(ActionResponseMessage.Operation operation) {
            return new TestActionPluginMessage(operation, new HashMap<>());
        }

        @Override
        public void send(ActionResponseMessage msg) throws Exception {
            // Nothing to do for testing here
        }
    }

    public static class TestActionPluginMessage implements ActionResponseMessage {

        private Operation operation;
        public Map<String, String> payload;

        public TestActionPluginMessage(Operation operation, Map<String, String> payload) {
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

}
