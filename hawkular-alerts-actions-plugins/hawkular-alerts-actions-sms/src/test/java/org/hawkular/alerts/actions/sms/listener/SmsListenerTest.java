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
package org.hawkular.alerts.actions.sms.listener;

import static org.hawkular.alerts.actions.sms.listener.SmsListener.ACCOUNT_SID_PROPERTY;
import static org.hawkular.alerts.actions.sms.listener.SmsListener.AUTH_TOKEN_PROPERTY;
import static org.hawkular.alerts.actions.sms.listener.SmsListener.FROM_PROPERTY;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.twilio.sdk.resource.factory.MessageFactory;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.hawkular.alerts.actions.api.model.ActionMessage;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author Thomas Segismont
 */
@RunWith(MockitoJUnitRunner.class)
public class SmsListenerTest {

    @BeforeClass
    public static void configureListener() {
        System.setProperty(ACCOUNT_SID_PROPERTY, "account");
        System.setProperty(AUTH_TOKEN_PROPERTY, "token");
        System.setProperty(FROM_PROPERTY, "+14158141829");
    }

    private MessageFactory messageFactory;
    private SmsListener smsListener;

    @Before
    public void setup() {
        messageFactory = mock(MessageFactory.class);
        smsListener = new SmsListener();
        smsListener.messageFactory = messageFactory;
    }

    @Test
    public void testSend() throws Exception {
        String expectedRecipient = "+14158141828";
        String expectedContent = "marseille";

        ActionMessage actionMessage = new ActionMessage();
        actionMessage.setProperties(ImmutableMap.of("phone", expectedRecipient));
        actionMessage.setMessage(expectedContent);
        smsListener.onBasicMessage(actionMessage);

        List<NameValuePair> expectedParams = new ImmutableList.Builder<NameValuePair>()
            .add(new BasicNameValuePair("To", expectedRecipient))
            .add(new BasicNameValuePair("From", System.getProperty(FROM_PROPERTY)))
            .add(new BasicNameValuePair("Body", expectedContent))
            .build();
        verify(messageFactory, times(1)).create(eq(expectedParams));
    }
}