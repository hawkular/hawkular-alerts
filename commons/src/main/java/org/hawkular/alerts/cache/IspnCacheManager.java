package org.hawkular.alerts.cache;

import java.io.IOException;

import org.hawkular.alerts.properties.AlertProperties;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.jboss.logging.Logger;

/**
 * Load the DefaultCacheManager from infinispan
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class IspnCacheManager {
    private static final Logger log = Logger.getLogger(IspnCacheManager.class);
    private static final String ISPN_CONFIG_DISTRIBUTED = "/alerting-distributed.xml";
    private static final String ISPN_CONFIG_LOCAL = "/alerting-local.xml";
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

    private static synchronized void init() {
        if (cacheManager == null) {
            try {
                distributed = Boolean.valueOf(AlertProperties.getProperty(ALERTS_DISTRIBUTED, ALERTS_DISTRIBUTED_ENV,
                        ALERTS_DISTRIBUTED_DEFAULT));
                cacheManager = new DefaultCacheManager(IspnCacheManager.class
                        .getResourceAsStream(distributed ? ISPN_CONFIG_DISTRIBUTED : ISPN_CONFIG_LOCAL));
            } catch (IOException e) {
                log.error(e);
            }
        }
    }
}
