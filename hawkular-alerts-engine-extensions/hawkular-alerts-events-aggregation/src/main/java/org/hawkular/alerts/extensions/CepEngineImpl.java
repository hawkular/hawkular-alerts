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
package org.hawkular.alerts.extensions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;

import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.trigger.FullTrigger;
import org.hawkular.alerts.api.services.AlertsService;
import org.jboss.logging.Logger;
import org.kie.api.KieBase;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.AgendaGroupPoppedEvent;
import org.kie.api.event.rule.AgendaGroupPushedEvent;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.MatchCancelledEvent;
import org.kie.api.event.rule.MatchCreatedEvent;
import org.kie.api.event.rule.ObjectDeletedEvent;
import org.kie.api.event.rule.ObjectInsertedEvent;
import org.kie.api.event.rule.ObjectUpdatedEvent;
import org.kie.api.event.rule.RuleFlowGroupActivatedEvent;
import org.kie.api.event.rule.RuleFlowGroupDeactivatedEvent;
import org.kie.api.event.rule.RuleRuntimeEventListener;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.kie.internal.KnowledgeBaseFactory;
import org.kie.internal.utils.KieHelper;

/**
 * It evaluates events externally and send data into alerting
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class CepEngineImpl implements CepEngine {
    private final Logger log = Logger.getLogger(CepEngineImpl.class);

    Expression expression;
    List<Event> results;
    KieSession kieSession;

    private AlertsService alertsService;

    private ExecutorService executor;

    public void setAlertsService(AlertsService alertsService) {
        this.alertsService = alertsService;
    }

    public void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    public void processEvents(TreeSet<Event> events) {
        if (kieSession != null) {
            events.stream().forEach(e -> kieSession.insert(e));
        }
    }

    public void sendResult(Event event) {
        log.debugf("Resulted event %s", event);
        executor.submit(() -> {
            try {
                alertsService.sendEvents(Arrays.asList(event));
            } catch (Exception e) {
                log.error("Error sending result events.", e);
            }
        });
    }

    public void updateConditions(String expiration, Collection<FullTrigger> activeTriggers) {
        expression = new Expression(expiration, activeTriggers);
        log.debugf("Rules: \n %s", expression);
        stop();
        KieBaseConfiguration kieBaseConfiguration = KnowledgeBaseFactory.newKnowledgeBaseConfiguration();
        kieBaseConfiguration.setOption( EventProcessingOption.STREAM );
        KieBase kieBase = new KieHelper().addContent(expression.getDrl(), ResourceType.DRL).build(kieBaseConfiguration);
        KieSessionConfiguration kieSessionConf = KnowledgeBaseFactory.newKnowledgeSessionConfiguration();
        kieSessionConf.setOption( ClockTypeOption.get( "realtime" ) );

        kieSession = kieBase.newKieSession(kieSessionConf, null);
        results = new ArrayList<>();
        kieSession.setGlobal("results", this);
        kieSession.setGlobal("log", log);
        if (log.isDebugEnabled()) {
            kieSession.addEventListener(new CepAgendaEventListener());
            kieSession.addEventListener(new CepRuleRuntimeEventListener());
        }
        log.infof("Clock time [%s] ", kieSession.getSessionClock().getCurrentTime());

        kieSession.getKieBase().getKiePackages().stream().forEach(p -> log.debugf("Rules: %s", p.getRules()));

        executor.submit(() -> {
            log.info("Starting fireUntilHalt()");
            kieSession.fireUntilHalt();
            log.info("Stopping fireUntilHalt()");
        });
    }

    @Override
    public void stop() {
        if (kieSession != null) {
            kieSession.halt();
            kieSession.dispose();
            kieSession.destroy();
            kieSession = null;
            log.info("Sent halt() signal to CEP session");
        }
    }

    public static class CepAgendaEventListener implements AgendaEventListener {
        private final Logger log = Logger.getLogger(CepAgendaEventListener.class);

        @Override
        public void matchCreated(MatchCreatedEvent event) {
            log.debug(event);
        }

        @Override
        public void matchCancelled(MatchCancelledEvent event) {
            log.debug(event);
        }

        @Override
        public void beforeMatchFired(BeforeMatchFiredEvent event) {
            log.debug(event);
        }

        @Override
        public void afterMatchFired(AfterMatchFiredEvent event) {
            log.debug(event);
        }

        @Override
        public void agendaGroupPopped(AgendaGroupPoppedEvent event) {
            log.debug(event);
        }

        @Override
        public void agendaGroupPushed(AgendaGroupPushedEvent event) {
            log.debug(event);
        }

        @Override
        public void beforeRuleFlowGroupActivated(RuleFlowGroupActivatedEvent event) {
            log.debug(event);
        }

        @Override
        public void afterRuleFlowGroupActivated(RuleFlowGroupActivatedEvent event) {
            log.debug(event);
        }

        @Override
        public void beforeRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent event) {
            log.debug(event);
        }

        @Override
        public void afterRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent event) {
            log.debug(event);
        }
    }

    public static class CepRuleRuntimeEventListener implements RuleRuntimeEventListener {
        private final Logger log = Logger.getLogger(CepRuleRuntimeEventListener.class);

        @Override
        public void objectInserted(ObjectInsertedEvent event) {
            log.debug(event);
        }

        @Override
        public void objectUpdated(ObjectUpdatedEvent event) {
            log.debug(event);
        }

        @Override
        public void objectDeleted(ObjectDeletedEvent event) {
            log.debug(event);
        }
    }
}
