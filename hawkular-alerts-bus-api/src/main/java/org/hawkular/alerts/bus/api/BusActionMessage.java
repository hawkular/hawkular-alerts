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
package org.hawkular.alerts.bus.api;

import org.hawkular.alerts.actions.api.ActionMessage;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.bus.common.AbstractMessage;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Message sent from the alerts engine through the bus and received by the action plugins architecture.
 *
 * @author Lucas Ponce
 */
public class BusActionMessage extends AbstractMessage implements ActionMessage {

    @JsonInclude
    Action action;

    public BusActionMessage() {
    }

    public BusActionMessage(Action action) {
        this.action = action;
    }

    @Override
    public Action getAction() {
        return action;
    }

    @Override
    public String toString() {
        return "BusActionMessage" + '[' +
                "action=" + action +
                ']';
    }
}
