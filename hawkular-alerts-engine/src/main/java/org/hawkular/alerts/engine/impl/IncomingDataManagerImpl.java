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
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.inject.Inject;

import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.engine.service.AlertsEngine;
import org.hawkular.alerts.engine.service.IncomingDataManager;
import org.hawkular.alerts.engine.service.PartitionManager;
import org.hawkular.alerts.engine.service.RulesEngine;
import org.hawkular.alerts.filter.CacheClient;
import org.jboss.logging.Logger;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Singleton
@Startup
@Local(IncomingDataManager.class)
@TransactionAttribute(value = TransactionAttributeType.NOT_SUPPORTED)
public class IncomingDataManagerImpl implements IncomingDataManager {
    private final Logger log = Logger.getLogger(IncomingDataManagerImpl.class);

    private int minReportingInterval;

    @Resource
    private ManagedExecutorService executor;

    @EJB
    DataDrivenGroupCacheManager dataDrivenGroupCacheManager;

    @EJB
    DefinitionsService definitionsService;

    @EJB
    PartitionManager partitionManager;

    @EJB
    AlertsEngine alertsEngine;

    @Inject
    CacheClient dataIdCache;

    @PostConstruct
    public void init() {
        try {
            minReportingInterval = new Integer(
                    AlertProperties.getProperty(RulesEngine.MIN_REPORTING_INTERVAL,
                            RulesEngine.MIN_REPORTING_INTERVAL_ENV,
                            RulesEngine.MIN_REPORTING_INTERVAL_DEFAULT));
        } catch (Throwable t) {
            if (log.isDebugEnabled()) {
                t.printStackTrace();
            }
            log.errorf("Failed to initialize: %s", t.getMessage());
        }
    }

    @Override
    public void bufferData(IncomingData incomingData) {
        executor.submit(() -> {
            processData(incomingData);
        });
    }

    @Override
    public void bufferEvents(IncomingEvents incomingEvents) {
        executor.submit(() -> {
            processEvents(incomingEvents);
        });
    }

    private void processData(IncomingData incomingData) {
        log.debugf("Processing [%d] datums for AlertsEngine.", incomingData.incomingData.size());

        // remove data not needed by the defined triggers
        // remove duplicates and apply natural ordering
        TreeSet<Data> filteredData = new TreeSet<Data>(filterIncomingData(incomingData));

        // remove offenders of minReportingInterval. Note, this filters only this incoming batch, this is
        // performed again, downstream,after data has been "stitched together" for evaluation.
        enforceMinReportingInterval(filteredData);

        // check to see if any data can be used to generate data-driven group members
        checkDataDrivenGroupTriggers(filteredData);

        try {
            log.debugf("Sending [%d] datums to AlertsEngine.", filteredData.size());
            alertsEngine.sendData(filteredData);

        } catch (Exception e) {
            log.errorf("Failed to send [%d] datums:", filteredData.size(), e.getMessage());
        }
    }

    private void processEvents(IncomingEvents incomingEvents) {
        log.debugf("Processing [%d] events to AlertsEngine.", incomingEvents.incomingEvents.size());

        // remove events not needed by the defined triggers
        // remove duplicates and apply natural ordering
        TreeSet<Event> filteredEvents = new TreeSet<Event>(filterIncomingEvents(incomingEvents));

        // remove offenders of minReportingInterval. Note, this filters only this incoming batch, this is
        // performed again, downstream,after data has been "stitched together" for evaluation.
        enforceMinReportingIntervalEvents(filteredEvents);

        try {
            alertsEngine.sendEvents(filteredEvents);
        } catch (Exception e) {
            log.errorf("Failed sending [%d] events: %s", filteredEvents.size(), e.getMessage());
        }
    }

    private Collection<Data> filterIncomingData(IncomingData incomingData) {
        Collection<Data> data = incomingData.getIncomingData();
        data = incomingData.isRaw() ? dataIdCache.filterData(data) : data;

        return data;
    }

    private Collection<Event> filterIncomingEvents(IncomingEvents incomingEvents) {
        Collection<Event> events = incomingEvents.getIncomingEvents();
        events = incomingEvents.isRaw() ? dataIdCache.filterEvents(events) : events;

        return events;
    }

    private void enforceMinReportingInterval(TreeSet<Data> orderedData) {
        int beforeSize = orderedData.size();
        Data prev = null;
        for (Iterator<Data> i = orderedData.iterator(); i.hasNext();) {
            Data d = i.next();
            if (!d.same(prev)) {
                prev = d;
            } else {
                if ((d.getTimestamp() - prev.getTimestamp()) < minReportingInterval) {
                    log.tracef("MinReportingInterval violation, prev: %s, removed: %s", prev, d);
                    i.remove();
                }
            }
        }
        if (log.isDebugEnabled() && beforeSize != orderedData.size()) {
            log.debugf("MinReportingInterval Data violations: [%d]", beforeSize - orderedData.size());
        }
    }

    private void enforceMinReportingIntervalEvents(TreeSet<Event> orderedEvents) {
        int beforeSize = orderedEvents.size();
        Event prev = null;
        for (Iterator<Event> i = orderedEvents.iterator(); i.hasNext();) {
            Event e = i.next();
            if (!e.same(prev)) {
                prev = e;
            } else {
                if ((e.getCtime() - prev.getCtime()) < minReportingInterval) {
                    log.tracef("MinReportingInterval violation, prev: %s, removed: %s", prev, e);
                    i.remove();
                }
            }
        }
        if (log.isDebugEnabled() && beforeSize != orderedEvents.size()) {
            log.debugf("MinReportingInterval Events violations: [%d]", beforeSize - orderedEvents.size());
        }
    }

    private void checkDataDrivenGroupTriggers(Collection<Data> data) {
        if (!dataDrivenGroupCacheManager.isCacheActive()) {
            return;
        }

        for (Data d : data) {
            if (isEmpty(d.getSource())) {
                continue;
            }

            String tenantId = d.getTenantId();
            String dataId = d.getId();
            String dataSource = d.getSource();

            Set<String> groupTriggerIds = dataDrivenGroupCacheManager.needsSourceMember(tenantId, dataId, dataSource);

            // Add a trigger members for the source

            for (String groupTriggerId : groupTriggerIds) {
                try {
                    definitionsService.addDataDrivenMemberTrigger(tenantId, groupTriggerId, dataSource);

                } catch (Exception e) {
                    log.errorf("Failed to add Data-Driven Member Trigger for [%s:%s]: %s:", groupTriggerId, d,
                            e.getMessage());
                }
            }
        }
    }

    private boolean isEmpty(String s) {
        return null == s || s.isEmpty();
    }

    public static class IncomingData {
        private Collection<Data> incomingData;
        private boolean raw;

        public IncomingData(Collection<Data> incomingData, boolean raw) {
            super();
            this.incomingData = incomingData;
            this.raw = raw;
        }

        public Collection<Data> getIncomingData() {
            return incomingData;
        }

        public boolean isRaw() {
            return raw;
        }
    }

    public static class IncomingEvents {
        private Collection<Event> incomingEvents;
        private boolean raw;

        public IncomingEvents(Collection<Event> incomingEvents, boolean raw) {
            super();
            this.incomingEvents = incomingEvents;
            this.raw = raw;
        }

        public Collection<Event> getIncomingEvents() {
            return incomingEvents;
        }

        public boolean isRaw() {
            return raw;
        }
    }

}