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

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.hawkular.alerts.api.services.ActionListener;
import org.hawkular.alerts.api.services.DefinitionsEvent;
import org.hawkular.alerts.api.services.DefinitionsEvent.Type;
import org.hawkular.alerts.api.services.DefinitionsListener;
import org.hawkular.alerts.api.services.DistributedEvent;
import org.hawkular.alerts.api.services.DistributedListener;
import org.hawkular.alerts.engine.service.PartitionManager;
import org.hawkular.alerts.engine.service.PartitionTriggerListener;
import org.jboss.logging.Logger;

/**
 * Register DefinitionListener and ActionListener instances.
 * Store initialization state of the whole Alerts engine.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class AlertsContext {
    private final Logger log = Logger.getLogger(AlertsContext.class);

    private Map<DefinitionsListener, Set<Type>> definitionListeners = new HashMap<>();

    List<ActionListener> actionsListeners = new CopyOnWriteArrayList<>();

    List<DistributedListener> distributedListener = new CopyOnWriteArrayList<>();

    private boolean distributed = false;

    PartitionManager partitionManager;

    public void setPartitionManager(PartitionManager partitionManager) {
        this.partitionManager = partitionManager;
    }

    public void init() {
        if (partitionManager != null) {
            distributed = partitionManager.isDistributed();
        }
        if (distributed) {
            partitionManager.registerTriggerListener(new PartitionTriggerListener() {
                @Override
                public void onTriggerChange(PartitionManager.Operation operation, String tenantId, String triggerId) {
                    DistributedEvent.Operation op = DistributedEvent.Operation.valueOf(operation.name());
                    DistributedEvent event = new DistributedEvent(op, tenantId, triggerId);
                    distributedListener.stream().forEach(listener -> listener.onChange(Collections.singleton(event)));
                }

                @Override
                public void onPartitionChange(Map<String, List<String>> partition, Map<String, List<String>> removed,
                                              Map<String, List<String>> added) {
                    Set<DistributedEvent> events = new HashSet<>();
                    removed.entrySet().stream().forEach(entry -> {
                        String tenantId = entry.getKey();
                        entry.getValue().stream().forEach(triggerId ->
                            events.add(new DistributedEvent(DistributedEvent.Operation.REMOVE, tenantId, triggerId))
                        );
                    });
                    added.entrySet().stream().forEach(entry -> {
                        String tenantId = entry.getKey();
                        entry.getValue().stream().forEach(triggerId ->
                                events.add(new DistributedEvent(DistributedEvent.Operation.ADD, tenantId, triggerId))
                        );
                    });
                    distributedListener.stream().forEach(listener -> listener.onChange(events));
                }
            });
        }
    }

    public void registerDefinitionListener(DefinitionsListener listener, Type eventType, Type... eventTypes) {
        EnumSet<Type> types = EnumSet.of(eventType, eventTypes);
        if (log.isDebugEnabled()) {
            log.debug("Registering listeners " + listener + " for event types " + types);
        }
        definitionListeners.put(listener, types);
    }

    public void registerDistributedListener(DistributedListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("DistributedListener must not be null");
        }
        distributedListener.add(listener);
    }

    public Map<DefinitionsListener, Set<Type>> getDefinitionListeners() {
        return definitionListeners;
    }

    public void registerActionListener(ActionListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("ActionListener must not be null");
        }
        actionsListeners.add(listener);
    }

    public List<ActionListener> getActionsListeners() {
        return actionsListeners;
    }

    public void notifyListeners(List<DefinitionsEvent> notifications) {
        Set<DefinitionsEvent.Type> notificationTypes = notifications.stream()
                .map(n -> n.getType())
                .collect(Collectors.toSet());
        log.debugf("Notifying applicable listeners %s of events %s", definitionListeners, notifications);
        definitionListeners.entrySet().stream()
                .filter(e -> shouldNotify(e.getValue(), notificationTypes))
                .forEach(e -> {
                    log.debugf("Notified Listener %s of %s", e.getKey(), notificationTypes);
                    e.getKey().onChange(notifications.stream()
                            .filter(de -> e.getValue().contains(de.getType()))
                            .collect(Collectors.toList()));
                });
        if (!distributed) {
            distributedListener.stream().forEach(listener -> listener.onChange(mapDistributedEvents(notifications)));
        }
    }

    private boolean shouldNotify(Set<DefinitionsEvent.Type> listenerTypes, Set<DefinitionsEvent.Type> eventTypes) {
        HashSet<Type> intersection = new HashSet<>(listenerTypes);
        intersection.retainAll(eventTypes);
        return !intersection.isEmpty();
    }

    private Set<DistributedEvent> mapDistributedEvents(List<DefinitionsEvent> notification) {
        if (notification == null) {
            return null;
        }
        Set<DistributedEvent> events = new LinkedHashSet<>();
        for (DefinitionsEvent definitionsEvent : notification) {
            DistributedEvent.Operation op = null;
            String tenantId = definitionsEvent.getTargetTenantId();
            String triggerId = definitionsEvent.getTargetId();
            switch (definitionsEvent.getType()) {
                case TRIGGER_CREATE:
                    op = DistributedEvent.Operation.ADD;
                    break;
                case TRIGGER_UPDATE:
                    op = DistributedEvent.Operation.UPDATE;
                    break;
                case TRIGGER_REMOVE:
                    op = DistributedEvent.Operation.REMOVE;
                    break;
                case TRIGGER_CONDITION_CHANGE:
                    op = DistributedEvent.Operation.UPDATE;
                    break;
            }
            if (op != null) {
                events.add(new DistributedEvent(op, tenantId, triggerId));
            }
        }
        return events;
    }
}
