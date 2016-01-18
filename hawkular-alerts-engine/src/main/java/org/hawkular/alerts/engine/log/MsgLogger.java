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
package org.hawkular.alerts.engine.log;

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
@ValidIdRange(min = 220000, max = 229999)
public interface MsgLogger extends BasicLogger {
    MsgLogger LOGGER = Logger.getMessageLogger(MsgLogger.class, MsgLogger.class.getPackage().getName());

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
    void errorCannotInitializeDefinitionsService(String msg);

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
}
