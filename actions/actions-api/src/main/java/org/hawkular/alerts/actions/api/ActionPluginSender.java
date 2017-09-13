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
package org.hawkular.alerts.actions.api;

/**
 * A sender interface that allows to a plugin to send operations message to the alerts engine.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public interface ActionPluginSender {

    /**
     * Factory to create new ActionResponseMessage messages.
     * There could be different implementation of messages depending on the context (bus, standalone) so
     * new instances of ActionResponseMessage should be created through this factory method.
     *
     * @param operation the type of operation
     * @return a new ActionResponseMessage
     */
    ActionResponseMessage createMessage(ActionResponseMessage.Operation operation);

    /**
     * Send a message to the engine.
     * Plugin should not have access to the implementation used.
     *
     * @param msg the response message to be sent
     * @throws Exception any problem
     */
    void send(ActionResponseMessage msg) throws Exception;
}
