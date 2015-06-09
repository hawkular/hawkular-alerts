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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.EJB;
import javax.ejb.Singleton;

import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.condition.ExternalCondition;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.services.DefinitionsEvent;
import org.hawkular.alerts.api.services.DefinitionsListener;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.external.metrics.Expression.Func;
import org.jboss.logging.Logger;

/**
 * Manages the Metrics expression evaluations and interacts with the Alerts system.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Singleton
public class Manager {
    private final Logger log = Logger.getLogger(Manager.class);

    private static final String TAG_CATEGORY = "HawkularMetrics";
    private static final String TAG_NAME = "MetricsCondition";

    // private static final String THREAD_POOL_NAME = "HawkularAlertsMetricsExpression";
    private static final Integer THREAD_POOL_SIZE = 20;

    ScheduledThreadPoolExecutor expressionExecutor;
    Set<ScheduledFuture<?>> scheduledFutures = new HashSet<>();  // TODO: Is this needed in any way?

    @EJB
    DefinitionsService definitions;

    @PostConstruct
    public void init() {

        definitions.registerListener(new DefinitionsListener() {
            @Override
            public void onChange(DefinitionsEvent event) {
                if (DefinitionsEvent.EventType.CONDITION_CHANGE == event.getEventType()) {
                }
            }
        });

        refresh();
    }

    @PreDestroy
    public void shutdown() {
        if (null != scheduledFutures) {
            scheduledFutures.forEach(f -> f.cancel(true));
        }
        if (null != expressionExecutor) {
            expressionExecutor.shutdown();
        }
    }

    private void refresh() {
        try {
            shutdown();
            expressionExecutor = new ScheduledThreadPoolExecutor(THREAD_POOL_SIZE);

            // get all of the triggers tagged for hawkular metrics
            Collection<Trigger> triggers = definitions.getAllTriggersByTag(TAG_CATEGORY, TAG_NAME);

            // for each trigger look for Metrics Conditions and start running them
            Collection<Condition> conditions = null;
            for (Trigger trigger : triggers) {
                try {
                    conditions = definitions.getTriggerConditions(trigger.getTenantId(),
                            trigger.getId(), null);
                } catch (Exception e) {
                    log.error("Failed to fetch Conditions when scheduling metrics conditions for " + trigger, e);
                    continue;
                }
                for (Condition condition : conditions) {
                    if (condition instanceof ExternalCondition) {
                        ExternalCondition ec = (ExternalCondition) condition;
                        if (TAG_NAME.equals(ec.getSystemId())) {
                            try {
                                Expression expression = new Expression(ec.getExpression());
                                ExpressionRunner runner = new ExpressionRunner(trigger, ec, expression);
                                // wait a minute for any initialization and then start the job
                                scheduledFutures.add(expressionExecutor.scheduleAtFixedRate(runner, 1L,
                                        expression.getInterval(), TimeUnit.MINUTES));
                            } catch (Exception e) {
                                log.error("Failed to schedule expression for metrics condition " + ec, e);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch Triggers for scheduling metrics conditions.", e);
        }
    }

    private static class ExpressionRunner implements Runnable {
        private final Logger log = Logger.getLogger(Manager.ExpressionRunner.class);

        private Trigger trigger;
        private ExternalCondition externalCondition;
        private Expression expression;

        public ExpressionRunner(Trigger trigger, ExternalCondition externalCondition, Expression expression) {
            super();
            this.trigger = trigger;
            this.externalCondition = externalCondition;
            this.expression = expression;
        }

        @Override
        public void run() {
            try {
                Func func = expression.getFunc();
                switch (func) {
                    case avg:
                        
                        handleAvg(Trigger)
                        break;
                    default:
                        log.errorf("Unexpected Expression Function: %s", func);
                }
            } catch (Exception e) {
                log.errorf("Unexpected failure in Expression handling: %s", expression);
            }
        }
    }
}
