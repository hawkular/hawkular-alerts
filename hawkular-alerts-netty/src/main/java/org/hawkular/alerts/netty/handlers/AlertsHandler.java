package org.hawkular.alerts.netty.handlers;

import static io.netty.handler.codec.http.HttpMethod.DELETE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpMethod.PUT;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.hawkular.alerts.api.json.JsonUtil.collectionFromJson;
import static org.hawkular.alerts.api.json.JsonUtil.toJson;
import static org.hawkular.alerts.netty.HandlersManager.TENANT_HEADER_NAME;
import static org.hawkular.alerts.netty.util.ResponseUtil.badRequest;
import static org.hawkular.alerts.netty.util.ResponseUtil.extractPaging;
import static org.hawkular.alerts.netty.util.ResponseUtil.handleExceptions;
import static org.hawkular.alerts.netty.util.ResponseUtil.isEmpty;
import static org.hawkular.alerts.netty.util.ResponseUtil.ok;
import static org.hawkular.alerts.netty.util.ResponseUtil.paginatedOk;
import static org.hawkular.alerts.netty.util.ResponseUtil.parseTagQuery;
import static org.hawkular.alerts.netty.util.ResponseUtil.parseTags;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.services.AlertsCriteria;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.engine.StandaloneAlerts;
import org.hawkular.alerts.log.MsgLogger;
import org.hawkular.alerts.netty.RestEndpoint;
import org.hawkular.alerts.netty.RestHandler;
import org.hawkular.alerts.netty.handlers.AlertsWatcher.AlertsListener;
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
@RestEndpoint(path = "/")
public class AlertsHandler implements RestHandler {
    private static final MsgLogger log = Logger.getMessageLogger(MsgLogger.class, AlertsHandler.class.getName());
    private static final String ROOT = "/";
    private static final String WATCH = "/watch";
    private static final String ACK = "/ack";
    private static final String NOTE = "/note";
    private static final String TAGS = "/tags";
    private static final String ALERT = "/alert";
    private static final String RESOLVE = "/resolve";
    private static final String DATA = "/data";
    private static final String _DELETE = "/delete";
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
    private static final String PARAM_ACK_BY = "ackBy";
    private static final String PARAM_ACK_NOTES = "ackNotes";
    private static final String PARAM_USER = "user";
    private static final String PARAM_TEXT = "text";
    private static final String PARAM_TAG_NAMES = "tagNames";
    private static final String PARAM_THIN = "thin";
    private static final String PARAM_RESOLVED_BY = "resolvedBy";
    private static final String PARAM_RESOLVED_NOTES = "resolvedNotes";

    AlertsService alertsService;

    public AlertsHandler() {
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
        // GET /
        if (method == GET && subpath.equals(ROOT)) {
            return findAlerts(req, resp, tenantId, params, req.uri());
        }
        // GET /watch
        if (method == GET && subpath.equals(WATCH)) {
            return watchAlerts(resp, tenantId, params);
        }
        // PUT /tags
        if (method == PUT && subpath.equals(TAGS)) {
            return addTags(req, resp, tenantId, params);
        }
        // DELETE /tags
        if (method == DELETE && subpath.equals(TAGS)) {
            return removeTags(req, resp, tenantId, params);
        }
        // PUT /ack
        if (method == PUT && subpath.equals(ACK)) {
            return ackAlerts(req, resp, tenantId, params);
        }
        // PUT /delete
        if (method == PUT && subpath.equals(_DELETE)) {
            return deleteAlerts(req, resp, tenantId, null, params);
        }
        // PUT /resolve
        if (method == PUT && subpath.equals(RESOLVE)) {
            return resolveAlerts(req, resp, tenantId, null, params);
        }
        // POST /data
        if (method == POST && subpath.equals(DATA)) {
            return sendData(req, resp, tenantId);
        }

        String[] tokens = subpath.substring(1).split(ROOT);
        // DELETE /{alertId}
        if (method == DELETE && tokens.length == 1) {
            return deleteAlerts(req, resp, tenantId, tokens[0], params);
        }
        // PUT /ack/{alertId}
        if (method == PUT && subpath.startsWith(ACK) && tokens.length == 2) {
            return ackAlert(req, resp, tenantId, tokens[1], params);
        }
        // PUT /note/{alertId}
        if (method == PUT && subpath.startsWith(NOTE) && tokens.length == 2) {
            return addAlertNote(req, resp, tenantId, tokens[1], params);
        }
        // GET /alert/{alertId}
        if (method == GET && subpath.startsWith(ALERT) && tokens.length == 2) {
            return getAlert(req, resp, tenantId, tokens[1], params);
        }
        // PUT /resolve/{alertId}
        if (method == PUT && subpath.startsWith(RESOLVE) && tokens.length == 2) {
            return resolveAlerts(req, resp, tenantId, tokens[1], params);
        }

        return badRequest(resp, "Wrong path " + method + " " + subpath);
    }

    Publisher<Void> findAlerts(HttpServerRequest req, HttpServerResponse resp, String tenantId, Map<String, List<String>> params, String uri) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    try {
                        Pager pager = extractPaging(params);
                        AlertsCriteria criteria = buildCriteria(params);
                        Page<Alert> alertPage = alertsService.getAlerts(tenantId, criteria, pager);
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

    Publisher<Void> watchAlerts(HttpServerResponse resp, String tenantId, Map<String, List<String>> params) {
        AlertsCriteria criteria = buildCriteria(params);
        Flux<String> watcherFlux = Flux.create(sink -> {
            Long watchInterval = null;
            if (params.get(PARAM_WATCH_INTERVAL) != null) {
                watchInterval = Long.valueOf(params.get(PARAM_WATCH_INTERVAL).get(0));
            }
            AlertsListener listener = alert -> {
                sink.next(toJson(alert) + "\r\n");
            };
            String channelId = resp.context().channel().id().asShortText();
            AlertsWatcher watcher = new AlertsWatcher(channelId, listener, Collections.singleton(tenantId), criteria, watchInterval);
            sink.onCancel(() -> watcher.dispose());
            watcher.start();
        });
        resp.status(OK);
        // Watcher send alerts one by one, so flux is splited in windows of one element
        return watcherFlux.window(1).concatMap(w -> resp.sendString(w));
    }

    Publisher<Void> ackAlert(HttpServerRequest req, HttpServerResponse resp, String tenantId, String alertId, Map<String, List<String>> params) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    String ackBy = null;
                    String ackNotes = null;
                    if (isEmpty(alertId)) {
                        return badRequest(resp, "AlertId required for ack");
                    }
                    if (params.get(PARAM_ACK_BY) != null) {
                        ackBy = params.get(PARAM_ACK_BY).get(0);
                    }
                    if (params.get(PARAM_ACK_NOTES) != null) {
                        ackNotes = params.get(PARAM_ACK_NOTES).get(0);
                    }
                    try {
                        alertsService.ackAlerts(tenantId, Arrays.asList(alertId), ackBy, ackNotes);
                        log.debugf("Ack AlertId: ", alertId);
                        return ackBy;
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }))
                .flatMap(ackBy -> ok(resp))
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> addAlertNote(HttpServerRequest req, HttpServerResponse resp, String tenantId, String alertId, Map<String, List<String>> params) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    String user = null;
                    String text = null;
                    if (isEmpty(alertId)) {
                        throw new BadRequestException("AlertId required for adding notes");
                    }
                    if (params.get(PARAM_USER) != null) {
                        user = params.get(PARAM_USER).get(0);
                    }
                    if (params.get(PARAM_TEXT) != null) {
                        text = params.get(PARAM_TEXT).get(0);
                    }
                    try {
                        alertsService.addNote(tenantId, alertId, user, text);
                        log.debugf("Noted AlertId: ", alertId);
                        return user;
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }))
                .flatMap(user -> ok(resp))
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> addTags(HttpServerRequest req, HttpServerResponse resp, String tenantId, Map<String, List<String>> params) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    String alertIds = null;
                    String tags = null;
                    if (params.get(PARAM_ALERT_IDS) != null) {
                        alertIds = params.get(PARAM_ALERT_IDS).get(0);
                    }
                    if (params.get(PARAM_TAGS) != null) {
                        tags = params.get(PARAM_TAGS).get(0);
                    }
                    if (isEmpty(alertIds) || isEmpty(tags)) {
                        throw new BadRequestException("AlertIds and Tags required for adding tags");
                    }
                    try {
                        List<String> alertIdList = Arrays.asList(alertIds.split(","));
                        Map<String, String> tagsMap = parseTags(tags);
                        alertsService.addAlertTags(tenantId, alertIdList, tagsMap);
                        log.debugf("Tagged alertIds:%s, %s", alertIdList, tagsMap);
                        return tags;
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        throw new InternalServerException(e.toString());
                    }
                }))
                .flatMap(tags -> ok(resp))
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> removeTags(HttpServerRequest req, HttpServerResponse resp, String tenantId, Map<String, List<String>> params) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    String alertIds = null;
                    String tagNames = null;
                    if (params.get(PARAM_ALERT_IDS) != null) {
                        alertIds = params.get(PARAM_ALERT_IDS).get(0);
                    }
                    if (params.get(PARAM_TAG_NAMES) != null) {
                        tagNames = params.get(PARAM_TAG_NAMES).get(0);
                    }
                    if (isEmpty(alertIds) || isEmpty(tagNames)) {
                        throw new BadRequestException("AlertIds and Tags required for removing tags");
                    }
                    try {
                        Collection<String> ids = Arrays.asList(alertIds.split(","));
                        Collection<String> tags = Arrays.asList(tagNames.split(","));
                        alertsService.removeAlertTags(tenantId, ids, tags);
                        log.debugf("Untagged alertIds:%s, %s", ids, tags);
                        return tags;
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        throw new InternalServerException(e.toString());
                    }
                }))
                .flatMap(tags -> ok(resp))
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> ackAlerts(HttpServerRequest req, HttpServerResponse resp, String tenantId, Map<String, List<String>> params) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    String alertIds = null;
                    String ackBy = null;
                    String ackNotes = null;
                    if (params.get(PARAM_ALERT_IDS) != null) {
                        alertIds = params.get(PARAM_ALERT_IDS).get(0);
                    }
                    if (params.get(PARAM_ACK_BY) != null) {
                        ackBy = params.get(PARAM_ACK_BY).get(0);
                    }
                    if (params.get(PARAM_ACK_NOTES) != null) {
                        ackNotes = params.get(PARAM_ACK_NOTES).get(0);
                    }
                    if (isEmpty(alertIds)) {
                        throw new BadRequestException("AlertIds required for ack");
                    }
                    try {
                        alertsService.ackAlerts(tenantId, Arrays.asList(alertIds.split(",")), ackBy, ackNotes);
                        log.debugf("Acked alertIds: %s", alertIds);
                        return alertIds;
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        throw new InternalServerException(e.toString());
                    }
                }))
                .flatMap(alertIds -> ok(resp))
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> deleteAlerts(HttpServerRequest req, HttpServerResponse resp, String tenantId, String alertId, Map<String, List<String>> params) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    AlertsCriteria criteria = buildCriteria(params);
                    criteria.setAlertId(alertId);
                    int numDeleted = -1;
                    try {
                        numDeleted = alertsService.deleteAlerts(tenantId, criteria);
                        log.debugf("Alerts deleted: %s", numDeleted);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                    if (numDeleted <= 0 && alertId != null) {
                        throw new ResponseUtil.NotFoundException("Alert " + alertId + " doesn't exist for delete");
                    }
                    Map<String, Integer> deleted = new HashMap<>();
                    deleted.put("deleted", new Integer(numDeleted));
                    return deleted;
                }))
                .flatMap(deleted -> ok(resp,deleted))
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> getAlert(HttpServerRequest req, HttpServerResponse resp, String tenantId, String alertId, Map<String, List<String>> params) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    boolean thin = false;
                    if (params.get(PARAM_THIN) != null) {
                        thin = Boolean.valueOf(params.get(PARAM_THIN).get(0));
                    }
                    Alert found;
                    try {
                        found = alertsService.getAlert(tenantId, alertId, thin);
                        log.debugf("Alert: ", found);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                    if (found == null) {
                        throw new ResponseUtil.NotFoundException("alertId: " + alertId + " not found");
                    }
                    return found;
                }))
                .flatMap(found -> ok(resp, found))
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> resolveAlerts(HttpServerRequest req, HttpServerResponse resp, String tenantId, String alertId, Map<String, List<String>> params) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    String alertIds = null;
                    String resolvedBy = null;
                    String resolvedNotes = null;
                    if (params.get(PARAM_ALERT_IDS) != null) {
                        alertIds = params.get(PARAM_ALERT_IDS).get(0);
                    }
                    if (alertIds == null) {
                        alertIds = alertId;
                    }
                    if (params.get(PARAM_RESOLVED_BY) != null) {
                        resolvedBy = params.get(PARAM_RESOLVED_BY).get(0);
                    }
                    if (params.get(PARAM_RESOLVED_NOTES) != null) {
                        resolvedNotes = params.get(PARAM_RESOLVED_NOTES).get(0);
                    }
                    if (isEmpty(alertIds)) {
                        throw new BadRequestException("AlertIds required for resolve");
                    }
                    try {
                        alertsService.resolveAlerts(tenantId, Arrays.asList(alertIds.split(",")), resolvedBy, resolvedNotes, null);
                        log.debugf("Resolved alertIds: ", alertIds);
                        return alertIds;
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        throw new InternalServerException(e.toString());
                    }
                }))
                .flatMap(alertIds -> ok(resp))
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> sendData(HttpServerRequest req, HttpServerResponse resp, String tenantId) {
        return req
                .receive()
                .aggregate()
                .asString()
                .publishOn(Schedulers.elastic())
                .map(json -> {
                    Collection<Data> parsed;
                    try {
                        parsed = collectionFromJson(json, Data.class);
                        return parsed;
                    } catch (Exception e) {
                        log.errorf(e, "Error parsing Datums json: %s. Reason: %s", json, e.toString());
                        throw new BadRequestException(e.toString());
                    }
                })
                .flatMap(datums -> {
                    if (isEmpty(datums)) {
                        throw new BadRequestException("Data is empty");
                    }
                    try {
                        datums.stream().forEach(d -> d.setTenantId(tenantId));
                        alertsService.sendData(datums);
                        log.debugf("Datums: %s", datums);
                        return ok(resp);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        throw new InternalServerException(e.toString());
                    }
                })
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    AlertsCriteria buildCriteria(Map<String, List<String>> params) {
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
}
