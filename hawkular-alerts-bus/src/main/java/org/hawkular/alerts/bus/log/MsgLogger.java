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
package org.hawkular.alerts.bus.log;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.Message;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.logging.annotations.ValidIdRange;

/**
 * Common log for INFO, WARN, ERROR and FATAL messages.
 *
 * @author Lucas Ponce
 */
@MessageLogger(projectCode = "HAWKALERT")
@ValidIdRange(min = 210000, max = 219999)
public interface MsgLogger extends BasicLogger {
    MsgLogger LOGGER = Logger.getMessageLogger(MsgLogger.class, MsgLogger.class.getPackage().getName());

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 210001, value = "Notifier type registration received without op.")
    void warnNotifierTypeRegistrationWithoutOp();

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 210002, value = "NotifierType [%s] registered")
    void infoNotifierTypeRegistration(String notifierType);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 210003, value = "NotifierType [%s] is already registered")
    void warnNotifierTypeAlreadyRegistered(String notifierType);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 210004, value = "Notifier type [%s] registration received with unkwown op [%s]")
    void warnNotifierTypeRegistrationWithUnknownOp(String notifierType, String op);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 210005, value = "Cannot connect to hawkular bus")
    void warnCannotConnectToBus();

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 210006, value = "Sent notification message [%s] to the bus")
    void infoSentNotificationMessage(String msg);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 210007, value = "Cannot access to DefinitionsService")
    void warnCannotAccessToDefinitionsService();

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 210008, value = "Error processing notification. Description: [%s]")
    void errorProcessingNotification(String msg);

}
