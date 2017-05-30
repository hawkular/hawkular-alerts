package org.hawkular.alerts.handlers;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.hawkular.alerts.api.json.JsonUtil.toJson;

import java.util.Set;

import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.services.AlertsCriteria;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.EventsCriteria;
import org.hawkular.alerts.engine.StandaloneAlerts;
import org.hawkular.alerts.handlers.util.ResponseUtil;
import org.hawkular.handlers.RestEndpoint;
import org.hawkular.handlers.RestHandler;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@RestEndpoint(path = "/admin")
public class CrossTenantHandler implements RestHandler {
    private static final MsgLogger log = MsgLogging.getMsgLogger(CrossTenantHandler.class);
    private static final String PARAM_WATCH_INTERVAL = "watchInterval";

    AlertsService alertsService;

    public CrossTenantHandler() {
        alertsService = StandaloneAlerts.getAlertsService();
    }

    @Override
    public void initRoutes(String baseUrl, Router router) {
        String path = baseUrl + "/admin";
        router.get(path + "/alerts").handler(this::findAlerts);
        router.get(path + "/events").handler(this::findEvents);
        router.get(path + "/watch/alerts").blockingHandler(this::watchAlerts);
        router.get(path + "/watch/events").blockingHandler(this::watchEvents);
    }

    void findAlerts(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    Set<String> tenantIds = ResponseUtil.getTenants(tenantId);
                    try {
                        Pager pager = ResponseUtil.extractPaging(routing.request().params());
                        AlertsCriteria criteria = AlertsHandler.buildCriteria(routing.request().params());
                        Page<Alert> alertPage = alertsService.getAlerts(tenantIds, criteria, pager);
                        log.debugf("Alerts: %s", alertPage);
                        future.complete(alertPage);
                    } catch (IllegalArgumentException e) {
                        throw new ResponseUtil.BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                }, res -> ResponseUtil.result(routing, res));
    }

    void findEvents(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    Set<String> tenantIds = ResponseUtil.getTenants(tenantId);
                    try {
                        Pager pager = ResponseUtil.extractPaging(routing.request().params());
                        EventsCriteria criteria = EventsHandler.buildCriteria(routing.request().params());
                        Page<Event> eventPage = alertsService.getEvents(tenantIds, criteria, pager);
                        log.debugf("Events: %s", eventPage);
                        future.complete(eventPage);
                    } catch (IllegalArgumentException e) {
                        throw new ResponseUtil.BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                }, res -> ResponseUtil.result(routing, res));
    }

    void watchAlerts(RoutingContext routing) {
        String tenantId = ResponseUtil.checkTenant(routing);
        Set<String> tenantIds = ResponseUtil.getTenants(tenantId);
        AlertsCriteria criteria = AlertsHandler.buildCriteria(routing.request().params());
        Long watchInterval = null;
        if (routing.request().params().get(PARAM_WATCH_INTERVAL) != null) {
            watchInterval = Long.valueOf(routing.request().params().get(PARAM_WATCH_INTERVAL));
        }
        routing.response()
                .putHeader(ResponseUtil.ACCEPT, ResponseUtil.APPLICATION_JSON)
                .putHeader(ResponseUtil.CONTENT_TYPE, ResponseUtil.APPLICATION_JSON)
                .setChunked(true)
                .setStatusCode(OK.code());

        AlertsWatcher.AlertsListener listener = alert -> {
            routing.response().write(toJson(alert) + "\r\n");
        };
        String channelId = routing.request().connection().remoteAddress().toString();
        AlertsWatcher watcher = new AlertsWatcher(channelId, listener, tenantIds, criteria, watchInterval);
        watcher.start();
        log.infof("AlertsWatcher [%s] created", channelId);
        routing.response().closeHandler(e -> {
            watcher.dispose();
            log.infof("AlertsWatcher [%s] finished", channelId);
        });
    }

    void watchEvents(RoutingContext routing) {
        String tenantId = ResponseUtil.checkTenant(routing);
        Set<String> tenantIds = ResponseUtil.getTenants(tenantId);
        EventsCriteria criteria = EventsHandler.buildCriteria(routing.request().params());
        Long watchInterval = null;
        if (routing.request().params().get(PARAM_WATCH_INTERVAL) != null) {
            watchInterval = Long.valueOf(routing.request().params().get(PARAM_WATCH_INTERVAL));
        }
        routing.response()
                .putHeader(ResponseUtil.ACCEPT, ResponseUtil.APPLICATION_JSON)
                .putHeader(ResponseUtil.CONTENT_TYPE, ResponseUtil.APPLICATION_JSON)
                .setChunked(true)
                .setStatusCode(OK.code());
        EventsWatcher.EventsListener listener = event -> {
            routing.response().write(toJson(event) + "\r\n");
        };
        String channelId = routing.request().connection().remoteAddress().toString();
        EventsWatcher watcher = new EventsWatcher(channelId, listener, tenantIds, criteria, watchInterval);
        watcher.start();
        log.infof("EventsWatcher [%s] created", channelId);
        routing.response().closeHandler(e -> {
            watcher.dispose();
            log.infof("EventsWatcher [%s] finished", channelId);
        });
    }
}
