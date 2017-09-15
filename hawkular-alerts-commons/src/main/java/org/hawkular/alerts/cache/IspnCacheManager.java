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
package org.hawkular.alerts.cache;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;
import org.hawkular.commons.properties.HawkularProperties;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;

/**
 * Load the DefaultCacheManager from infinispan
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class IspnCacheManager {
    private static final MsgLogger log = MsgLogging.getMsgLogger(IspnCacheManager.class);
    private static final String ISPN_CONFIG_DISTRIBUTED = "ispn-alerting-distributed.xml";
    private static final String ISPN_CONFIG_LOCAL = "ispn-alerting-local.xml";
    private static final String ALERTS_DISTRIBUTED = "hawkular-alerts.distributed";
    private static final String ALERTS_DISTRIBUTED_ENV = "HAWKULAR_ALERTS_DISTRIBUTED";
    private static final String ALERTS_DISTRIBUTED_DEFAULT = "false";

    private static EmbeddedCacheManager cacheManager = null;
    private static boolean distributed = false;

    public static EmbeddedCacheManager getCacheManager() {
        if (cacheManager == null) {
            init();
        }
        return cacheManager;
    }

    public static boolean isDistributed() {
        return distributed;
    }

    public static void stop() {
        if (cacheManager != null) {
            cacheManager.stop();
            cacheManager = null;
        }
    }

    private static synchronized void init() {
        if (cacheManager == null) {
            try {
                distributed = Boolean.valueOf(HawkularProperties.getProperty(ALERTS_DISTRIBUTED, ALERTS_DISTRIBUTED_ENV,
                        ALERTS_DISTRIBUTED_DEFAULT));
                String configFilename = distributed ? ISPN_CONFIG_DISTRIBUTED : ISPN_CONFIG_LOCAL;
                Path configPath = Paths.get(System.getProperty("jboss.server.config.dir"), "hawkular");
                File cacheConfigFile = new File(configPath.toFile(), configFilename);
                if (cacheConfigFile.exists()) {
                    cacheManager = new DefaultCacheManager(cacheConfigFile.getPath());
                } else {
                    cacheManager = new DefaultCacheManager(
                            IspnCacheManager.class.getResourceAsStream("/" + configFilename));
                }
            } catch (IOException e) {
                log.error(e);
            }
        }
    }
}
