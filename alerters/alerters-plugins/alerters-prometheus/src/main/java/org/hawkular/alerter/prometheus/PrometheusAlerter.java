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
package org.hawkular.alerter.prometheus;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.hawkular.alerts.alerters.api.Alerter;
import org.hawkular.alerts.alerters.api.AlerterPlugin;
import org.hawkular.alerts.api.json.JsonUtil;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.condition.ExternalCondition;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.model.trigger.TriggerKey;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.api.services.DistributedEvent;
import org.hawkular.commons.properties.HawkularProperties;
import org.jboss.logging.Logger;

/**
 * Manages the Prometheus evaluations and interacts with the Alerts system.  Sets up fixed rate thread
 * jobs to back each ExternalCondition.</p>
 * <pre>
 * Defining a Trigger to be processed by the Prometheus External Alerter:
 *   [Required]    trigger.tags["prometheus"] // the value is ignored
 *   [Optional]    trigger.context["prometheus.frequency"] = "<seconds between queries to Prometheus, default = 120>"
 *                 - note that the same frequency will apply to all Prometheus external conditions on the trigger
 *   [Optional]    trigger.context["prometheus.url"] = "<url, default = global setting>"
 *
 * Defining an ExternalCondition to be processed by the Prometheus External Alerter:
 *   [Required]    the owning trigger must be defined as specified above.
 *   [Required]    externalcondition.alerterId = "prometheus"
 *   [Required]    externalcondition.expression = <BooleanExpression> | <ALERTSExpression>
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Alerter(name = "prometheus")
public class PrometheusAlerter implements AlerterPlugin {
    private final Logger log = Logger.getLogger(PrometheusAlerter.class);

    private static final String PROMETHEUS_ALERTER = "hawkular-alerts.prometheus-alerter";
    private static final String PROMETHEUS_ALERTER_ENV = "PROMETHEUS_ALERTER";
    private static final String PROMETHEUS_ALERTER_DEFAULT = "true";

    // Note, the globally set URL can be overridden in the trigger context
    private static final String PROMETHEUS_URL = "hawkular-alerts.prometheus-url";
    private static final String PROMETHEUS_URL_ENV = "PROMETHEUS_URL";
    private static final String PROMETHEUS_URL_DEFAULT = "http://localhost:9090";
    private static final String URL = "url";

    private static final String PROMETHEUS_THREAD_POOL_SIZE = "hawkular-alerts.prometheus-thread-pool-size";
    private static final String PROMETHEUS_THREAD_POOL_SIZE_ENV = "PROMETHEUS_THREAD_POOL_SIZE";
    private static final String PROMETHEUS_THREAD_POOL_SIZE_DEFAULT = "20";
    private static final String THREAD_POOL_SIZE = "thread-pool-size";

    private static final String CONTEXT_URL = "prometheus.url";
    private static final String CONTEXT_FREQUENCY = "prometheus.frequency";
    private static final String CONTEXT_FREQUENCY_DEFAULT = "120";

    private static final String ALERTER_ID = "prometheus";
    private static final String UTF_8 = "UTF-8";

    private Map<TriggerKey, Trigger> activeTriggers = new ConcurrentHashMap<>();

    ScheduledThreadPoolExecutor expressionExecutor;
    Map<ExternalCondition, ScheduledFuture<?>> expressionFutures = new HashMap<>();

    private boolean prometheusAlerter;
    private Map<String, String> defaultProperties;
    private DefinitionsService definitions;
    private AlertsService alerts;

    private ExecutorService executor;

    @Override
    public void init(DefinitionsService definitions, AlertsService alerts, ExecutorService executor) {
        if (definitions == null || alerts == null || executor == null) {
            throw new IllegalStateException("Prometheus Alerter cannot connect with Hawkular Alerting");
        }
        this.definitions = definitions;
        this.alerts = alerts;
        this.executor = executor;
        prometheusAlerter = Boolean.parseBoolean(HawkularProperties.getProperty(PROMETHEUS_ALERTER,
                PROMETHEUS_ALERTER_ENV, PROMETHEUS_ALERTER_DEFAULT));
        defaultProperties = new HashMap<>();
        defaultProperties.put(URL, HawkularProperties.getProperty(PROMETHEUS_URL, PROMETHEUS_URL_ENV,
                PROMETHEUS_URL_DEFAULT));
        defaultProperties.put(THREAD_POOL_SIZE,
                HawkularProperties.getProperty(PROMETHEUS_THREAD_POOL_SIZE, PROMETHEUS_THREAD_POOL_SIZE_ENV,
                        PROMETHEUS_THREAD_POOL_SIZE_DEFAULT));

        if (prometheusAlerter) {
            log.infof("Starting Hawkular Prometheus External Alerter");
            definitions.registerDistributedListener(events -> refresh(events));
            initialRefresh();
        }
    }

    @Override
    public void stop() {
        log.infof("Stopping Hawkular Prometheus External Alerter");

        if (null != expressionFutures) {
            expressionFutures.values().forEach(f -> f.cancel(true));
        }
        if (null != expressionExecutor) {
            expressionExecutor.shutdown();
            expressionExecutor = null;
        }
    }

    private void initialRefresh() {
        try {
            Collection<Trigger> triggers = definitions.getAllTriggersByTag(ALERTER_ID, "*");
            triggers.stream().forEach(
                    trigger -> activeTriggers.put(new TriggerKey(trigger.getTenantId(), trigger.getId()), trigger));
            update();
        } catch (Exception e) {
            log.error("Failed to fetch Triggers for external conditions.", e);
        }
    }

    private void refresh(Set<DistributedEvent> distEvents) {
        log.debugf("Events received %s", distEvents);
        executor.submit(() -> {
            try {
                for (DistributedEvent distEvent : distEvents) {
                    TriggerKey triggerKey = new TriggerKey(distEvent.getTenantId(), distEvent.getTriggerId());
                    switch (distEvent.getOperation()) {
                        case REMOVE:
                            activeTriggers.remove(triggerKey);
                            break;
                        case ADD:
                            if (activeTriggers.containsKey(triggerKey)) {
                                break;
                            }
                        case UPDATE:
                            Trigger trigger = definitions.getTrigger(distEvent.getTenantId(),
                                    distEvent.getTriggerId());
                            if (trigger != null && trigger.getTags().containsKey(ALERTER_ID)) {
                                if (!trigger.isLoadable()) {
                                    activeTriggers.remove(triggerKey);
                                    break;
                                } else {
                                    activeTriggers.put(triggerKey, trigger);
                                }
                            }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to fetch Triggers for external conditions.", e);
            }

            update();
        });
    }

    private synchronized void update() {
        log.debug("Refreshing External Prometheus Triggers!");
        try {
            if (expressionExecutor == null) {
                expressionExecutor = new ScheduledThreadPoolExecutor(
                        Integer.valueOf(defaultProperties.get(THREAD_POOL_SIZE)));
            }

            Set<ExternalCondition> activeConditions = new HashSet<>();
            log.debugf("Found [%d] active External Prometheus Triggers!", activeTriggers.size());

            // for each trigger look for Prometheus Conditions and start running them
            Collection<Condition> conditions = null;
            for (Trigger trigger : activeTriggers.values()) {
                try {
                    conditions = definitions.getTriggerConditions(trigger.getTenantId(), trigger.getId(), null);
                    log.debugf("Checking [%s] Conditions for external Prometheus trigger [%s]", conditions.size(),
                            trigger.getName());
                } catch (Exception e) {
                    log.error("Failed to fetch Conditions when scheduling prometheus conditions for " + trigger, e);
                    continue;
                }
                for (Condition condition : conditions) {
                    if (condition instanceof ExternalCondition) {
                        ExternalCondition externalCondition = (ExternalCondition) condition;
                        if (ALERTER_ID.equals(externalCondition.getAlerterId())) {
                            log.debugf("Found Prometheus ExternalCondition %s", externalCondition);
                            activeConditions.add(externalCondition);
                            if (expressionFutures.containsKey(externalCondition)) {
                                log.debugf("Skipping, already evaluating %s", externalCondition);

                            } else {
                                try {
                                    // start the job. TODO: Do we need a delay for any reason?
                                    log.debugf("Adding runner for %s", externalCondition);

                                    Map<String, String> properties = new HashMap<>(defaultProperties);
                                    if (trigger.getContext().containsKey(CONTEXT_URL)) {
                                        properties.put(URL, trigger.getContext().get(CONTEXT_URL));
                                    }
                                    String frequency = trigger.getContext().containsKey(CONTEXT_FREQUENCY)
                                            ? trigger.getContext().get(CONTEXT_FREQUENCY) : CONTEXT_FREQUENCY_DEFAULT;

                                    ExpressionRunner runner = new ExpressionRunner(alerts, properties,
                                            externalCondition);
                                    expressionFutures.put(
                                            externalCondition,
                                            expressionExecutor.scheduleAtFixedRate(runner, 0L, Long.valueOf(frequency),
                                                    TimeUnit.SECONDS));
                                } catch (Exception e) {
                                    log.error("Failed to schedule expression for Prometheus condition "
                                            + externalCondition, e);
                                }
                            }
                        }
                    }
                }
            }

            // cancel obsolete expressions
            Set<ExternalCondition> temp = new HashSet<>();
            for (Map.Entry<ExternalCondition, ScheduledFuture<?>> me : expressionFutures.entrySet()) {
                ExternalCondition ec = me.getKey();
                if (!activeConditions.contains(ec)) {
                    log.debugf("Canceling evaluation of obsolete External Prometheus Condition %s", ec);

                    me.getValue().cancel(true);
                    temp.add(ec);
                }
            }
            expressionFutures.keySet().removeAll(temp);
            temp.clear();

        } catch (Exception e) {
            log.error("Failed to fetch Triggers for scheduling Prometheus conditions.", e);
        }
    }

    private static class ExpressionRunner implements Runnable {
        private final Logger log = Logger.getLogger(PrometheusAlerter.ExpressionRunner.class);

        private Map<String, String> properties;
        private AlertsService alertsService;
        private ExternalCondition externalCondition;

        public ExpressionRunner(AlertsService alerts, Map<String, String> properties,
                ExternalCondition externalCondition) {
            super();
            this.alertsService = alerts;
            this.externalCondition = externalCondition;
            this.properties = properties;
        }

        @Override
        public void run() {
            CloseableHttpClient httpClient = null;
            try {
                httpClient = HttpClients.createDefault();

                StringBuffer url = new StringBuffer(properties.get(URL));
                BasicNameValuePair param = new BasicNameValuePair("query", externalCondition.getExpression());
                url.append("/api/v1/query?").append(URLEncodedUtils.format(Arrays.asList(param), UTF_8));
                HttpGet getRequest = new HttpGet(url.toString());
                HttpResponse response = httpClient.execute(getRequest);
                if (response.getStatusLine().getStatusCode() >= 300) {
                    log.warnf("Prometheus GET failed. Status=[%d], message=[%s], url=[%s]",
                            response.getStatusLine().getStatusCode(),
                            response.getStatusLine().getReasonPhrase(), url.toString());
                } else {
                    QueryResponse queryResponse = JsonUtil.getMapper().readValue(response.getEntity().getContent(),
                            QueryResponse.class);
                    if (isValid(queryResponse, response)) {
                        evaluate(queryResponse.getData().getResult());
                    }
                    return;
                }
            } catch (Throwable t) {
                if (log.isDebugEnabled()) {
                    t.printStackTrace();
                }
                log.warnf("Failed data fetch for %s: %s", externalCondition.getExpression(), t.getMessage());
            } finally {
                if (httpClient != null) {
                    try {
                        httpClient.close();
                    } catch (IOException e) {
                        log.debugf(e, "Failed closing http client");
                    }
                }
            }
        }

        private boolean isValid(QueryResponse queryResponse, Object response) {
            if (!"success".equals(queryResponse.getStatus())) {
                log.warnf("Prometheus query did not return success, can not process external condition: [%s]",
                        response);
                return false;
            }
            if (!"vector".equals(queryResponse.getData().getResultType())) {
                log.warnf("resultType [%s] is not yet supported. Supported resultTyes are [vector]: [%s]",
                        queryResponse.getData().getResultType(), response);
                return false;
            }

            return true;
        }

        private void evaluate(QueryResponse.Result[] result) throws Exception {
            for (QueryResponse.Result r : result) {
                // just send all of the time series labels as context for event
                // TODO: Should these be tags or context?
                Map<String, String> context = r.getMetric();

                Event externalEvent = new Event(externalCondition.getTenantId(), UUID.randomUUID().toString(),
                        System.currentTimeMillis(), externalCondition.getDataId(),
                        ALERTER_ID, Arrays.toString(r.getValue()), context, null);
                log.debugf("Sending External Condition Event to Alerting %s", externalEvent);
                alertsService.sendEvents(Collections.singleton(externalEvent));
            }
        }
    }
}
