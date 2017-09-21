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

import static org.hawkular.alerts.api.util.Util.isEmpty;
import static org.hawkular.alerts.engine.impl.ispn.IspnPk.pk;
import static org.hawkular.alerts.engine.impl.ispn.IspnPk.pkFromEventId;
import static org.hawkular.alerts.engine.tags.ExpressionTagQueryParser.ExpressionTagResolver.EQ;
import static org.hawkular.alerts.engine.tags.ExpressionTagQueryParser.ExpressionTagResolver.NEQ;
import static org.hawkular.alerts.engine.util.Utils.extractAlertIds;
import static org.hawkular.alerts.engine.util.Utils.extractCategories;
import static org.hawkular.alerts.engine.util.Utils.extractEventIds;
import static org.hawkular.alerts.engine.util.Utils.extractSeverity;
import static org.hawkular.alerts.engine.util.Utils.extractStatus;
import static org.hawkular.alerts.engine.util.Utils.extractTriggerIds;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.hawkular.alerts.api.model.Severity;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.event.Alert.Status;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.event.EventType;
import org.hawkular.alerts.api.model.paging.AlertComparator;
import org.hawkular.alerts.api.model.paging.EventComparator;
import org.hawkular.alerts.api.model.paging.Order;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.model.trigger.Mode;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.services.ActionsService;
import org.hawkular.alerts.api.services.AlertsCriteria;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.api.services.EventsCriteria;
import org.hawkular.alerts.api.services.PropertiesService;
import org.hawkular.alerts.cache.IspnCacheManager;
import org.hawkular.alerts.engine.impl.IncomingDataManagerImpl;
import org.hawkular.alerts.engine.impl.ispn.model.IspnEvent;
import org.hawkular.alerts.engine.impl.ispn.model.TagsBridge;
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

    Cache<String, Object> backend;

    QueryFactory queryFactory;

    IspnExpressionTagQueryParser parser;

    public void init() {
        backend = IspnCacheManager.getCacheManager().getCache("backend");
        if (backend == null) {
            log.error("Ispn backend cache not found. Check configuration.");
            throw new RuntimeException("backend cache not found");
        }
        queryFactory = Search.getQueryFactory(backend);
        parser = new IspnExpressionTagQueryParser((tokens, query) -> {
            if (tokens != null) {
                String tag;
                if (tokens.size() == 1) {
                    // tag
                    tag = tokens.get(0);
                    query.append("'").append(tag).append("'");
                } else if (tokens.size() == 2) {
                    // not tag
                    tag = tokens.get(1);
                    query.append("not '").append(tag).append("'");
                } else {
                    tag = tokens.get(0);
                    String op;
                    String value;
                    if (tokens.size() == 3) {
                        op = tokens.get(1);
                        value = tokens.get(2);
                        boolean isRegexp = value.startsWith("'");
                        String regexp = "";
                        if (isRegexp) {
                            regexp = value.substring(1, value.length() - 1);
                            regexp = regexp.equals("*") ? ".*" : regexp;
                        }
                        if (op.equalsIgnoreCase(EQ)) {
                            // tag =
                            if (isRegexp) {
                                query.append("/").append(tag).append(TagsBridge.SEPARATOR).append(regexp).append("/");
                            } else {
                                query.append("'").append(tag).append(TagsBridge.SEPARATOR).append(value).append("'");
                            }
                        } else if (op.equalsIgnoreCase(NEQ)) {
                            // tag !=
                            query.append("'").append(tag).append("' and ").append("not ");
                            if (isRegexp) {
                                query.append("/").append(tag).append(TagsBridge.SEPARATOR).append(regexp).append("/");
                            } else {
                                query.append("'").append(tag).append(TagsBridge.SEPARATOR).append(value).append("'");
                            }
                        } else {
                            // tag in []
                            String array = value.substring(1, value.length() - 1);
                            String[] values = array.split(",");
                            for (int i = 0; i < values.length; i++) {
                                String item = values[i];
                                isRegexp = item.startsWith("'");
                                regexp = item.substring(1, item.length() - 1);
                                regexp = regexp.equals("*") ? ".*" : regexp;
                                if (isRegexp) {
                                    query.append("/").append(tag).append(TagsBridge.SEPARATOR).append(regexp).append("/");
                                } else {
                                    query.append("'").append(tag).append(TagsBridge.SEPARATOR).append(item).append("'");
                                }
                                if (i + 1 < values.length) {
                                    query.append(" or ");
                                }
                            }
                        }
                    } else {
                        // not in array
                        String array = tokens.get(3).substring(1, tokens.get(3).length() - 1);
                        String[] values = array.split(",");
                        query.append("'").append(tag).append("' and ");
                        for (int i = 0; i < values.length; i++) {
                            String item = values[i];
                            boolean isRegexp = item.startsWith("'");
                            String regexp = item.substring(1, item.length() - 1);
                            regexp = regexp.equals("*") ? ".*" : regexp;
                            query.append("(");
                            if (isRegexp) {
                                query.append("not /").append(tag).append(TagsBridge.SEPARATOR).append(regexp).append("/");
                            } else {
                                query.append("not '").append(tag).append(TagsBridge.SEPARATOR).append(item).append("'");
                            }
                            query.append(")");
                            if (i + 1 < values.length) {
                                query.append(" and ");
                            }
                        }
                    }
                }
            }
        });
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

    @Override
    public void ackAlerts(String tenantId, Collection<String> alertIds, String ackBy, String ackNotes) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(alertIds)) {
            return;
        }

        if (isEmpty(ackBy)) {
            ackBy = "unknown";
        }
        if (isEmpty(ackNotes)) {
            ackNotes = "none";
        }

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setAlertIds(alertIds);
        List<Alert> alertsToAck = getAlerts(tenantId, criteria, null);

        for (Alert alert : alertsToAck) {
            alert.addNote(ackBy, ackNotes);
            alert.addLifecycle(Status.ACKNOWLEDGED, ackBy, System.currentTimeMillis());
            backend.put(pk(alert), new IspnEvent(alert));
            sendAction(alert);
        }
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
            backend.put(pk(alert), new IspnEvent(alert));
        }
    }

    @Override
    public void addAlertTags(String tenantId, Collection<String> alertIds, Map<String, String> tags) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(alertIds)) {
            throw new IllegalArgumentException("AlertIds must be not null");
        }
        if (isEmpty(tags)) {
            throw new IllegalArgumentException("Tags must be not null");
        }

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setAlertIds(alertIds);
        Page<Alert> existingAlerts = getAlerts(tenantId, criteria, null);

        for (Alert alert : existingAlerts) {
            tags.entrySet().stream().forEach(tag -> alert.addTag(tag.getKey(), tag.getValue()));
            backend.put(pk(alert), new IspnEvent(alert));
        }
    }

    @Override
    public void addEvents(Collection<Event> events) throws Exception {
        if (null == events || events.isEmpty()) {
            return;
        }
        persistEvents(events);
        sendEvents(events);
    }

    @Override
    public void addEventTags(String tenantId, Collection<String> eventIds, Map<String, String> tags) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(eventIds)) {
            throw new IllegalArgumentException("AlertIds must be not null");
        }
        if (isEmpty(tags)) {
            throw new IllegalArgumentException("Tags must be not null");
        }

        EventsCriteria criteria = new EventsCriteria();
        criteria.setEventIds(eventIds);
        Page<Event> existingEvents = getEvents(tenantId, criteria, null);

        for (Event event : existingEvents) {
            tags.entrySet().stream().forEach(tag -> event.addTag(tag.getKey(), tag.getValue()));
            backend.put(pk(event), new IspnEvent(event));
        }
    }

    @Override
    public void persistEvents(Collection<Event> events) throws Exception {
        if (events == null) {
            throw new IllegalArgumentException("Events must be not null");
        }
        if (events.isEmpty()) {
            return;
        }
        log.debugf("Adding %s events", events.size());
        for (Event event : events) {
            backend.put(pk(event), new IspnEvent(event));
        }
    }

    @Override
    public void addNote(String tenantId, String alertId, String user, String text) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(alertId)) {
            throw new IllegalArgumentException("AlertId must be not null");
        }
        if (isEmpty(user) || isEmpty(text)) {
            throw new IllegalArgumentException("user or text must be not null");
        }

        Alert alert = getAlert(tenantId, alertId, false);
        if (alert == null) {
            return;
        }

        alert.addNote(user, text);

        backend.put(pk(alert), new IspnEvent(alert));
    }

    @Override
    public int deleteAlerts(String tenantId, AlertsCriteria criteria) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (null == criteria) {
            throw new IllegalArgumentException("Criteria must be not null");
        }
        // no need to fetch the evalSets to perform the necessary deletes
        criteria.setThin(true);
        List<Alert> alertsToDelete = getAlerts(tenantId, criteria, null);

        if (alertsToDelete.isEmpty()) {
            return 0;
        }
        try {
            backend.startBatch();
            for (Alert alert : alertsToDelete) {
                backend.remove(pk(alert));
            }
            backend.endBatch(true);
        } catch (Exception e) {
            backend.endBatch(false);
            throw e;
        }
        return alertsToDelete.size();
    }

    @Override
    public int deleteEvents(String tenantId, EventsCriteria criteria) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (null == criteria) {
            throw new IllegalArgumentException("Criteria must be not null");
        }
        // no need to fetch the evalSets to perform the necessary deletes
        criteria.setThin(true);
        List<Event> eventsToDelete = getEvents(tenantId, criteria, null);

        if (eventsToDelete.isEmpty()) {
            return 0;
        }
        try {
            backend.startBatch();
            for (Event event : eventsToDelete) {
                backend.remove(pk(event));
            }
            backend.endBatch(true);
        } catch (Exception e) {
            backend.endBatch(false);
            throw e;
        }
        return eventsToDelete.size();
    }

    @Override
    public Alert getAlert(String tenantId, String alertId, boolean thin) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(alertId)) {
            throw new IllegalArgumentException("AlertId must be not null");
        }

        String pk = pkFromEventId(tenantId, alertId);
        IspnEvent ispnEvent = (IspnEvent) backend.get(pk);
        return ispnEvent != null && ispnEvent.getEvent() instanceof Alert ? (Alert) ispnEvent.getEvent() : null;
    }

    @Override
    public Page<Alert> getAlerts(String tenantId, AlertsCriteria criteria, Pager pager) throws Exception {
        return getAlerts(Collections.singleton(tenantId), criteria, pager);
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

        StringBuilder query = new StringBuilder("from org.hawkular.alerts.engine.impl.ispn.model.IspnEvent where ");
        query.append("eventType = 'ALERT' and ");
        query.append("(");
        Iterator<String> iter = tenantIds.iterator();
        while (iter.hasNext()) {
            String tenantId = iter.next();
            query.append("tenantId = '").append(tenantId).append("' ");
            if (iter.hasNext()) {
                query.append("or ");
            }
        }
        query.append(") ");

        if (filter) {
           if (criteria.hasAlertIdCriteria()) {
               query.append("and (");
               iter = extractAlertIds(criteria).iterator();
               while (iter.hasNext()) {
                   String alertId = iter.next();
                   query.append("id = '").append(alertId).append("' ");
                   if (iter.hasNext()) {
                       query.append("or ");
                   }
               }
               query.append(") ");
           }
           if (criteria.hasTagQueryCriteria()) {
               query.append("and (tags : ");
               parseTagQuery(criteria.getTagQuery(), query);
               query.append(") ");
           }
           if (criteria.hasTriggerIdCriteria()) {
                query.append("and (");
                iter = extractTriggerIds(criteria).iterator();
                while (iter.hasNext()) {
                    String triggerId = iter.next();
                    query.append("triggerId = '").append(triggerId).append("' ");
                    if (iter.hasNext()) {
                        query.append("or ");
                    }
                }
                query.append(") ");
           }
           if (criteria.hasCTimeCriteria()) {
                query.append("and (");
                if (criteria.getStartTime() != null) {
                    query.append("ctime >= ").append(criteria.getStartTime()).append(" ");
                }
                if (criteria.getEndTime() != null) {
                    if (criteria.getStartTime() != null) {
                        query.append("and ");
                    }
                    query.append("ctime <= ").append(criteria.getEndTime()).append(" ");
                }
                query.append(") ");
           }
           if (criteria.hasResolvedTimeCriteria()) {
               query.append("and (status = '").append(Status.RESOLVED.name()).append("' and ");
               if (criteria.getStartResolvedTime() != null) {
                   query.append("stime >= ").append(criteria.getStartResolvedTime()).append(" ");
               }
               if (criteria.getEndResolvedTime() != null) {
                   if (criteria.getStartResolvedTime() != null) {
                       query.append("and ");
                   }
                   query.append("stime <= ").append(criteria.getEndResolvedTime()).append(" ");
               }
               query.append(") ");
           }
           if (criteria.hasAckTimeCriteria()) {
               query.append("and (status = '").append(Status.ACKNOWLEDGED.name()).append("' and ");
               if (criteria.getStartAckTime() != null) {
                   query.append("stime >= ").append(criteria.getStartAckTime()).append(" ");
               }
               if (criteria.getEndAckTime() != null) {
                   if (criteria.getStartAckTime() != null) {
                       query.append("and ");
                   }
                   query.append("stime <= ").append(criteria.getEndAckTime()).append(" ");
               }
               query.append(") ");
           }
           if (criteria.hasStatusTimeCriteria()) {
               query.append("and (");
               if (criteria.getStartStatusTime() != null) {
                   query.append("stime >= ").append(criteria.getStartStatusTime()).append(" ");
               }
               if (criteria.getEndTime() != null) {
                   if (criteria.getStartTime() != null) {
                       query.append("and ");
                   }
                   query.append("stime <= ").append(criteria.getEndStatusTime()).append(" ");
               }
               query.append(") ");
           }
           if (criteria.hasSeverityCriteria()) {
               query.append("and (");
                Iterator<Severity> iterSev = extractSeverity(criteria).iterator();
               while (iterSev.hasNext()) {
                   Severity severity = iterSev.next();
                   query.append("severity = '").append(severity.name()).append("' ");
                   if (iterSev.hasNext()) {
                       query.append(" or ");
                   }
               }
               query.append(") ");
           }
           if (criteria.hasStatusCriteria()) {
               query.append("and (");
               Iterator<Status> iterStatus = extractStatus(criteria).iterator();
               while (iterStatus.hasNext()) {
                   Status status = iterStatus.next();
                   query.append("status = '").append(status.name()).append("' ");
                   if (iterStatus.hasNext()) {
                       query.append(" or ");
                   }
               }
               query.append(") ");
           }
        }

        List<IspnEvent> ispnEvents = queryFactory.create(query.toString()).list();
        List<Alert> alerts = ispnEvents.stream().map(ispnEvent -> {
            if (criteria != null && criteria.isThin()) {
                Alert alert = new Alert((Alert) ispnEvent.getEvent());
                alert.setDampening(null);
                alert.setEvalSets(null);
                alert.setResolvedEvalSets(null);
                return alert;
            }
            return (Alert) ispnEvent.getEvent();
        }).collect(Collectors.toList());
        if (alerts.isEmpty()) {
            return new Page<>(alerts, pager, 0);
        } else {
            return preparePage(alerts, pager);
        }
    }

    @Override
    public Event getEvent(String tenantId, String eventId, boolean thin) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(eventId)) {
            throw new IllegalArgumentException("EventId must be not null");
        }

        String pk = pkFromEventId(tenantId, eventId);
        IspnEvent ispnEvent = (IspnEvent) backend.get(pk);
        return ispnEvent != null ? ispnEvent.getEvent() : null;
    }

    @Override
    public Page<Event> getEvents(String tenantId, EventsCriteria criteria, Pager pager) throws Exception {
        return getEvents(Collections.singleton(tenantId), criteria, pager);
    }

    @Override
    public Page<Event> getEvents(Set<String> tenantIds, EventsCriteria criteria, Pager pager) throws Exception {
        if (isEmpty(tenantIds)) {
            throw new IllegalArgumentException("TenantIds must be not null");
        }
        boolean filter = (null != criteria && criteria.hasCriteria());
        if (filter) {
            log.debugf("getEvents criteria: %s", criteria.toString());
        }

        StringBuilder query = new StringBuilder("from org.hawkular.alerts.engine.impl.ispn.model.IspnEvent where ");
        query.append("(");
        Iterator<String> iter = tenantIds.iterator();
        while (iter.hasNext()) {
            String tenantId = iter.next();
            query.append("tenantId = '").append(tenantId).append("' ");
            if (iter.hasNext()) {
                query.append("or ");
            }
        }
        query.append(") ");

        if (filter) {
            if (criteria.hasEventTypeCriteria()) {
                try {
                    EventType eventType = EventType.valueOf(criteria.getEventType());
                    query.append("and eventType = '").append(eventType.name()).append("' ");
                } catch (Exception e) {
                    log.debugf("EventType [%s] is not valid, ignoring this criteria", criteria.getEventType());
                }
            }
            if (criteria.hasEventIdCriteria()) {
                query.append("and (");
                iter = extractEventIds(criteria).iterator();
                while (iter.hasNext()) {
                    String eventId = iter.next();
                    query.append("id = '").append(eventId).append("' ");
                    if (iter.hasNext()) {
                        query.append("or ");
                    }
                }
                query.append(") ");
            }
            if (criteria.hasTagQueryCriteria()) {
                query.append("and (tags : ");
                parseTagQuery(criteria.getTagQuery(), query);
                query.append(") ");
            }
            if (criteria.hasTriggerIdCriteria()) {
                query.append("and (");
                iter = extractTriggerIds(criteria).iterator();
                while (iter.hasNext()) {
                    String triggerId = iter.next();
                    query.append("triggerId = '").append(triggerId).append("' ");
                    if (iter.hasNext()) {
                        query.append("or ");
                    }
                }
                query.append(") ");
            }
            if (criteria.hasCTimeCriteria()) {
                query.append("and (");
                if (criteria.getStartTime() != null) {
                    query.append("ctime >= ").append(criteria.getStartTime()).append(" ");
                }
                if (criteria.getEndTime() != null) {
                    if (criteria.getStartTime() != null) {
                        query.append("and ");
                    }
                    query.append("ctime <= ").append(criteria.getEndTime()).append(" ");
                }
                query.append(") ");
            }
            if (criteria.hasCategoryCriteria()) {
                query.append("and (");
                iter = extractCategories(criteria).iterator();
                while (iter.hasNext()) {
                    String category = iter.next();
                    query.append("category = '").append(category).append("' ");
                    if (iter.hasNext()) {
                        query.append(" or ");
                    }
                }
                query.append(") ");
            }
        }

        List<IspnEvent> ispnEvents = queryFactory.create(query.toString()).list();
        List<Event> events = ispnEvents.stream().map(e -> e.getEvent()).collect(Collectors.toList());
        if (events.isEmpty()) {
            return new Page<>(events, pager, 0);
        } else {
            return prepareEventsPage(events, pager);
        }
    }

    @Override
    public void removeAlertTags(String tenantId, Collection<String> alertIds, Collection<String> tags) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(alertIds)) {
            throw new IllegalArgumentException("AlertIds must be not null");
        }
        if (isEmpty(tags)) {
            throw new IllegalArgumentException("Tags must be not null");
        }

        // Only untag existing alerts
        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setAlertIds(alertIds);
        Page<Alert> existingAlerts = getAlerts(tenantId, criteria, null);

        for (Alert alert : existingAlerts) {
            boolean modified = false;
            for (String tag : tags) {
                if (alert.getTags().containsKey(tag)) {
                    alert.removeTag(tag);
                    modified = true;
                }
            }
            if (modified) {
                backend.put(pk(alert), new IspnEvent(alert));
            }
        }
    }

    @Override
    public void removeEventTags(String tenantId, Collection<String> eventIds, Collection<String> tags) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(eventIds)) {
            throw new IllegalArgumentException("EventIds must be not null");
        }
        if (isEmpty(tags)) {
            throw new IllegalArgumentException("Tags must be not null");
        }

        // Only untag existing events
        EventsCriteria criteria = new EventsCriteria();
        criteria.setEventIds(eventIds);
        Page<Event> existingEvents = getEvents(tenantId, criteria, null);

        for (Event event : existingEvents) {
            boolean modified = false;
            for (String tag : tags) {
                if (event.getTags().containsKey(tag)) {
                    event.removeTag(tag);
                    modified = true;
                }
            }
            if (modified) {
                backend.put(pk(event), new IspnEvent(event));
            }
        }
    }

    @Override
    public void resolveAlerts(String tenantId, Collection<String> alertIds, String resolvedBy, String resolvedNotes, List<Set<ConditionEval>> resolvedEvalSets) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(alertIds)) {
            return;
        }

        if (isEmpty(resolvedBy)) {
            resolvedBy = "unknown";
        }
        if (isEmpty(resolvedNotes)) {
            resolvedNotes = "none";
        }

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setAlertIds(alertIds);
        List<Alert> alertsToResolve = getAlerts(tenantId, criteria, null);

        // resolve the alerts
        for (Alert alert : alertsToResolve) {
            alert.addNote(resolvedBy, resolvedNotes);
            alert.setResolvedEvalSets(resolvedEvalSets);
            alert.addLifecycle(Status.RESOLVED, resolvedBy, System.currentTimeMillis());
            backend.put(pk(alert), new IspnEvent(alert));
            sendAction(alert);
        }

        // gather the triggerIds of the triggers we need to check for resolve options
        Set<String> triggerIds = alertsToResolve.stream().map(alert -> alert.getTriggerId()).collect(Collectors.toSet());

        // handle resolve options
        triggerIds.stream().forEach(tid -> handleResolveOptions(tenantId, tid, true));

    }

    @Override
    public void resolveAlertsForTrigger(String tenantId, String triggerId, String resolvedBy, String resolvedNotes, List<Set<ConditionEval>> resolvedEvalSets) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(triggerId)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }

        if (isEmpty(resolvedBy)) {
            resolvedBy = "unknown";
        }
        if (isEmpty(resolvedNotes)) {
            resolvedNotes = "none";
        }

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setTriggerId(triggerId);
        criteria.setStatusSet(EnumSet.complementOf(EnumSet.of(Status.RESOLVED)));
        List<Alert> alertsToResolve = getAlerts(tenantId, criteria, null);

        for (Alert alert : alertsToResolve) {
            alert.addNote(resolvedBy, resolvedNotes);
            alert.setResolvedEvalSets(resolvedEvalSets);
            alert.addLifecycle(Status.RESOLVED, resolvedBy, System.currentTimeMillis());
            backend.put(pk(alert), new IspnEvent(alert));
            sendAction(alert);
        }

        handleResolveOptions(tenantId, triggerId, false);

    }

    @Override
    public void sendData(Collection<Data> data) throws Exception {
        sendData(data, false);
    }

    @Override
    public void sendData(Collection<Data> data, boolean ignoreFiltering) throws Exception {
        if (isEmpty(data)) {
            return;
        }

        if (incomingDataManager == null) {
            log.debug("incomingDataManager is not defined. Only valid for testing.");
            return;
        }

        incomingDataManager.bufferData(new IncomingDataManagerImpl.IncomingData(data, !ignoreFiltering));
    }

    @Override
    public void sendEvents(Collection<Event> events) throws Exception {
        sendEvents(events, false);
    }

    @Override
    public void sendEvents(Collection<Event> events, boolean ignoreFiltering) throws Exception {
        if (isEmpty(events)) {
            return;
        }

        if (incomingDataManager == null) {
            log.debug("incomingDataManager is not defined. Only valid for testing.");
            return;
        }

        incomingDataManager.bufferEvents(new IncomingDataManagerImpl.IncomingEvents(events, !ignoreFiltering));
    }

    protected void parseTagQuery(String tagQuery, StringBuilder query) throws Exception {
        parser.resolveQuery(tagQuery, query);
    }

    // Private methods

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

    private void sendAction(Alert a) {
        if (actionsService != null && a != null && a.getTrigger() != null) {
            actionsService.send(a.getTrigger(), a);
        }
    }

    private void handleResolveOptions(String tenantId, String triggerId, boolean checkIfAllResolved) {

        if (definitionsService == null || alertsEngine == null) {
            log.debug("definitionsService or alertsEngine are not defined. Only valid for testing.");
            return;
        }

        try {
            Trigger trigger = definitionsService.getTrigger(tenantId, triggerId);
            if (null == trigger) {
                return;
            }

            boolean setEnabled = trigger.isAutoEnable() && !trigger.isEnabled();
            boolean setFiring = trigger.isAutoResolve();

            // Only reload the trigger if it is not already in firing mode, otherwise we could lose partial matching.
            // This is a rare case because a trigger with autoResolve=true will not be in firing mode with an
            // unresolved trigger. But it is possible, either by mistake, or timing,  for a client to try and
            // resolve an already-resolved alert.
            if (setFiring) {
                Trigger loadedTrigger = alertsEngine.getLoadedTrigger(trigger);
                if (null != loadedTrigger && Mode.FIRING == loadedTrigger.getMode()) {
                    if (log.isDebugEnabled()) {
                        log.debug("Ignoring setFiring, loaded Trigger already in firing mode " +
                                loadedTrigger.toString());
                    }
                    setFiring = false;
                }
            }

            if (!(setEnabled || setFiring)) {
                return;
            }

            boolean allResolved = true;
            if (checkIfAllResolved) {
                AlertsCriteria ac = new AlertsCriteria();
                ac.setTriggerId(triggerId);
                ac.setStatusSet(EnumSet.complementOf(EnumSet.of(Status.RESOLVED)));
                Page<Alert> unresolvedAlerts = getAlerts(tenantId, ac, new Pager(0, 1, Order.unspecified()));
                allResolved = unresolvedAlerts.isEmpty();
            }

            if (!allResolved) {
                log.debugf("Ignoring resolveOptions, not all Alerts for Trigger %s are resolved", trigger.toString());
                return;
            }

            // Either update the trigger, which implicitly reloads the trigger (and as such resets to firing mode)
            // or perform an explicit reload to reset to firing mode.
            if (setEnabled) {
                trigger.setEnabled(true);
                definitionsService.updateTrigger(tenantId, trigger);
            } else {
                alertsEngine.reloadTrigger(tenantId, triggerId);
            }
        } catch (Exception e) {
            log.errorDatabaseException(e.getMessage());
        }

    }

    private Page<Event> prepareEventsPage(List<Event> events, Pager pager) {
        if (pager != null) {
            if (pager.getOrder() != null
                    && !pager.getOrder().isEmpty()
                    && pager.getOrder().get(0).getField() == null) {
                pager = Pager.builder()
                        .withPageSize(pager.getPageSize())
                        .withStartPage(pager.getPageNumber())
                        .orderBy(EventComparator.Field.ID.getName(), Order.Direction.DESCENDING).build();
            }
            List<Event> ordered = events;
            if (pager.getOrder() != null) {
                pager.getOrder()
                        .stream()
                        .filter(o -> o.getField() != null && o.getDirection() != null)
                        .forEach(o -> {
                            EventComparator comparator = new EventComparator(o.getField(), o.getDirection());
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
            EventComparator.Field defaultField = EventComparator.Field.ID;
            Order.Direction defaultDirection = Order.Direction.ASCENDING;
            pager = Pager.builder().withPageSize(events.size()).orderBy(defaultField.getName(),
                    defaultDirection).build();
            EventComparator comparator = new EventComparator(defaultField.getName(), defaultDirection);
            Collections.sort(events, comparator);
            return new Page<>(events, pager, events.size());
        }
    }

}
