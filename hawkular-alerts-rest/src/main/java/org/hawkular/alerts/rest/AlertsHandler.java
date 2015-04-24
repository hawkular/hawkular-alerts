/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
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
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ejb.EJB;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Response;

import org.hawkular.alerts.api.model.condition.Alert;
import org.hawkular.alerts.api.model.trigger.Tag;
import org.hawkular.alerts.api.services.AlertsCriteria;
import org.hawkular.alerts.api.services.AlertsService;
import org.jboss.logging.Logger;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

/**
 * REST endpoint for alerts
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Path("/")
@Api(value = "/", description = "Hawkular-Alerts REST API for Alert Handling")
public class AlertsHandler {
    // private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(AlertsHandler.class);

    public AlertsHandler() {
        log.debugf("Creating instance.");
    }

    @EJB
    AlertsService alerts;

    @GET
    @Path("/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(
            value = "Get alerts with optional filtering",
            responseContainer = "Collection<Alert>",
            response = Alert.class,
            notes = "Pagination is not yet implemented.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Alerts found and returned"),
            @ApiResponse(code = 204, message = "Success, no Alerts found"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public void findAlerts(
            @Suspended
            final AsyncResponse response,
            @ApiParam(required = false, value = "filter out alerts created before this time, millisecond since epoch")
            @QueryParam("startTime")
            Long startTime,
            @ApiParam(required = false, value = "filter out alerts created after this time, millisecond since epoch")
            @QueryParam("endTime")
            Long endTime,
            @ApiParam(required = false, value = "filter out alerts for unspecified alertIds, " +
                    "comma separated list of alert IDs")
            @QueryParam("alertIds")
            String alertIds,
            @ApiParam(required = false, value = "filter out alerts for unspecified triggers, " +
                    "comma separated list of trigger IDs")
            @QueryParam("triggerIds")
            String triggerIds,
            @ApiParam(required = false, value = "filter out alerts for unspecified lifecycle status, " +
                    "comma separated list of status values")
            @QueryParam("statuses")
            String statuses,
            @ApiParam(required = false, value = "filter out alerts for unspecified tags, comma separated list of tags, "
                    + "each tag of format [category|]name")
            @QueryParam("tags")
            String tags) {

        try {
            AlertsCriteria criteria = new AlertsCriteria();
            criteria.setStartTime(startTime);
            criteria.setEndTime(endTime);
            if (null != alertIds && !alertIds.trim().isEmpty()) {
                criteria.setAlertIds(Arrays.asList(alertIds.split(",")));
            }
            if (null != triggerIds && !triggerIds.trim().isEmpty()) {
                criteria.setTriggerIds(Arrays.asList(triggerIds.split(",")));
            }
            if (null != statuses && !statuses.trim().isEmpty()) {
                Set<Alert.Status> statusSet = new HashSet<>();
                for (String s : statuses.split(",")) {
                    statusSet.add(Alert.Status.valueOf(s));
                }
                criteria.setStatusSet(statusSet);
            }
            if (null != tags && !tags.trim().isEmpty()) {
                String[] tagTokens = tags.split(",");
                List<Tag> tagList = new ArrayList<>(tagTokens.length);
                for (String tagToken : tagTokens) {
                    String[] fields = tagToken.split("\\|");
                    tagList.add(fields.length == 1 ? new Tag(fields[0]) : new Tag(fields[0], fields[1]));
                }
                criteria.setTags(tagList);
            }

            List<Alert> alertList = alerts.getAlerts(criteria);
            if (alertList.isEmpty()) {
                log.debugf("GET - findAlerts - Empty");
                response.resume(Response.status(Response.Status.NO_CONTENT).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("GET - findAlerts - %s alerts", alertList.size());
                response.resume(Response.status(Response.Status.OK).entity(alertList).type(APPLICATION_JSON_TYPE)
                        .build());
            }
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            Map<String, String> errors = new HashMap<String, String>();
            errors.put("errorMsg", "Internal Error: " + e.getMessage());
            response.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errors).type(APPLICATION_JSON_TYPE).build());
        }

    }

    @GET
    @Path("/reload")
    @ApiOperation(
            value = "Reload all definitions into the alerts service",
            notes = "This service is temporal for demos/poc, this functionality will be handled internally" +
                    "between definitions and alerts services")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public void reloadAlerts(
            @Suspended
            final AsyncResponse response) {
        alerts.reload();
        response.resume(Response.status(Response.Status.OK).build());
    }

    @GET
    @Path("/reload/{triggerId}")
    @ApiOperation(value = "Reload a specific trigger into the alerts service")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public void reloadTrigger(
            @Suspended
            final AsyncResponse response,
            @PathParam("triggerId")
            String triggerId) {
        alerts.reloadTrigger(triggerId);
        response.resume(Response.status(Response.Status.OK).build());
    }

    @PUT
    @Path("/ack")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Set one or more alerts Acknowledged")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Alerts Acknowledged"),
            @ApiResponse(code = 404, message = "AlertIds invalid or not found"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public void ackAlerts(
            @Suspended
            final AsyncResponse response,
            @ApiParam(required = true, value = "comma separated list of alertIds to Ack")
            @QueryParam("alertIds")
            String alertIds,
            @ApiParam(required = false, value = "user acknowledging the alerts")
            @QueryParam("ackBy")
            String ackBy,
            @ApiParam(required = false, value = "additional notes asscoiated with the acknowledgement")
            @QueryParam("ackNotes")
            String ackNotes) {
        try {
            if (alertIds != null && !alertIds.isEmpty()) {
                log.debugf("PUT - ackAlerts : %s ", alertIds);
                alerts.ackAlerts(Arrays.asList(alertIds.split(",")), ackBy, ackNotes);
                response.resume(Response.status(Response.Status.OK).build());
            } else {
                log.debugf("PUT - ackAlerts - alertIds required.");
                Map<String, String> errors = new HashMap<String, String>();
                errors.put("errorMsg", "alertIds required");
                response.resume(Response.status(Response.Status.NOT_FOUND)
                        .entity(errors).type(APPLICATION_JSON_TYPE).build());
            }
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            Map<String, String> errors = new HashMap<String, String>();
            errors.put("errorMsg", "Internal Error: " + e.getMessage());
            response.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errors).type(APPLICATION_JSON_TYPE).build());
        }
    }

    @PUT
    @Path("/resolve")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Set one or more alerts Resolved")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Alerts Resolveded"),
            @ApiResponse(code = 404, message = "AlertIds invalid or not found"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public void resolveAlerts(
            @Suspended
            final AsyncResponse response,
            @ApiParam(required = true, value = "comma separated list of alertIds to set Resolved")
            @QueryParam("alertIds")
            String alertIds,
            @ApiParam(required = false, value = "user resolving the alerts")
            @QueryParam("resolvedBy")
            String resolvedBy,
            @ApiParam(required = false, value = "additional notes asscoiated with the resolution")
            @QueryParam("resolvedNotes")
            String resolvedNotes) {
        try {
            if (alertIds != null && !alertIds.isEmpty()) {
                log.debugf("PUT - resolveAlerts : %s ", alertIds);
                alerts.resolveAlerts(Arrays.asList(alertIds.split(",")), resolvedBy, resolvedNotes, null);
                response.resume(Response.status(Response.Status.OK).build());
            } else {
                log.debugf("PUT - resolveAlerts - alertIds required.");
                Map<String, String> errors = new HashMap<String, String>();
                errors.put("errorMsg", "alertIds required");
                response.resume(Response.status(Response.Status.NOT_FOUND)
                        .entity(errors).type(APPLICATION_JSON_TYPE).build());
            }
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            Map<String, String> errors = new HashMap<String, String>();
            errors.put("errorMsg", "Internal Error: " + e.getMessage());
            response.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errors).type(APPLICATION_JSON_TYPE).build());
        }
    }

}
