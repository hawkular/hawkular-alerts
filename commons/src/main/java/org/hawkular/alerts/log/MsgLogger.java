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
package org.hawkular.alerts.log;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

/**
 * Common log for INFO, WARN, ERROR and FATAL messages.
 *
 * @author Lucas Ponce
 */
public class MsgLogger {
    private Logger instance;

    private MsgLogger(Class clazz) {
        instance = LoggerFactory.getLogger(clazz);
    }

    public static MsgLogger getLogger(Class clazz) {
        return new MsgLogger(clazz);
    }

    public void error(Object o) {
        instance.error(o.toString());
    }

    public void error(Throwable e) {
        instance.error(e.getMessage(), e);
    }

    public void error(String msg, Throwable e) {
        instance.error(msg, e);
    }

    public void error(String msg, Object o) {
        instance.error(msg, o);
    }

    public void error(String msg, Object o, Object o1) {
        instance.error(msg, o, o1);
    }

    public void error(String msg, Object o, Object o1, Object o2) {
        instance.error(msg, o, o1, o2);
    }

    public void warn(Object o) {
        instance.warn(o.toString());
    }

    public void warn(String msg, Object o) {
        instance.warn(msg, o);
    }

    public void warn(String msg, Object o, Object o1) {
        instance.warn(msg, o, o1);
    }

    public void warn(String msg, Object o, Object o1, Object o2) {
        instance.warn(msg, o, o1, o2);
    }

    public void warn(String msg, Object o, Object o1, Object o2, Object o3) {
        instance.warn(msg, o, o1, o2, o3);
    }

    public void info(Object o) {
        instance.info(o.toString());
    }

    public void info(String msg, Object o) {
        instance.info(msg, o);
    }

    public void info(String msg, Object o, Object o1) {
        instance.info(msg, o, o1);
    }

    public boolean isDebugEnabled() {
        return instance.isDebugEnabled();
    }

    public void debug(Object o) {
        instance.debug(o.toString());
    }

    public void debug(String msg, Object o) {
        instance.debug(msg, o);
    }

    public void debug(String msg, Object o, Object o1) {
        instance.debug(msg, o, o1);
    }

    public void debug(String msg, Object o, Object o1, Object o2) {
        instance.debug(msg, o, o1, o2);
    }

    public boolean isTraceEnabled() {
        return instance.isTraceEnabled();
    }

    public void trace(String msg, Object o) {
        instance.trace(msg, o);
    }

    public void trace(String msg, Object o, Object o1) {
        instance.trace(msg, o, o1);
    }

    public void errorProcessingRules(String msg) {
        instance.error("HWKALERT-220001 Error processing rules: [{}].", msg);
    }

    public void errorFolderNotFound(String folder) {
        instance.error("HWKALERT-220002 Folder [{}] not found for rules initialization.", folder);
    }

    public void warningFileNotFound(String file) {
        instance.warn("[220004] File [{}] not found", file);
    }

    public void errorFolderMustBeNotNull() {
        instance.error("[220005] Folder must be not null.");
    }

    public void infoActionListenerRegistered(String msg) {
        instance.info("HWKALERT-220006 ActionListener [{}] registered.", msg);
    }

    public void errorProcessInitialData(String msg) {
        instance.error("HWKALERT-220007 Initial data cannot be processed. Msg: [{}].", msg);
    }

    public void errorDatabaseException(String msg) {
        instance.error("HWKALERT-220008 Database Exception. Msg: [{}].", msg);
    }

    public void errorDefinitionsService(String msg, String errorMsg) {
        instance.error("HWKALERT-220009 Definitions Service error in [{}]. Msg: [{}].", msg, errorMsg);
    }

    public void errorCannotUpdateAction(String msg) {
        instance.error("HWKALERT-220011 DefinitionsService cannot be initialized. Msg: [{}].", msg);
    }

    public void errorCannotInitializeAlertsService(String msg) {
        instance.error("HWKALERT-220012 AlertsService cannot be initialized. Msg: [{}].", msg);
    }

    public void infoPartitionManagerDisabled() {
        instance.info("HWKALERT-220014 Hawkular Alerting deployed in single node mode.");
    }

    public void infoPartitionManagerEnabled() {
        instance.info("HWKALERT-220015 Hawkular Alerts deployed in distributed mode.");
    }

    public void errorCannotInitializePartitionManager(String msg) {
        instance.error("HWKALERT-220016 PartitionManager cannot be initialized. Msg: [{}].", msg);
    }

    public void errorCannotValidateAction(String msg) {
        instance.error("HWKALERT-220017 Action cannot be validated. Msg: [{}].", msg);
    }

    public void warningDeleteDefinitionsTenant(String tenantId) {
        instance.warn("HWKALERT-220018 Deleting all definitions on tenantId [{}] before import.", tenantId);
    }

    public void errorCannotSendPublishMessage(String msg) {
        instance.error("HWKALERT-220019 Error sending publish message to the bus. Error: [{}].", msg);
    }

    public void infoInitPublishCache() {
        instance.info("HWKALERT-220020 Init Publish Cache.");
    }

    public void warnClearPublishCache() {
        instance.warn("HWKALERT-220021 Clear Publish Cache.");
    }

    public void warnDisabledPublishCache() {
        instance.warn("HWKALERT-220022 Publish Cache is disabled.");
    }

    public void infoInitActionsCache() {
        instance.info("HWKALERT-220023 Init Actions Cache.");
    }

    public void infoActionReceived(String actionPlugin, String msg) {
        instance.info("HWKALERT-240001 Plugin [{}] has received an action message: [{}].", actionPlugin, msg);
    }

    public void errorCannotProcessMessage(String actionPlugin, String msg) {
        instance.error("HWKALERT-240005 Plugin [{}] cannot process an action message. Error: [{}].", actionPlugin, msg);
    }

    public void errorCannotBeStarted(String actionPlugin, String msg) {
        instance.error("HWKALERT-240006 Plugin [{}] cannot be started. Error: [{}].", actionPlugin, msg);
    }

    public void warnMessageReceivedWithoutPayload(String actionPlugin) {
        instance.warn("HWKALERT-240007 Plugin [{}] received a message without payload.", actionPlugin);
    }

    public void infoActionPluginRegistration(String actionPlugin) {
        instance.info("HWKALERT-270001 Action plugin [{}] registered", actionPlugin);
    }

    public void warnCannotAccessToDefinitionsService() {
        instance.warn("HWKALERT-270002 Cannot access to DefinitionsService.");
    }

    public void warnNoPluginsFound() {
        instance.warn("HWKALERT-270003 No ActionPluginListener found on plugin deployment.");
    }

    public void errorProcessingAction(String msg) {
        instance.error("HWKALERT-270004 Error processing action. Description: [{}]");
    }

    public void errorCannotRegisterPlugin(String actionPlugin, String msg) {
        instance.error("HWKALERT-270005 Plugin [{}] cannot be registered into the engine. Error: [{}].");
    }

    public void warnMessageReceivedWithoutPluginInfo() {
        instance.warn("HWKALERT-270007 Plugin received a message without plugin info.");
    }

    public void warnActionResponseMessageWithoutPayload() {
        instance.warn("HWKALERT-270008 ActionResponse message without payload.");
    }
}
