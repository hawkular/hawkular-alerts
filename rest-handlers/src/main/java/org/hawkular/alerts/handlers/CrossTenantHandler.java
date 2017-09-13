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

import static org.hawkular.alerts.api.doc.DocConstants.GET;
import static org.hawkular.alerts.api.json.JsonUtil.toJson;
import static org.hawkular.alerts.handlers.util.ResponseUtil.checkForUnknownQueryParams;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;

import java.util.Set;

import org.hawkular.alerts.api.doc.DocEndpoint;
import org.hawkular.alerts.api.doc.DocParameter;
import org.hawkular.alerts.api.doc.DocParameters;
import org.hawkular.alerts.api.doc.DocPath;
import org.hawkular.alerts.api.doc.DocResponse;
import org.hawkular.alerts.api.doc.DocResponses;
import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.services.AlertsCriteria;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.EventsCriteria;
import org.hawkular.alerts.engine.StandaloneAlerts;
import org.hawkular.alerts.handlers.util.ResponseUtil;
import org.hawkular.alerts.handlers.util.ResponseUtil.ApiError;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;
import org.hawkular.handlers.RestEndpoint;
import org.hawkular.handlers.RestHandler;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@RestEndpoint(path = "/admin")
@DocEndpoint(value = "/admin", description = "Cross tenant Operations")
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

    @DocPath(method = GET,
            path = "/alerts",
            name = "Get alerts with optional filtering from multiple tenants.",
            notes = "If not criteria defined, it fetches all alerts available in the system. + \n" +
                    " + \n" +
                    "Multiple tenants are expected on HawkularTenant header as a comma separated list. + \n" +
                    "i.e. HawkularTenant: tenant1,tenant2,tenant3 + \n" +
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
                    String tenantId = ResponseUtil.checkTenant(routing);
                    Set<String> tenantIds = ResponseUtil.getTenants(tenantId);
                    try {
                        checkForUnknownQueryParams(routing.request().params(), AlertsHandler.queryParamValidationMap.get(AlertsHandler.FIND_ALERTS));
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

    @DocPath(method = GET,
            path = "/events",
            name = "Get events with optional filtering from multiple tenants.",
            notes = "If not criteria defined, it fetches all events stored in the system. + \n" +
                    " + \n" +
                    "Multiple tenants are expected on HawkularTenant header as a comma separated list. + \n" +
                    "i.e. HawkularTenant: tenant1,tenant2,tenant3 + \n" +
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
            @DocParameter(name = "startTime",
                    description = "Filter out events created before this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "endTime",
                    description = "Filter out events created after this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "eventIds",
                    description = "Filter out events for unspecified eventIds.",
                    allowableValues = "Comma separated list of event IDs."),
            @DocParameter(name = "triggerIds",
                    description = "Filter out events for unspecified triggers.",
                    allowableValues = "Comma separated list of trigger IDs."),
            @DocParameter(name = "categories",
                    description = "Filter out events for unspecified categories.",
                    allowableValues = "Comma separated list of category values."),
            @DocParameter(name = "tags",
                    description = "[DEPRECATED] Filter out events for unspecified tags.",
                    allowableValues = "Comma separated list of tags, each tag of format 'name\\|description'. + \n" +
                            "Specify '*' for description to match all values."),
            @DocParameter(name = "tagQuery",
                    description = "Filter out events for unspecified tags.",
                    allowableValues = "A tag query expression."),
            @DocParameter(name = "thin",
                    description = "Return only thin events, do not include: evalSets.")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Successfully fetched list of events.", response = Event.class, responseContainer = "List"),
            @DocResponse(code = 400, message = "Bad Request/Invalid Parameters.", response = ApiError.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void findEvents(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    Set<String> tenantIds = ResponseUtil.getTenants(tenantId);
                    try {
                        checkForUnknownQueryParams(routing.request().params(), EventsHandler.queryParamValidationMap.get(EventsHandler.FIND_EVENTS));
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

    @DocPath(method = GET,
            path = "/watch/alerts",
            name = "Get alerts with optional filtering from multiple tenants.",
            notes = "Return a stream of alerts ordered by the current lifecycle stime. + \n" +
                    "Changes on lifecycle alert are monitored and sent by the watcher. + \n" +
                    " + \n" +
                    "If not criteria defined, it fetches all alerts available in the system. + \n" +
                    " + \n" +
                    "Time criterias are used only for the initial query. + \n" +
                    "After initial query, time criterias are discarded, watching alerts by current lifecycle stime. + \n" +
                    "Non time criterias are active. + \n" +
                    " + \n" +
                    "Multiple tenants are expected on HawkularTenant header as a comma separated list. + \n" +
                    "i.e. HawkularTenant: tenant1,tenant2,tenant3 + \n" +
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
        String tenantId = ResponseUtil.checkTenant(routing);
        Set<String> tenantIds = ResponseUtil.getTenants(tenantId);
        try {
            checkForUnknownQueryParams(routing.request().params(), AlertsHandler.queryParamValidationMap.get(AlertsHandler.WATCH_ALERTS));
        } catch (IllegalArgumentException e) {
            ResponseUtil.badRequest(routing, e.getMessage());
            return;
        }
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

    @DocPath(method = GET,
            path = "/watch/events",
            name = "Watch events with optional filtering from multiple tenants.",
            notes =  "Return a stream of events ordered by ctime. + \n" +
                    " + \n" +
                    "If not criteria defined, it fetches all events stored in the system. + \n" +
                    " + \n" +
                    "Time criterias are used only for the initial query. + \n" +
                    "After initial query, time criterias are discarded, watching events by ctime. + \n" +
                    "Non time criterias are active. + \n" +
                    " + \n" +
                    "If not criteria defined, it fetches all events stored in the system. + \n" +
                    " + \n" +
                    "Multiple tenants are expected on HawkularTenant header as a comma separated list. + \n" +
                    "i.e. HawkularTenant: tenant1,tenant2,tenant3 + \n" +
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
            @DocParameter(name = "startTime",
                    description = "Filter out events created before this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "endTime",
                    description = "Filter out events created after this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "eventIds",
                    description = "Filter out events for unspecified eventIds.",
                    allowableValues = "Comma separated list of event IDs."),
            @DocParameter(name = "triggerIds",
                    description = "Filter out events for unspecified triggers.",
                    allowableValues = "Comma separated list of trigger IDs."),
            @DocParameter(name = "categories",
                    description = "Filter out events for unspecified categories.",
                    allowableValues = "Comma separated list of category values."),
            @DocParameter(name = "tags",
                    description = "[DEPRECATED] Filter out events for unspecified tags.",
                    allowableValues = "Comma separated list of tags, each tag of format 'name\\|description'. + \n" +
                            "Specify '*' for description to match all values."),
            @DocParameter(name = "tagQuery",
                    description = "Filter out events for unspecified tags.",
                    allowableValues = "A tag query expression."),
            @DocParameter(name = "watchInterval", type = Long.class,
                    description = "Define interval when watcher notifications will be sent.",
                    allowableValues = "Interval in seconds"),
            @DocParameter(name = "thin", type = Boolean.class,
                    description = "Return only thin events, do not include: evalSets.")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Stream of events.", response = Event.class),
            @DocResponse(code = 200, message = "Errors will close the stream. Description is sent before stream is closed.", response = ApiError.class)
    })
    public void watchEvents(RoutingContext routing) {
        String tenantId = ResponseUtil.checkTenant(routing);
        Set<String> tenantIds = ResponseUtil.getTenants(tenantId);
        try {
            checkForUnknownQueryParams(routing.request().params(), EventsHandler.queryParamValidationMap.get(EventsHandler.WATCH_EVENTS));
        } catch (IllegalArgumentException e) {
            ResponseUtil.badRequest(routing, e.getMessage());
            return;
        }
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
