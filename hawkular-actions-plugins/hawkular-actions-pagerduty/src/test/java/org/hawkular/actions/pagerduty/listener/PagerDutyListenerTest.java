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
package org.hawkular.actions.pagerduty.listener;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.hawkular.actions.api.model.ActionMessage;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author Thomas Segismont
 */
@RunWith(MockitoJUnitRunner.class)
public class PagerDutyListenerTest {

    @BeforeClass
    public static void configureListener() {
        // TODO configure listener with system properties
    }

    private PagerDutyListener pagerDutyListener;

    @Before
    public void setup() {
        pagerDutyListener = new PagerDutyListener();
    }

    @Test
    public void testSend() throws Exception {
        // TODO test
    }
}
