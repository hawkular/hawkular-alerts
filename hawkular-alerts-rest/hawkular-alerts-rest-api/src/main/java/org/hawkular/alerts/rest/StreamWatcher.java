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
package org.hawkular.alerts.rest;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ws.rs.core.StreamingOutput;

import org.hawkular.alerts.api.json.JsonUtil;
import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.paging.Order;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.PageContext;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.services.AlertsCriteria;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.EventsCriteria;
import org.jboss.logging.Logger;

/**
 * Handle StreamingOutput logic for events/alerts Watchers.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Stateless
public class StreamWatcher {
    private static final Logger log = Logger.getLogger(StreamWatcher.class);
    private static final Pager ctimePager;
    private static final Pager stimePager;
    private static final long WATCHER_INTERVAL_DEFAULT = 5 * 1000;
    private static final long CLEAN_INTERVAL = 10 * 1000;
    private static final long LEAP_INTERVAL = 1 * 1000;

    @EJB
    AlertsService alertsService;

    static {
        List<Order> ordering = new ArrayList<>();
        ordering.add(Order.by("stime", Order.Direction.ASCENDING));
        stimePager = new Pager(0, PageContext.UNLIMITED_PAGE_SIZE, ordering);
        ordering = new ArrayList<>();
        ordering.add(Order.by("ctime", Order.Direction.ASCENDING));
        ctimePager = new Pager(0, PageContext.UNLIMITED_PAGE_SIZE, ordering);
    }

    public StreamingOutput watchAlerts(Set<String> tenantIds, AlertsCriteria criteria, Long watchInterval) {
        return output -> {
            Writer writer = new BufferedWriter(new OutputStreamWriter(output));
            Long startWatchTime = criteria.getEndStatusTime();
            Page<Alert> initialAlerts;
            try {
                initialAlerts = alertsService.getAlerts(tenantIds, criteria, stimePager);
            } catch (Exception e) {
                log.debug(e.getMessage(), e);
                try {
                    writer.write(JsonUtil.toJson(new ResponseUtil.ApiError(e.getMessage())) + "\r\n");
                    writer.flush();
                } catch (IOException io) {
                    log.debug("Watcher client disconnected");
                    try {
                        writer.close();
                    } catch (Exception ignored) {}
                }
                return;
            }
            if (initialAlerts == null) {
                return;
            }
            Set<WatchedId> watchedIds = new HashSet<>();
            initialAlerts.stream().forEach(alert -> {
                try {
                    writer.write(JsonUtil.toJson(alert) + "\r\n");
                    writer.flush();
                } catch (IOException io) {
                    log.debug("Watcher client disconnected");
                    try {
                        writer.close();
                    } catch (Exception ignored) {}
                    return;
                }
                watchedIds.add(new WatchedId(alert.getId(), alert.getCurrentLifecycle().getStime()));
            });
            startWatchTime = startWatchTime == null ? System.currentTimeMillis() : startWatchTime;
            long sleepWatcher = watchInterval == null ? WATCHER_INTERVAL_DEFAULT : watchInterval * 1000;
            /*
                Watcher will reuse the AlertsCriteria but without time constraints.
                Time constraints are defined by the watcher on stime via regular intervals.
             */
            criteria.setStartTime(null);
            criteria.setEndTime(null);
            criteria.setStartAckTime(null);
            criteria.setEndAckTime(null);
            criteria.setStartResolvedTime(null);
            criteria.setEndResolvedTime(null);
            boolean connected = true;
            long lastWatched = System.currentTimeMillis();
            Set<WatchedId> newWatchedIds = new HashSet<>();
            while (connected) {
                startWatchTime = criteria.getEndStatusTime() == null ? startWatchTime : criteria.getEndStatusTime();
                criteria.setStartStatusTime(startWatchTime);
                criteria.setEndStatusTime(System.currentTimeMillis());
                try {
                    Thread.sleep(LEAP_INTERVAL);
                    log.debugf("Query timestamp %s. startStatusTime: %s endStatusTime: %s",
                            System.currentTimeMillis(), criteria.getStartStatusTime(), criteria.getEndStatusTime());
                    Page<Alert> watchedAlerts = alertsService.getAlerts(tenantIds, criteria, stimePager);
                    for (Alert alert : watchedAlerts) {
                        WatchedId watchedId = new WatchedId(alert.getId(), alert.getCurrentLifecycle().getStime());
                        if (!watchedIds.contains(watchedId)) {
                            try {
                                writer.write(JsonUtil.toJson(alert) + "\r\n");
                                writer.flush();
                            } catch (IOException io) {
                                log.debug("Watcher client disconnected");
                                connected = false;
                                try {
                                    writer.close();
                                } catch (Exception ignored) {}
                            }
                            newWatchedIds.add(watchedId);
                        }
                    }
                    if (System.currentTimeMillis() - lastWatched > CLEAN_INTERVAL) {
                        watchedIds.clear();
                        lastWatched = System.currentTimeMillis();
                    }
                    watchedIds.addAll(newWatchedIds);
                    try {
                        writer.write(0);
                        writer.flush();
                    } catch (IOException io) {
                        log.debug("Watcher client disconnected");
                        connected = false;
                        try {
                            writer.close();
                        } catch (Exception ignored) {}
                    }
                    Thread.sleep(sleepWatcher);
                } catch (InterruptedException e) {
                    log.debug("Watcher interrupted");
                    try {
                        writer.close();
                    } catch (Exception ignored) {}
                    return;
                } catch (Exception e) {
                    log.debug(e.getMessage(), e);
                    try {
                        writer.write(JsonUtil.toJson(new ResponseUtil.ApiError(e.getMessage())) + "\r\n");
                        writer.flush();
                        writer.close();
                    } catch (IOException io) {
                        log.debug("Watcher client disconnected");
                    }
                    return;
                }
            }
        };
    }

    public StreamingOutput watchEvents(Set<String> tenantIds, EventsCriteria criteria, Long watchInterval) {
        return output -> {
            Writer writer = new BufferedWriter(new OutputStreamWriter(output));
            Long startWatchTime = criteria.getEndTime();
            Page<Event> initialEvents;
            try {
                initialEvents = alertsService.getEvents(tenantIds, criteria, ctimePager);
            } catch (Exception e) {
                log.debug(e.getMessage(), e);
                try {
                    writer.write(JsonUtil.toJson(new ResponseUtil.ApiError(e.getMessage())) + "\r\n");
                    writer.flush();
                } catch (IOException io) {
                    log.debug("Watcher client disconnected");
                }
                return;
            }
            if (initialEvents == null) {
                return;
            }
            Set<WatchedId> watchedIds = new HashSet<>();
            initialEvents.stream().forEach(event -> {
                try {
                    writer.write(JsonUtil.toJson(event) + "\r\n");
                    writer.flush();
                } catch (IOException io) {
                    log.debug("Watcher client disconnected");
                    return;
                }
                watchedIds.add(new WatchedId(event.getId(), event.getCtime()));
            });
            startWatchTime = startWatchTime == null ? System.currentTimeMillis() : startWatchTime;
            long sleepWatcher = watchInterval == null ? WATCHER_INTERVAL_DEFAULT : watchInterval * 1000;
            /*
                Watcher will reuse the AlertsCriteria but without time constraints.
                Time constraints are defined by the watcher on stime via regular intervals.
             */
            criteria.setStartTime(null);
            criteria.setEndTime(null);
            boolean connected = true;
            long lastWatched = System.currentTimeMillis();
            Set<WatchedId> newWatchedIds = new HashSet<>();
            while (connected) {
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
                            try {
                                writer.write(JsonUtil.toJson(event) + "\r\n");
                                writer.flush();
                            } catch (IOException io) {
                                log.debug("Watcher client disconnected");
                                connected = false;
                            }
                            newWatchedIds.add(watchedId);
                        }
                    }
                    if (System.currentTimeMillis() - lastWatched > CLEAN_INTERVAL) {
                        watchedIds.clear();
                        lastWatched = System.currentTimeMillis();
                    }
                    watchedIds.addAll(newWatchedIds);
                    try {
                        writer.write(0);
                        writer.flush();
                    } catch (IOException io) {
                        log.debug("Watcher client disconnected");
                        connected = false;
                    }
                    Thread.sleep(sleepWatcher);
                } catch (InterruptedException e) {
                    log.debug("Watcher interrupted");
                    return;
                } catch (Exception e) {
                    log.debug(e.getMessage(), e);
                    try {
                        writer.write(JsonUtil.toJson(new ResponseUtil.ApiError(e.getMessage())) + "\r\n");
                        writer.flush();
                    } catch (IOException io) {
                        log.debug("Watcher client disconnected");
                    }
                    return;
                }
            }
        };
    }

    private static class WatchedId {
        String id;
        long stime;

        public WatchedId(String id, long stime) {
            this.id = id;
            this.stime = stime;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            WatchedId watchedId = (WatchedId) o;

            if (stime != watchedId.stime) return false;
            return id != null ? id.equals(watchedId.id) : watchedId.id == null;
        }

        @Override
        public int hashCode() {
            int result = id != null ? id.hashCode() : 0;
            result = 31 * result + (int) (stime ^ (stime >>> 32));
            return result;
        }
    }
}
