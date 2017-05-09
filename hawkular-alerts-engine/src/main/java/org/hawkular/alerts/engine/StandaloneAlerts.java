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
import org.hawkular.alerts.engine.impl.CassActionsServiceImpl;
import org.hawkular.alerts.engine.impl.CassAlertsServiceImpl;
import org.hawkular.alerts.engine.impl.CassCluster;
import org.hawkular.alerts.engine.impl.CassDefinitionsServiceImpl;
import org.hawkular.alerts.engine.impl.DataDrivenGroupCacheManager;
import org.hawkular.alerts.engine.impl.DroolsRulesEngineImpl;
import org.hawkular.alerts.engine.impl.ExtensionsServiceImpl;
import org.hawkular.alerts.engine.impl.IncomingDataManagerImpl;
import org.hawkular.alerts.engine.impl.PartitionManagerImpl;
import org.hawkular.alerts.engine.impl.PropertiesServiceImpl;
import org.hawkular.alerts.engine.impl.StatusServiceImpl;
import org.hawkular.alerts.extensions.CepEngine;
import org.hawkular.alerts.extensions.CepEngineImpl;
import org.hawkular.alerts.extensions.EventsAggregationExtension;
import org.hawkular.alerts.filter.CacheClient;
import org.infinispan.manager.EmbeddedCacheManager;
import org.jboss.logging.Logger;

import com.datastax.driver.core.Session;

/**
 * Factory helper for standalone use cases.
 *
 * @author Lucas Ponce
 */
public class StandaloneAlerts {
    private static final Logger log = Logger.getLogger(StandaloneAlerts.class);
    private static StandaloneAlerts instance;
    private static ExecutorService executor;

    private boolean distributed;

    private AlertsThreadFactory threadFactory;

    private EmbeddedCacheManager cacheManager;
    private Session session;
    private CassCluster cassCluster;
    private PropertiesServiceImpl properties;
    private AlertsContext alertsContext;
    private CassActionsServiceImpl actions;
    private CassAlertsServiceImpl alerts;
    private CassDefinitionsServiceImpl definitions;
    private AlertsEngineImpl engine;
    private DroolsRulesEngineImpl rules;
    private PartitionManagerImpl partitionManager;
    private StatusServiceImpl status;
    private DataDrivenGroupCacheManager dataDrivenGroupCacheManager;
    private ExtensionsServiceImpl extensions;
    private IncomingDataManagerImpl incoming;
    private CacheClient dataIdCache;
    private ActionsCacheManager actionsCacheManager;
    private PublishCacheManager publishCacheManager;
    private CepEngineImpl cepEngineImpl;
    private EventsAggregationExtension eventsAggregationExtension;

    private StandaloneAlerts() {

        distributed = IspnCacheManager.isDistributed();
        cacheManager = IspnCacheManager.getCacheManager();

        dataIdCache = new CacheClient();
        threadFactory = new AlertsThreadFactory();
        cassCluster = new CassCluster();
        actions = new CassActionsServiceImpl();
        rules = new DroolsRulesEngineImpl();
        engine = new AlertsEngineImpl();
        definitions = new CassDefinitionsServiceImpl();
        properties = new PropertiesServiceImpl();
        alerts = new CassAlertsServiceImpl();
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

        if (executor == null) {
            executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), threadFactory);
        }

        // Initialization of Cassandra cluster

        cassCluster.initCassCluster();
        session = cassCluster.getSession();

        // Initialization of components

        actions.setSession(session);
        actions.setAlertsContext(alertsContext);
        actions.setDefinitions(definitions);
        actions.setExecutor(executor);
        actions.setActionsCacheManager(actionsCacheManager);

        actionsCacheManager.setDefinitions(definitions);
        actionsCacheManager.setGlobalActionsCache(cacheManager.getCache("globalActions"));

        alerts.setAlertsEngine(engine);
        alerts.setDefinitionsService(definitions);
        alerts.setExecutor(executor);
        alerts.setIncomingDataManager(incoming);
        alerts.setSession(session);
        alerts.setProperties(properties);
        alerts.setActionsService(actions);

        alertsContext.setPartitionManager(partitionManager);

        dataDrivenGroupCacheManager.setDefinitions(definitions);

        dataIdCache.setCache(cacheManager.getCache("publish"));

        definitions.setSession(session);
        definitions.setAlertsEngine(engine);
        definitions.setAlertsContext(alertsContext);
        definitions.setProperties(properties);

        engine.setDefinitions(definitions);
        engine.setActions(actions);
        engine.setRules(rules);
        engine.setPartitionManager(partitionManager);
        engine.setExecutor(executor);
        engine.setExtensionsService(extensions);
        engine.setAlertsService(alerts);

        incoming.setAlertsEngine(engine);
        incoming.setDataDrivenGroupCacheManager(dataDrivenGroupCacheManager);
        incoming.setDataIdCache(dataIdCache);
        incoming.setDefinitionsService(definitions);
        incoming.setExecutor(executor);
        incoming.setPartitionManager(partitionManager);

        partitionManager.setDefinitionsService(definitions);

        actionsCacheManager.setDefinitions(definitions);
        actionsCacheManager.setGlobalActionsCache(cacheManager.getCache("globalActions"));

        publishCacheManager.setProperties(properties);
        publishCacheManager.setDefinitions(definitions);
        publishCacheManager.setPublishDataIdsCache(cacheManager.getCache("dataIds"));
        publishCacheManager.setPublishCache(cacheManager.getCache("publish"));

        status.setPartitionManager(partitionManager);
        status.setSession(session);

        cepEngineImpl.setAlertsService(alerts);
        cepEngineImpl.setExecutor(executor);

        eventsAggregationExtension.setCep(cepEngineImpl);
        eventsAggregationExtension.setDefinitions(definitions);
        eventsAggregationExtension.setExtensions(extensions);
        eventsAggregationExtension.setProperties(properties);
        eventsAggregationExtension.setExecutor(executor);

        // Initialization needs order

        alerts.init();
        definitions.init();
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
            instance.cassCluster.shutdown();
            instance.cacheManager.stop();
        }
    }

    public static Session getSession() {
        if (instance == null) {
            init();
        }
        return instance.cassCluster.getSession();
    }

    public static DefinitionsService getDefinitionsService() {
        if (instance == null) {
            init();
        }
        return instance.definitions;
    }

    public static AlertsService getAlertsService() {
        if (instance == null) {
            init();
        }
        return instance.alerts;
    }

    public static ActionsService getActionsService() {
        if (instance == null) {
            init();
        }
        return instance.actions;
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
            return new Thread(r, "DefaultHawkularAlerts-" + (++count));
        }
    }
}
