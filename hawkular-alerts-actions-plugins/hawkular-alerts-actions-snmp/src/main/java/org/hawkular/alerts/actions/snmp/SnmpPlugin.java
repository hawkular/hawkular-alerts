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
package org.hawkular.alerts.actions.snmp;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.actions.api.ActionPlugin;
import org.hawkular.alerts.actions.api.ActionPluginListener;
import org.hawkular.alerts.actions.api.MsgLogger;
import org.hawkular.alerts.actions.api.PluginMessage;

/**
 * An example of listener for snmp processing.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@ActionPlugin(name = "snmp")
public class SnmpPlugin implements ActionPluginListener {
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    Map<String, String> defaultProperties = new HashMap<>();

    public SnmpPlugin() {
        defaultProperties.put("host", "localhost");
        defaultProperties.put("port", "1234");
        defaultProperties.put("oid", "default-oid");
        defaultProperties.put("description", "default-description");
    }

    @Override
    public Set<String> getProperties() {
        return defaultProperties.keySet();
    }

    @Override
    public Map<String, String> getDefaultProperties() {
        return defaultProperties;
    }

    @Override
    public void process(PluginMessage msg) throws Exception {
        msgLog.infoActionReceived("snmp", msg.toString());
    }
}
