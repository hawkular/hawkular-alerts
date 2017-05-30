package org.hawkular.alerts.handlers;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.hawkular.alerts.api.json.JsonUtil.collectionFromJson;
import static org.hawkular.alerts.api.json.JsonUtil.toJson;
import static org.hawkular.alerts.handlers.util.ResponseUtil.isEmpty;

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
import org.hawkular.alerts.handlers.util.ResponseUtil;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;
import org.hawkular.handlers.RestEndpoint;
import org.hawkular.handlers.RestHandler;

import io.vertx.core.MultiMap;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@RestEndpoint(path = "/")
public class AlertsHandler implements RestHandler {
    private static final MsgLogger log = MsgLogging.getMsgLogger(AlertsHandler.class);
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
    public void initRoutes(String baseUrl, Router router) {
        router.get(baseUrl).handler(this::findAlerts);
        router.get(baseUrl + "/watch").blockingHandler(this::watchAlerts);
        router.put(baseUrl + "/tags").handler(this::addTags);
        router.delete(baseUrl + "/tags").handler(this::removeTags);
        router.put(baseUrl + "/ack").handler(this::ackAlerts);
        router.put(baseUrl + "/delete").handler(this::deleteAlerts);
        router.put(baseUrl + "/resolve").handler(this::resolveAlerts);
        router.post(baseUrl + "/data").handler(this::sendData);
        router.delete(baseUrl + "/:alertId").handler(this::deleteAlerts);
        router.put(baseUrl + "/ack/:alertId").handler(this::ackAlert);
        router.put(baseUrl + "/note/:alertId").handler(this::addAlertNote);
        router.get(baseUrl + "/alert/:alertId").handler(this::getAlert);
        router.put(baseUrl + "/resolve/:alertId").handler(this::resolveAlerts);
    }

    void findAlerts(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    try {
                        Pager pager = ResponseUtil.extractPaging(routing.request().params());
                        AlertsCriteria criteria = buildCriteria(routing.request().params());
                        Page<Alert> alertPage = alertsService.getAlerts(tenantId, criteria, pager);
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

    void watchAlerts(RoutingContext routing) {
        String tenantId = ResponseUtil.checkTenant(routing);
        AlertsCriteria criteria = buildCriteria(routing.request().params());
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
        AlertsWatcher watcher = new AlertsWatcher(channelId, listener, Collections.singleton(tenantId), criteria, watchInterval);
        watcher.start();
        log.infof("AlertsWatcher [%s] created", channelId);
        routing.response().closeHandler(e -> {
            watcher.dispose();
            log.infof("AlertsWatcher [%s] finished", channelId);
        });
    }

    void addTags(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    String alertIds = null;
                    String tags = null;
                    if (routing.request().params().get(PARAM_ALERT_IDS) != null) {
                        alertIds = routing.request().params().get(PARAM_ALERT_IDS);
                    }
                    if (routing.request().params().get(PARAM_TAGS) != null) {
                        tags = routing.request().params().get(PARAM_TAGS);
                    }
                    if (ResponseUtil.isEmpty(alertIds) || ResponseUtil.isEmpty(tags)) {
                        throw new ResponseUtil.BadRequestException("AlertIds and Tags required for adding tags");
                    }
                    try {
                        List<String> alertIdList = Arrays.asList(alertIds.split(","));
                        Map<String, String> tagsMap = ResponseUtil.parseTags(tags);
                        alertsService.addAlertTags(tenantId, alertIdList, tagsMap);
                        log.debugf("Tagged alertIds:%s, %s", alertIdList, tagsMap);
                        future.complete(tags);
                    } catch (IllegalArgumentException e) {
                        throw new ResponseUtil.BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                }, res -> ResponseUtil.result(routing, res));
    }

    void removeTags(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    String alertIds = null;
                    String tagNames = null;
                    if (routing.request().params().get(PARAM_ALERT_IDS) != null) {
                        alertIds = routing.request().params().get(PARAM_ALERT_IDS);
                    }
                    if (routing.request().params().get(PARAM_TAG_NAMES) != null) {
                        tagNames = routing.request().params().get(PARAM_TAG_NAMES);
                    }
                    if (ResponseUtil.isEmpty(alertIds) || ResponseUtil.isEmpty(tagNames)) {
                        throw new ResponseUtil.BadRequestException("AlertIds and Tags required for removing tags");
                    }
                    try {
                        Collection<String> ids = Arrays.asList(alertIds.split(","));
                        Collection<String> tags = Arrays.asList(tagNames.split(","));
                        alertsService.removeAlertTags(tenantId, ids, tags);
                        log.debugf("Untagged alertIds:%s, %s", ids, tags);
                        future.complete(tags);
                    } catch (IllegalArgumentException e) {
                        throw new ResponseUtil.BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                }, res -> ResponseUtil.result(routing, res));
    }

    void ackAlerts(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    String alertIds = null;
                    String ackBy = null;
                    String ackNotes = null;
                    if (routing.request().params().get(PARAM_ALERT_IDS) != null) {
                        alertIds = routing.request().params().get(PARAM_ALERT_IDS);
                    }
                    if (routing.request().params().get(PARAM_ACK_BY) != null) {
                        ackBy = routing.request().params().get(PARAM_ACK_BY);
                    }
                    if (routing.request().params().get(PARAM_ACK_NOTES) != null) {
                        ackNotes = routing.request().params().get(PARAM_ACK_NOTES);
                    }
                    if (ResponseUtil.isEmpty(alertIds)) {
                        throw new ResponseUtil.BadRequestException("AlertIds required for ack");
                    }
                    try {
                        alertsService.ackAlerts(tenantId, Arrays.asList(alertIds.split(",")), ackBy, ackNotes);
                        log.debugf("Acked alertIds: %s", alertIds);
                        future.complete(alertIds);
                    } catch (IllegalArgumentException e) {
                        throw new ResponseUtil.BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                }, res -> ResponseUtil.result(routing, res));
    }

    void deleteAlerts(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    AlertsCriteria criteria = buildCriteria(routing.request().params());
                    String alertId = routing.request().getParam("alertId");
                    criteria.setAlertId(alertId);
                    int numDeleted;
                    try {
                        numDeleted = alertsService.deleteAlerts(tenantId, criteria);
                        log.debugf("Alerts deleted: %s", numDeleted);
                    } catch (IllegalArgumentException e) {
                        throw new ResponseUtil.BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                    if (numDeleted <= 0 && alertId != null) {
                        throw new ResponseUtil.NotFoundException("Alert " + alertId + " doesn't exist for delete");
                    }
                    Map<String, Integer> deleted = new HashMap<>();
                    deleted.put("deleted", new Integer(numDeleted));
                    future.complete(deleted);
                }, res -> ResponseUtil.result(routing, res));
    }

    void resolveAlerts(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    String alertIds = null;
                    String resolvedBy = null;
                    String resolvedNotes = null;
                    String alertId = routing.request().getParam("alertId");
                    if (routing.request().params().get(PARAM_ALERT_IDS) != null) {
                        alertIds = routing.request().params().get(PARAM_ALERT_IDS);
                    }
                    if (alertIds == null) {
                        alertIds = alertId;
                    }
                    if (routing.request().params().get(PARAM_RESOLVED_BY) != null) {
                        resolvedBy = routing.request().params().get(PARAM_RESOLVED_BY);
                    }
                    if (routing.request().params().get(PARAM_RESOLVED_NOTES) != null) {
                        resolvedNotes = routing.request().params().get(PARAM_RESOLVED_NOTES);
                    }
                    if (ResponseUtil.isEmpty(alertIds)) {
                        throw new ResponseUtil.BadRequestException("AlertIds required for resolve");
                    }
                    try {
                        alertsService.resolveAlerts(tenantId, Arrays.asList(alertIds.split(",")), resolvedBy, resolvedNotes, null);
                        log.debugf("Resolved alertIds: ", alertIds);
                        future.complete(alertIds);
                    } catch (IllegalArgumentException e) {
                        throw new ResponseUtil.BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                }, res -> ResponseUtil.result(routing, res));
    }

    void sendData(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    String json = routing.getBodyAsString();
                    Collection<Data> datums;
                    try {
                        datums = collectionFromJson(json, Data.class);
                    } catch (Exception e) {
                        log.errorf("Error parsing Datums json: %s. Reason: %s", json, e.toString());
                        throw new ResponseUtil.BadRequestException(e.toString());
                    }
                    if (ResponseUtil.isEmpty(datums)) {
                        throw new ResponseUtil.BadRequestException("Data is empty");
                    }
                    try {
                        datums.stream().forEach(d -> d.setTenantId(tenantId));
                        alertsService.sendData(datums);
                        log.debugf("Datums: %s", datums);
                        future.complete();
                    } catch (IllegalArgumentException e) {
                        throw new ResponseUtil.BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                }, res -> ResponseUtil.result(routing, res));
    }

    void ackAlert(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    String ackBy = null;
                    String ackNotes = null;
                    String alertId = routing.request().getParam("alertId");
                    if (ResponseUtil.isEmpty(alertId)) {
                        throw new ResponseUtil.BadRequestException("AlertId required for ack");
                    }
                    if (routing.request().params().get(PARAM_ACK_BY) != null) {
                        ackBy = routing.request().params().get(PARAM_ACK_BY);
                    }
                    if (routing.request().params().get(PARAM_ACK_NOTES) != null) {
                        ackNotes = routing.request().params().get(PARAM_ACK_NOTES);
                    }
                    try {
                        alertsService.ackAlerts(tenantId, Arrays.asList(alertId), ackBy, ackNotes);
                        log.debugf("Ack AlertId: ", alertId);
                        future.complete(ackBy);
                    } catch (IllegalArgumentException e) {
                        throw new ResponseUtil.BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                }, res -> ResponseUtil.result(routing, res));
    }

    void addAlertNote(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    String user = null;
                    String text = null;
                    String alertId = routing.request().getParam("alertId");
                    if (ResponseUtil.isEmpty(alertId)) {
                        throw new ResponseUtil.BadRequestException("AlertId required for adding notes");
                    }
                    if (routing.request().params().get(PARAM_USER) != null) {
                        user = routing.request().params().get(PARAM_USER);
                    }
                    if (routing.request().params().get(PARAM_TEXT) != null) {
                        text = routing.request().params().get(PARAM_TEXT);
                    }
                    try {
                        alertsService.addNote(tenantId, alertId, user, text);
                        log.debugf("Noted AlertId: ", alertId);
                        future.complete(user);
                    } catch (IllegalArgumentException e) {
                        throw new ResponseUtil.BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                }, res -> ResponseUtil.result(routing, res));
    }

    void getAlert(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    boolean thin = false;
                    String alertId = routing.request().getParam("alertId");
                    if (routing.request().params().get(PARAM_THIN) != null) {
                        thin = Boolean.valueOf(routing.request().params().get(PARAM_THIN));
                    }
                    Alert found;
                    try {
                        found = alertsService.getAlert(tenantId, alertId, thin);
                        log.debugf("Alert: ", found);
                    } catch (IllegalArgumentException e) {
                        throw new ResponseUtil.BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                    if (found == null) {
                        throw new ResponseUtil.NotFoundException("alertId: " + alertId + " not found");
                    }
                    future.complete(found);
                }, res -> ResponseUtil.result(routing, res));
    }

    public static AlertsCriteria buildCriteria(MultiMap params) {
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
            startTime = Long.valueOf(params.get(PARAM_START_TIME));
        }
        if (params.get(PARAM_END_TIME) != null) {
            endTime = Long.valueOf(params.get(PARAM_END_TIME));
        }
        if (params.get(PARAM_ALERT_IDS) != null) {
            alertIds = params.get(PARAM_ALERT_IDS);
        }
        if (params.get(PARAM_TRIGGER_IDS) != null) {
            triggerIds = params.get(PARAM_TRIGGER_IDS);
        }
        if (params.get(PARAM_STATUSES) != null) {
            statuses = params.get(PARAM_STATUSES);
        }
        if (params.get(PARAM_SEVERITIES) != null) {
            severities = params.get(PARAM_SEVERITIES);
        }
        if (params.get(PARAM_TAGS) != null) {
            tags = params.get(PARAM_TAGS);
        }
        if (params.get(PARAM_TAG_QUERY) != null) {
            tagQuery = params.get(PARAM_TAG_QUERY);
        }
        String unifiedTagQuery;
        if (!ResponseUtil.isEmpty(tags)) {
            unifiedTagQuery = ResponseUtil.parseTagQuery(ResponseUtil.parseTags(tags));
        } else {
            unifiedTagQuery = tagQuery;
        }
        if (params.get(PARAM_START_RESOLVED_TIME) != null) {
            startResolvedTime = Long.valueOf(params.get(PARAM_START_RESOLVED_TIME));
        }
        if (params.get(PARAM_END_RESOLVED_TIME) != null) {
            endResolvedTime = Long.valueOf(params.get(PARAM_END_RESOLVED_TIME));
        }
        if (params.get(PARAM_START_ACK_TIME) != null) {
            startAckTime = Long.valueOf(params.get(PARAM_START_ACK_TIME));
        }
        if (params.get(PARAM_END_ACK_TIME) != null) {
            endAckTime = Long.valueOf(params.get(PARAM_END_ACK_TIME));
        }
        if (params.get(PARAM_START_STATUS_TIME) != null) {
            startStatusTime = Long.valueOf(params.get(PARAM_START_STATUS_TIME));
        }
        if (params.get(PARAM_END_STATUS_TIME) != null) {
            endStatusTime = Long.valueOf(params.get(PARAM_END_STATUS_TIME));
        }
        if (params.get(PARAM_THIN) != null) {
            thin = Boolean.valueOf(params.get(PARAM_THIN));
        }
        return new AlertsCriteria(startTime, endTime, alertIds, triggerIds, statuses, severities,
                unifiedTagQuery, startResolvedTime, endResolvedTime, startAckTime, endAckTime, startStatusTime,
                endStatusTime, thin);
    }
}
