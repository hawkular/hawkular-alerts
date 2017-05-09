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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import org.hawkular.alerts.api.json.JsonUtil;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.model.action.ActionDefinition;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.paging.ActionComparator;
import org.hawkular.alerts.api.model.paging.ActionComparator.Field;
import org.hawkular.alerts.api.model.paging.Order;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.model.trigger.TriggerAction;
import org.hawkular.alerts.api.services.ActionListener;
import org.hawkular.alerts.api.services.ActionsCriteria;
import org.hawkular.alerts.api.services.ActionsService;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.engine.cache.ActionsCacheManager;
import org.hawkular.alerts.engine.util.ActionsValidator;
import org.hawkular.alerts.log.MsgLogger;
import org.jboss.logging.Logger;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.google.common.util.concurrent.Futures;

/**
 * Cassandra implementation of {@link org.hawkular.alerts.api.services.ActionsService}.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class CassActionsServiceImpl implements ActionsService {
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(CassActionsServiceImpl.class);

    private static final String WAITING_RESULT = "WAITING";
    private static final String UNKNOWN_RESULT = "UNKNOWN";

    AlertsContext alertsContext;

    DefinitionsService definitions;

    ActionsCacheManager actionsCacheManager;

    private ExecutorService executor;

    Session session;

    public CassActionsServiceImpl() {
        log.debug("Creating instance.");
    }

    public void setAlertsContext(AlertsContext alertsContext) {
        this.alertsContext = alertsContext;
    }

    public void setDefinitions(DefinitionsService definitions) {
        this.definitions = definitions;
    }

    public void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public void setActionsCacheManager(ActionsCacheManager actionsCacheManager) {
        this.actionsCacheManager = actionsCacheManager;
    }

    @Override
    public void send(Trigger trigger, Event event) {
        if (trigger == null) {
            throw new IllegalArgumentException("Trigger must be not null");
        }
        if (!isEmpty(trigger.getActions())) {
            for (TriggerAction triggerAction : trigger.getActions()) {
                send(triggerAction, event);
            }
        }
        if (actionsCacheManager.hasGlobalActions()) {
            Collection<ActionDefinition> globalActions = actionsCacheManager.getGlobalActions(trigger.getTenantId());
            for (ActionDefinition globalAction : globalActions) {
                send(globalAction, event);
            }
        }
    }

    private void send(final TriggerAction triggerAction, final Event event) {
        if (triggerAction == null || isEmpty(triggerAction.getTenantId()) ||
                isEmpty(triggerAction.getActionPlugin()) || isEmpty(triggerAction.getActionId())) {
            throw new IllegalArgumentException("TriggerAction must be not null");
        }
        if (event == null || isEmpty(event.getTenantId())) {
            throw new IllegalArgumentException("Event must be not null");
        }
        if (!triggerAction.getTenantId().equals(event.getTenantId())) {
            throw new IllegalArgumentException("TriggerAction and Event must have same tenantId");
        }
        executor.submit(() -> {
            Action action = new Action(triggerAction.getTenantId(), triggerAction.getActionPlugin(),
                    triggerAction.getActionId(), event);
            try {
                ActionDefinition actionDefinition = definitions.getActionDefinition(triggerAction.getTenantId(),
                        triggerAction.getActionPlugin(), triggerAction.getActionId());
                Map<String, String> defaultProperties =
                        definitions.getDefaultActionPlugin(triggerAction.getActionPlugin());
                if (actionDefinition != null && defaultProperties != null) {
                    Map<String, String> mixedProps = mixProperties(actionDefinition.getProperties(), defaultProperties);
                    action.setProperties(mixedProps);
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("Action " + action + " has not an ActionDefinition");
                    }
                }
                //  If no constraints defined at TriggerAction level, ActionDefinition constraints are used.
                if (isEmpty(triggerAction.getStates()) && triggerAction.getCalendar() == null) {
                    triggerAction.setStates(actionDefinition.getStates());
                    triggerAction.setCalendar(actionDefinition.getCalendar());
                    if (log.isDebugEnabled()) {
                        log.debug("Using ActionDefinition constraints: " + actionDefinition);
                    }
                }
                if (ActionsValidator.validate(triggerAction, event)) {
                    for (ActionListener listener : alertsContext.getActionsListeners()) {
                        listener.process(action);
                    }
                    insertActionHistory(action);
                }
            } catch (Exception e) {
                log.debug(e.getMessage(), e);
                msgLog.errorCannotUpdateAction(e.getMessage());
            }
        });
    }

    private void send(final ActionDefinition globalActionDefinition, final Event event) {
        if (globalActionDefinition == null || isEmpty(globalActionDefinition.getTenantId()) ||
                isEmpty(globalActionDefinition.getActionPlugin()) || isEmpty(globalActionDefinition.getActionId())) {
            throw new IllegalArgumentException("ActionDefinition must be not null");
        }
        if (event == null || isEmpty(event.getTenantId())) {
            throw new IllegalArgumentException("Event must be not null");
        }
        if (!globalActionDefinition.getTenantId().equals(event.getTenantId())) {
            throw new IllegalArgumentException("ActionDefinition and Event must have same tenantId");
        }
        executor.submit(() -> {
            TriggerAction globalTriggerAction = new TriggerAction(globalActionDefinition.getTenantId(),
                    globalActionDefinition.getActionPlugin(), globalActionDefinition.getActionId());
            Action action = new Action(globalTriggerAction.getTenantId(), globalTriggerAction.getActionPlugin(),
                    globalTriggerAction.getActionId(), event);
            try {
                Map<String, String> defaultProperties =
                        definitions.getDefaultActionPlugin(globalTriggerAction.getActionPlugin());
                if (defaultProperties != null) {
                    Map<String, String> mixedProps = mixProperties(globalActionDefinition.getProperties(),
                            defaultProperties);
                    action.setProperties(mixedProps);
                }
                globalTriggerAction.setStates(globalActionDefinition.getStates());
                globalTriggerAction.setCalendar(globalActionDefinition.getCalendar());
                if (ActionsValidator.validate(globalTriggerAction, event)) {
                    for (ActionListener listener : alertsContext.getActionsListeners()) {
                        listener.process(action);
                    }
                    insertActionHistory(action);
                }
            } catch (Exception e) {
                log.debug(e.getMessage(), e);
                msgLog.errorCannotUpdateAction(e.getMessage());
            }
        });
    }

    @Override
    public void updateResult(Action action) {
        if (action == null || action.getActionPlugin() == null || action.getActionId() == null
                || action.getActionPlugin().isEmpty()
                || action.getActionId().isEmpty()) {
            throw new IllegalArgumentException("Action must be not null");
        }
        if (action.getEvent() == null) {
            throw new IllegalArgumentException("Action must have an alert");
        }
        executor.submit(() -> {
            updateActionHistory(action);
        });
    }

    private void insertActionHistory(Action action) {
        if (action.getResult() == null) {
            action.setResult(WAITING_RESULT);
        }
        try {
            PreparedStatement insertActionHistory = CassStatement.get(session,
                    CassStatement.INSERT_ACTION_HISTORY);
            PreparedStatement insertActionHistoryAction = CassStatement.get(session,
                    CassStatement.INSERT_ACTION_HISTORY_ACTION);
            PreparedStatement insertActionHistoryAlert = CassStatement.get(session,
                    CassStatement.INSERT_ACTION_HISTORY_ALERT);
            PreparedStatement insertActionHistoryCtime = CassStatement.get(session,
                    CassStatement.INSERT_ACTION_HISTORY_CTIME);
            PreparedStatement insertActionHistoryResult = CassStatement.get(session,
                    CassStatement.INSERT_ACTION_HISTORY_RESULT);

            List<ResultSetFuture> futures = new ArrayList<>();

            futures.add(session.executeAsync(insertActionHistory.bind(action.getTenantId(), action.getActionPlugin(),
                    action.getActionId(), action.getEvent().getId(), action.getCtime(), JsonUtil.toJson(action))));
            futures.add(session.executeAsync(insertActionHistoryAction.bind(action.getTenantId(),
                    action.getActionId(), action.getActionPlugin(), action.getEvent().getId(),
                    action.getCtime())));
            futures.add(session.executeAsync(insertActionHistoryAlert.bind(action.getTenantId(),
                    action.getEvent().getId(), action.getActionPlugin(), action.getActionId(),
                    action.getCtime())));
            futures.add(session.executeAsync(insertActionHistoryCtime.bind(action.getTenantId(),
                    action.getCtime(), action.getActionPlugin(), action.getActionId(),
                    action.getEvent().getId())));
            futures.add(session.executeAsync(insertActionHistoryResult.bind(action.getTenantId(),
                    action.getResult(), action.getActionPlugin(), action.getActionId(), action.getEvent().getId(),
                    action.getCtime())));

            Futures.allAsList(futures).get();
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
        }
    }

    private Action selectActionHistory(String tenantId, String actionPlugin, String actionId, String alertId,
            long ctime) {
        Action actionHistory = null;
        try {
            PreparedStatement selectActionHistory = CassStatement.get(session, CassStatement.SELECT_ACTION_HISTORY);
            ResultSet rsActionHistory = session.execute(selectActionHistory.bind(tenantId, actionPlugin, actionId,
                    alertId, ctime));
            Iterator<Row> itActionHistory = rsActionHistory.iterator();
            if (itActionHistory.hasNext()) {
                Row row = itActionHistory.next();
                actionHistory = JsonUtil.fromJson(row.getString("payload"), Action.class);
            }
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
        }
        return actionHistory;
    }

    private void updateActionHistory(Action action) {
        if (action.getResult() == null) {
            action.setResult(UNKNOWN_RESULT);
        }
        try {
            Action oldActionHistory = selectActionHistory(action.getTenantId(), action.getActionPlugin(),
                    action.getActionId(), action.getEvent().getId(), action.getCtime());
            if (oldActionHistory == null) {
                insertActionHistory(action);
                return;
            }
            String oldResult = oldActionHistory.getResult();
            PreparedStatement deleteActionHistoryResult = CassStatement.get(session,
                    CassStatement.DELETE_ACTION_HISTORY_RESULT);
            PreparedStatement insertActionHistoryResult = CassStatement.get(session,
                    CassStatement.INSERT_ACTION_HISTORY_RESULT);
            PreparedStatement updateActionHistory = CassStatement.get(session, CassStatement.UPDATE_ACTION_HISTORY);

            List<ResultSetFuture> futures = new ArrayList<>();

            futures.add(session.executeAsync(deleteActionHistoryResult.bind(action.getTenantId(), oldResult,
                    action.getActionPlugin(), action.getActionId(), action.getEvent().getId(),
                    action.getCtime())));
            futures.add(session.executeAsync(insertActionHistoryResult.bind(action.getTenantId(),
                    action.getResult(), action.getActionPlugin(), action.getActionId(), action.getEvent().getId(),
                    action.getCtime())));

            futures.add(session.executeAsync(updateActionHistory.bind(JsonUtil.toJson(action), action.getTenantId(),
                    action.getActionPlugin(), action.getActionId(), action.getEvent().getId(),
                    action.getCtime())));

            Futures.allAsList(futures).get();
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
        }
    }

    @Override
    public void addListener(ActionListener listener) {
        alertsContext.registerActionListener(listener);
        msgLog.infoActionListenerRegistered(listener.toString());
    }

    @Override
    public Page<Action> getActions(String tenantId, ActionsCriteria criteria, Pager pager) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        boolean thin = (null != criteria && criteria.isThin());
        boolean filter = (null != criteria && criteria.hasCriteria());

        List<Action> actions = new ArrayList<>();
        Set<ActionHistoryPK> actionPks = new HashSet<>();

        if (filter) {
            /*
             * Get Action PKs filtered by ctime
             */
            Set<ActionHistoryPK> actionPKsfilteredByCtime = new HashSet<>();
            boolean filterByCtime = filterByCtime(tenantId, actionPKsfilteredByCtime, criteria);
            if (filterByCtime) {
                actionPks.addAll(actionPKsfilteredByCtime);
                if (actionPks.isEmpty()) {
                    return new Page<>(actions, pager, 0);
                }
            }

            /*
             * Get Action PKs filtered by actionPlugin
             */
            Set<ActionHistoryPK> actionPKsfilteredByActionPlugin = new HashSet<>();
            boolean filterByActionPlugin = filterByActionPlugin(tenantId, actionPKsfilteredByActionPlugin, criteria);
            if (filterByActionPlugin) {
                if (actionPks.isEmpty()) {
                    actionPks.addAll(actionPKsfilteredByActionPlugin);
                } else {
                    actionPks.retainAll(actionPKsfilteredByActionPlugin);
                }
                if (actionPks.isEmpty()) {
                    return new Page<>(actions, pager, 0);
                }
            }

            /*
             * Get Action PKs filtered by actionId
             */
            Set<ActionHistoryPK> actionPKsfilteredByActionId = new HashSet<>();
            boolean filterByActionId = filterByActionId(tenantId, actionPKsfilteredByActionId, criteria);
            if (filterByActionId) {
                if (actionPks.isEmpty()) {
                    actionPks.addAll(actionPKsfilteredByActionId);
                } else {
                    actionPks.retainAll(actionPKsfilteredByActionId);
                }
                if (actionPks.isEmpty()) {
                    return new Page<>(actions, pager, 0);
                }
            }

            /*
             * Get Action PKs filtered by alertId
             */
            Set<ActionHistoryPK> actionPKsfilteredByAlertId = new HashSet<>();
            boolean filterByAlertId = filterByAlertId(tenantId, actionPKsfilteredByAlertId, criteria);
            if (filterByAlertId) {
                if (actionPks.isEmpty()) {
                    actionPks.addAll(actionPKsfilteredByAlertId);
                } else {
                    actionPks.retainAll(actionPKsfilteredByAlertId);
                }
                if (actionPks.isEmpty()) {
                    return new Page<>(actions, pager, 0);
                }
            }

            /*
             * Get Action PKs filtered by result
             */
            Set<ActionHistoryPK> actionPKsfilteredByResult = new HashSet<>();
            boolean filterByResult = filterByResult(tenantId, actionPKsfilteredByResult, criteria);
            if (filterByResult) {
                if (actionPks.isEmpty()) {
                    actionPks.addAll(actionPKsfilteredByResult);
                } else {
                    actionPks.retainAll(actionPKsfilteredByResult);
                }
                if (actionPks.isEmpty()) {
                    return new Page<>(actions, pager, 0);
                }
            }
        }

        if (!filter) {
            /*
             * Get all actions
             */
            PreparedStatement selectActionHistoryByTenant = CassStatement.get(session,
                    CassStatement.SELECT_ACTION_HISTORY_BY_TENANT);
            ResultSet rsActionHistoryByTenant = session.execute(selectActionHistoryByTenant.bind(tenantId));
            Iterator<Row> itActionHistoryByTenant = rsActionHistoryByTenant.iterator();
            while (itActionHistoryByTenant.hasNext()) {
                Row row = itActionHistoryByTenant.next();
                Action actionHistory = JsonUtil.fromJson(row.getString("payload"), Action.class, thin);
                actions.add(actionHistory);
            }
        } else {
            PreparedStatement selectActionHistory = CassStatement.get(session,
                    CassStatement.SELECT_ACTION_HISTORY);
            List<ResultSetFuture> futures = actionPks.stream().map(actionPk ->
                    session.executeAsync(selectActionHistory.bind(actionPk.tenantId, actionPk.actionPlugin, actionPk
                            .actionId, actionPk.alertId, actionPk.ctime))).collect(Collectors.toList());
            List<ResultSet> rsActionHistory = Futures.allAsList(futures).get();
            rsActionHistory.stream().forEach(r -> {
                for (Row row : r) {
                    Action actionHistory = JsonUtil.fromJson(row.getString("payload"), Action.class, thin);
                    actions.add(actionHistory);
                }
            });
        }

        return preparePage(actions, pager);
    }

    private boolean filterByCtime(String tenantId, Set<ActionHistoryPK> actionPks, ActionsCriteria criteria)
            throws Exception {
        boolean filterByCtime = false;
        if (criteria.getStartTime() != null || criteria.getEndTime() != null) {
            filterByCtime = true;

            BoundStatement boundCtime;
            if (criteria.getStartTime() != null && criteria.getEndTime() != null) {
                PreparedStatement selectActionHistoryCTimeStartEnd = CassStatement.get(session,
                        CassStatement.SELECT_ACTION_HISTORY_CTIME_START_END);
                boundCtime = selectActionHistoryCTimeStartEnd.bind(tenantId, criteria.getStartTime(),
                        criteria.getEndTime());
            } else if (criteria.getStartTime() != null) {
                PreparedStatement selectActionHistoryCTimeStart = CassStatement.get(session,
                        CassStatement.SELECT_ACTION_HISTORY_CTIME_START);
                boundCtime = selectActionHistoryCTimeStart.bind(tenantId, criteria.getStartTime());
            } else {
                PreparedStatement selectActionHistoryCTimeEnd = CassStatement.get(session,
                        CassStatement.SELECT_ACTION_HISTORY_CTIME_END);
                boundCtime = selectActionHistoryCTimeEnd.bind(tenantId, criteria.getEndTime());
            }

            ResultSet rsActionHistoryCtimes = session.execute(boundCtime);
            Iterator<Row> itActionHistoryCtimes = rsActionHistoryCtimes.iterator();
            while (itActionHistoryCtimes.hasNext()) {
                Row row = itActionHistoryCtimes.next();
                ActionHistoryPK actionHistoryPK = new ActionHistoryPK();
                actionHistoryPK.tenantId = tenantId;
                actionHistoryPK.actionPlugin = row.getString("actionPlugin");
                actionHistoryPK.actionId = row.getString("actionId");
                actionHistoryPK.alertId = row.getString("alertId");
                actionHistoryPK.ctime = row.getLong("ctime");
                actionPks.add(actionHistoryPK);
            }
        }
        return filterByCtime;
    }

    private boolean filterByActionPlugin(String tenantId, Set<ActionHistoryPK> actionPks, ActionsCriteria criteria)
            throws Exception {
        boolean filterByActionPlugin = false;
        if (criteria.getActionPlugin() != null
                || (criteria.getActionPlugins() != null && !criteria.getActionPlugins().isEmpty())) {
            filterByActionPlugin = true;

            PreparedStatement selectActionHistoryActionPlugin = CassStatement.get(session,
                    CassStatement.SELECT_ACTION_HISTORY_ACTION_PLUGIN);

            List<ResultSetFuture> futures = new ArrayList<>();
            if (criteria.getActionPlugin() != null) {
                futures.add(session.executeAsync(selectActionHistoryActionPlugin.bind(tenantId,
                        criteria.getActionPlugin())));
            }
            if (criteria.getActionPlugins() != null && !criteria.getActionPlugins().isEmpty()) {
                for (String actionPlugin : criteria.getActionPlugins()) {
                    futures.add(session.executeAsync(selectActionHistoryActionPlugin.bind(tenantId, actionPlugin)));
                }
            }

            List<ResultSet> rsActionHistory = Futures.allAsList(futures).get();
            rsActionHistory.stream().forEach(r -> {
                for (Row row : r) {
                    ActionHistoryPK actionHistoryPK = new ActionHistoryPK();
                    actionHistoryPK.tenantId = tenantId;
                    actionHistoryPK.actionPlugin = row.getString("actionPlugin");
                    actionHistoryPK.actionId = row.getString("actionId");
                    actionHistoryPK.alertId = row.getString("alertId");
                    actionHistoryPK.ctime = row.getLong("ctime");
                    actionPks.add(actionHistoryPK);
                }
            });
        }
        return filterByActionPlugin;
    }

    private boolean filterByActionId(String tenantId, Set<ActionHistoryPK> actionPks, ActionsCriteria criteria)
            throws Exception {
        boolean filterByActionId = false;
        if (criteria.getActionId() != null
                || (criteria.getActionIds() != null && !criteria.getActionIds().isEmpty())) {
            filterByActionId = true;

            PreparedStatement selectActionHistoryActionId = CassStatement.get(session,
                    CassStatement.SELECT_ACTION_HISTORY_ACTION_ID);

            List<ResultSetFuture> futures = new ArrayList<>();
            if (criteria.getActionId() != null) {
                futures.add(session.executeAsync(selectActionHistoryActionId.bind(tenantId, criteria.getActionId())));
            }
            if (criteria.getActionIds() != null && !criteria.getActionIds().isEmpty()) {
                for (String actionId : criteria.getActionIds()) {
                    futures.add(session.executeAsync(selectActionHistoryActionId.bind(tenantId, actionId)));
                }
            }

            List<ResultSet> rsActionHistory = Futures.allAsList(futures).get();
            rsActionHistory.stream().forEach(r -> {
                for (Row row : r) {
                    ActionHistoryPK actionHistoryPK = new ActionHistoryPK();
                    actionHistoryPK.tenantId = tenantId;
                    actionHistoryPK.actionPlugin = row.getString("actionPlugin");
                    actionHistoryPK.actionId = row.getString("actionId");
                    actionHistoryPK.alertId = row.getString("alertId");
                    actionHistoryPK.ctime = row.getLong("ctime");
                    actionPks.add(actionHistoryPK);
                }
            });
        }
        return filterByActionId;
    }

    private boolean filterByAlertId(String tenantId, Set<ActionHistoryPK> actionPks, ActionsCriteria criteria)
            throws Exception {
        boolean filterByAlertId = false;
        if (criteria.getAlertId() != null
                || (criteria.getAlertIds() != null && !criteria.getAlertIds().isEmpty())) {
            filterByAlertId = true;

            PreparedStatement selectActionHistoryAlertId = CassStatement.get(session,
                    CassStatement.SELECT_ACTION_HISTORY_ALERT_ID);

            List<ResultSetFuture> futures = new ArrayList<>();
            if (criteria.getAlertId() != null) {
                futures.add(session.executeAsync(selectActionHistoryAlertId.bind(tenantId, criteria.getAlertId())));
            }
            if (criteria.getAlertIds() != null && !criteria.getAlertIds().isEmpty()) {
                for (String alertId : criteria.getAlertIds()) {
                    futures.add(session.executeAsync(selectActionHistoryAlertId.bind(tenantId, alertId)));
                }
            }

            List<ResultSet> rsActionHistory = Futures.allAsList(futures).get();
            rsActionHistory.stream().forEach(r -> {
                for (Row row : r) {
                    ActionHistoryPK actionHistoryPK = new ActionHistoryPK();
                    actionHistoryPK.tenantId = tenantId;
                    actionHistoryPK.actionPlugin = row.getString("actionPlugin");
                    actionHistoryPK.actionId = row.getString("actionId");
                    actionHistoryPK.alertId = row.getString("alertId");
                    actionHistoryPK.ctime = row.getLong("ctime");
                    actionPks.add(actionHistoryPK);
                }
            });
        }
        return filterByAlertId;
    }

    private boolean filterByResult(String tenantId, Set<ActionHistoryPK> actionPks, ActionsCriteria criteria)
            throws Exception {
        boolean filterByResult = false;
        if (criteria.getResult() != null
                || (criteria.getResults() != null && !criteria.getResults().isEmpty())) {
            filterByResult = true;

            PreparedStatement selectActionHistoryResult = CassStatement.get(session,
                    CassStatement.SELECT_ACTION_HISTORY_RESULT);

            List<ResultSetFuture> futures = new ArrayList<>();
            if (criteria.getResult() != null) {
                futures.add(session.executeAsync(selectActionHistoryResult.bind(tenantId, criteria.getResult())));
            }
            if (criteria.getResults() != null && !criteria.getResults().isEmpty()) {
                for (String result : criteria.getResults()) {
                    futures.add(session.executeAsync(selectActionHistoryResult.bind(tenantId, result)));
                }
            }

            List<ResultSet> rsActionHistory = Futures.allAsList(futures).get();
            rsActionHistory.stream().forEach(r -> {
                for (Row row : r) {
                    ActionHistoryPK actionHistoryPK = new ActionHistoryPK();
                    actionHistoryPK.tenantId = tenantId;
                    actionHistoryPK.actionPlugin = row.getString("actionPlugin");
                    actionHistoryPK.actionId = row.getString("actionId");
                    actionHistoryPK.alertId = row.getString("alertId");
                    actionHistoryPK.ctime = row.getLong("ctime");
                    actionPks.add(actionHistoryPK);
                }
            });
        }
        return filterByResult;
    }

    private Page<Action> preparePage(List<Action> actions, Pager pager) {
        if (pager != null) {
            if (pager.getOrder() != null
                    && !pager.getOrder().isEmpty()
                    && pager.getOrder().get(0).getField() == null) {
                pager = Pager.builder()
                        .withPageSize(pager.getPageSize())
                        .withStartPage(pager.getPageNumber())
                        .orderBy(Field.ALERT_ID.getText(), Order.Direction.DESCENDING).build();
            }
            List<Action> ordered = actions;
            if (pager.getOrder() != null) {
                pager.getOrder().stream().filter(o -> o.getField() != null && o.getDirection() != null)
                        .forEach(o -> {
                            ActionComparator comparator = new ActionComparator(
                                    Field.getField(o.getField()),
                                    o.getDirection());
                            Collections.sort(ordered, comparator);
                        });
            }
            if (!pager.isLimited() || ordered.size() < pager.getStart()) {
                pager = new Pager(0, ordered.size(), pager.getOrder());
                return new Page(ordered, pager, ordered.size());
            }
            if (pager.getEnd() >= ordered.size()) {
                return new Page(ordered.subList(pager.getStart(), ordered.size()), pager, ordered.size());
            }
            return new Page(ordered.subList(pager.getStart(), pager.getEnd()), pager, ordered.size());
        } else {
            pager = Pager.builder().withPageSize(actions.size()).orderBy(Field.ALERT_ID.getText(),
                    Order.Direction.ASCENDING).build();
            return new Page(actions, pager, actions.size());
        }
    }

    @Override
    public int deleteActions(String tenantId, ActionsCriteria criteria) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (null == criteria) {
            throw new IllegalArgumentException("Criteria must be not null");
        }

        List<Action> actionsToDelete = getActions(tenantId, criteria, null);
        if (actionsToDelete == null || actionsToDelete.isEmpty()) {
            return 0;
        }

        PreparedStatement deleteActionHistory = CassStatement.get(session,
                CassStatement.DELETE_ACTION_HISTORY);
        PreparedStatement deleteActionHistoryAction = CassStatement.get(session,
                CassStatement.DELETE_ACTION_HISTORY_ACTION);
        PreparedStatement deleteActionHistoryAlert = CassStatement.get(session,
                CassStatement.DELETE_ACTION_HISTORY_ALERT);
        PreparedStatement deleteActionHistoryCtime = CassStatement.get(session,
                CassStatement.DELETE_ACTION_HISTORY_CTIME);
        PreparedStatement deleteActionHistoryResult = CassStatement.get(session,
                CassStatement.DELETE_ACTION_HISTORY_RESULT);

        for (Action action : actionsToDelete) {
            List<ResultSetFuture> futures = new ArrayList<>();
            futures.add(session.executeAsync(deleteActionHistory.bind(action.getTenantId(), action.getActionPlugin(),
                    action.getActionId(), action.getEvent().getId(), action.getCtime())));
            futures.add(session.executeAsync(deleteActionHistoryAction.bind(action.getTenantId(),
                    action.getActionId(),
                    action.getActionPlugin(), action.getEvent().getId(), action.getCtime())));
            futures.add(session.executeAsync(deleteActionHistoryAlert.bind(action.getTenantId(),
                    action.getEvent().getId(), action.getActionPlugin(), action.getActionId(),
                    action.getCtime())));
            futures.add(session.executeAsync(deleteActionHistoryCtime.bind(action.getTenantId(), action.getCtime(),
                    action.getActionPlugin(), action.getActionId(), action.getEvent().getId())));
            futures.add(session.executeAsync(deleteActionHistoryResult.bind(action.getTenantId(),
                    action.getResult(), action.getActionPlugin(), action.getActionId(), action.getEvent().getId(),
                    action.getCtime())));
            Futures.allAsList(futures).get();
        }

        return actionsToDelete.size();
    }

    private boolean isEmpty(String s) {
        return null == s || s.trim().isEmpty();
    }

    private boolean isEmpty(Collection c) {
        return null == c || c.isEmpty();
    }

    private Map<String, String> mixProperties(Map<String, String> props, Map<String, String> defProps) {
        Map<String, String> mixed = new HashMap<>();
        if (props != null) {
            mixed.putAll(props);
        }
        if (defProps != null) {
            for (String defKey : defProps.keySet()) {
                mixed.putIfAbsent(defKey, defProps.get(defKey));
            }
        }
        return mixed;
    }

    private class ActionHistoryPK {
        public String tenantId;
        public String actionPlugin;
        public String actionId;
        public String alertId;
        public long ctime;

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            ActionHistoryPK that = (ActionHistoryPK) o;

            if (ctime != that.ctime)
                return false;
            if (tenantId != null ? !tenantId.equals(that.tenantId) : that.tenantId != null)
                return false;
            if (actionPlugin != null ? !actionPlugin.equals(that.actionPlugin) : that.actionPlugin != null)
                return false;
            if (actionId != null ? !actionId.equals(that.actionId) : that.actionId != null)
                return false;
            return !(alertId != null ? !alertId.equals(that.alertId) : that.alertId != null);

        }

        @Override
        public int hashCode() {
            int result = tenantId != null ? tenantId.hashCode() : 0;
            result = 31 * result + (actionPlugin != null ? actionPlugin.hashCode() : 0);
            result = 31 * result + (actionId != null ? actionId.hashCode() : 0);
            result = 31 * result + (alertId != null ? alertId.hashCode() : 0);
            result = 31 * result + (int) (ctime ^ (ctime >>> 32));
            return result;
        }
    }

}
