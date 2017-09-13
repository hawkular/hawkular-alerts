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
package org.hawkular.alerts.engine.impl;

import static org.hawkular.alerts.api.util.Util.isEmpty;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;

import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.engine.service.AlertsEngine;
import org.hawkular.alerts.engine.service.IncomingDataManager;
import org.hawkular.alerts.engine.service.PartitionManager;
import org.hawkular.alerts.engine.service.RulesEngine;
import org.hawkular.alerts.filter.CacheClient;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;
import org.hawkular.commons.properties.HawkularProperties;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class IncomingDataManagerImpl implements IncomingDataManager {
    private final MsgLogger log = MsgLogging.getMsgLogger(IncomingDataManagerImpl.class);

    private int minReportingIntervalData;
    private int minReportingIntervalEvents;

    private ExecutorService executor;

    DataDrivenGroupCacheManager dataDrivenGroupCacheManager;

    DefinitionsService definitionsService;

    PartitionManager partitionManager;

    AlertsEngine alertsEngine;

    CacheClient dataIdCache;

    public void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    public void setDataDrivenGroupCacheManager(DataDrivenGroupCacheManager dataDrivenGroupCacheManager) {
        this.dataDrivenGroupCacheManager = dataDrivenGroupCacheManager;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void setPartitionManager(PartitionManager partitionManager) {
        this.partitionManager = partitionManager;
    }

    public void setAlertsEngine(AlertsEngine alertsEngine) {
        this.alertsEngine = alertsEngine;
    }

    public void setDataIdCache(CacheClient dataIdCache) {
        this.dataIdCache = dataIdCache;
    }

    public void init() {
        try {
            minReportingIntervalData = new Integer(
                    HawkularProperties.getProperty(RulesEngine.MIN_REPORTING_INTERVAL_DATA,
                            RulesEngine.MIN_REPORTING_INTERVAL_DATA_ENV,
                            RulesEngine.MIN_REPORTING_INTERVAL_DATA_DEFAULT));

            minReportingIntervalEvents = new Integer(
                    HawkularProperties.getProperty(RulesEngine.MIN_REPORTING_INTERVAL_EVENTS,
                            RulesEngine.MIN_REPORTING_INTERVAL_EVENTS_ENV,
                            RulesEngine.MIN_REPORTING_INTERVAL_EVENTS_DEFAULT));
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
        log.debugf("Processing [%s] datums for AlertsEngine.", incomingData.incomingData.size());

        // remove data not needed by the defined triggers
        // remove duplicates and apply natural ordering
        TreeSet<Data> filteredData = new TreeSet<Data>(filterIncomingData(incomingData));

        // remove offenders of minReportingInterval. Note, this filters only this incoming batch, this is
        // performed again, downstream,after data has been "stitched together" for evaluation.
        enforceMinReportingInterval(filteredData);

        // check to see if any data can be used to generate data-driven group members
        checkDataDrivenGroupTriggers(filteredData);

        try {
            log.debugf("Sending [%s] datums to AlertsEngine.", filteredData.size());
            alertsEngine.sendData(filteredData);

        } catch (Exception e) {
            log.errorf("Failed to send [%s] datums:", filteredData.size(), e.getMessage());
        }
    }

    private void processEvents(IncomingEvents incomingEvents) {
        log.debugf("Processing [%s] events to AlertsEngine.", incomingEvents.incomingEvents.size());

        // remove events not needed by the defined triggers
        // remove duplicates and apply natural ordering
        TreeSet<Event> filteredEvents = new TreeSet<Event>(filterIncomingEvents(incomingEvents));

        // remove offenders of minReportingInterval. Note, this filters only this incoming batch, this is
        // performed again, downstream,after data has been "stitched together" for evaluation.
        enforceMinReportingIntervalEvents(filteredEvents);

        try {
            alertsEngine.sendEvents(filteredEvents);
        } catch (Exception e) {
            log.errorf("Failed sending [%s] events: %s", filteredEvents.size(), e.getMessage());
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
                if ((d.getTimestamp() - prev.getTimestamp()) < minReportingIntervalData) {
                    log.tracef("MinReportingInterval violation, prev: %s, removed: %s", prev, d);
                    i.remove();
                }
            }
        }
        if (log.isDebugEnabled() && beforeSize != orderedData.size()) {
            log.debugf("MinReportingInterval Data violations: [%s]", beforeSize - orderedData.size());
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
                if ((e.getCtime() - prev.getCtime()) < minReportingIntervalEvents) {
                    log.tracef("MinReportingInterval violation, prev: %s, removed: %s", prev, e);
                    i.remove();
                }
            }
        }
        if (log.isDebugEnabled() && beforeSize != orderedEvents.size()) {
            log.debugf("MinReportingInterval Events violations: [%s]", beforeSize - orderedEvents.size());
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