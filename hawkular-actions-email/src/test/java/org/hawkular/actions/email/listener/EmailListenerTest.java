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
package org.hawkular.actions.email.listener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Map;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Message.RecipientType;

import com.google.common.collect.ImmutableMap;

import org.hawkular.actions.api.model.ActionMessage;
import org.junit.Test;

/**
 * @author Thomas Segismont
 */
public class EmailListenerTest {

    private EmailListener emailListener = new EmailListener();

    @Test
    public void testMimeMessage() throws Exception {
        String expectedRecipient = "root@localhost";
        String expectedCarbonCopy = "ruth@localhost";
        String expectedContent = "marseille";

        ActionMessage actionMessage = new ActionMessage();
        actionMessage.setProperties(ImmutableMap.of("to", expectedRecipient, "cc", expectedCarbonCopy));
        actionMessage.setMessage(expectedContent);

        Message mimeMessage = emailListener.createMimeMessage(actionMessage);

        assertAddressIs("sender", "noreply@hawkular", mimeMessage.getFrom());
        assertAddressIs("recipient", expectedRecipient, mimeMessage.getRecipients(RecipientType.TO));
        assertAddressIs("carbon copy", expectedCarbonCopy, mimeMessage.getRecipients(RecipientType.CC));

        assertEquals("Unexpected subject", "Hawkular alert", mimeMessage.getSubject());
        assertEquals("Unexpected content type", "text/plain", mimeMessage.getContentType());
        assertEquals("Unexpected content", expectedContent, mimeMessage.getContent());
    }

    @Test
    public void testDescription() throws Exception {
        String expectedRecipient = "root@localhost";
        String expectedCarbonCopy = "ruth@localhost";
        String expectedContent = "marseille";
        String expectedDescription = "calanques";

        ActionMessage actionMessage = new ActionMessage();
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("to", expectedRecipient)
                .put("cc", expectedCarbonCopy)
                .put("description", expectedDescription)
                .build();
        actionMessage.setProperties(properties);
        actionMessage.setMessage(expectedContent);

        Message mimeMessage = emailListener.createMimeMessage(actionMessage);

        assertEquals("Unexpected subject", "Hawkular alert - " + expectedDescription, mimeMessage.getSubject());
    }

    private void assertAddressIs(String type, String expected, Address[] actual) {
        assertNotNull("Expected " + type + " to be set", actual);
        assertEquals("Expected 1 " + type, 1, actual.length);
        assertEquals("Unexpected " + type, expected, actual[0].toString());
    }
}