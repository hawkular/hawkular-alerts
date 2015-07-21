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
package org.hawkular.alerts.actions.aerogear.listener;

import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Collections;

import com.google.common.collect.ImmutableMap;

import org.hawkular.alerts.actions.api.model.ActionMessage;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import org.jboss.aerogear.unifiedpush.PushSender;

/**
 * @author Thomas Segismont
 */
@RunWith(MockitoJUnitRunner.class)
public class AerogearListenerTest {

    @BeforeClass
    public static void configureListener() {
        System.setProperty(AerogearListener.ROOT_SERVER_URL_PROPERTY, "http://localhost:9191/ag-push");
        System.setProperty(AerogearListener.APPLICATION_ID_PROPERTY, "4d564d56qs4056-d0sq564065");
        System.setProperty(AerogearListener.MASTER_SECRET_PROPERTY, "sddqs--sqd-qs--d-qs000dsq0d");
    }

    private PushSender pushSender;
    private AerogearListener aerogearListener;

    @Before
    public void setup() {
        pushSender = mock(PushSender.class);
        aerogearListener = new AerogearListener();
        aerogearListener.pushSender = pushSender;
    }

    @Test
    public void testSend() throws Exception {
        String expectedAlias = "GeorgeAbitbol";
        String expectedContent = "marseille";

        ActionMessage actionMessage = new ActionMessage();
        actionMessage.setProperties(ImmutableMap.of("alias", expectedAlias));
        actionMessage.setMessage(expectedContent);
        aerogearListener.onBasicMessage(actionMessage);

        verify(pushSender, times(1)).send(argThat(UnifiedMessageMatcher
                .matchesUnifiedMessage(expectedAlias, expectedContent)));
    }

    @Test
    public void testBroadcast() throws Exception {
        String expectedContent = "marseille";

        ActionMessage actionMessage = new ActionMessage();
        actionMessage.setProperties(Collections.emptyMap());
        actionMessage.setMessage(expectedContent);
        aerogearListener.onBasicMessage(actionMessage);

        verify(pushSender, times(1)).send(argThat(UnifiedMessageMatcher.matchesUnifiedMessage(null, expectedContent)));
    }
}