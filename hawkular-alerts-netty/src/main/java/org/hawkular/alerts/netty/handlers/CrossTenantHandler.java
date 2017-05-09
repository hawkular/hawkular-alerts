package org.hawkular.alerts.netty.handlers;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.hawkular.alerts.api.json.JsonUtil.toJson;
import static org.hawkular.alerts.netty.HandlersManager.TENANT_HEADER_NAME;
import static org.hawkular.alerts.netty.util.ResponseUtil.badRequest;
import static org.hawkular.alerts.netty.util.ResponseUtil.extractPaging;
import static org.hawkular.alerts.netty.util.ResponseUtil.getTenants;
import static org.hawkular.alerts.netty.util.ResponseUtil.handleExceptions;
import static org.hawkular.alerts.netty.util.ResponseUtil.internalServerError;
import static org.hawkular.alerts.netty.util.ResponseUtil.isEmpty;
import static org.hawkular.alerts.netty.util.ResponseUtil.ok;
import static org.hawkular.alerts.netty.util.ResponseUtil.paginatedOk;
import static org.hawkular.alerts.netty.util.ResponseUtil.parseTagQuery;
import static org.hawkular.alerts.netty.util.ResponseUtil.parseTags;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.services.AlertsCriteria;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.EventsCriteria;
import org.hawkular.alerts.engine.StandaloneAlerts;
import org.hawkular.alerts.log.MsgLogger;
import org.hawkular.alerts.netty.RestEndpoint;
import org.hawkular.alerts.netty.RestHandler;
import org.hawkular.alerts.netty.util.ResponseUtil;
import org.hawkular.alerts.netty.util.ResponseUtil.BadRequestException;
import org.hawkular.alerts.netty.util.ResponseUtil.InternalServerException;
import org.jboss.logging.Logger;
import org.reactivestreams.Publisher;

import io.netty.handler.codec.http.HttpMethod;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.ipc.netty.http.server.HttpServerRequest;
import reactor.ipc.netty.http.server.HttpServerResponse;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@RestEndpoint(path = "/admin")
public class CrossTenantHandler implements RestHandler {
    private static final MsgLogger log = Logger.getMessageLogger(MsgLogger.class, CrossTenantHandler.class.getName());
    private static final String ALERTS = "/alerts";
    private static final String EVENTS = "/events";
    private static final String WATCH_ALERTS = "/watch/alerts";
    private static final String WATCH_EVENTS = "/watch/events";
    private static final String PARAM_START_TIME = "startTime";
    private static final String PARAM_END_TIME = "endTime";
    private static final String PARAM_ALERT_IDS = "alertIds";
    private static final String PARAM_TRIGGER_IDS = "triggerIds";
    private static final String PARAM_STATUSES = "statuses";
    private static final String PARAM_SEVERITIES = "severities";
    private static final String PARAM_TAGS = "tags";
    private static final String PARAM_TAG_QUERY = "tagQuery";
    private static final String PARAM_START_RESOLVED_TIME = "startResolvedTime";
    private static final String PARAM_END_RESOLVED_TIME = "endResolvedTime";
    private static final String PARAM_START_ACK_TIME = "startAckTime";
    private static final String PARAM_END_ACK_TIME = "endAckTime";
    private static final String PARAM_START_STATUS_TIME = "startStatusTime";
    private static final String PARAM_END_STATUS_TIME = "endStatusTime";
    private static final String PARAM_WATCH_INTERVAL = "watchInterval";
    private static final String PARAM_THIN = "thin";
    private static final String PARAM_EVENT_IDS = "eventIds";
    private static final String PARAM_CATEGORIES = "categories";

    AlertsService alertsService;

    public CrossTenantHandler() {
        alertsService = StandaloneAlerts.getAlertsService();
    }

    @Override
    public Publisher<Void> process(HttpServerRequest req,
                                   HttpServerResponse resp,
                                   String tenantId,
                                   String subpath,
                                   Map<String, List<String>> params) {
        HttpMethod method = req.method();
        if (isEmpty(tenantId)) {
            return badRequest(resp, TENANT_HEADER_NAME + " header is required");
        }
        Set<String> tenantIds = getTenants(tenantId);
        // GET /alerts
        if (method == GET && subpath.equals(ALERTS)) {
            return findAlerts(req, resp, tenantIds, params, req.uri());
        }
        // GET /events
        if (method == GET && subpath.equals(EVENTS)) {
            return findEvents(req, resp, tenantIds, params, req.uri());
        }
        // GET /watch/alerts
        if (method == GET && subpath.equals(WATCH_ALERTS)) {
            return watchAlerts(resp, tenantIds, params);
        }
        // GET /watch/events
        if (method == GET && subpath.equals(WATCH_EVENTS)) {
            return watchEvents(resp, tenantIds, params);
        }

        return badRequest(resp, "Wrong path " + method + " " + subpath);
    }

    Publisher<Void> findAlerts(HttpServerRequest req, HttpServerResponse resp, Set<String> tenantIds, Map<String, List<String>> params, String uri) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    try {
                        Pager pager = extractPaging(params);
                        AlertsCriteria criteria = buildAlertsCriteria(params);
                        Page<Alert> alertPage = alertsService.getAlerts(tenantIds, criteria, pager);
                        log.debugf("Alerts: %s", alertPage);
                        return alertPage;
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }))
                .flatMap(alertPage -> {
                    if (isEmpty(alertPage)) {
                        return ok(resp, alertPage);
                    }
                    return paginatedOk(req, resp, alertPage, uri);
                })
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> findEvents(HttpServerRequest req, HttpServerResponse resp, Set<String> tenantIds, Map<String, List<String>> params, String uri) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    try {
                        Pager pager = extractPaging(params);
                        EventsCriteria criteria = buildEventsCriteria(params);
                        Page<Event> eventPage = alertsService.getEvents(tenantIds, criteria, pager);
                        log.debugf("Events: %s", eventPage);
                        return eventPage;
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }))
                .flatMap(eventPage -> {
                    if (isEmpty(eventPage)) {
                        return ok(resp, eventPage);
                    }
                    return paginatedOk(req, resp, eventPage, uri);
                })
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> watchAlerts(HttpServerResponse resp, Set<String> tenantIds, Map<String, List<String>> params) {
        AlertsCriteria criteria = buildAlertsCriteria(params);
        Flux<String> watcherFlux = Flux.create(sink -> {
            Long watchInterval = null;
            if (params.get(PARAM_WATCH_INTERVAL) != null) {
                watchInterval = Long.valueOf(params.get(PARAM_WATCH_INTERVAL).get(0));
            }
            AlertsWatcher.AlertsListener listener = alert -> {
                sink.next(toJson(alert) + "\r\n");
            };
            String channelId = resp.context().channel().id().asShortText();
            AlertsWatcher watcher = new AlertsWatcher(channelId, listener, tenantIds, criteria, watchInterval);
            sink.onCancel(() -> watcher.dispose());
            watcher.start();
        });
        resp.status(OK);
        // Watcher send alerts one by one, so flux is splited in windows of one element
        return watcherFlux.window(1).concatMap(w -> resp.sendString(w));
    }

    Publisher<Void> watchEvents(HttpServerResponse resp, Set<String> tenantIds, Map<String, List<String>> params) {
        EventsCriteria criteria = buildEventsCriteria(params);
        Flux<String> watcherFlux = Flux.create(sink -> {
            Long watchInterval = null;
            if (params.get(PARAM_WATCH_INTERVAL) != null) {
                watchInterval = Long.valueOf(params.get(PARAM_WATCH_INTERVAL).get(0));
            }
            EventsWatcher.EventsListener listener = event -> {
                sink.next(toJson(event) + "\r\n");
            };
            String channelId = resp.context().channel().id().asShortText();
            EventsWatcher watcher = new EventsWatcher(channelId, listener, tenantIds, criteria, watchInterval);
            sink.onCancel(() -> watcher.dispose());
            watcher.start();
        });
        resp.status(OK);
        // Watcher send events one by one, so flux is splited in windows of one element
        return watcherFlux.window(1).concatMap(w -> resp.sendString(w));
    }

    AlertsCriteria buildAlertsCriteria(Map<String, List<String>> params) {
        Long startTime = null;
        Long endTime = null;
        String alertIds = null;
        String triggerIds = null;
        String statuses = null;
        String severities = null;
        String tags = null;
        String tagQuery = null;
        Long startResolvedTime = null;
        Long endResolvedTime = null;
        Long startAckTime = null;
        Long endAckTime = null;
        Long startStatusTime = null;
        Long endStatusTime = null;
        boolean thin = false;

        if (params.get(PARAM_START_TIME) != null) {
            startTime = Long.valueOf(params.get(PARAM_START_TIME).get(0));
        }
        if (params.get(PARAM_END_TIME) != null) {
            endTime = Long.valueOf(params.get(PARAM_END_TIME).get(0));
        }
        if (params.get(PARAM_ALERT_IDS) != null) {
            alertIds = params.get(PARAM_ALERT_IDS).get(0);
        }
        if (params.get(PARAM_TRIGGER_IDS) != null) {
            triggerIds = params.get(PARAM_TRIGGER_IDS).get(0);
        }
        if (params.get(PARAM_STATUSES) != null) {
            statuses = params.get(PARAM_STATUSES).get(0);
        }
        if (params.get(PARAM_SEVERITIES) != null) {
            severities = params.get(PARAM_SEVERITIES).get(0);
        }
        if (params.get(PARAM_TAGS) != null) {
            tags = params.get(PARAM_TAGS).get(0);
        }
        if (params.get(PARAM_TAG_QUERY) != null) {
            tagQuery = params.get(PARAM_TAG_QUERY).get(0);
        }
        String unifiedTagQuery;
        if (!isEmpty(tags)) {
            unifiedTagQuery = parseTagQuery(parseTags(tags));
        } else {
            unifiedTagQuery = tagQuery;
        }
        if (params.get(PARAM_START_RESOLVED_TIME) != null) {
            startResolvedTime = Long.valueOf(params.get(PARAM_START_RESOLVED_TIME).get(0));
        }
        if (params.get(PARAM_END_RESOLVED_TIME) != null) {
            endResolvedTime = Long.valueOf(params.get(PARAM_END_RESOLVED_TIME).get(0));
        }
        if (params.get(PARAM_START_ACK_TIME) != null) {
            startAckTime = Long.valueOf(params.get(PARAM_START_ACK_TIME).get(0));
        }
        if (params.get(PARAM_END_ACK_TIME) != null) {
            endAckTime = Long.valueOf(params.get(PARAM_END_ACK_TIME).get(0));
        }
        if (params.get(PARAM_START_STATUS_TIME) != null) {
            startStatusTime = Long.valueOf(params.get(PARAM_START_STATUS_TIME).get(0));
        }
        if (params.get(PARAM_END_STATUS_TIME) != null) {
            endStatusTime = Long.valueOf(params.get(PARAM_END_STATUS_TIME).get(0));
        }
        if (params.get(PARAM_THIN) != null) {
            thin = Boolean.valueOf(params.get(PARAM_THIN).get(0));
        }
        return new AlertsCriteria(startTime, endTime, alertIds, triggerIds, statuses, severities,
                unifiedTagQuery, startResolvedTime, endResolvedTime, startAckTime, endAckTime, startStatusTime,
                endStatusTime, thin);
    }

    EventsCriteria buildEventsCriteria(Map<String, List<String>> params) {
        Long startTime = null;
        Long endTime = null;
        String eventIds = null;
        String triggerIds = null;
        String categories = null;
        String tags = null;
        String tagQuery = null;
        boolean thin = false;

        if (params.get(PARAM_START_TIME) != null) {
            startTime = Long.valueOf(params.get(PARAM_START_TIME).get(0));
        }
        if (params.get(PARAM_END_TIME) != null) {
            endTime = Long.valueOf(params.get(PARAM_END_TIME).get(0));
        }
        if (params.get(PARAM_EVENT_IDS) != null) {
            eventIds = params.get(PARAM_EVENT_IDS).get(0);
        }
        if (params.get(PARAM_TRIGGER_IDS) != null) {
            triggerIds = params.get(PARAM_TRIGGER_IDS).get(0);
        }
        if (params.get(PARAM_CATEGORIES) != null) {
            categories = params.get(PARAM_CATEGORIES).get(0);
        }
        if (params.get(PARAM_TAGS) != null) {
            tags = params.get(PARAM_TAGS).get(0);
        }
        if (params.get(PARAM_TAG_QUERY) != null) {
            tagQuery = params.get(PARAM_TAG_QUERY).get(0);
        }
        String unifiedTagQuery;
        if (!isEmpty(tags)) {
            unifiedTagQuery = parseTagQuery(parseTags(tags));
        } else {
            unifiedTagQuery = tagQuery;
        }
        if (params.get(PARAM_THIN) != null) {
            thin = Boolean.valueOf(params.get(PARAM_THIN).get(0));
        }
        return new EventsCriteria(startTime, endTime, eventIds, triggerIds, categories, unifiedTagQuery, thin);
    }
}
