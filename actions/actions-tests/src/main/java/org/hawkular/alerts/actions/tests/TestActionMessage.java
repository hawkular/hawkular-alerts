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
package org.hawkular.alerts.actions.tests;

import org.hawkular.alerts.actions.api.ActionMessage;
import org.hawkular.alerts.api.model.action.Action;

/**
 * Test implementation of PluginMessage interface
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class TestActionMessage implements ActionMessage {
    Action action;

    public TestActionMessage(Action action) {
        this.action = action;
    }

    @Override
    public Action getAction() {
        return action;
    }
}
