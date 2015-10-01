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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.ejb.Asynchronous;
import javax.ejb.Local;
import javax.ejb.Singleton;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import org.hawkular.alerts.api.json.JsonUtil;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.services.ActionListener;
import org.hawkular.alerts.api.services.ActionsService;
import org.hawkular.alerts.engine.log.MsgLogger;
import org.jboss.logging.Logger;

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
@Local(ActionsService.class)
@Singleton
@TransactionAttribute(value= TransactionAttributeType.NOT_SUPPORTED)
public class CassActionsServiceImpl implements ActionsService {
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(CassActionsServiceImpl.class);

    private static final String WAITING_RESULT = "WAITING";
    private static final String UNKNOWN_RESULT = "UNKWON";

    List<ActionListener> listeners = new CopyOnWriteArrayList<ActionListener>();

    private Session session;

    public CassActionsServiceImpl() {
        log.debugf("Creating instance.");
    }

    @Asynchronous
    @Override
    public void send(Action action) {
        if (action == null || action.getActionPlugin() == null || action.getActionId() == null
                || action.getActionPlugin().isEmpty()
                || action.getActionId().isEmpty()) {
            throw new IllegalArgumentException("Action must be not null");
        }
        if (action.getAlert() == null) {
            throw new IllegalArgumentException("Action must have an alert");
        }
        for (ActionListener listener : listeners) {
            listener.process(action);
        }
        insertActionHistory(action);
    }

    @Asynchronous
    @Override
    public void updateResult(Action action) {
        if (action == null || action.getActionPlugin() == null || action.getActionId() == null
                || action.getActionPlugin().isEmpty()
                || action.getActionId().isEmpty()) {
            throw new IllegalArgumentException("Action must be not null");
        }
        if (action.getAlert() == null) {
            throw new IllegalArgumentException("Action must have an alert");
        }
        updateActionHistory(action);
    }

    private void insertActionHistory(Action action) {
        String result = action.getResult() == null ? WAITING_RESULT : action.getResult();
        try {
            session = CassCluster.getSession();
            PreparedStatement insertActionHistory = CassStatement.get(session,
                    CassStatement.INSERT_ACTION_HISTORY);
            PreparedStatement insertActionHistoryResult = CassStatement.get(session,
                    CassStatement.INSERT_ACTION_HISTORY_RESULT);

            List<ResultSetFuture> futures = new ArrayList<>();

            futures.add(session.executeAsync(insertActionHistory.bind(action.getTenantId(), action.getActionPlugin(),
                    action.getActionId(), action.getAlert().getAlertId(), action.getCtime(), JsonUtil.toJson(action))));
            futures.add(session.executeAsync(insertActionHistoryResult.bind(action.getTenantId(),
                    action.getActionPlugin(), action.getActionId(), action.getAlert().getAlertId(), action.getCtime(),
                    result)));

            Futures.allAsList(futures).get();
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
        }
    }

    private Action selectActionHistory(String tenantId, String actionPlugin, String actionId, String alertId,
                                       long ctime) {
        Action actionHistory = null;
        try {
            session = CassCluster.getSession();
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
        String result = action.getResult() == null ? UNKNOWN_RESULT : action.getResult();

        try {
            Action oldActionHistory = selectActionHistory(action.getTenantId(), action.getActionPlugin(),
                    action.getActionId(), action.getAlert().getAlertId(), action.getCtime());
            if (oldActionHistory == null) {
                insertActionHistory(action);
                return;
            }
            String oldResult = oldActionHistory.getResult();
            session = CassCluster.getSession();
            PreparedStatement deleteActionHistoryResult = CassStatement.get(session,
                    CassStatement.DELETE_ACTION_HISTORY_RESULT);
            PreparedStatement insertActionHistoryResult = CassStatement.get(session,
                    CassStatement.INSERT_ACTION_HISTORY_RESULT);
            PreparedStatement updateActionHistory = CassStatement.get(session, CassStatement.UPDATE_ACTION_HISTORY);

            List<ResultSetFuture> futures = new ArrayList<>();

            futures.add(session.executeAsync(deleteActionHistoryResult.bind(action.getTenantId(), oldResult,
                    action.getActionPlugin(), action.getActionId(), action.getAlert().getAlertId(),
                    action.getCtime())));
            futures.add(session.executeAsync(insertActionHistoryResult.bind(action.getTenantId(), result,
                    action.getActionPlugin(), action.getActionId(), action.getAlert().getAlertId(),
                    action.getCtime())));
            futures.add(session.executeAsync(updateActionHistory.bind(JsonUtil.toJson(action), action.getTenantId(),
                    action.getActionPlugin(), action.getActionId(), action.getAlert().getAlertId(),
                    action.getCtime())));

            Futures.allAsList(futures).get();
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
        }
    }

    @Override
    public void addListener(ActionListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("ActionListener must not be null");
        }
        listeners.add(listener);
        msgLog.infoActionListenerRegistered(listener.toString());
    }

}
