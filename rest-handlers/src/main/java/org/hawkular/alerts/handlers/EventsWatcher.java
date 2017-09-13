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
package org.hawkular.alerts.handlers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.paging.Order;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.PageContext;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.EventsCriteria;
import org.hawkular.alerts.engine.StandaloneAlerts;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class EventsWatcher extends Thread {
    private static final MsgLogger log = MsgLogging.getMsgLogger(EventsWatcher.class);
    private static final Pager ctimePager;
    private static final long WATCHER_INTERVAL_DEFAULT = 5 * 1000;
    private static final long CLEAN_INTERVAL = 10 * 1000;
    private static final long LEAP_INTERVAL = 1 * 1000;

    static {
        List<Order> ordering = new ArrayList<>();
        ordering.add(Order.by("ctime", Order.Direction.ASCENDING));
        ctimePager = new Pager(0, PageContext.UNLIMITED_PAGE_SIZE, ordering);
    }

    String id;
    EventsCriteria criteria;
    EventsListener listener;
    AlertsService alertsService;
    Set<String> tenantIds;

    Long watchInterval;
    boolean running = true;

    public EventsWatcher(String id, EventsListener listener, Set<String> tenantIds, EventsCriteria criteria, Long watchInterval) {
        super("EventsWatcher[" + id + "]");
        this.id = id;
        this.listener = listener;
        this.criteria = criteria;
        this.watchInterval = watchInterval;
        this.tenantIds = tenantIds;
        alertsService = StandaloneAlerts.getAlertsService();
    }

    public void dispose() {
        running = false;
    }

    @Override
    public void run() {
        if (listener == null) {
            log.error("Listener is null");
            return;
        }
        Long startWatchTime = criteria.getEndTime();
        Page<Event> initialEvents;
        try {
            initialEvents = alertsService.getEvents(tenantIds, criteria, ctimePager);
        } catch (Exception e) {
            log.error(e);
            return;
        }
        if (initialEvents == null) {
            log.error("initialEvents is null");
            return;
        }
        Set<WatchedId> watchedIds = new HashSet<>();
        initialEvents.forEach(ev -> {
            listener.onEvent(ev);
            watchedIds.add(new WatchedId(ev.getId(), ev.getCtime()));
        });
        startWatchTime = startWatchTime == null ? System.currentTimeMillis() : startWatchTime;
        long sleepWatcher = watchInterval == null ? WATCHER_INTERVAL_DEFAULT : watchInterval * 1000;
        /*
            Watcher will reuse the EventsCriteria but without time constraints.
            Time constraints are defined by the watcher on ctime via regular intervals.
         */
        criteria.setStartTime(null);
        criteria.setEndTime(null);
        long lastWatched = System.currentTimeMillis();
        Set<WatchedId> newWatchedIds = new HashSet<>();
        while (running) {
            startWatchTime = criteria.getEndTime() == null ? startWatchTime : criteria.getEndTime();
            criteria.setStartTime(startWatchTime);
            criteria.setEndTime(System.currentTimeMillis());
            try {
                Thread.sleep(LEAP_INTERVAL);
                log.debugf("Query timestamp %s. startTime: %s endTime: %s",
                        System.currentTimeMillis(), criteria.getStartTime(), criteria.getEndTime());
                Page<Event> watchedEvents = alertsService.getEvents(tenantIds, criteria, ctimePager);
                for (Event event : watchedEvents) {
                    WatchedId watchedId = new WatchedId(event.getId(), event.getCtime());
                    if (!watchedIds.contains(watchedId)) {
                        listener.onEvent(event);
                        newWatchedIds.add(watchedId);
                    }
                }
                if (System.currentTimeMillis() - lastWatched > CLEAN_INTERVAL) {
                    watchedIds.clear();
                    lastWatched = System.currentTimeMillis();
                }
                watchedIds.addAll(newWatchedIds);
                Thread.sleep(sleepWatcher);
            } catch (Exception e) {
                log.error(e);
                return;
            }
        }
        log.infof("EventsWatcher[%s] finished", id);
    }

    public interface EventsListener {
        void onEvent(Event a);
    }

    static class WatchedId {
        String id;
        long ctime;

        public WatchedId(String id, long ctime) {
            this.id = id;
            this.ctime = ctime;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            WatchedId watchedId = (WatchedId) o;

            if (ctime != watchedId.ctime) return false;
            return id != null ? id.equals(watchedId.id) : watchedId.id == null;
        }

        @Override
        public int hashCode() {
            int result = id != null ? id.hashCode() : 0;
            result = 31 * result + (int) (ctime ^ (ctime >>> 32));
            return result;
        }
    }
}
