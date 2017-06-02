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
package org.hawkular.alerts.engine.impl.ispn;

import static org.hawkular.alerts.engine.impl.ispn.IspnPk.pk;
import static org.hawkular.alerts.engine.util.Utils.isEmpty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.paging.AlertComparator;
import org.hawkular.alerts.api.model.paging.Order;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.services.ActionsService;
import org.hawkular.alerts.api.services.AlertsCriteria;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.api.services.EventsCriteria;
import org.hawkular.alerts.api.services.PropertiesService;
import org.hawkular.alerts.cache.IspnCacheManager;
import org.hawkular.alerts.engine.service.AlertsEngine;
import org.hawkular.alerts.engine.service.IncomingDataManager;
import org.hawkular.alerts.log.AlertingLogger;
import org.hawkular.commons.log.MsgLogging;
import org.infinispan.Cache;
import org.infinispan.query.Search;
import org.infinispan.query.dsl.QueryFactory;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class IspnAlertsServiceImpl implements AlertsService {
    private static final AlertingLogger log = MsgLogging.getMsgLogger(AlertingLogger.class, IspnAlertsServiceImpl.class);

    AlertsEngine alertsEngine;

    DefinitionsService definitionsService;

    ActionsService actionsService;

    IncomingDataManager incomingDataManager;

    PropertiesService properties;

    ExecutorService executor;

    Cache<String, Object> backend;

    QueryFactory queryFactory;

    public void init() {
        backend = IspnCacheManager.getCacheManager().getCache("backend");
        if (backend == null) {
            log.error("Ispn backend cache not found. Check configuration.");
            throw new RuntimeException("backend cache not found");
        }
        queryFactory = Search.getQueryFactory(backend);
    }

    public void setAlertsEngine(AlertsEngine alertsEngine) {
        this.alertsEngine = alertsEngine;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void setActionsService(ActionsService actionsService) {
        this.actionsService = actionsService;
    }

    public void setIncomingDataManager(IncomingDataManager incomingDataManager) {
        this.incomingDataManager = incomingDataManager;
    }

    public void setProperties(PropertiesService properties) {
        this.properties = properties;
    }

    public void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    @Override
    public void ackAlerts(String tenantId, Collection<String> alertIds, String ackBy, String ackNotes) throws Exception {

    }

    @Override
    public void addAlerts(Collection<Alert> alerts) throws Exception {
        if (alerts == null) {
            throw new IllegalArgumentException("Alerts must be not null");
        }
        if (alerts.isEmpty()) {
            return;
        }
        log.debugf("Adding %s alerts", alerts.size());
        for (Alert alert : alerts) {
            backend.put(pk(alert), alert);
        }
    }

    @Override
    public void addAlertTags(String tenantId, Collection<String> alertIds, Map<String, String> tags) throws Exception {

    }

    @Override
    public void addEvents(Collection<Event> events) throws Exception {

    }

    @Override
    public void addEventTags(String tenantId, Collection<String> eventIds, Map<String, String> tags) throws Exception {

    }

    @Override
    public void persistEvents(Collection<Event> events) throws Exception {

    }

    @Override
    public void addNote(String tenantId, String alertId, String user, String text) throws Exception {

    }

    @Override
    public int deleteAlerts(String tenantId, AlertsCriteria criteria) throws Exception {
        return 0;
    }

    @Override
    public int deleteEvents(String tenantId, EventsCriteria criteria) throws Exception {
        return 0;
    }

    @Override
    public Alert getAlert(String tenantId, String alertId, boolean thin) throws Exception {
        return null;
    }

    @Override
    public Page<Alert> getAlerts(String tenantId, AlertsCriteria criteria, Pager pager) throws Exception {
        return null;
    }

    @Override
    public Page<Alert> getAlerts(Set<String> tenantIds, AlertsCriteria criteria, Pager pager) throws Exception {
        if (isEmpty(tenantIds)) {
            throw new IllegalArgumentException("TenantIds must be not null");
        }
        boolean filter = (null != criteria && criteria.hasCriteria());
        if (filter) {
            log.debugf("getAlerts criteria: %s", criteria.toString());
        }

        StringBuilder query = new StringBuilder("from org.hawkular.alerts.api.model.event.Alert where ");
        query.append("(");
        Iterator<String> iter = tenantIds.iterator();
        while (iter.hasNext()) {
            String tenantId = iter.next();
            query.append("tenantId = '").append(tenantId).append("' ");
            if (iter.hasNext()) {
                query.append("or ");
            }
        }
        query.append(")");

        if (filter) {

        }

        List<Alert> alerts = queryFactory.create(query.toString()).list();
        if (alerts.isEmpty()) {
            return new Page<>(alerts, pager, 0);
        } else {
            return preparePage(alerts, pager);
        }
    }

    @Override
    public Event getEvent(String tenantId, String eventId, boolean thin) throws Exception {
        return null;
    }

    @Override
    public Page<Event> getEvents(String tenantId, EventsCriteria criteria, Pager pager) throws Exception {
        return null;
    }

    @Override
    public Page<Event> getEvents(Set<String> tenantIds, EventsCriteria criteria, Pager pager) throws Exception {
        return null;
    }

    @Override
    public void removeAlertTags(String tenantId, Collection<String> alertIds, Collection<String> tags) throws Exception {

    }

    @Override
    public void removeEventTags(String tenantId, Collection<String> eventIds, Collection<String> tags) throws Exception {

    }

    @Override
    public void resolveAlerts(String tenantId, Collection<String> alertIds, String resolvedBy, String resolvedNotes, List<Set<ConditionEval>> resolvedEvalSets) throws Exception {

    }

    @Override
    public void resolveAlertsForTrigger(String tenantId, String triggerId, String resolvedBy, String resolvedNotes, List<Set<ConditionEval>> resolvedEvalSets) throws Exception {

    }

    @Override
    public void sendData(Collection<Data> data) throws Exception {

    }

    @Override
    public void sendData(Collection<Data> data, boolean ignoreFiltering) throws Exception {

    }

    @Override
    public void sendEvents(Collection<Event> events) throws Exception {

    }

    @Override
    public void sendEvents(Collection<Event> events, boolean ignoreFiltering) throws Exception {

    }

    private Page<Alert> preparePage(List<Alert> alerts, Pager pager) {
        if (pager != null) {
            if (pager.getOrder() != null
                    && !pager.getOrder().isEmpty()
                    && pager.getOrder().get(0).getField() == null) {
                pager = Pager.builder()
                        .withPageSize(pager.getPageSize())
                        .withStartPage(pager.getPageNumber())
                        .orderBy(AlertComparator.Field.ALERT_ID.getText(), Order.Direction.DESCENDING).build();
            }
            List<Alert> ordered = alerts;
            if (pager.getOrder() != null) {
                pager.getOrder().stream()
                        .filter(o -> o.getField() != null && o.getDirection() != null)
                        .forEach(o -> {
                            AlertComparator comparator = new AlertComparator(o.getField(), o.getDirection());
                            Collections.sort(ordered, comparator);
                        });
            }
            if (!pager.isLimited() || ordered.size() < pager.getStart()) {
                pager = new Pager(0, ordered.size(), pager.getOrder());
                return new Page<>(ordered, pager, ordered.size());
            }
            if (pager.getEnd() >= ordered.size()) {
                return new Page<>(ordered.subList(pager.getStart(), ordered.size()), pager, ordered.size());
            }
            return new Page<>(ordered.subList(pager.getStart(), pager.getEnd()), pager, ordered.size());
        } else {
            AlertComparator.Field defaultField = AlertComparator.Field.ALERT_ID;
            Order.Direction defaultDirection = Order.Direction.ASCENDING;
            AlertComparator comparator = new AlertComparator(defaultField.getText(), defaultDirection.ASCENDING);
            pager = Pager.builder().withPageSize(alerts.size()).orderBy(defaultField.getText(), defaultDirection)
                    .build();
            Collections.sort(alerts, comparator);
            return new Page<>(alerts, pager, alerts.size());
        }
    }
}
