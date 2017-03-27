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

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import static org.hawkular.alerts.rest.CommonUtil.isEmpty;
import static org.hawkular.alerts.rest.CommonUtil.parseTagQuery;
import static org.hawkular.alerts.rest.CommonUtil.parseTags;
import static org.hawkular.alerts.rest.HawkularAlertsApp.TENANT_HEADER_NAME;

import java.util.Set;
import java.util.TreeSet;

import javax.ejb.EJB;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.services.AlertsCriteria;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.EventsCriteria;
import org.jboss.logging.Logger;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * REST endpoint for cross tenant operations
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Path("/admin")
@Api(value = "/admin", description = "Cross tenant Operations")
public class CrossTenantHandler {
    private static final Logger log = Logger.getLogger(CrossTenantHandler.class);

    @HeaderParam(TENANT_HEADER_NAME)
    String tenantId;

    @EJB
    AlertsService alertsService;

    @EJB
    StreamWatcher streamWatcher;

    public CrossTenantHandler() {
        log.debug("Creating instance.");
    }

    @GET
    @Path("/alerts")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get alerts with optional filtering from multiple tenants.",
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
                    "---- \n",
            response = Alert.class, responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully fetched list of alerts."),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters", response = ResponseUtil.ApiError.class),
            @ApiResponse(code = 500, message = "Internal server error.", response = ResponseUtil.ApiError.class)
    })
    public Response findAlerts(
            @ApiParam(required = false, value = "Filter out alerts created before this time.",
                    allowableValues = "Timestamp in millisecond since epoch.")
            @QueryParam("startTime")
            final Long startTime,
            @ApiParam(required = false, value = "Filter out alerts created after this time.",
                    allowableValues = "Timestamp in millisecond since epoch.")
            @QueryParam("endTime")
            final Long endTime,
            @ApiParam(required = false, value = "Filter out alerts for unspecified alertIds.",
                    allowableValues = "Comma separated list of alert IDs.")
            @QueryParam("alertIds")
            final String alertIds,
            @ApiParam(required = false, value = "Filter out alerts for unspecified triggers. ",
                    allowableValues = "Comma separated list of trigger IDs.")
            @QueryParam("triggerIds")
            final String triggerIds,
            @ApiParam(required = false, value = "Filter out alerts for unspecified lifecycle status.",
                    allowableValues = "Comma separated list of [OPEN, ACKNOWLEDGED, RESOLVED]")
            @QueryParam("statuses")
            final String statuses,
            @ApiParam(required = false, value = "Filter out alerts for unspecified severity. ",
                    allowableValues = "Comma separated list of [LOW, MEDIUM, HIGH, CRITICAL]")
            @QueryParam("severities")
            final String severities,
            @ApiParam(required = false, value = "[DEPRECATED] Filter out events for unspecified tags.",
                    allowableValues = "Comma separated list of tags, each tag of format 'name\\|value'. + \n" +
                            "Specify '*' for value to match all values.")
            @QueryParam("tags")
            final String tags,
            @ApiParam(required = false, value = "Filter out alerts for unspecified tags.",
                    allowableValues = "A tag query expression.")
            @QueryParam("tagQuery")
            final String tagQuery,
            @ApiParam(required = false, value = "Filter out alerts resolved before this time.",
                    allowableValues = "Timestamp in millisecond since epoch.")
            @QueryParam("startResolvedTime")
            final Long startResolvedTime,
            @ApiParam(required = false, value = "Filter out alerts resolved after this time.",
                    allowableValues = "Timestamp in millisecond since epoch.")
            @QueryParam("endResolvedTime")
            final Long endResolvedTime,
            @ApiParam(required = false, value = "Filter out alerts acknowledged before this time.",
                    allowableValues = "Timestamp in millisecond since epoch.")
            @QueryParam("startAckTime")
            final Long startAckTime,
            @ApiParam(required = false, value = "Filter out alerts acknowledged after this time.",
                    allowableValues = "Timestamp in millisecond since epoch.")
            @QueryParam("endAckTime")
            final Long endAckTime,
            @ApiParam(required = false, value = "Filter out alerts with some lifecycle state before this time.",
                    allowableValues = "Timestamp in millisecond since epoch.")
            @QueryParam("startStatusTime")
            final Long startStatusTime,
            @ApiParam(required = false, value = "Filter out alerts with some lifecycle after this time.",
                    allowableValues = "Timestamp in millisecond since epoch.")
            @QueryParam("endStatusTime")
            final Long endStatusTime,
            @ApiParam(required = false, value = "Return only thin alerts, do not include: evalSets, resolvedEvalSets.")
            @QueryParam("thin")
            final Boolean thin,
            @Context
            final UriInfo uri) {
        Pager pager = RequestUtil.extractPaging(uri);
        try {
            Set<String> tenantIds = getTenants(tenantId);
            /*
                We maintain old tags criteria as deprecated (it can be removed in a next major version).
                If present, the tags criteria has precedence over tagQuery parameter.
             */
            String unifiedTagQuery;
            if (!isEmpty(tags)) {
                unifiedTagQuery = parseTagQuery(parseTags(tags));
            } else {
                unifiedTagQuery = tagQuery;
            }
            AlertsCriteria criteria = new AlertsCriteria(startTime, endTime, alertIds, triggerIds, statuses,
                    severities, unifiedTagQuery, startResolvedTime, endResolvedTime, startAckTime, endAckTime,
                    startStatusTime, endStatusTime, thin);
            Page<Alert> alertPage = alertsService.getAlerts(tenantIds, criteria, pager);
            if (log.isDebugEnabled()) {
                log.debug("Alerts: " + alertPage);
            }
            if (isEmpty(alertPage)) {
                return ResponseUtil.ok(alertPage);
            }
            return ResponseUtil.paginatedOk(alertPage, uri);
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            if (e.getCause() != null && e.getCause() instanceof IllegalArgumentException) {
                return ResponseUtil.badRequest("Bad arguments: " + e.getMessage());
            }
            return ResponseUtil.internalError(e);
        }
    }

    @GET
    @Path("/events")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get events with optional filtering from multiple tenants.",
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
                    "; <identifier> and <simple_value> follows pattern [a-zA-Z_0-9][\\-a-zA-Z_0-9]* \n" +
                    "; <regexp> follows any valid Java Regular Expression format \n" +
                    "---- \n",
            response = Event.class, responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully fetched list of events."),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters.", response = ResponseUtil.ApiError.class),
            @ApiResponse(code = 500, message = "Internal server error.", response = ResponseUtil.ApiError.class)
    })
    public Response findEvents(
            @ApiParam(required = false, value = "Filter out events created before this time.",
                    allowableValues = "Timestamp in millisecond since epoch.")
            @QueryParam("startTime")
            final Long startTime,
            @ApiParam(required = false, value = "Filter out events created after this time.",
                    allowableValues = "Timestamp in millisecond since epoch.")
            @QueryParam("endTime")
            final Long endTime,
            @ApiParam(required = false, value = "Filter out events for unspecified eventIds.",
                    allowableValues = "Comma separated list of event IDs.")
            @QueryParam("eventIds") final String eventIds,
            @ApiParam(required = false, value = "Filter out events for unspecified triggers.",
                    allowableValues = "Comma separated list of trigger IDs.")
            @QueryParam("triggerIds")
            final String triggerIds,
            @ApiParam(required = false, value = "Filter out events for unspecified categories. ",
                    allowableValues = "Comma separated list of category values.")
            @QueryParam("categories")
            final String categories,
            @ApiParam(required = false, value = "[DEPRECATED] Filter out events for unspecified tags.",
                    allowableValues = "Comma separated list of tags, each tag of format 'name\\|value'. + \n" +
                            "Specify '*' for value to match all values.")
            @QueryParam("tags")
            final String tags,
            @ApiParam(required = false, value = "Filter out events for unspecified tags.",
                    allowableValues = "A tag query expression.")
            @QueryParam("tagQuery")
            final String tagQuery,
            @ApiParam(required = false, value = "Return only thin events, do not include: evalSets.")
            @QueryParam("thin")
            final Boolean thin,
            @Context
            final UriInfo uri) {
        Pager pager = RequestUtil.extractPaging(uri);
        try {
            Set<String> tenantIds = getTenants(tenantId);
            /*
                We maintain old tags criteria as deprecated (it can be removed in a next major version).
                If present, the tags criteria has precedence over tagQuery parameter.
             */
            String unifiedTagQuery;
            if (!isEmpty(tags)) {
                unifiedTagQuery = parseTagQuery(parseTags(tags));
            } else {
                unifiedTagQuery = tagQuery;
            }
            EventsCriteria criteria = new EventsCriteria(startTime, endTime, eventIds, triggerIds, categories,
                    unifiedTagQuery, thin);
            Page<Event> eventPage = alertsService.getEvents(tenantIds, criteria, pager);
            if (log.isDebugEnabled()) {
                log.debug("Events: " + eventPage);
            }
            if (isEmpty(eventPage)) {
                return ResponseUtil.ok(eventPage);
            }
            return ResponseUtil.paginatedOk(eventPage, uri);
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            if (e.getCause() != null && e.getCause() instanceof IllegalArgumentException) {
                return ResponseUtil.badRequest("Bad arguments: " + e.getMessage());
            }
            return ResponseUtil.internalError(e);
        }
    }

    @GET
    @Path("/watch/alerts")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Watch alerts with optional filtering from multiple tenants.",
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
                    "---- \n",
            response = Alert.class, responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Stream of alerts.", response = Alert.class),
            @ApiResponse(code = 200, message = "Errors will close the stream. Description is sent before stream is closed.", response = ResponseUtil.ApiError.class)
    })
    public Response watchAlerts(
            @ApiParam(required = false, value = "Filter out alerts created before this time.",
                    allowableValues = "Timestamp in millisecond since epoch.")
            @QueryParam("startTime")
            final Long startTime,
            @ApiParam(required = false, value = "Filter out alerts created after this time.",
                    allowableValues = "Timestamp in millisecond since epoch.")
            @QueryParam("endTime")
            final Long endTime,
            @ApiParam(required = false, value = "Filter out alerts for unspecified alertIds.",
                    allowableValues = "Comma separated list of alert IDs.")
            @QueryParam("alertIds")
            final String alertIds,
            @ApiParam(required = false, value = "Filter out alerts for unspecified triggers. ",
                    allowableValues = "Comma separated list of trigger IDs.")
            @QueryParam("triggerIds")
            final String triggerIds,
            @ApiParam(required = false, value = "Filter out alerts for unspecified lifecycle status.",
                    allowableValues = "Comma separated list of [OPEN, ACKNOWLEDGED, RESOLVED]")
            @QueryParam("statuses")
            final String statuses,
            @ApiParam(required = false, value = "Filter out alerts for unspecified severity. ",
                    allowableValues = "Comma separated list of [LOW, MEDIUM, HIGH, CRITICAL]")
            @QueryParam("severities")
            final String severities,
            @ApiParam(required = false, value = "[DEPRECATED] Filter out events for unspecified tags.",
                    allowableValues = "Comma separated list of tags, each tag of format 'name\\|value'. + \n" +
                            "Specify '*' for value to match all values.")
            @QueryParam("tags")
            final String tags,
            @ApiParam(required = false, value = "Filter out alerts for unspecified tags.",
                    allowableValues = "A tag query expression.")
            @QueryParam("tagQuery")
            final String tagQuery,
            @ApiParam(required = false, value = "Filter out alerts resolved before this time.",
                    allowableValues = "Timestamp in millisecond since epoch.")
            @QueryParam("startResolvedTime")
            final Long startResolvedTime,
            @ApiParam(required = false, value = "Filter out alerts resolved after this time.",
                    allowableValues = "Timestamp in millisecond since epoch.")
            @QueryParam("endResolvedTime")
            final Long endResolvedTime,
            @ApiParam(required = false, value = "Filter out alerts acknowledged before this time.",
                    allowableValues = "Timestamp in millisecond since epoch.")
            @QueryParam("startAckTime")
            final Long startAckTime,
            @ApiParam(required = false, value = "Filter out alerts acknowledged after this time.",
                    allowableValues = "Timestamp in millisecond since epoch.")
            @QueryParam("endAckTime")
            final Long endAckTime,
            @ApiParam(required = false, value = "Filter out alerts with some lifecycle state before this time.",
                    allowableValues = "Timestamp in millisecond since epoch.")
            @QueryParam("startStatusTime")
            final Long startStatusTime,
            @ApiParam(required = false, value = "Filter out alerts with some lifecycle after this time.",
                    allowableValues = "Timestamp in millisecond since epoch.")
            @QueryParam("endStatusTime")
            final Long endStatusTime,
            @ApiParam(required = false, value = "Define interval when watcher notifications will be sent.",
                    allowableValues = "Interval in seconds")
            @QueryParam("watchInterval")
            final Long watchInterval,
            @ApiParam(required = false, value = "Return only thin alerts, do not include: evalSets, resolvedEvalSets.")
            @QueryParam("thin")
            final Boolean thin,
            @Context
            final UriInfo uri) {
        Set<String> tenantIds = getTenants(tenantId);
        String unifiedTagQuery;
        if (!isEmpty(tags)) {
            unifiedTagQuery = parseTagQuery(parseTags(tags));
        } else {
            unifiedTagQuery = tagQuery;
        }
        AlertsCriteria criteria = new AlertsCriteria(startTime, endTime, alertIds, triggerIds, statuses,
                severities, unifiedTagQuery, startResolvedTime, endResolvedTime, startAckTime, endAckTime,
                startStatusTime, endStatusTime, thin);
        return Response.ok(streamWatcher.watchAlerts(tenantIds, criteria, watchInterval)).build();
    }

    @GET
    @Path("/watch/events")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Watch events with optional filtering from multiple tenants.",
            notes = "Return a stream of events ordered by ctime. + \n" +
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
                    "; <identifier> and <simple_value> follows pattern [a-zA-Z_0-9][\\-a-zA-Z_0-9]* \n" +
                    "; <regexp> follows any valid Java Regular Expression format \n" +
                    "---- \n",
            response = Event.class, responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Stream of events.", response = Event.class),
            @ApiResponse(code = 200, message = "Errors will close the stream. Description is sent before stream is closed.", response = ResponseUtil.ApiError.class)
    })
    public Response watchEvents(
            @ApiParam(required = false, value = "Filter out events created before this time.",
                    allowableValues = "Timestamp in millisecond since epoch.")
            @QueryParam("startTime")
            final Long startTime,
            @ApiParam(required = false, value = "Filter out events created after this time.",
                    allowableValues = "Timestamp in millisecond since epoch.")
            @QueryParam("endTime")
            final Long endTime,
            @ApiParam(required = false, value = "Filter out events for unspecified eventIds.",
                    allowableValues = "Comma separated list of event IDs.")
            @QueryParam("eventIds") final String eventIds,
            @ApiParam(required = false, value = "Filter out events for unspecified triggers.",
                    allowableValues = "Comma separated list of trigger IDs.")
            @QueryParam("triggerIds")
            final String triggerIds,
            @ApiParam(required = false, value = "Filter out events for unspecified categories. ",
                    allowableValues = "Comma separated list of category values.")
            @QueryParam("categories")
            final String categories,
            @ApiParam(required = false, value = "[DEPRECATED] Filter out events for unspecified tags.",
                    allowableValues = "Comma separated list of tags, each tag of format 'name\\|value'. + \n" +
                            "Specify '*' for value to match all values.")
            @QueryParam("tags")
            final String tags,
            @ApiParam(required = false, value = "Filter out events for unspecified tags.",
                    allowableValues = "A tag query expression.")
            @QueryParam("tagQuery")
            final String tagQuery,
            @ApiParam(required = false, value = "Define interval when watcher notifications will be sent.",
                    allowableValues = "Interval in seconds")
            @QueryParam("watchInterval")
            final Long watchInterval,
            @ApiParam(required = false, value = "Return only thin events, do not include: evalSets.")
            @QueryParam("thin")
            final Boolean thin,
            @Context
            final UriInfo uri) {
        Set<String> tenantIds = getTenants(tenantId);
        String unifiedTagQuery;
        if (!isEmpty(tags)) {
            unifiedTagQuery = parseTagQuery(parseTags(tags));
        } else {
            unifiedTagQuery = tagQuery;
        }
        EventsCriteria criteria = new EventsCriteria(startTime, endTime, eventIds, triggerIds, categories,
                unifiedTagQuery, thin);
        return Response.ok(streamWatcher.watchEvents(tenantIds, criteria, watchInterval)).build();
    }

    private Set<String> getTenants(String tenantId) {
        Set<String> tenantIds = new TreeSet<>();
        for (String t : tenantId.split(",")) {
            tenantIds.add(t);
        }
        return tenantIds;
    }
}
