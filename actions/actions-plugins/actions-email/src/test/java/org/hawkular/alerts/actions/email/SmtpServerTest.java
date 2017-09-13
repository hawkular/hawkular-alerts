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

import java.util.Properties;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class SmtpServerTest {

    private static final String TEST_SMTP_HOST = "localhost";
    private static int TEST_SMTP_PORT = 2525;

    private GreenMail server;

    @Before
    public void startSmtpServer() {
        server = new GreenMail(new ServerSetup(TEST_SMTP_PORT, TEST_SMTP_HOST, "smtp"));
        server.start();
    }

    @After
    public void stopSmtpServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void checkSmtpServer() throws Exception {
        Properties props = new Properties();
        props.setProperty("mail.smtp.host", TEST_SMTP_HOST);
        props.setProperty("mail.smtp.port", String.valueOf(TEST_SMTP_PORT));

        Session session = Session.getInstance(props);

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress("alerts-test-sender@hawkular.org"));
        message.setRecipients(Message.RecipientType.TO,
                InternetAddress.parse("alerts-test-receiver@hawkular.org"));
        message.setSubject("Check SMTP Server");
        message.setText("This is the text of the message");
        Transport.send(message);

        assertEquals(1, server.getReceivedMessages().length);
    }
}
