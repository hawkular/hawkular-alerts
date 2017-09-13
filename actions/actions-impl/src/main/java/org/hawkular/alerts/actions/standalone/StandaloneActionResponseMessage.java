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
package org.hawkular.alerts.actions.standalone;

import java.util.HashMap;
import java.util.Map;

import org.hawkular.alerts.actions.api.ActionResponseMessage;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class StandaloneActionResponseMessage implements ActionResponseMessage {

    Operation operation;

    Map<String, String> payload;

    public StandaloneActionResponseMessage() {
        this.operation = Operation.RESULT;
        this.payload = new HashMap<>();
    }

    public StandaloneActionResponseMessage(Operation operation) {
        this.operation = operation;
        this.payload = new HashMap<>();
    }

    public StandaloneActionResponseMessage(Operation operation, Map<String, String> payload) {
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
