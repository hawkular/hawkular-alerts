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
package org.hawkular.alerts.actions.standalone;

import javax.naming.InitialContext;

import org.hawkular.alerts.actions.api.ActionPluginSender;
import org.hawkular.alerts.actions.api.OperationMessage;
import org.hawkular.alerts.actions.api.OperationMessage.Operation;
import org.hawkular.alerts.api.json.JsonUtil;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.services.ActionsService;
import org.jboss.logging.Logger;

/**
 * Main standalone sender for plugins implementations
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class StandaloneActionPluginSender implements ActionPluginSender {
    public static final String ACTIONS_SERVICE = "java:app/hawkular-alerts-rest/CassActionsServiceImpl";
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(StandaloneActionPluginListener.class);

    private InitialContext ctx;
    private ActionsService actions;

    private String actionPlugin;

    public StandaloneActionPluginSender(String actionPlugin) {
        this.actionPlugin = actionPlugin;
    }

    private void init() throws Exception {
        if (ctx == null) {
            ctx = new InitialContext();
        }
        if (actions == null) {
            actions = (ActionsService) ctx.lookup(ACTIONS_SERVICE);
        }
    }

    @Override
    public OperationMessage createMessage(Operation operation) {
        if (operation == null) {
            return new StandaloneOperationMessage();
        }
        return new StandaloneOperationMessage(operation);
    }

    @Override
    public void send(OperationMessage msg) throws Exception {
        init();
        log.debugf("Message received: [%s]", msg);
        if (msg != null && msg.getPayload().containsKey("action")) {
            String jsonAction = msg.getPayload().get("action");
            Action updatedAction = JsonUtil.fromJson(jsonAction, Action.class);
            actions.updateResult(updatedAction);
            log.debugf("Operation message received from plugin [%s] with payload [%s]",
                    updatedAction.getActionPlugin(), updatedAction.getResult());
        } else {
            msgLog.warnOperationMessageWithoutPayload();
        }
    }
}
