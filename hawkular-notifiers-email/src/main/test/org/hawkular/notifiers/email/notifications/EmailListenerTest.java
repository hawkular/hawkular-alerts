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
package org.hawkular.notifiers.email.notifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Message.RecipientType;

import org.hawkular.notifiers.api.model.NotificationMessage;
import org.junit.Test;

/**
 * @author Thomas Segismont
 */
public class EmailListenerTest {

    private EmailListener emailListener = new EmailListener();

    @Test
    public void testMessageContent() throws Exception {
        String recipient = "root@localhost";
        String alertContent = "marseille";

        NotificationMessage notificationMessage = new NotificationMessage();
        notificationMessage.setNotifierId(recipient);
        notificationMessage.setMessage(alertContent);

        Message mimeMessage = emailListener.createMimeMessage(notificationMessage);

        Address[] from = mimeMessage.getFrom();
        assertNotNull(from);
        assertEquals(1, from.length);
        assertEquals("noreply@hawkular", from[0].toString());

        Address[] recipients = mimeMessage.getRecipients(RecipientType.TO);
        assertNotNull(recipients);
        assertEquals(1, recipients.length);
        assertEquals(recipient, recipients[0].toString());

        assertEquals("Hawkular alert", mimeMessage.getSubject());

        assertEquals("text/plain", mimeMessage.getContentType());
        assertEquals(alertContent, mimeMessage.getContent());
    }
}