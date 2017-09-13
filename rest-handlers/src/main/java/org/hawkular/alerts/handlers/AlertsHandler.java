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


import static org.hawkular.alerts.api.doc.DocConstants.DELETE;
import static org.hawkular.alerts.api.doc.DocConstants.GET;
import static org.hawkular.alerts.api.doc.DocConstants.POST;
import static org.hawkular.alerts.api.doc.DocConstants.PUT;
import static org.hawkular.alerts.api.json.JsonUtil.collectionFromJson;
import static org.hawkular.alerts.api.json.JsonUtil.toJson;
import static org.hawkular.alerts.api.util.Util.isEmpty;
import static org.hawkular.alerts.handlers.util.ResponseUtil.ACCEPT;
import static org.hawkular.alerts.handlers.util.ResponseUtil.CONTENT_TYPE;
import static org.hawkular.alerts.handlers.util.ResponseUtil.PARAMS_PAGING;
import static org.hawkular.alerts.handlers.util.ResponseUtil.badRequest;
import static org.hawkular.alerts.handlers.util.ResponseUtil.checkForUnknownQueryParams;
import static org.hawkular.alerts.handlers.util.ResponseUtil.checkTenant;
import static org.hawkular.alerts.handlers.util.ResponseUtil.extractPaging;
import static org.hawkular.alerts.handlers.util.ResponseUtil.parseTagQuery;
import static org.hawkular.alerts.handlers.util.ResponseUtil.parseTags;
import static org.hawkular.alerts.handlers.util.ResponseUtil.result;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.api.doc.DocEndpoint;
import org.hawkular.alerts.api.doc.DocParameter;
import org.hawkular.alerts.api.doc.DocParameters;
import org.hawkular.alerts.api.doc.DocPath;
import org.hawkular.alerts.api.doc.DocResponse;
import org.hawkular.alerts.api.doc.DocResponses;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.services.AlertsCriteria;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.engine.StandaloneAlerts;
import org.hawkular.alerts.handlers.util.ResponseUtil;
import org.hawkular.alerts.handlers.util.ResponseUtil.ApiDeleted;
import org.hawkular.alerts.handlers.util.ResponseUtil.ApiError;
import org.hawkular.alerts.handlers.util.ResponseUtil.BadRequestException;
import org.hawkular.alerts.handlers.util.ResponseUtil.InternalServerException;
import org.hawkular.alerts.handlers.util.ResponseUtil.NotFoundException;
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
@DocEndpoint(value = "/", description = "Alerts Handling")
public class AlertsHandler implements RestHandler {
    private static final MsgLogger log = MsgLogging.getMsgLogger(AlertsHandler.class);
    private static final String PARAM_START_TIME = "startTime";
    private static final String PARAM_END_TIME = "endTime";
    private static final String PARAM_ALERT_ID = "alertId";
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

    protected static final String FIND_ALERTS = "findAlerts";
    protected static final String WATCH_ALERTS = "watchAlerts";
    private static final String DELETE_ALERTS = "deleteAlerts";
    protected static final Map<String, Set<String>> queryParamValidationMap = new HashMap<>();
    static {
        Collection<String> ALERTS_CRITERIA = Arrays.asList(PARAM_START_TIME,
                PARAM_END_TIME,
                PARAM_ALERT_IDS,
                PARAM_TRIGGER_IDS,
                PARAM_STATUSES,
                PARAM_SEVERITIES,
                PARAM_TAGS,
                PARAM_TAG_QUERY,
                PARAM_START_RESOLVED_TIME,
                PARAM_END_RESOLVED_TIME,
                PARAM_START_ACK_TIME,
                PARAM_END_ACK_TIME,
                PARAM_START_STATUS_TIME,
                PARAM_END_STATUS_TIME,
                PARAM_THIN);
        queryParamValidationMap.put(FIND_ALERTS, new HashSet<>(ALERTS_CRITERIA));
        queryParamValidationMap.get(FIND_ALERTS).addAll(PARAMS_PAGING);
        queryParamValidationMap.put(WATCH_ALERTS, new HashSet<>(ALERTS_CRITERIA));
        queryParamValidationMap.get(WATCH_ALERTS).add(PARAM_WATCH_INTERVAL);
        queryParamValidationMap.put(DELETE_ALERTS, new HashSet<>(ALERTS_CRITERIA));
        queryParamValidationMap.get(DELETE_ALERTS).add(PARAM_ALERT_ID);
    }

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
        router.delete(baseUrl + "/:alertId").handler(this::deleteAlert);
        router.put(baseUrl + "/ack/:alertId").handler(this::ackAlert);
        router.put(baseUrl + "/note/:alertId").handler(this::addAlertNote);
        router.get(baseUrl + "/alert/:alertId").handler(this::getAlert);
        router.put(baseUrl + "/resolve/:alertId").handler(this::resolveAlert);
    }

    @DocPath(method = GET,
            path = "/",
            name = "Get alerts with optional filtering",
            notes = "If not criteria defined, it fetches all alerts available in the system. + \n" +
                    "Tags Query language (BNF): + \n" +
                    "[source] \n" +
                    "---- \n" +
                    "<tag_query> ::= ( <expression> | \"(\" <object> \")\" " +
                    "| <object> <logical_operator> <object> ) \n" +
                    "<expression> ::= ( <tag_name> | <not> <tag_name> " +
                    "| <tag_name> <boolean_operator> <tag_value> | " +
                    "<tag_name> <array_operator> <array> ) \n" +
                    "<not> ::= [ \"NOT\" | \"not\" ] \n" +
                    "<logical_operator> ::= [ \"AND\" | \"OR\" | \"and\" | \"or\" ] \n" +
                    "<boolean_operator> ::= [ \"==\" | \"!=\" ] \n" +
                    "<array_operator> ::= [ \"IN\" | \"NOT IN\" | \"in\" | \"not in\" ] \n" +
                    "<array> ::= ( \"[\" \"]\" | \"[\" ( \",\" <tag_value> )* ) \n" +
                    "<tag_name> ::= <identifier> \n" +
                    "<tag_value> ::= ( \"'\" <regexp> \"'\" | <simple_value> ) \n" +
                    "; \n" +
                    "; <identifier> and <simple_value> follow pattern [a-zA-Z_0-9][\\-a-zA-Z_0-9]* \n" +
                    "; <regexp> follows any valid Java Regular Expression format \n" +
                    "---- \n")
    @DocParameters(value = {
            @DocParameter(name = "startTime", type = Long.class,
                    description = "Filter out alerts created before this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "entTime", type = Long.class,
                    description = "Filter out alerts created after this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "alertIds",
                    description = "Filter out alerts for unspecified alertIds.",
                    allowableValues = "Comma separated list of alert IDs."),
            @DocParameter(name = "triggerIds",
                    description = "Filter out alerts for unspecified triggers. ",
                    allowableValues = "Comma separated list of trigger IDs."),
            @DocParameter(name = "statuses",
                    description = "Filter out alerts for unspecified lifecycle status.",
                    allowableValues = "Comma separated list of [OPEN, ACKNOWLEDGED, RESOLVED]"),
            @DocParameter(name = "severities",
                    description = "Filter out alerts for unspecified severity. ",
                    allowableValues = "Comma separated list of [LOW, MEDIUM, HIGH, CRITICAL]"),
            @DocParameter(name = "tags",
                    description = "[DEPRECATED] Filter out alerts for unspecified tags.",
                    allowableValues = "Comma separated list of tags, each tag of format 'name\\|description'. + \n" +
                            "Specify '*' for description to match all values."),
            @DocParameter(name = "tagQuery",
                    description = "Filter out alerts for unspecified tags.",
                    allowableValues = "A tag query expression."),
            @DocParameter(name = "startResolvedTime", type = Long.class,
                    description = "Filter out alerts resolved before this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "endResolvedTime", type = Long.class,
                    description = "Filter out alerts resolved after this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "startAckTime", type = Long.class,
                    description = "Filter out alerts acknowledged before this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "endAckTime", type = Long.class,
                    description = "Filter out alerts acknowledged after this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "startStatusTime", type = Long.class,
                    description = "Filter out alerts with some lifecycle state before this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "endStatusTime", type = Long.class,
                    description = "Filter out alerts with some lifecycle after this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "thin", type = Boolean.class,
                    description = "Return only thin alerts, do not include: evalSets, resolvedEvalSets.")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Successfully fetched list of alerts.", response = Alert.class, responseContainer = "List"),
            @DocResponse(code = 400, message = "Bad Request/Invalid Parameters", response = ApiError.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void findAlerts(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    try {
                        checkForUnknownQueryParams(routing.request().params(), queryParamValidationMap.get(FIND_ALERTS));
                        Pager pager = extractPaging(routing.request().params());
                        AlertsCriteria criteria = buildCriteria(routing.request().params());
                        Page<Alert> alertPage = alertsService.getAlerts(tenantId, criteria, pager);
                        log.debugf("Alerts: %s", alertPage);
                        future.complete(alertPage);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    @DocPath(method = GET,
            path = "/watch",
            name = "Get alerts with optional filtering",
            notes = "Return a stream of alerts ordered by the current lifecycle stime. + \n" +
                    "Changes on lifecycle alert are monitored and sent by the watcher. + \n" +
                    " + \n" +
                    "If not criteria defined, it fetches all alerts available in the system. + \n" +
                    " + \n" +
                    "Time criterias are used only for the initial query. + \n" +
                    "After initial query, time criterias are discarded, watching alerts by current lifecycle stime. + \n" +
                    "Non time criterias are active. + \n" +
                    " + \n" +
                    "If not criteria defined, it fetches all alerts available in the system. + \n" +
                    "Tags Query language (BNF): + \n" +
                    "[source] \n" +
                    "---- \n" +
                    "<tag_query> ::= ( <expression> | \"(\" <object> \")\" " +
                    "| <object> <logical_operator> <object> ) \n" +
                    "<expression> ::= ( <tag_name> | <not> <tag_name> " +
                    "| <tag_name> <boolean_operator> <tag_value> | " +
                    "<tag_name> <array_operator> <array> ) \n" +
                    "<not> ::= [ \"NOT\" | \"not\" ] \n" +
                    "<logical_operator> ::= [ \"AND\" | \"OR\" | \"and\" | \"or\" ] \n" +
                    "<boolean_operator> ::= [ \"==\" | \"!=\" ] \n" +
                    "<array_operator> ::= [ \"IN\" | \"NOT IN\" | \"in\" | \"not in\" ] \n" +
                    "<array> ::= ( \"[\" \"]\" | \"[\" ( \",\" <tag_value> )* ) \n" +
                    "<tag_name> ::= <identifier> \n" +
                    "<tag_value> ::= ( \"'\" <regexp> \"'\" | <simple_value> ) \n" +
                    "; \n" +
                    "; <identifier> and <simple_value> follow pattern [a-zA-Z_0-9][\\-a-zA-Z_0-9]* \n" +
                    "; <regexp> follows any valid Java Regular Expression format \n" +
                    "---- \n")
    @DocParameters(value = {
            @DocParameter(name = "startTime", type = Long.class,
                    description = "Filter out alerts created before this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "entTime", type = Long.class,
                    description = "Filter out alerts created after this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "alertIds",
                    description = "Filter out alerts for unspecified alertIds.",
                    allowableValues = "Comma separated list of alert IDs."),
            @DocParameter(name = "triggerIds",
                    description = "Filter out alerts for unspecified triggers. ",
                    allowableValues = "Comma separated list of trigger IDs."),
            @DocParameter(name = "statuses",
                    description = "Filter out alerts for unspecified lifecycle status.",
                    allowableValues = "Comma separated list of [OPEN, ACKNOWLEDGED, RESOLVED]"),
            @DocParameter(name = "severities",
                    description = "Filter out alerts for unspecified severity. ",
                    allowableValues = "Comma separated list of [LOW, MEDIUM, HIGH, CRITICAL]"),
            @DocParameter(name = "tags",
                    description = "[DEPRECATED] Filter out alerts for unspecified tags.",
                    allowableValues = "Comma separated list of tags, each tag of format 'name\\|description'. + \n" +
                            "Specify '*' for description to match all values."),
            @DocParameter(name = "tagQuery",
                    description = "Filter out alerts for unspecified tags.",
                    allowableValues = "A tag query expression."),
            @DocParameter(name = "startResolvedTime", type = Long.class,
                    description = "Filter out alerts resolved before this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "endResolvedTime", type = Long.class,
                    description = "Filter out alerts resolved after this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "startAckTime", type = Long.class,
                    description = "Filter out alerts acknowledged before this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "endAckTime", type = Long.class,
                    description = "Filter out alerts acknowledged after this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "startStatusTime", type = Long.class,
                    description = "Filter out alerts with some lifecycle state before this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "endStatusTime", type = Long.class,
                    description = "Filter out alerts with some lifecycle after this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "watchInterval", type = Long.class,
                    description = "Define interval when watcher notifications will be sent.",
                    allowableValues = "Interval in seconds"),
            @DocParameter(name = "thin", type = Boolean.class,
                    description = "Return only thin alerts, do not include: evalSets, resolvedEvalSets.")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Stream of alerts.", response = Alert.class),
            @DocResponse(code = 200, message = "Errors will close the stream. Description is sent before stream is closed.", response = ApiError.class)
    })
    public void watchAlerts(RoutingContext routing) {
        String tenantId = checkTenant(routing);
        try {
            checkForUnknownQueryParams(routing.request().params(), queryParamValidationMap.get(WATCH_ALERTS));
        } catch (IllegalArgumentException e) {
            badRequest(routing, e.getMessage());
            return;
        }
        AlertsCriteria criteria = buildCriteria(routing.request().params());
        Long watchInterval = null;
        if (routing.request().params().get(PARAM_WATCH_INTERVAL) != null) {
            watchInterval = Long.valueOf(routing.request().params().get(PARAM_WATCH_INTERVAL));
        }
        routing.response()
                .putHeader(ACCEPT, ResponseUtil.APPLICATION_JSON)
                .putHeader(CONTENT_TYPE, ResponseUtil.APPLICATION_JSON)
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

    @DocPath(method = PUT,
            path = "/tags",
            name = "Add tags to existing Alerts.")
    @DocParameters(value = {
            @DocParameter(name = "alertIds", required = true,
                description = "List of alerts to tag.",
                allowableValues = "Comma separated list of alert IDs."),
            @DocParameter(name = "tags", required = true,
                description = "List of tags to add.",
                allowableValues = "Comma separated list of tags. + \n" +
                            "Each tag of format 'name\\|description'.")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Success, Alerts tagged successfully."),
            @DocResponse(code = 400, message = "Bad Request/Invalid Parameters.", response = ApiError.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void addTags(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String alertIds = null;
                    String tags = null;
                    if (routing.request().params().get(PARAM_ALERT_IDS) != null) {
                        alertIds = routing.request().params().get(PARAM_ALERT_IDS);
                    }
                    if (routing.request().params().get(PARAM_TAGS) != null) {
                        tags = routing.request().params().get(PARAM_TAGS);
                    }
                    if (isEmpty(alertIds) || isEmpty(tags)) {
                        throw new BadRequestException("AlertIds and Tags required for adding tags");
                    }
                    try {
                        List<String> alertIdList = Arrays.asList(alertIds.split(","));
                        Map<String, String> tagsMap = parseTags(tags);
                        alertsService.addAlertTags(tenantId, alertIdList, tagsMap);
                        log.debugf("Tagged alertIds:%s, %s", alertIdList, tagsMap);
                        future.complete(tags);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    @DocPath(method = DELETE,
            path = "/tags",
            name = "Remove tags from existing Alerts.")
    @DocParameters(value = {
            @DocParameter(name = "alertIds", required = true,
                    description = "List of alerts to untag.",
                    allowableValues = "Comma separated list of alert IDs."),
            @DocParameter(name = "tagNames", required = true,
                    description = "List of tag names to remove.",
                    allowableValues = "Comma separated list of tags names.")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Success, Tags deleted successfully."),
            @DocResponse(code = 400, message = "Bad Request/Invalid Parameters.", response = ApiError.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void removeTags(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String alertIds = null;
                    String tagNames = null;
                    if (routing.request().params().get(PARAM_ALERT_IDS) != null) {
                        alertIds = routing.request().params().get(PARAM_ALERT_IDS);
                    }
                    if (routing.request().params().get(PARAM_TAG_NAMES) != null) {
                        tagNames = routing.request().params().get(PARAM_TAG_NAMES);
                    }
                    if (isEmpty(alertIds) || isEmpty(tagNames)) {
                        throw new BadRequestException("AlertIds and Tags required for removing tags");
                    }
                    try {
                        Collection<String> ids = Arrays.asList(alertIds.split(","));
                        Collection<String> tags = Arrays.asList(tagNames.split(","));
                        alertsService.removeAlertTags(tenantId, ids, tags);
                        log.debugf("Untagged alertIds:%s, %s", ids, tags);
                        future.complete(tags);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    @DocPath(method = PUT,
            path = "/ack",
            name = "Set one or more alerts Acknowledged.")
    @DocParameters(value = {
            @DocParameter(name = "alertIds", required = true,
                    description = "List of alerts to Ack.",
                    allowableValues = "Comma separated list of alert IDs."),
            @DocParameter(name = "ackBy",
                    description = "User acknowledging the alerts."),
            @DocParameter(name = "ackNotes",
                    description = "Additional notes associated with the acknowledgement.")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Success, Alerts Acknowledged invoked successfully."),
            @DocResponse(code = 400, message = "Bad Request/Invalid Parameters.", response = ApiError.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void ackAlerts(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
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
                    if (isEmpty(alertIds)) {
                        throw new BadRequestException("AlertIds required for ack");
                    }
                    try {
                        alertsService.ackAlerts(tenantId, Arrays.asList(alertIds.split(",")), ackBy, ackNotes);
                        log.debugf("Acked alertIds: %s", alertIds);
                        future.complete(alertIds);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    @DocPath(method = DELETE,
            path = "/delete",
            name = "Delete alerts with optional filtering.",
            notes = "If not criteria defined, it fetches all alerts available in the system. + \n" +
                    "Tags Query language (BNF): + \n" +
                    "[source] \n" +
                    "---- \n" +
                    "<tag_query> ::= ( <expression> | \"(\" <object> \")\" " +
                    "| <object> <logical_operator> <object> ) \n" +
                    "<expression> ::= ( <tag_name> | <not> <tag_name> " +
                    "| <tag_name> <boolean_operator> <tag_value> | " +
                    "<tag_name> <array_operator> <array> ) \n" +
                    "<not> ::= [ \"NOT\" | \"not\" ] \n" +
                    "<logical_operator> ::= [ \"AND\" | \"OR\" | \"and\" | \"or\" ] \n" +
                    "<boolean_operator> ::= [ \"==\" | \"!=\" ] \n" +
                    "<array_operator> ::= [ \"IN\" | \"NOT IN\" | \"in\" | \"not in\" ] \n" +
                    "<array> ::= ( \"[\" \"]\" | \"[\" ( \",\" <tag_value> )* ) \n" +
                    "<tag_name> ::= <identifier> \n" +
                    "<tag_value> ::= ( \"'\" <regexp> \"'\" | <simple_value> ) \n" +
                    "; \n" +
                    "; <identifier> and <simple_value> follow pattern [a-zA-Z_0-9][\\-a-zA-Z_0-9]* \n" +
                    "; <regexp> follows any valid Java Regular Expression format \n" +
                    "---- \n")
    @DocParameters(value = {
            @DocParameter(name = "startTime", type = Long.class,
                    description = "Filter out alerts created before this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "entTime", type = Long.class,
                    description = "Filter out alerts created after this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "alertIds",
                    description = "Filter out alerts for unspecified alertIds.",
                    allowableValues = "Comma separated list of alert IDs."),
            @DocParameter(name = "triggerIds",
                    description = "Filter out alerts for unspecified triggers. ",
                    allowableValues = "Comma separated list of trigger IDs."),
            @DocParameter(name = "statuses",
                    description = "Filter out alerts for unspecified lifecycle status.",
                    allowableValues = "Comma separated list of [OPEN, ACKNOWLEDGED, RESOLVED]"),
            @DocParameter(name = "severities",
                    description = "Filter out alerts for unspecified severity. ",
                    allowableValues = "Comma separated list of [LOW, MEDIUM, HIGH, CRITICAL]"),
            @DocParameter(name = "tags",
                    description = "[DEPRECATED] Filter out alerts for unspecified tags.",
                    allowableValues = "Comma separated list of tags, each tag of format 'name\\|description'. + \n" +
                            "Specify '*' for description to match all values."),
            @DocParameter(name = "tagQuery",
                    description = "Filter out alerts for unspecified tags.",
                    allowableValues = "A tag query expression."),
            @DocParameter(name = "startResolvedTime", type = Long.class,
                    description = "Filter out alerts resolved before this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "endResolvedTime", type = Long.class,
                    description = "Filter out alerts resolved after this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "startAckTime", type = Long.class,
                    description = "Filter out alerts acknowledged before this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "endAckTime", type = Long.class,
                    description = "Filter out alerts acknowledged after this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "startStatusTime", type = Long.class,
                    description = "Filter out alerts with some lifecycle state before this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "endStatusTime", type = Long.class,
                    description = "Filter out alerts with some lifecycle after this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "thin", type = Boolean.class,
                    description = "Return only thin alerts, do not include: evalSets, resolvedEvalSets.")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Success, Alerts deleted.", response = ApiDeleted.class),
            @DocResponse(code = 400, message = "Bad Request/Invalid Parameters", response = ApiError.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void deleteAlerts(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    AlertsCriteria criteria = buildCriteria(routing.request().params());
                    String alertId = routing.request().getParam(PARAM_ALERT_ID);
                    criteria.setAlertId(alertId);
                    int numDeleted;
                    try {
                        checkForUnknownQueryParams(routing.request().params(), queryParamValidationMap.get(DELETE_ALERTS));
                        numDeleted = alertsService.deleteAlerts(tenantId, criteria);
                        log.debugf("Alerts deleted: %s", numDeleted);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                    if (numDeleted <= 0 && alertId != null) {
                        throw new NotFoundException("Alert " + alertId + " doesn't exist for delete");
                    }
                    future.complete(new ApiDeleted(numDeleted));
                }, res -> result(routing, res));
    }

    @DocPath(method = DELETE,
            path = "/{alertId}",
            name = "Delete an existing Alert.")
    @DocParameters(value = {
            @DocParameter(name = "alertId", required = true, path = true,
                    description = "Alert id to be deleted.")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Success, Alerts deleted.", response = ApiDeleted.class),
            @DocResponse(code = 400, message = "Bad Request/Invalid Parameters", response = ApiError.class),
            @DocResponse(code = 404, message = "Alert not found.", response = ApiError.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void deleteAlert(RoutingContext routing) {
        // Sharing same implementation but having a different method to document a different endpoint
        deleteAlerts(routing);
    }

    @DocPath(method = PUT,
            path = "/resolve",
            name = "Set one or more alerts resolved.")
    @DocParameters(value = {
            @DocParameter(name = "alertIds", required = true,
                    description = "List of alerts to set resolved.",
                    allowableValues = "Comma separated list of alert IDs."),
            @DocParameter(name = "resolvedBy",
                    description = "User resolving the alerts."),
            @DocParameter(name = "resolvedNotes",
                    description = "Additional notes associated with the resolution.")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Success, Alerts Resolution invoked successfully."),
            @DocResponse(code = 400, message = "Bad Request/Invalid Parameters.", response = ApiError.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void resolveAlerts(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String alertIds = null;
                    String resolvedBy = null;
                    String resolvedNotes = null;
                    String alertId = routing.request().getParam(PARAM_ALERT_ID);
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
                    if (isEmpty(alertIds)) {
                        throw new BadRequestException("AlertIds required for resolve");
                    }
                    try {
                        alertsService.resolveAlerts(tenantId, Arrays.asList(alertIds.split(",")), resolvedBy, resolvedNotes, null);
                        log.debugf("Resolved alertIds: ", alertIds);
                        future.complete(alertIds);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    @DocPath(method = PUT,
            path = "/resolve/{alertId}",
            name = "Set one alert resolved.")
    @DocParameters(value = {
            @DocParameter(name = "alertId", required = true,
                    description = "The alertId to set resolved."),
            @DocParameter(name = "resolvedBy",
                    description = "User resolving the alerts."),
            @DocParameter(name = "resolvedNotes",
                    description = "Additional notes associated with the resolution.")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Success, Alerts Resolution invoked successfully."),
            @DocResponse(code = 400, message = "Bad Request/Invalid Parameters.", response = ApiError.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void resolveAlert(RoutingContext routing) {
        resolveAlerts(routing);
    }

    @DocPath(method = POST,
            path = "/data",
            name = "Set one or more alerts resolved.")
    @DocParameters(
            @DocParameter(required = true, body = true, type = Data.class, typeContainer = "List",
                    description = "Data to be processed by alerting.")
    )
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Success, data added."),
            @DocResponse(code = 400, message = "Bad Request/Invalid Parameters.", response = ApiError.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void sendData(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String json = routing.getBodyAsString();
                    Collection<Data> datums;
                    try {
                        datums = collectionFromJson(json, Data.class);
                    } catch (Exception e) {
                        log.errorf("Error parsing Datums json: %s. Reason: %s", json, e.toString());
                        throw new BadRequestException(e.toString());
                    }
                    if (isEmpty(datums)) {
                        throw new BadRequestException("Data is empty");
                    }
                    try {
                        datums.stream().forEach(d -> d.setTenantId(tenantId));
                        alertsService.sendData(datums);
                        log.debugf("Datums: %s", datums);
                        future.complete();
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    @DocPath(method = PUT,
            path = "/alert/{alertId}",
            name = "Get an existing Alert.")
    @DocParameters(value = {
            @DocParameter(name = "alertId", required = true, path = true,
                    description = "Get an existing Alert."),
            @DocParameter(name = "thin", type = Boolean.class,
                    description = "Return only a thin alert, do not include: evalSets, resolvedEvalSets.")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Success, Alert found.", response = Alert.class),
            @DocResponse(code = 404, message = "Alert not found.", response = ApiError.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class),
    })
    public void getAlert(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
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
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                    if (found == null) {
                        throw new NotFoundException("alertId: " + alertId + " not found");
                    }
                    future.complete(found);
                }, res -> result(routing, res));
    }

    @DocPath(method = PUT,
            path = "/ack/{alertId}",
            name = "Set one alert Acknowledged.")
    @DocParameters(value = {
            @DocParameter(name = "alertId", required = true, path = true,
                    description = "The alertId to Ack.",
                    allowableValues = "An existing alertId."),
            @DocParameter(name = "ackBy",
                    description = "User acknowledging the alerts."),
            @DocParameter(name = "ackNotes",
                    description = "Additional notes associated with the acknowledgement.")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Success, Alert Acknowledged invoked successfully."),
            @DocResponse(code = 400, message = "Bad Request/Invalid Parameters.", response = ApiError.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void ackAlert(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String ackBy = null;
                    String ackNotes = null;
                    String alertId = routing.request().getParam("alertId");
                    if (isEmpty(alertId)) {
                        throw new BadRequestException("AlertId required for ack");
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
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    @DocPath(method = PUT,
            path = "/note/{alertId}",
            name = "Add a note into an existing Alert.")
    @DocParameters(value = {
            @DocParameter(name = "alertId", required = true, path = true,
                    description = "The alertId to add the note.",
                    allowableValues = "An existing alertId."),
            @DocParameter(name = "user",
                    description = "Author of the note."),
            @DocParameter(name = "text",
                    description = "Text of the note.")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Success, Alert note added successfully."),
            @DocResponse(code = 400, message = "Bad Request/Invalid Parameters.", response = ApiError.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void addAlertNote(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String user = null;
                    String text = null;
                    String alertId = routing.request().getParam("alertId");
                    if (isEmpty(alertId)) {
                        throw new BadRequestException("AlertId required for adding notes");
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
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
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
        if (!isEmpty(tags)) {
            unifiedTagQuery = parseTagQuery(parseTags(tags));
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
