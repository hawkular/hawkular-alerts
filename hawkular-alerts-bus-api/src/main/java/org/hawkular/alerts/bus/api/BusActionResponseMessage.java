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

import java.util.HashMap;
import java.util.Map;

import org.hawkular.alerts.actions.api.ActionResponseMessage;
import org.hawkular.bus.common.AbstractMessage;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Message sent from the actions plugins through the bus and received by the alerts engine
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class BusActionResponseMessage extends AbstractMessage implements ActionResponseMessage {

    @JsonInclude
    Operation operation;

    @JsonInclude
    Map<String, String> payload;

    public BusActionResponseMessage() {
        this.operation = Operation.RESULT;
        this.payload = new HashMap<>();
    }

    public BusActionResponseMessage(Operation operation) {
        this.operation = operation;
        this.payload = new HashMap<>();
    }

    public BusActionResponseMessage(Operation operation, Map<String, String> payload) {
        this.operation = operation;
        this.payload = new HashMap<>(payload);
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
