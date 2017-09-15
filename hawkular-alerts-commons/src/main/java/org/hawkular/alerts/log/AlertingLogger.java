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
package org.hawkular.alerts.log;

import org.hawkular.commons.log.MsgLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.logging.annotations.ValidIdRange;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@MessageLogger(projectCode = "HAWKALERT")
@ValidIdRange(min = 220000, max = 299999)
public interface AlertingLogger extends MsgLogger {

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 220001, value = "Error processing rules: [%s]")
    void errorProcessingRules(String msg);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 220002, value = "Folder [%s] not found for rules initialization.")
    void errorFolderNotFound(String folder);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 220004, value = "File [%s] not found")
    void warningFileNotFound(String file);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 220005, value = "Folder must be not null.")
    void errorFolderMustBeNotNull();

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 220006, value = "ActionListener [%s] registered")
    void infoActionListenerRegistered(String msg);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 220007, value = "Initial data cannot be processed. Msg: [%s]")
    void errorProcessInitialData(String msg);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 220008, value = "Database Exception. Msg: [%s]")
    void errorDatabaseException(String msg);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 220009, value = "Definitions Service error in [%s]. Msg: [%s]")
    void errorDefinitionsService(String msg, String errorMsg);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 220011, value = "DefinitionsService cannot be initialized. Msg: [%s]")
    void errorCannotUpdateAction(String msg);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 220012, value = "AlertsService cannot be initialized. Msg: [%s]")
    void errorCannotInitializeAlertsService(String msg);

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 220014, value = "Hawkular Alerts deployed in single node mode")
    void infoPartitionManagerDisabled();

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 220015, value = "Hawkular Alerts deployed in distributed mode")
    void infoPartitionManagerEnabled();

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 220016, value = "PartitionManager cannot be initialized. Msg: [%s]")
    void errorCannotInitializePartitionManager(String msg);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 220017, value = "Action cannot be validated. Msg: [%s]")
    void errorCannotValidateAction(String msg);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 220018, value = "Deleting all definitions on tenantId [%s] before import.")
    void warningDeleteDefinitionsTenant(String tenantId);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 220019, value = "Error sending publish message to the bus. Error: [%s]")
    void errorCannotSendPublishMessage(String msg);

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 220020, value = "Init Publish Cache")
    void infoInitPublishCache();

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 220021, value = "Clear Publish Cache")
    void warnClearPublishCache();

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 220022, value = "Publish Cache is disabled")
    void warnDisabledPublishCache();

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 220023, value = "Init Actions Cache")
    void infoInitActionsCache();

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 240001, value = "Plugin [%s] has received an action message: [%s]")
    void infoActionReceived(String actionPlugin, String msg);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 240005, value = "Plugin [%s] cannot process an action message. Error: [%s]")
    void errorCannotProcessMessage(String actionPlugin, String msg);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 240006, value = "Plugin [%s] cannot be started. Error: [%s]")
    void errorCannotBeStarted(String actionPlugin, String msg);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 240007, value = "Plugin [%s] received a message without payload.")
    void warnMessageReceivedWithoutPayload(String actionPlugin);

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
