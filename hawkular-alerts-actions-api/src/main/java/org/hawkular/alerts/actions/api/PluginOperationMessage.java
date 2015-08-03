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
package org.hawkular.alerts.actions.api;

import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A message sent to the alerts engine from the plugin
 * It defines a code of operation and payloads
 *
 * It used for plugin registration but additional operations should be supported
 *
 * @author Lucas Ponce
 */
public interface PluginOperationMessage {

    enum Operation {
        REGISTRATION
    }

    @JsonInclude
    Operation getOperation();

    @JsonInclude
    String getActionPlugin();

    @JsonInclude
    Set<String> getPropertyNames();

    @JsonInclude
    Map<String, String> getDefaultProperties();
}
