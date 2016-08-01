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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.enterprise.concurrent.ManagedExecutorService;

import org.hawkular.alerts.api.model.condition.CompareCondition;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.services.ActionsService;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.engine.impl.AlertsEngineCache.DataEntry;
import org.hawkular.alerts.engine.log.MsgLogger;
import org.hawkular.alerts.engine.service.AlertsEngine;
import org.hawkular.alerts.engine.service.PartitionDataListener;
import org.hawkular.alerts.engine.service.PartitionManager;
import org.hawkular.alerts.engine.service.PartitionManager.Operation;
import org.hawkular.alerts.engine.service.PartitionTriggerListener;
import org.hawkular.alerts.engine.service.RulesEngine;
import org.jboss.logging.Logger;

/**
 * Cassandra implementation for {@link org.hawkular.alerts.api.services.AlertsService}.
 * This implementation processes data asynchronously using a buffer queue.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Singleton
@Startup
@Local(AlertsEngine.class)
@TransactionAttribute(value= TransactionAttributeType.NOT_SUPPORTED)
public class AlertsEngineImpl implements AlertsEngine, PartitionTriggerListener, PartitionDataListener {
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(AlertsEngineImpl.class);

    /*
        ENGINE_DELAY defined in milliseconds
     */
    private static final String ENGINE_DELAY = "hawkular-alerts.engine-delay";

    /*
        ENGINE_PERIOD defined in milliseconds
     */
    private static final String ENGINE_PERIOD = "hawkular-alerts.engine-period";

    private int delay;
    private int period;

    private final List<Data> pendingData;
    private final List<Event> pendingEvents;
    private final List<Alert> alerts;
    private final List<Event> events;
    private final Set<Dampening> pendingTimeouts;
    private final Map<Trigger, List<Set<ConditionEval>>> autoResolvedTriggers;
    private final Set<Trigger> disabledTriggers;
    private final AlertsEngineCache alertsEngineCache;

    private final Timer wakeUpTimer;
    private TimerTask rulesTask;

    @EJB
    RulesEngine rules;

    @EJB
    DefinitionsService definitions;

    @EJB
    ActionsService actions;

    @EJB
    AlertsService alertsService;

    @EJB
    PartitionManager partitionManager;

    @Resource
    private ManagedExecutorService executor;

    boolean distributed = false;

    public AlertsEngineImpl() {
        pendingData = new ArrayList<>();
        pendingEvents = new ArrayList<>();
        alerts = new ArrayList<>();
        events = new ArrayList<>();
        pendingTimeouts = new HashSet<>();
        autoResolvedTriggers = new HashMap<>();
        disabledTriggers = new HashSet<>();
        alertsEngineCache = new AlertsEngineCache();

        wakeUpTimer = new Timer("CassAlertsServiceImpl-Timer");

        delay = new Integer(AlertProperties.getProperty(ENGINE_DELAY, "1000"));
        period = new Integer(AlertProperties.getProperty(ENGINE_PERIOD, "2000"));
    }

    public RulesEngine getRules() {
        return rules;
    }

    public void setRules(RulesEngine rules) {
        this.rules = rules;
    }

    public DefinitionsService getDefinitions() {
        return definitions;
    }

    public void setDefinitions(DefinitionsService definitions) {
        this.definitions = definitions;
    }

    public ActionsService getActions() {
        return actions;
    }

    public void setActions(ActionsService actions) {
        this.actions = actions;
    }

    public AlertsService getAlertsService() {
        return alertsService;
    }

    public void setAlertsService(AlertsService alertsService) {
        this.alertsService = alertsService;
    }

    @PostConstruct
    public void initServices() {
        try {
            distributed = partitionManager.isDistributed();
            if (distributed) {
                log.debug("Registering PartitionManager listeners...");
                partitionManager.registerDataListener(this);
                partitionManager.registerTriggerListener(this);
            }
            executor.submit(() -> {
                /*
                    A reload() operation means that all triggers from the backend should be reloaded into
                    the AlertsEngine memory. In a distributed environment, the node that execute the reload()
                    operation loads the triggers assigned to it and notify other nodes to load rest of the triggers.
                 */
                reload();
            });
        } catch (Throwable t) {
            if (log.isDebugEnabled()) {
                t.printStackTrace();
            }
            msgLog.errorCannotInitializeAlertsService(t.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        rulesTask.cancel();
        wakeUpTimer.cancel();
    }

    public void clear() {
        rulesTask.cancel();

        rules.clear();

        pendingData.clear();
        pendingEvents.clear();
        alerts.clear();
        events.clear();
        pendingTimeouts.clear();
        autoResolvedTriggers.clear();
        disabledTriggers.clear();

        rulesTask = new RulesInvoker();
        wakeUpTimer.schedule(rulesTask, delay, period);
    }

    @Override
    public void reload() {
        log.debug("Start a full reload of the AlertsEngine");
        rules.reset();
        alertsEngineCache.clear();
        if (rulesTask != null) {
            rulesTask.cancel();
        }

        Collection<Trigger> triggers = null;
        try {
            triggers = definitions.getAllTriggers();
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            msgLog.errorDefinitionsService("Triggers", e.getMessage());
        }

        if (triggers != null && !triggers.isEmpty()) {

            triggers.stream().filter(Trigger::isLoadable).forEach(t -> {
                /*
                    In distributed scenario a reload should delegate into the PartitionManager to load the trigger on
                    the node which belongs
                 */
                if (distributed) {
                    partitionManager.notifyTrigger(Operation.UPDATE, t.getTenantId(), t.getId());
                } else {
                    reloadTrigger(t);
                }
            });
        }

        rules.addGlobal("log", log);
        rules.addGlobal("actions", actions);
        rules.addGlobal("alerts", alerts);
        rules.addGlobal("events", events);
        rules.addGlobal("pendingTimeouts", pendingTimeouts);
        rules.addGlobal("autoResolvedTriggers", autoResolvedTriggers);
        rules.addGlobal("disabledTriggers", disabledTriggers);

        rulesTask = new RulesInvoker();
        wakeUpTimer.schedule(rulesTask, delay, period);
    }

    @Override
    public void addTrigger(final String tenantId, final String triggerId) {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(triggerId)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        if (distributed) {
            Trigger trigger = null;
            try {
                trigger = definitions.getTrigger(tenantId, triggerId);
                if (log.isDebugEnabled()) {
                    log.debug("addTrigger(" + trigger + ")");
                }
            } catch (Exception e) {
                log.debug(e.getMessage(), e);
                msgLog.errorDefinitionsService("Trigger", e.getMessage());
            }
            if (trigger != null && trigger.isLoadable()) {
                partitionManager.notifyTrigger(Operation.ADD, trigger.getTenantId(), trigger.getId());
            }
        }
    }

    @Override
    public void reloadTrigger(final String tenantId, final String triggerId) {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(triggerId)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }

        Trigger trigger = null;
        try {
            trigger = definitions.getTrigger(tenantId, triggerId);
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            msgLog.errorDefinitionsService("Trigger", e.getMessage());
        }
        if (null == trigger) {
            if (log.isDebugEnabled()) {
                log.debug("Trigger not found for triggerId [" + triggerId + "], removing from rulebase if it exists");
            }
            Trigger doomedTrigger = new Trigger(tenantId, triggerId, "doomed");
            removeTrigger(doomedTrigger);
            return;
        }
        /*
            Non loadable triggers are filtered at this level.
            So in this case we can maintain a generic partition of all triggers on all nodes.
         */
        if (!trigger.isLoadable()) {
            if (log.isDebugEnabled()) {
                log.debug("Skipping reload of trigger [" + trigger.getTenantId() + "/" + trigger.getId() + "]");
            }
            return;
        }
        /*
            In distributed scenario a reload should delegate into the PartitionManager to load the trigger on the node
            which belongs.
         */
        if (distributed) {
            partitionManager.notifyTrigger(Operation.UPDATE, trigger.getTenantId(), trigger.getId());
        } else {
            reloadTrigger(trigger);
        }
    }

    private void reloadTrigger(Trigger trigger) {
        if (null == trigger) {
            throw new IllegalArgumentException("Trigger must be not null");
        }
        if (log.isDebugEnabled()) {
            log.debug("Reloading " + trigger);
        }
        // Look for the Trigger in the rules engine, if it is there then remove everything about it
        removeTrigger(trigger);

        try {
            if (distributed) {
                trigger = definitions.getTrigger(trigger.getTenantId(), trigger.getId());
            }
            if (trigger != null && trigger.isLoadable()) {
                Collection<Condition> conditionSet = definitions.getTriggerConditions(trigger.getTenantId(),
                        trigger.getId(), null);
                Collection<Dampening> dampenings = definitions.getTriggerDampenings(trigger.getTenantId(),
                        trigger.getId(), null);

                /*
                    Caching dataId from conditions.
                 */
                for (Condition c : conditionSet) {
                    DataEntry entry = new DataEntry(c.getTenantId(), c.getTriggerId(), c.getDataId());
                    alertsEngineCache.add(entry);
                    if (Condition.Type.COMPARE == c.getType()) {
                        String data2Id = ((CompareCondition) c).getData2Id();
                        DataEntry entry2 = new DataEntry(c.getTenantId(), c.getTriggerId(), data2Id);
                        alertsEngineCache.add(entry2);
                    }
                }

                rules.addFact(trigger);
                rules.addFacts(conditionSet);
                if (!dampenings.isEmpty()) {
                    rules.addFacts(dampenings);
                }
            }
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            msgLog.errorDefinitionsService("Conditions/Dampening", e.getMessage());
        }

    }

    @Override
    public Trigger getLoadedTrigger(Trigger trigger) {
        if (null == trigger) {
            throw new IllegalArgumentException("Trigger must be not null");
        }

        Trigger loadedTrigger = null;
        try {
            loadedTrigger = (Trigger)rules.getFact(trigger);

        } catch (Exception e) {
            log.errorf("Failed to get Trigger from engine %s: %s", trigger, e);
        }
        return loadedTrigger;
    }

    @Override
    public void removeTrigger(String tenantId, String triggerId) {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(triggerId)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }

        Trigger triggerToRemove = new Trigger(tenantId, triggerId, "trigger-to-remove");
        if (distributed) {
            partitionManager.notifyTrigger(Operation.REMOVE, triggerToRemove.getTenantId(), triggerToRemove.getId());
        } else {
            removeTrigger(triggerToRemove);
        }
    }

    private void removeTrigger(Trigger trigger) {
        if (null != rules.getFact(trigger)) {
            // First remove the related Trigger facts from the engine
            rules.removeFact(trigger);

            // Remove dataId associated from cache
            alertsEngineCache.remove(trigger.getTenantId(), trigger.getId());

            // then remove everything else.
            // We may want to do this with rules, because as is, we need to loop through every Fact in
            // the rules engine doing a relatively slow check.
            final String triggerId = trigger.getId();
            rules.removeFacts(t -> {
                if (t instanceof Dampening) {
                    return ((Dampening)t).getTriggerId().equals(triggerId);
                } else if (t instanceof Condition) {
                    return ((Condition)t).getTriggerId().equals(triggerId);
                }
                return false;
            });
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Trigger not found. Not removed from rulebase " + trigger.toString());
            }
        }
    }

    @Override
    public void sendData(Collection<Data> data) {
        if (data == null) {
            throw new IllegalArgumentException("Data must be not null");
        }
        addPendingData(data);
        if (distributed) {
            partitionManager.notifyData(data);
        }
    }

    @Override
    public void sendData(Data data) {
        sendData(Collections.singleton(data));
    }

    @Override
    public void sendEvent(Event event) {
        sendEvents(Collections.singleton(event));
    }

    @Override
    public void sendEvents(Collection<Event> events) {
        if (events == null) {
            throw new IllegalArgumentException("Events must be not null");
        }
        addPendingEvents(events);
        if (distributed) {
            partitionManager.notifyEvents(events);
        }
    }

    private void addPendingData(Collection<Data> data) {
        ArrayList<Data> filteredData = new ArrayList<>(data);
        for (Iterator<Data> i = filteredData.iterator(); i.hasNext(); ) {
            Data d = i.next();
            if (!alertsEngineCache.isDataIdActive(d.getId())) {
                i.remove();
            }
        }
        synchronized (pendingData) {
            pendingData.addAll(filteredData);
        }
    }

    private void addPendingData(Data data) {
        if (data != null && data.getId() != null && alertsEngineCache.isDataIdActive(data.getId())) {
            synchronized (pendingData) {
                pendingData.add(data);
            }
        }
    }

    private void addPendingEvents(Collection<Event> events) {
        ArrayList<Event> filteredEvents = new ArrayList<>(events);
        for (Iterator<Event> i = filteredEvents.iterator(); i.hasNext(); ) {
            Event e = i.next();
            if (!alertsEngineCache.isDataIdActive(e.getDataId())) {
                i.remove();
            }
        }
        synchronized (pendingEvents) {
            pendingEvents.addAll(filteredEvents);
        }
    }

    private void addPendingEvent(Event event) {
        if (event != null && event.getDataId() != null && alertsEngineCache.isDataIdActive(event.getDataId())) {
            synchronized (pendingEvents) {
                pendingEvents.add(event);
            }
        }
    }

    private synchronized Collection<Data> getAndClearPendingData() {
        Collection<Data> result = new ArrayList<>(pendingData);
        pendingData.clear();
        return result;
    }

    private synchronized Collection<Event> getAndClearPendingEvents() {
        Collection<Event> result = new ArrayList<>(pendingEvents);
        pendingEvents.clear();
        return result;
    }

    private class RulesInvoker extends TimerTask {
        @Override
        public void run() {
            int numTimeouts = checkPendingTimeouts();

            if (!pendingData.isEmpty() || !pendingEvents.isEmpty() || numTimeouts > 0) {
                Collection<Data> newData = getAndClearPendingData();
                Collection<Event> newEvents = getAndClearPendingEvents();

                if (log.isDebugEnabled()) {
                    log.debug("Executing rules engine on " + newData.size() + " datums, "
                            + newEvents.size() + " events and "
                            + numTimeouts +" dampening timeouts.");
                }

                try {
                    if (newData.isEmpty() && newEvents.isEmpty()) {
                        rules.fireNoData();
                    } else {
                        if (!newData.isEmpty()) {
                            rules.addData(newData);
                            newData.clear();
                        }
                        if (!newEvents.isEmpty()) {
                            rules.addEvents(newEvents);
                            newEvents.clear();
                        }
                    }

                    rules.fire();
                    alertsService.addAlerts(alerts);
                    alerts.clear();
                    alertsService.persistEvents(events);
                    if (distributed) {
                        /*
                            Generated events on a node should be notified to other nodes for chained triggers
                         */
                        partitionManager.notifyEvents(events);
                    }
                    events.clear();
                    handleDisabledTriggers();
                    handleAutoResolvedTriggers();

                } catch (Exception e) {
                    e.printStackTrace();
                    if (log.isDebugEnabled()) {
                        log.debug("Error on rules processing: " + e);
                    }
                    msgLog.errorProcessingRules(e.getMessage());
                } finally {
                    alerts.clear();
                    events.clear();
                }
            }
        }

        private int checkPendingTimeouts() {
            if (pendingTimeouts.isEmpty()) {
                return 0;
            }

            long now = System.currentTimeMillis();
            Set<Dampening> timeouts = null;
            for (Dampening d : pendingTimeouts) {
                if (now < d.getTrueEvalsStartTime() + d.getEvalTimeSetting()) {
                    continue;
                }

                d.setSatisfied(true);
                try {
                    if (log.isDebugEnabled()) {
                        log.debug("Dampening Timeout Hit! " + d.toString());
                    }
                    rules.updateFact(d);
                    if (null == timeouts) {
                        timeouts = new HashSet<>();
                    }
                    timeouts.add(d);
                } catch (Exception e) {
                    log.error("Unable to update Dampening Fact on Timeout! " + d.toString(), e);
                }

            }

            if (null == timeouts) {
                return 0;
            }

            pendingTimeouts.removeAll(timeouts);
            return timeouts.size();
        }
    }

    private void handleDisabledTriggers() {
        try {
            for (Trigger t : disabledTriggers) {
                try {
                    definitions.updateTriggerEnablement(t.getTenantId(), t.getId(), false);

                } catch (Exception e) {
                    log.errorf("Failed to persist updated trigger. Could not autoDisable %s", t);
                }
            }
        } finally {
            disabledTriggers.clear();
        }
    }

    private void handleAutoResolvedTriggers() {
        try {
            for (Entry<Trigger, List<Set<ConditionEval>>> entry : autoResolvedTriggers.entrySet()) {
                Trigger t = entry.getKey();
                boolean manualReload = !t.isAutoResolveAlerts();

                // calling resolveAlertsForTrigger will result in a trigger reload (unless it fails),
                // otherwise, manually reload the trigger back into the engine (in firing mode).
                if (t.isAutoResolveAlerts()) {
                    try {
                        alertsService.resolveAlertsForTrigger(t.getTenantId(), t.getId(), "AutoResolve",
                                "Trigger AutoResolve=True", entry.getValue());
                    } catch (Exception e) {
                        manualReload = true;
                        log.errorf("Failed to resolve Alerts. Could not AutoResolve alerts for trigger %s", t);
                    }
                }

                if (manualReload) {
                    try {
                        reloadTrigger(t.getTenantId(), t.getId());
                    } catch (Exception e) {
                        log.errorf("Failed to reload AutoResolved Trigger: %s", t);
                    }
                }
            }
        } finally {
            autoResolvedTriggers.clear();
        }
    }

    /*
        Invoked when a data is added on a different node and this data should be propagated
     */
    @Override
    public void onNewData(Data data) {
        addPendingData(data);
    }

    @Override
    public void onNewData(Collection<Data> data) {
        addPendingData(data);
    }

    /*
        Invoked when an event is added on a different node and this data should be propagated
     */
    @Override
    public void onNewEvent(Event event) {
        addPendingEvent(event);
    }

    @Override
    public void onNewEvents(Collection<Event> events) {
        addPendingEvents(events);
    }

    /*
        This listener method is invoked on distributed scenarios.
        When a trigger is modified, PartitionManager detects which node holds the trigger and send the event.
        Local node is responsible to remove/reload the trigger from AlertsEngine memory.
     */
    @Override
    public void onTriggerChange(Operation operation, String tenantId, String triggerId) {
        if (log.isDebugEnabled()) {
            log.debug("Executing: " + operation + " tenantId: " + tenantId + " triggerId: " + triggerId);
        }
        switch(operation) {
            case ADD:
            case UPDATE:
                Trigger reloadTrigger = new Trigger(tenantId, triggerId, "reload-trigger");
                reloadTrigger(reloadTrigger);
                break;
            case REMOVE:
                Trigger removeTrigger = new Trigger(tenantId, triggerId, "remove-trigger");
                removeTrigger(removeTrigger);
                break;
        }
    }

    /*
        This listener method is invoked on distributed scenarios.
        When topology changes, new nodes added or removed, PartitionManager recalculate global triggers partition.
        On each node, PartitionManager invokes this method to indicate triggers should hold, and the "delta" of
        additions/removals.
        With this delta, the process of remove/reload triggers across cluster is minimized.
     */
    @Override
    public void onPartitionChange(Map<String, List<String>> partition, Map<String, List<String>> removed,
                                            Map<String, List<String>> added) {
        if (!pendingData.isEmpty() || !pendingEvents.isEmpty()) {
            if (!pendingData.isEmpty()) {
                log.warn("Pending Data onPartitionChange: " + pendingData);
            }
            if (!!pendingEvents.isEmpty()) {
                log.warn("Pending Events onPartitionChange: " + pendingData);
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("Executing: PartitionChange ");
            log.debug("Local partition: " + partition);
            log.debug("Removed: " + removed);
            log.debug("Added: " + added);
        }
        /*
            Removing old triggers for this node
         */
        for (Entry<String, List<String>> entry : removed.entrySet()) {
            String tenantId = entry.getKey();
            entry.getValue().stream().forEach(triggerId -> {
                Trigger removeTrigger = new Trigger(tenantId, triggerId, "to-remove-from-alerts-engine");
                removeTrigger(removeTrigger);
            });
        }

        /*
            Reloading new triggers for this node
         */
        for (Entry<String, List<String>> entry : added.entrySet()) {
            String tenantId = entry.getKey();
            entry.getValue().stream().forEach(triggerId -> {
                reloadTrigger(tenantId, triggerId);
            });
        }
    }

    private boolean isEmpty(String s) {
        return null == s || s.trim().isEmpty();
    }

}
