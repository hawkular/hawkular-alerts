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
package org.hawkular.alerts.engine.impl;

import java.util.Collection;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.function.Predicate;

import org.drools.core.event.DebugAgendaEventListener;
import org.drools.core.event.DebugRuleRuntimeEventListener;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.engine.service.RulesEngine;
import org.hawkular.alerts.log.MsgLogger;
import org.hawkular.alerts.properties.AlertProperties;
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
 * The RulesEngine is invoked only by the AlertsEngine impl and is not invoked concurrently, so
 * single-threading is a fair assumption.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class DroolsRulesEngineImpl implements RulesEngine {
    // private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final MsgLogger log = MsgLogger.getLogger(DroolsRulesEngineImpl.class);
    private static final String SESSION_NAME = "hawkular-alerts-engine-session";
    private static final long PERF_BATCHING_THRESHOLD = 3000L; // 3 seconds
    private static final long PERF_FIRING_THRESHOLD = 5000L; // 5 seconds

    private int minReportingIntervalData;
    private int minReportingIntervalEvents;

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

        if (log.isTraceEnabled()) {
            kSession.addEventListener(new DebugAgendaEventListener());
            kSession.addEventListener(new DebugRuleRuntimeEventListener());
        }

        minReportingIntervalData = new Integer(
                AlertProperties.getProperty(MIN_REPORTING_INTERVAL_DATA,
                        MIN_REPORTING_INTERVAL_DATA_ENV,
                        MIN_REPORTING_INTERVAL_DATA_DEFAULT));

        minReportingIntervalEvents = new Integer(
                AlertProperties.getProperty(MIN_REPORTING_INTERVAL_EVENTS,
                        MIN_REPORTING_INTERVAL_EVENTS_ENV,
                        MIN_REPORTING_INTERVAL_EVENTS_DEFAULT));
    }

    @Override
    public void addFact(Object fact) {
        if (fact instanceof Data || fact instanceof Event) {
            throw new IllegalArgumentException(fact.toString());
        }
        kSession.insert(fact);
        if (log.isDebugEnabled()) {
            log.debug("addFact( {} )", fact.toString());
            log.debug("==> Begin Dump");
            for (FactHandle f : kSession.getFactHandles()) {
                Object sessionObject = kSession.getObject(f);
                log.debug("Fact:  {}", sessionObject.toString());
            }
            log.debug("==> End Dump");
        }
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
                log.debug("Insert {}", fact);
            }
            kSession.insert(fact);
        }
        if (log.isDebugEnabled()) {
            log.debug("addFacts( {} )", facts.toString());
            log.debug("==> Begin Dump");
            for (FactHandle f : kSession.getFactHandles()) {
                Object sessionObject = kSession.getObject(f);
                log.debug("Fact:  {}", sessionObject.toString());
            }
            log.debug("==> End Dump");
        }
    }

    @Override
    public void addData(TreeSet<Data> data) {
        pendingData.addAll(data);
    }

    @Override
    public void addEvents(TreeSet<Event> events) {
        pendingEvents.addAll(events);
    }

    @Override
    public void addGlobal(String name, Object global) {
        if (log.isDebugEnabled()) {
            log.debug("Add Global {} = {} ", name, global);
        }
        kSession.setGlobal(name, global);
    }

    @Override
    public void clear() {
        for (FactHandle factHandle : kSession.getFactHandles()) {
            if (log.isDebugEnabled()) {
                log.debug("Delete {}", factHandle);
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
        int initialPendingData = pendingData.size();
        int initialPendingEvents = pendingEvents.size();
        int fireCycle = 0;
        long startFiring = System.currentTimeMillis();
        while (!pendingData.isEmpty() || !pendingEvents.isEmpty()) {
            log.debug("Firing rules... PendingData [{}] PendingEvents [{}]", initialPendingData,
                    initialPendingEvents);

            batchData();
            batchEvents();

            if (log.isTraceEnabled()) {
                log.trace("Firing cycle [{}] - with these facts: ", fireCycle);
                for (FactHandle fact : kSession.getFactHandles()) {
                    Object o = kSession.getObject(fact);
                    log.trace("Fact: {}", o);
                }
            }

            kSession.fireAllRules();
            fireCycle++;
        }
        long firingTime = System.currentTimeMillis() - startFiring;
        if (log.isDebugEnabled()) {
            log.debug("Firing took [{}] ms", firingTime);
        }
        if (firingTime > PERF_FIRING_THRESHOLD) {
            log.warn("Firing rules... PendingData [{}] PendingEvents [{}] took [{}] ms exceeding [{}] ms",
                    initialPendingData, initialPendingEvents, firingTime, PERF_FIRING_THRESHOLD);
        }
    }

    private void batchData() {
        long startBatching = System.currentTimeMillis();
        TreeSet<Data> batchData = pendingData;
        pendingData = new TreeSet<>();

        // Keep only the least recent datum for any dataId. Remove minReportingInterval violators, defer the rest
        Data previousData = null;
        for (Iterator<Data> i = batchData.iterator(); i.hasNext();) {
            Data d = i.next();
            if (!d.same(previousData)) {
                previousData = d;
                kSession.insert(d);

            } else {
                if ((d.getTimestamp() - previousData.getTimestamp()) < minReportingIntervalData) {
                    log.trace("MinReportingInterval violation, prev: {}, removed: {}", previousData, d);
                } else {
                    pendingData.add(d);
                    log.trace("Deferring data, keep: {}, defer: {}", previousData, d);
                }
            }

            if (!pendingData.isEmpty()) {
                log.debug("Deferring [%d] Datum(s) to next firing !!", pendingData.size());
            }
        }

        long batchingTime = System.currentTimeMillis() - startBatching;
        log.debug("Batching Data [{}] took [{}]", batchData.size(), batchingTime);
        if (batchingTime > PERF_BATCHING_THRESHOLD) {
            log.warn("Batching Data [{}] took [{}] ms exceeding [{}] ms",
                    batchData.size(), batchingTime, PERF_BATCHING_THRESHOLD);
        }
    }

    private void batchEvents() {
        long startBatching = System.currentTimeMillis();
        TreeSet<Event> batchEvents = pendingEvents;
        pendingEvents = new TreeSet<>();

        // Keep only the least recent datum for any dataId. Remove minReportingInterval violators, defer the rest
        Event previousEvent = null;
        for (Iterator<Event> i = batchEvents.iterator(); i.hasNext();) {
            Event e = i.next();
            if (!e.same(previousEvent)) {
                previousEvent = e;
                kSession.insert(e);

            } else {
                if ((e.getCtime() - previousEvent.getCtime()) < minReportingIntervalEvents) {
                    log.trace("MinReportingInterval violation, prev: {}, removed: {}", previousEvent, e);
                } else {
                    pendingEvents.add(e);
                    log.trace("Deferring event, keep: {}, defer: {}", previousEvent, e);
                }
            }
        }

        if (!pendingEvents.isEmpty()) {
            log.debug("Deferring [%d] Event(s) to next firing !!", pendingEvents.size());
        }

        long batchingTime = System.currentTimeMillis() - startBatching;
        log.debug("Batching Events [{}] took [{}]", batchEvents.size(), batchingTime);
        if (batchingTime > PERF_BATCHING_THRESHOLD) {
            log.warn("Batching Events [{}] took [{}] ms exceeding [{}] ms",
                    batchEvents.size(), batchingTime, PERF_BATCHING_THRESHOLD);
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
        if (log.isDebugEnabled()) {
            log.debug("getFact( {} )", o.toString());
            log.debug("==> Begin Dump");
            for (FactHandle fact : kSession.getFactHandles()) {
                Object sessionObject = kSession.getObject(fact);
                log.debug("Fact:  {}", sessionObject.toString());
            }
            log.debug("==> End Dump");
        }
        return result;
    }

    @Override
    public void removeFact(Object fact) {
        FactHandle factHandle = kSession.getFactHandle(fact);
        if (factHandle != null) {
            if (log.isDebugEnabled()) {
                log.debug("Delete {}", factHandle);
            }
            kSession.delete(factHandle);
        }
        if (log.isDebugEnabled()) {
            log.debug("removeFact( {} )", fact.toString());
            log.debug("==> Begin Dump");
            for (FactHandle f : kSession.getFactHandles()) {
                Object sessionObject = kSession.getObject(f);
                log.debug("Fact:  {}", sessionObject.toString());
            }
            log.debug("==> End Dump");
        }
    }

    @Override
    public void updateFact(Object fact) {
        FactHandle factHandle = kSession.getFactHandle(fact);
        if (factHandle != null) {
            if (log.isDebugEnabled()) {
                log.debug("Update {}", factHandle);
            }
            kSession.update(factHandle, fact);
        }
        if (log.isDebugEnabled()) {
            log.debug("updateFact( {} )", fact.toString());
            log.debug("==> Begin Dump");
            for (FactHandle f : kSession.getFactHandles()) {
                Object sessionObject = kSession.getObject(f);
                log.debug("Fact:  {}", sessionObject.toString());
            }
            log.debug("==> End Dump");
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
            removeFact(h);
        }
    }

    @Override
    public void removeGlobal(String name) {
        if (log.isDebugEnabled()) {
            log.debug("Remove Global {}", name);
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
