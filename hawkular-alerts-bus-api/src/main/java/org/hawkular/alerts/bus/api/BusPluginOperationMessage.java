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

import java.util.Map;
import java.util.Set;
import org.hawkular.alerts.actions.api.PluginOperationMessage;
import org.hawkular.bus.common.BasicMessage;

/**
 * Message sent from the action plugins architect to the alerts engine through the bus
 *
 * @author Lucas Ponce
 */
public class BusPluginOperationMessage extends BasicMessage implements PluginOperationMessage {

    Operation operation;
    String actionPlugin;
    Set<String> propertyNames;
    Map<String, String> defaultProperties;

    public BusPluginOperationMessage() {
    }

    public BusPluginOperationMessage(Operation operation, String actionPlugin, Set<String> propertyNames,
            Map<String, String> defaultProperties) {
        this.operation = operation;
        this.actionPlugin = actionPlugin;
        this.propertyNames = propertyNames;
        this.defaultProperties = defaultProperties;
    }

    public void setOperation(Operation operation) {
        this.operation = operation;
    }

    public void setActionPlugin(String actionPlugin) {
        this.actionPlugin = actionPlugin;
    }

    public void setPropertyNames(Set<String> propertyNames) {
        this.propertyNames = propertyNames;
    }

    public void setDefaultProperties(Map<String, String> defaultProperties) {
        this.defaultProperties = defaultProperties;
    }

    @Override
    public Operation getOperation() {
        return operation;
    }

    @Override
    public String getActionPlugin() {
        return actionPlugin;
    }

    @Override
    public Set<String> getPropertyNames() {
        return propertyNames;
    }

    @Override
    public Map<String, String> getDefaultProperties() {
        return defaultProperties;
    }
}
