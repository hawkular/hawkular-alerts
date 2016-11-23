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

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.enterprise.concurrent.ManagedExecutorService;

import org.hawkular.alerts.api.services.ActionsService;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.engine.impl.AlertsContext;
import org.hawkular.alerts.engine.impl.AlertsEngineImpl;
import org.hawkular.alerts.engine.impl.CassActionsServiceImpl;
import org.hawkular.alerts.engine.impl.CassAlertsServiceImpl;
import org.hawkular.alerts.engine.impl.CassDefinitionsServiceImpl;
import org.hawkular.alerts.engine.impl.DroolsRulesEngineImpl;
import org.hawkular.alerts.engine.impl.PropertiesServiceImpl;
import org.jboss.logging.Logger;

import com.datastax.driver.core.Session;

/**
 * Factory helper for standalone use cases.
 *
 * @author Lucas Ponce
 */
public class StandaloneAlerts {
    private static final int INIT_TIME_COUNT = 10;
    private static final int INIT_TIME_SLEEP = 500;

    private final Logger log = Logger.getLogger(StandaloneAlerts.class);

    private static StandaloneAlerts instance = null;

    private PropertiesServiceImpl propertiesService = null;
    private AlertsContext alertsContext = null;
    private CassActionsServiceImpl actions = null;
    private CassAlertsServiceImpl alerts = null;
    private CassDefinitionsServiceImpl definitions = null;
    private AlertsEngineImpl engine = null;
    private DroolsRulesEngineImpl rules = null;
    private StandaloneExecutorService executor = new StandaloneExecutorService();

    private StandaloneAlerts(Session session) {
        actions = new CassActionsServiceImpl();
        rules = new DroolsRulesEngineImpl();
        engine = new AlertsEngineImpl();
        definitions = new CassDefinitionsServiceImpl();
        propertiesService = new PropertiesServiceImpl();
        alerts = new CassAlertsServiceImpl();
        alerts.setSession(session);
        alerts.setExecutor(executor);
        alerts.setProperties(propertiesService);
        alerts.init();
        alertsContext = new AlertsContext();

        definitions.setSession(session);
        definitions.setAlertsEngine(engine);
        definitions.setAlertsContext(alertsContext);
        definitions.setProperties(propertiesService);
        definitions.init();

        actions.setSession(session);
        actions.setAlertsContext(alertsContext);
        actions.setDefinitions(definitions);
        actions.setExecutor(executor);

        engine.setDefinitions(definitions);
        engine.setActions(actions);
        engine.setRules(rules);

        log.debug("Waiting for initialization...");
        try {
            for (int i = 0; i < INIT_TIME_COUNT; i++) {
                log.debug(".");
                Thread.sleep(INIT_TIME_SLEEP);
            }
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
    }

    public static synchronized DefinitionsService getDefinitionsService(Session session) {
        if (instance == null) {
            instance = new StandaloneAlerts(session);
        }
        return instance.definitions;
    }

    public static synchronized AlertsService getAlertsService(Session session) {
        if (instance == null) {
            instance = new StandaloneAlerts(session);
        }
        return instance.alerts;
    }

    public static synchronized ActionsService getActionsService(Session session) {
        if (instance == null) {
            instance = new StandaloneAlerts(session);
        }
        return instance.actions;
    }

    public static class StandaloneExecutorService implements ManagedExecutorService {

        private ExecutorService executor;

        public StandaloneExecutorService() {
            executor = Executors.newSingleThreadExecutor();
        }

        @Override
        public void shutdown() {
            executor.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return executor.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return executor.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return executor.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return executor.awaitTermination(timeout, unit);
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            return executor.submit(task);
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            return executor.submit(task, result);
        }

        @Override
        public Future<?> submit(Runnable task) {
            return executor.submit(task);
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
                throws InterruptedException {
            return executor.invokeAll(tasks);
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
                throws InterruptedException {
            return executor.invokeAll(tasks, timeout, unit);
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
                throws InterruptedException, ExecutionException {
            return executor.invokeAny(tasks);
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return executor.invokeAny(tasks, timeout, unit);
        }

        @Override
        public void execute(Runnable command) {
            executor.execute(command);
        }
    }
}
