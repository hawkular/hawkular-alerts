package org.hawkular.alerts.netty.handlers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.paging.Order;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.PageContext;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.services.AlertsCriteria;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.engine.StandaloneAlerts;
import org.hawkular.alerts.log.MsgLogger;
import org.jboss.logging.Logger;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class AlertsWatcher extends Thread {
    private static final MsgLogger log = Logger.getMessageLogger(MsgLogger.class, AlertsWatcher.class.getName());
    private static final Pager stimePager;
    private static final long WATCHER_INTERVAL_DEFAULT = 5 * 1000;
    private static final long CLEAN_INTERVAL = 10 * 1000;
    private static final long LEAP_INTERVAL = 1 * 1000;

    static {
        List<Order> ordering = new ArrayList<>();
        ordering.add(Order.by("stime", Order.Direction.ASCENDING));
        stimePager = new Pager(0, PageContext.UNLIMITED_PAGE_SIZE, ordering);
    }

    String id;
    AlertsCriteria criteria;
    AlertsListener listener;
    AlertsService alertsService;
    Set<String> tenantIds;

    Long watchInterval;
    boolean running = true;

    public AlertsWatcher(String id, AlertsListener listener, Set<String> tenantIds, AlertsCriteria criteria, Long watchInterval) {
        super("AlertsWatcher[" + id + "]");
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
        Long startWatchTime = criteria.getEndStatusTime();
        Page<Alert> initialAlerts;
        try {
            initialAlerts = alertsService.getAlerts(tenantIds, criteria, stimePager);
        } catch (Exception e) {
            log.error(e);
            return;
        }
        if (initialAlerts == null) {
            log.error("initialAlerts is null");
            return;
        }
        Set<WatchedId> watchedIds = new HashSet<>();
        initialAlerts.forEach(a -> {
            listener.onAlert(a);
            watchedIds.add(new WatchedId(a.getId(), a.getCurrentLifecycle().getStime()));
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
        long lastWatched = System.currentTimeMillis();
        Set<WatchedId> newWatchedIds = new HashSet<>();
        while (running) {
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
                        listener.onAlert(alert);
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
        log.infof("AlertsWatcher[%s] finished", id);
    }

    public interface AlertsListener {
        void onAlert(Alert a);
    }

    static class WatchedId {
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
