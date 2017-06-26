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
package org.hawkular.alerts.engine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.hawkular.alerts.api.services.ActionsService;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.api.services.StatusService;
import org.hawkular.alerts.cache.IspnCacheManager;
import org.hawkular.alerts.engine.cache.ActionsCacheManager;
import org.hawkular.alerts.engine.cache.PublishCacheManager;
import org.hawkular.alerts.engine.impl.AlertsContext;
import org.hawkular.alerts.engine.impl.AlertsEngineImpl;
import org.hawkular.alerts.engine.impl.DataDrivenGroupCacheManager;
import org.hawkular.alerts.engine.impl.DroolsRulesEngineImpl;
import org.hawkular.alerts.engine.impl.ExtensionsServiceImpl;
import org.hawkular.alerts.engine.impl.IncomingDataManagerImpl;
import org.hawkular.alerts.engine.impl.PartitionManagerImpl;
import org.hawkular.alerts.engine.impl.PropertiesServiceImpl;
import org.hawkular.alerts.engine.impl.StatusServiceImpl;
import org.hawkular.alerts.engine.impl.cass.CassActionsServiceImpl;
import org.hawkular.alerts.engine.impl.cass.CassAlertsServiceImpl;
import org.hawkular.alerts.engine.impl.cass.CassCluster;
import org.hawkular.alerts.engine.impl.cass.CassDefinitionsServiceImpl;
import org.hawkular.alerts.engine.impl.ispn.IspnActionsServiceImpl;
import org.hawkular.alerts.engine.impl.ispn.IspnAlertsServiceImpl;
import org.hawkular.alerts.engine.impl.ispn.IspnDefinitionsServiceImpl;
import org.hawkular.alerts.extensions.CepEngineImpl;
import org.hawkular.alerts.extensions.EventsAggregationExtension;
import org.hawkular.alerts.filter.CacheClient;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;
import org.hawkular.commons.properties.HawkularProperties;
import org.infinispan.manager.EmbeddedCacheManager;

import com.datastax.driver.core.Session;

/**
 * Factory helper for standalone use cases.
 *
 * @author Lucas Ponce
 */
public class StandaloneAlerts {
    private static final MsgLogger log = MsgLogging.getMsgLogger(StandaloneAlerts.class);
    private static final String ALERTS_BACKEND = "hawkular-alerts.backend";
    private static final String ALERTS_BACKEND_DEFAULT = "cassandra";
    private static StandaloneAlerts instance;
    private static ExecutorService executor;
    private static boolean cass;

    private boolean distributed;

    private AlertsThreadFactory threadFactory;

    private ActionsCacheManager actionsCacheManager;
    private AlertsContext alertsContext;
    private AlertsEngineImpl engine;
    private CacheClient dataIdCache;
    private CassActionsServiceImpl cassActions;
    private CassAlertsServiceImpl cassAlerts;
    private CassCluster cassCluster;
    private CassDefinitionsServiceImpl cassDefinitions;
    private CepEngineImpl cepEngineImpl;
    private DataDrivenGroupCacheManager dataDrivenGroupCacheManager;
    private DroolsRulesEngineImpl rules;
    private EmbeddedCacheManager cacheManager;
    private EventsAggregationExtension eventsAggregationExtension;
    private ExtensionsServiceImpl extensions;
    private IncomingDataManagerImpl incoming;
    private IspnActionsServiceImpl ispnActions;
    private IspnAlertsServiceImpl ispnAlerts;
    private IspnDefinitionsServiceImpl ispnDefinitions;
    private Session cassSession;
    private StatusServiceImpl status;
    private PartitionManagerImpl partitionManager;
    private PropertiesServiceImpl properties;
    private PublishCacheManager publishCacheManager;

    private StandaloneAlerts() {
        cass = HawkularProperties.getProperty(ALERTS_BACKEND, ALERTS_BACKEND_DEFAULT).equals(ALERTS_BACKEND_DEFAULT);
        distributed = IspnCacheManager.isDistributed();
        cacheManager = IspnCacheManager.getCacheManager();

        threadFactory = new AlertsThreadFactory();
        if (executor == null) {
            executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), threadFactory);
        }

        dataIdCache = new CacheClient();
        rules = new DroolsRulesEngineImpl();
        engine = new AlertsEngineImpl();
        properties = new PropertiesServiceImpl();
        alertsContext = new AlertsContext();
        partitionManager = new PartitionManagerImpl();
        status = new StatusServiceImpl();
        extensions = new ExtensionsServiceImpl();
        dataDrivenGroupCacheManager = new DataDrivenGroupCacheManager();
        incoming = new IncomingDataManagerImpl();
        actionsCacheManager = new ActionsCacheManager();
        publishCacheManager = new PublishCacheManager();
        cepEngineImpl = new CepEngineImpl();
        eventsAggregationExtension = new EventsAggregationExtension();

        if (cass) {
            log.info("Hawkular Alerting uses Cassandra backend");
            cassActions = new CassActionsServiceImpl();
            cassAlerts = new CassAlertsServiceImpl();
            cassCluster = new CassCluster();
            cassDefinitions = new CassDefinitionsServiceImpl();

            // Initialization of Cassandra cluster

            cassCluster.initCassCluster();
            cassSession = cassCluster.getSession();

            // Initialization of components

            cassActions.setSession(cassSession);
            cassActions.setAlertsContext(alertsContext);
            cassActions.setDefinitions(cassDefinitions);
            cassActions.setExecutor(executor);
            cassActions.setActionsCacheManager(actionsCacheManager);

            cassAlerts.setAlertsEngine(engine);
            cassAlerts.setDefinitionsService(cassDefinitions);
            cassAlerts.setExecutor(executor);
            cassAlerts.setIncomingDataManager(incoming);
            cassAlerts.setSession(cassSession);
            cassAlerts.setProperties(properties);
            cassAlerts.setActionsService(cassActions);

            cassDefinitions.setSession(cassSession);
            cassDefinitions.setAlertsEngine(engine);
            cassDefinitions.setAlertsContext(alertsContext);
            cassDefinitions.setProperties(properties);
        } else {
            log.info("Hawkular Alerting uses Infinispan backend");
            ispnActions = new IspnActionsServiceImpl();
            ispnAlerts = new IspnAlertsServiceImpl();
            ispnDefinitions = new IspnDefinitionsServiceImpl();

            ispnActions.setActionsCacheManager(actionsCacheManager);
            ispnActions.setAlertsContext(alertsContext);
            ispnActions.setDefinitions(ispnDefinitions);

            ispnAlerts.setActionsService(ispnActions);
            ispnAlerts.setAlertsEngine(engine);
            ispnAlerts.setDefinitionsService(ispnDefinitions);
            ispnAlerts.setIncomingDataManager(incoming);
            ispnAlerts.setProperties(properties);

            ispnDefinitions.setAlertsEngine(engine);
            ispnDefinitions.setAlertsContext(alertsContext);
            ispnDefinitions.setProperties(properties);
        }

        actionsCacheManager.setDefinitions(cass ? cassDefinitions : ispnDefinitions);
        actionsCacheManager.setGlobalActionsCache(cacheManager.getCache("globalActions"));

        alertsContext.setPartitionManager(partitionManager);

        dataDrivenGroupCacheManager.setDefinitions(cass ? cassDefinitions : ispnDefinitions);

        dataIdCache.setCache(cacheManager.getCache("publish"));

        engine.setActions(cass ? cassActions : ispnActions);
        engine.setAlertsService(cass ? cassAlerts : ispnAlerts);
        engine.setDefinitions(cass ? cassDefinitions : ispnDefinitions);
        engine.setExecutor(executor);
        engine.setExtensionsService(extensions);
        engine.setPartitionManager(partitionManager);
        engine.setRules(rules);

        incoming.setAlertsEngine(engine);
        incoming.setDataDrivenGroupCacheManager(dataDrivenGroupCacheManager);
        incoming.setDataIdCache(dataIdCache);
        incoming.setDefinitionsService(cass ? cassDefinitions : ispnDefinitions);
        incoming.setExecutor(executor);
        incoming.setPartitionManager(partitionManager);

        partitionManager.setDefinitionsService(cassDefinitions);

        actionsCacheManager.setDefinitions(cass ? cassDefinitions : ispnDefinitions);
        actionsCacheManager.setGlobalActionsCache(cacheManager.getCache("globalActions"));

        publishCacheManager.setDefinitions(cass ? cassDefinitions : ispnDefinitions);
        publishCacheManager.setProperties(properties);
        publishCacheManager.setPublishCache(cacheManager.getCache("publish"));
        publishCacheManager.setPublishDataIdsCache(cacheManager.getCache("dataIds"));

        status.setPartitionManager(partitionManager);

        cepEngineImpl.setAlertsService(cass ? cassAlerts : ispnAlerts);
        cepEngineImpl.setExecutor(executor);

        eventsAggregationExtension.setCep(cepEngineImpl);
        eventsAggregationExtension.setDefinitions(cass ? cassDefinitions : ispnDefinitions);
        eventsAggregationExtension.setExtensions(extensions);
        eventsAggregationExtension.setProperties(properties);
        eventsAggregationExtension.setExecutor(executor);

        // Initialization needs order

        if (cass) {
            cassAlerts.init();
            cassDefinitions.init();
        } else {
            ispnAlerts.init();
            ispnDefinitions.init();
            ispnActions.init();
        }
        partitionManager.init();
        alertsContext.init();
        dataDrivenGroupCacheManager.init();
        actionsCacheManager.init();
        publishCacheManager.init();
        extensions.init();
        engine.initServices();
        eventsAggregationExtension.init();
    }

    private static synchronized void init() {
        instance = new StandaloneAlerts();
    }

    public static ExecutorService getExecutor() {
        return executor;
    }

    public static void setExecutor(ExecutorService executor) {
        StandaloneAlerts.executor = executor;
    }

    public static void start() {
        init();
    }

    public static void stop() {
        if (instance != null) {
            instance.engine.shutdown();
            instance.partitionManager.shutdown();
            if (cass) {
                instance.cassCluster.shutdown();
            }
            IspnCacheManager.stop();
            instance = null;
        }
    }

    public static DefinitionsService getDefinitionsService() {
        if (instance == null) {
            init();
        }
        return cass ? instance.cassDefinitions : instance.ispnDefinitions;
    }

    public static AlertsService getAlertsService() {
        if (instance == null) {
            init();
        }
        return cass ? instance.cassAlerts : instance.ispnAlerts;
    }

    public static ActionsService getActionsService() {
        if (instance == null) {
            init();
        }
        return cass ? instance.cassActions : instance.ispnActions;
    }

    public static StatusService getStatusService() {
        if (instance == null) {
            init();
        }
        return instance.status;
    }

    public class AlertsThreadFactory implements ThreadFactory {
        private int count = 0;

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "HawkularAlerts-" + (++count));
        }
    }
}
