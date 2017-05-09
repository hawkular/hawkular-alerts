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
package org.hawkular.alerts.properties;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import org.hawkular.alerts.log.MsgLogger;

/**
 * Read global properties from hawkular-alerts.properties registration
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
        return getProperty(key, null, defaultValue);
    }

    public static String getProperty(String key, String envKey, String defaultValue) {
        if (alertsProperties == null) {
            initConfiguration();
        }
        String value = System.getProperty(key);
        if (value == null) {
            if (envKey != null) {
                value = System.getenv(envKey);
            }
            if (value == null) {
                value = alertsProperties.getProperty(key, defaultValue);
            }
        }
        return value;
    }

    private static void initConfiguration() {
        try {
            String userHome = System.getProperty("user.home");
            InputStream is = null;
            if (userHome != null) {
                File extFile = new File(userHome, "." + ALERTS_CONF);
                if (extFile.exists()) {
                    is = new FileInputStream(extFile);
                }
            }
            String propertiesPath = System.getProperty(ALERTS_CONF);
            if (propertiesPath != null) {
                File extFile = new File(propertiesPath);
                if (extFile.exists()) {
                    is = new FileInputStream(extFile);
                }
            }
            if (is == null) {
                is = AlertProperties.class.getClassLoader().getResourceAsStream(ALERTS_CONF);
            }
            alertsProperties = new Properties();
            alertsProperties.load(is);
        } catch (Exception e) {
            msgLog.warningFileNotFound(ALERTS_CONF);
        }
    }
}
