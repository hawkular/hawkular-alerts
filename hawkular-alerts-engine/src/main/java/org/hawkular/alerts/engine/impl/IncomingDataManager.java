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
import java.util.Set;

import javax.annotation.Resource;
import javax.ejb.EJB;
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
import org.hawkular.alerts.filter.CacheClient;
import org.jboss.logging.Logger;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Singleton
@Startup
@TransactionAttribute(value = TransactionAttributeType.NOT_SUPPORTED)
public class IncomingDataManager {
    private final Logger log = Logger.getLogger(IncomingDataManager.class);

    @Resource
    private ManagedExecutorService executor;

    @EJB
    DataDrivenGroupCacheManager dataDrivenGroupCacheManager;

    @EJB
    DefinitionsService definitionsService;

    @EJB
    AlertsEngine alertsEngine;

    @Inject
    CacheClient dataIdCache;

    public void bufferData(IncomingData incomingData) {
        executor.submit(() -> {
            sendData(filterIncomingData(incomingData));
        });
    }

    public void bufferEvents(Collection<Event> events) {
        executor.submit(() ->  {
            sendEvents(events);
        });
    }

    private Collection<Data> filterIncomingData(IncomingData incomingData) {
        Collection<Data> data = incomingData.getIncomingData();
        data = incomingData.isRaw() ? dataIdCache.filterData(data) : data;

        // check to see if any data can be used to generate data-driven group members
        checkDataDrivenGroupTriggers(data);

        return data;
    }

    private void sendData(Collection<Data> data) {
        log.debugf("Sending %s data to AlertsEngine.", data.size());
        try {
            alertsEngine.sendData(data);

        } catch (Exception e) {
            log.errorf("Failed sending data: %s", e.getMessage());
        }
    }

    private void sendEvents(Collection<Event> events) {
        log.debugf("Sending %s events to AlertsEngine.", events.size());
        try {
            alertsEngine.sendEvents(events);
        } catch (Exception e) {
            log.errorf("Failed sending events: %s", e.getMessage());
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

}
