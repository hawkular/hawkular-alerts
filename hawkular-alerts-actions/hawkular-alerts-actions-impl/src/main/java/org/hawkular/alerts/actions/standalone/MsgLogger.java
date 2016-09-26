/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.logging.annotations.ValidIdRange;

/**
 * Common log for INFO, WARN, ERROR and FATAL messages.
 *
 * @author Lucas Ponce
 */
@MessageLogger(projectCode = "HAWKALERT")
@ValidIdRange(min = 270000, max = 279999)
public interface MsgLogger extends BasicLogger {
    MsgLogger LOGGER = Logger.getMessageLogger(MsgLogger.class, MsgLogger.class.getPackage().getName());

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 270001, value = "Action plugin [%s] registered")
    void infoActionPluginRegistration(String actionPlugin);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 270002, value = "Cannot access to DefinitionsService")
    void warnCannotAccessToDefinitionsService();

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 270003, value = "No ActionPluginListener found on plugin deployment")
    void warnNoPluginsFound();

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 270004, value = "Error processing action. Description: [%s]")
    void errorProcessingAction(String msg);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 270005, value = "Plugin [%s] cannot be registered into the engine. Error: [%s]")
    void errorCannotRegisterPlugin(String actionPlugin, String msg);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 270007, value = "Plugin received a message without plugin info.")
    void warnMessageReceivedWithoutPluginInfo();

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 270008, value = "ActionResponse message without payload")
    void warnActionResponseMessageWithoutPayload();

}
