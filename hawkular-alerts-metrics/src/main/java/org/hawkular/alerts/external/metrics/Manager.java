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
package org.hawkular.alerts.external.metrics;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.EJB;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;

import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.condition.ExternalCondition;
import org.hawkular.alerts.api.model.data.StringData;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.DefinitionsEvent;
import org.hawkular.alerts.api.services.DefinitionsEvent.EventType;
import org.hawkular.alerts.api.services.DefinitionsListener;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.external.metrics.Expression.Func;
import org.hawkular.metrics.core.api.Aggregate;
import org.hawkular.metrics.core.api.MetricId;
import org.hawkular.metrics.core.api.MetricsService;
import org.jboss.logging.Logger;

/**
 * Manages the Metrics expression evaluations and interacts with the Alerts system.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Startup
@Singleton
public class Manager {
    private final Logger log = Logger.getLogger(Manager.class);

    private static final String TAG_CATEGORY = "HawkularMetrics";
    private static final String TAG_NAME = "MetricsCondition";

    private static final long DAY = 24L * 60L * 1000L;
    private static final long WEEK = 7L * DAY;

    private static final Integer THREAD_POOL_SIZE = 20;

    ScheduledThreadPoolExecutor expressionExecutor;
    Map<ExternalCondition, ScheduledFuture<?>> expressionFutures = new HashMap<>();

    @Inject
    private MetricsService metrics;

    @EJB
    private DefinitionsService definitions;

    @EJB
    private AlertsService alerts;

    @PostConstruct
    public void init() {
        log.debugf("Initializing Hawkular Alerts-Metrics Manager...");
        expressionExecutor = new ScheduledThreadPoolExecutor(THREAD_POOL_SIZE);

        refresh();

        log.debugf("Registering Trigger UPDATE/REMOVE listener");
        definitions.registerListener(new DefinitionsListener() {
            @Override
            public void onChange(DefinitionsEvent event) {
                refresh();
            }
        }, DefinitionsEvent.EventType.TRIGGER_UPDATE, EventType.TRIGGER_REMOVE);
    }

    @PreDestroy
    public void shutdown() {
        log.debugf("Shutting down Hawkular Alerts-Metrics Manager...");

        if (null != expressionFutures) {
            expressionFutures.values().forEach(f -> f.cancel(true));
        }
        if (null != expressionExecutor) {
            expressionExecutor.shutdown();
        }
    }

    private void refresh() {
        log.debugf("Refreshing External Metrics Triggers!");
        try {
            Set<ExternalCondition> activeConditions = new HashSet<>();

            // get all of the triggers tagged for hawkular metrics
            Collection<Trigger> triggers = definitions.getAllTriggersByTag(TAG_CATEGORY, TAG_NAME);
            log.debugf("Found [%s] External Metrics Triggers!", triggers.size());

            // for each trigger look for Metrics Conditions and start running them
            Collection<Condition> conditions = null;
            for (Trigger trigger : triggers) {
                try {
                    if (trigger.isEnabled()) {
                        conditions = definitions.getTriggerConditions(trigger.getTenantId(), trigger.getId(), null);
                        log.debugf("Checking [%s] Conditions for enabled trigger [%s]!", conditions.size(),
                                trigger.getName());
                    }
                } catch (Exception e) {
                    log.error("Failed to fetch Conditions when scheduling metrics conditions for " + trigger, e);
                    continue;
                }
                if (null == conditions) {
                    continue;
                }
                for (Condition condition : conditions) {
                    if (condition instanceof ExternalCondition) {
                        ExternalCondition externalCondition = (ExternalCondition) condition;
                        if (TAG_CATEGORY.equals(externalCondition.getSystemId())) {
                            log.debugf("Found Metrics ExternalCondition! %s", externalCondition);
                            activeConditions.add(externalCondition);
                            if (expressionFutures.containsKey(externalCondition)) {
                                log.debugf("Skipping, already evaluating: %s", externalCondition);

                            } else {
                                try {
                                    // start the job. TODO: Do we need a delay for any reason?
                                    log.debugf("Adding runner for: %s", externalCondition);
                                    Expression expression = new Expression(externalCondition.getExpression());
                                    ExpressionRunner runner = new ExpressionRunner(metrics, alerts, trigger,
                                            externalCondition, expression);
                                    expressionFutures.put(
                                            externalCondition,
                                            expressionExecutor.scheduleAtFixedRate(runner, 0L,
                                                    expression.getInterval(), TimeUnit.MINUTES));
                                } catch (Exception e) {
                                    log.error("Failed to schedule expression for metrics condition "
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
                    log.debugf("Canceling evaluation of obsolete External Metric Condition %s", ec);
                    me.getValue().cancel(true);
                    temp.add(ec);
                }
            }
            expressionFutures.keySet().removeAll(temp);
            temp.clear();

        } catch (Exception e) {
            log.error("Failed to fetch Triggers for scheduling metrics conditions.", e);
        }
    }

    private static class ExpressionRunner implements Runnable {
        private final Logger log = Logger.getLogger(Manager.ExpressionRunner.class);

        private MetricsService metrics;
        private AlertsService alerts;
        private Trigger trigger;
        private ExternalCondition externalCondition;
        private Expression expression;

        public ExpressionRunner(MetricsService metrics, AlertsService alerts, Trigger trigger,
                ExternalCondition externalCondition,
                Expression expression) {
            super();
            this.metrics = metrics;
            this.alerts = alerts;
            this.trigger = trigger;
            this.externalCondition = externalCondition;
            this.expression = expression;
        }

        @Override
        public void run() {
            try {
                Func func = expression.getFunc();
                String tenantId = trigger.getTenantId();
                MetricId metricId = new MetricId(expression.getMetric());
                long end = System.currentTimeMillis();
                long start = end - (expression.getPeriod() * 60000);

                log.debugf("Running External Metrics Condition: %s", expression);

                Double value = Double.NaN;
                switch (func) {
                    case avg: {
                        value = metrics.findGaugeData(tenantId, metricId, start, end, Aggregate.Average)
                                .toBlocking().last();
                        break;
                    }
                    case avgd: {
                        Double avgToday = metrics
                                .findGaugeData(tenantId, metricId, start, end, Aggregate.Average)
                                .toBlocking().last();
                        Double avgYesterday = metrics
                                .findGaugeData(tenantId, metricId, (start - DAY), (end - DAY),
                                        Aggregate.Average).toBlocking().last();
                        value = ((avgToday - avgYesterday) / avgYesterday) * 100;
                        break;
                    }
                    case avgw: {
                        Double avgToday = metrics
                                .findGaugeData(tenantId, metricId, start, end, Aggregate.Average)
                                .toBlocking().last();
                        Double avgLastWeek = metrics
                                .findGaugeData(tenantId, metricId, (start - WEEK), (end - WEEK),
                                        Aggregate.Average).toBlocking().last();
                        value = ((avgToday - avgLastWeek) / avgLastWeek) * 100;
                        break;
                    }
                    case card: {
                        log.errorf("Not Yet Supported Function: %s", func);
                        break;
                    }
                    case range: {
                        Iterator<Double> iterator = metrics.findGaugeData(tenantId, metricId, start, end,
                                Aggregate.Min, Aggregate.Max)
                                .toBlocking().toIterable().iterator();
                        Double min = iterator.next();
                        Double max = iterator.next();
                        value = max - min;
                        break;
                    }
                    case rangep: {
                        Iterator<Double> iterator = metrics.findGaugeData(tenantId, metricId, start, end,
                                Aggregate.Min, Aggregate.Max, Aggregate.Average)
                                .toBlocking().toIterable().iterator();
                        Double min = iterator.next();
                        Double max = iterator.next();
                        Double avg = iterator.next();
                        value = (max - min) / avg;
                        break;
                    }
                    case down: {
                        log.errorf("Not Yet Supported Function: %s", func);
                        break;
                    }
                    case max: {
                        value = metrics.findGaugeData(tenantId, metricId, start, end, Aggregate.Max)
                                .toBlocking().last();
                        break;
                    }
                    case min: {
                        value = metrics.findGaugeData(tenantId, metricId, start, end, Aggregate.Min)
                                .toBlocking().last();
                        break;
                    }
                    case up: {
                        log.errorf("Not Yet Supported Function: %s", func);
                        break;
                    }
                    default: {
                        log.errorf("Unexpected Expression Function: %s", func);
                        break;
                    }
                }

                evaluate(value);

            } catch (Throwable t) {
                log.debugf("Failed data fetch for %s: %s", expression, t.getMessage());
            }
        }

        public void evaluate(Double value) {
            if (value.isNaN()) {
                log.debugf("NaN value, Ignoring External Metrics evaluation of %s", expression);
                return;
            }

            log.debugf("Running External Metrics Evaluation: %s : %s", expression, value);

            if (!expression.isTrue(value)) {
                return;
            }

            try {
                StringData externalData = new StringData(externalCondition.getDataId(), System.currentTimeMillis(),
                        value.toString());
                log.debugf("Sending External Condition Data to Alerts! %s", externalData);
                alerts.sendData(externalData);

            } catch (Exception e) {
                log.error("Failed to send external data to alerts system.", e);
            }
        }
    }

}
