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
package org.hawkular.alerts.actions.bus;

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
@ValidIdRange(min = 260000, max = 269999)
public interface MsgLogger extends BasicLogger {
    MsgLogger LOGGER = Logger.getMessageLogger(MsgLogger.class, MsgLogger.class.getPackage().getName());

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 260001, value = "Plugin [%s] has received a action message: [%s]")
    void infoActionReceived(String actionPlugin, String msg);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 260002, value = "No ActionPluginListener found on plugin deployment")
    void warnNoPluginsFound();

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 260003, value = "Plugin [%s] has sent a registration request: [%s]")
    void infoPluginRegistration(String actionPlugin, String msg);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 260005, value = "Plugin cannot send a message to the bus. Error: [%s]")
    void errorCannotSendMessage(String msg);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 260007, value = "Plugin received a message without plugin info.")
    void warnMessageReceivedWithoutPluginInfo();

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 260008, value = "Cannot connect to the broker. Attempt [%s]. Trying in [%s] ms. Error: [%s]")
    void warnCannotConnectBroker(int attempt, int next, String msg);
}
