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
package org.hawkular.alerts.engine.impl;

import java.io.InputStream;
import java.util.Properties;
import org.hawkular.alerts.engine.log.MsgLogger;

/**
 * Read global properties from hawkular-alerts.properties file
 *
 * @author Lucas Ponce
 */
public class AlertProperties {
    private static final MsgLogger msgLog = MsgLogger.LOGGER;

    private static final String ALERTS_CONF = "hawkular-alerts.properties";
    private static Properties alertsProperties = null;

    private AlertProperties() {

    }

    public static String getProperty(String key, String defaultValue) {
        if (alertsProperties == null) {
            initConfiguration();
        }
        return alertsProperties.getProperty(key, defaultValue);
    }

    private static void initConfiguration() {
        try {
            InputStream is = AlertProperties.class.getClassLoader().getResourceAsStream(ALERTS_CONF);
            alertsProperties = new Properties();
            alertsProperties.load(is);
        } catch (Exception e) {
            msgLog.warningFileNotFound(ALERTS_CONF);
        }
    }
}
