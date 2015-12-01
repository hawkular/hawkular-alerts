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
package org.hawkular.alerts.engine.impl;

import java.util.Collection;
import java.util.TreeSet;
import java.util.function.Predicate;

import javax.ejb.Singleton;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import org.drools.core.event.DebugAgendaEventListener;
import org.drools.core.event.DebugRuleRuntimeEventListener;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.engine.service.RulesEngine;
import org.jboss.logging.Logger;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.ObjectFilter;
import org.kie.api.runtime.rule.FactHandle;

/**
 * An implementation of RulesEngine based on drools framework.
 *
 * This implementations has an approach of fixed rules based on filesystem.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Singleton
@TransactionAttribute(value= TransactionAttributeType.NOT_SUPPORTED)
public class DroolsRulesEngineImpl implements RulesEngine {
    // private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(DroolsRulesEngineImpl.class);
    private static final String SESSION_NAME = "hawkular-alerts-engine-session";

    private KieServices ks;
    private KieContainer kc;
    private KieSession kSession;

    TreeSet<Data> pendingData = new TreeSet<>();
    TreeSet<Event> pendingEvents = new TreeSet<>();

    public DroolsRulesEngineImpl() {
        log.debug("Creating instance.");
        ks = KieServices.Factory.get();
        kc = ks.getKieClasspathContainer();
        kSession = kc.newKieSession(SESSION_NAME);

        if (log.isEnabled(Logger.Level.TRACE)) {
            kSession.addEventListener(new DebugAgendaEventListener());
            kSession.addEventListener(new DebugRuleRuntimeEventListener());
        }
    }

    @Override
    public void addFact(Object fact) {
        if (fact instanceof Data || fact instanceof Event) {
            throw new IllegalArgumentException(fact.toString());
        }
        if (log.isDebugEnabled()) {
            log.debug("Insert " + fact);
        }
        kSession.insert(fact);
    }

    @Override
    public void addFacts(Collection facts) {
        for (Object fact : facts) {
            if (fact instanceof Data || fact instanceof Event) {
                throw new IllegalArgumentException(fact.toString());
            }
        }
        for (Object fact : facts) {
            if (log.isDebugEnabled()) {
                log.debug("Insert " + fact);
            }
            kSession.insert(fact);
        }
    }

    @Override
    public void addData(Data data) {
        pendingData.add(data);
    }

    @Override
    public void addData(Collection<Data> data) {
        pendingData.addAll(data);
    }

    @Override
    public void addEvent(Event event) {
        pendingEvents.add(event);
    }

    @Override
    public void addEvents(Collection<Event> events) {
        pendingEvents.addAll(events);
    }

    @Override
    public void addGlobal(String name, Object global) {
        if (log.isDebugEnabled()) {
            log.debug("Add Global " + name + " = " + global);
        }
        kSession.setGlobal(name, global);
    }

    @Override
    public void clear() {
        for (FactHandle factHandle : kSession.getFactHandles()) {
            if (log.isDebugEnabled()) {
                log.debug("Delete " + factHandle);
            }
            kSession.delete(factHandle);
        }
    }

    @Override
    public void fire() {
        // The rules engine requires that for any DataId only the oldest Data instance is processed in one
        // execution of the rules.  So, if we find multiple Data instances for the same Id, defer all but
        // the oldest to a subsequent run. Note that pendingData is already sorted by (id ASC, timestamp ASC) so
        // the iterator will present Data with the same id together, and time-ordered.
        log.debug("firing rules...");
        int fireCycle = 0;
        while (!pendingData.isEmpty() || !pendingEvents.isEmpty()) {

            if (log.isDebugEnabled()) {
                log.debug("Data found. Firing rules on [" + pendingData.size() + "] datums and " +
                        "[" + pendingEvents.size() + "] events.");
            }

            TreeSet<Data> batchData = new TreeSet<Data>(pendingData);
            Data previousData = null;

            pendingData.clear();

            for (Data data : batchData) {
                if (null == previousData || !data.getId().equals(previousData.getId())) {
                    kSession.insert(data);
                    previousData = data;

                } else {
                    pendingData.add(data);
                    if (log.isDebugEnabled()) {
                        log.debug("Deferring more recent " + data + " until older " + previousData + " is " +
                                "processed");
                    }
                }
            }

            if (!pendingData.isEmpty() && log.isDebugEnabled()) {
                log.debug("Deferring [" + pendingData.size() + "] Datum(s) to next firing !!");
            }

            batchData.clear();


            TreeSet<Event> batchEvent = new TreeSet<Event>(pendingEvents);
            Event previousEvent = null;

            pendingEvents.clear();

            for (Event event : batchEvent) {
                if (null == previousEvent
                        || (null != event.getDataId() && !event.getDataId().equals(previousEvent.getDataId()))) {
                    kSession.insert(event);
                    previousEvent = event;
                } else {
                    pendingEvents.add(event);
                    if (log.isDebugEnabled()) {
                        log.debug("Deferring more recent " + event + " until older " + previousEvent + " is " +
                                "processed ");
                    }
                }
            }

            if (log.isDebugEnabled()) {
                log.debug("Firing cycle [" + fireCycle + "] - with these facts: ");
                for (FactHandle fact : kSession.getFactHandles()) {
                    Object o = kSession.getObject(fact);
                    log.debug("Fact:  " + o.toString());
                }
            }

            kSession.fireAllRules();
            fireCycle++;
        }
    }

    @Override
    public void fireNoData() {
        kSession.fireAllRules();
    }

    @Override
    public Object getFact(Object o) {
        Object result = null;
        FactHandle factHandle = kSession.getFactHandle(o);
        if (null != factHandle) {
            result = kSession.getObject(factHandle);
        }
        return result;
    }

    @Override
    public void removeFact(Object fact) {
        FactHandle factHandle = kSession.getFactHandle(fact);
        if (factHandle != null) {
            if (log.isDebugEnabled()) {
                log.debug("Delete " + factHandle);
            }
            kSession.delete(factHandle);
        }
    }

    @Override
    public void updateFact(Object fact) {
        FactHandle factHandle = kSession.getFactHandle(fact);
        if (factHandle != null) {
            if (log.isDebugEnabled()) {
                log.debug("Update " + factHandle);
            }
            kSession.update(factHandle, fact);
        }
    }

    @Override
    public void removeFacts(Collection facts) {
        for (Object fact : facts) {
            removeFact(fact);
        }
    }

    @Override
    public void removeFacts(Predicate<Object> factFilter) {
        Collection<FactHandle> handles = kSession.getFactHandles(new ObjectFilter() {
            @Override
            public boolean accept(Object object) {
                return factFilter.test(object);
            }
        });

        if (null == handles) {
            return;
        }

        for (FactHandle h : handles) {
            if (log.isDebugEnabled()) {
                log.debug("Delete " + h);
            }
            removeFact(h);
        }
    }

    @Override
    public void removeGlobal(String name) {
        if (log.isDebugEnabled()) {
            log.debug("Remove Global " + name);
        }
        kSession.setGlobal(name, null);
    }

    @Override
    public void reset() {
        log.debug("Reset session");
        kSession.dispose();
        kSession = kc.newKieSession(SESSION_NAME);
    }
}
