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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.hawkular.accounts.api.model.Persona;
import org.hawkular.alerts.api.model.condition.Alert;
import org.hawkular.alerts.api.model.data.MixedData;
import org.hawkular.alerts.api.model.trigger.Tag;
import org.hawkular.alerts.api.services.AlertsCriteria;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.engine.service.AlertsEngine;
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
@Api(value = "/", description = "Alert Handling")
public class AlertsHandler {
    private final Logger log = Logger.getLogger(AlertsHandler.class);

    @Inject
    Persona persona;

    @EJB
    AlertsService alertsService;

    @EJB
    AlertsEngine alertsEngine;

    public AlertsHandler() {
        log.debugf("Creating instance.");
    }

    @GET
    @Path("/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(
            value = "Get alerts with optional filtering",
            notes = "Pagination is not yet implemented.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success. Alerts found."),
            @ApiResponse(code = 204, message = "Success. Not alerts found."),
            @ApiResponse(code = 500, message = "Internal server error") })
    public Response findAlerts(
            @ApiParam(required = false, value = "filter out alerts created before this time, millisecond since epoch")
            @QueryParam("startTime")
            final Long startTime,
            @ApiParam(required = false, value = "filter out alerts created after this time, millisecond since epoch")
            @QueryParam("endTime")
            final Long endTime,
            @ApiParam(required = false, value = "filter out alerts for unspecified alertIds, " +
                    "comma separated list of alert IDs")
            @QueryParam("alertIds")
            final String alertIds,
            @ApiParam(required = false, value = "filter out alerts for unspecified triggers, " +
                    "comma separated list of trigger IDs")
            @QueryParam("triggerIds")
            final String triggerIds,
            @ApiParam(required = false, value = "filter out alerts for unspecified lifecycle status, " +
                    "comma separated list of status values")
            @QueryParam("statuses")
            final String statuses,
            @ApiParam(required = false, value = "filter out alerts for unspecified tags, comma separated list of tags, "
                    + "each tag of format [category|]name")
            @QueryParam("tags")
            final String tags,
            @ApiParam(required = false, value = "return only thin alerts, do not include: evalSets, resolvedEvalSets")
            @QueryParam("thin")
            final Boolean thin) {
        if (!checkPersona()) {
            return ResponseUtil.internalError("No persona found");
        }
        try {
            AlertsCriteria criteria = new AlertsCriteria();
            criteria.setStartTime(startTime);
            criteria.setEndTime(endTime);
            if (!isEmpty(alertIds)) {
                criteria.setAlertIds(Arrays.asList(alertIds.split(",")));
            }
            if (!isEmpty(triggerIds)) {
                criteria.setTriggerIds(Arrays.asList(triggerIds.split(",")));
            }
            if (!isEmpty(statuses)) {
                Set<Alert.Status> statusSet = new HashSet<>();
                for (String s : statuses.split(",")) {
                    statusSet.add(Alert.Status.valueOf(s));
                }
                criteria.setStatusSet(statusSet);
            }
            if (!isEmpty(tags)) {
                String[] tagTokens = tags.split(",");
                List<Tag> tagList = new ArrayList<>(tagTokens.length);
                for (String tagToken : tagTokens) {
                    String[] fields = tagToken.split("\\|");
                    Tag newTag;
                    if (fields.length > 0 && fields.length < 3) {
                        if (fields.length == 1) {
                            newTag = new Tag(fields[0]);
                        } else {
                            newTag = new Tag(fields[0], fields[1]);
                        }
                        newTag.setTenantId(persona.getId());
                        tagList.add(newTag);
                    }
                }
                criteria.setTags(tagList);
            }
            if (null != thin) {
                criteria.setThin(thin.booleanValue());
            }

            List<Alert> alertList = alertsService.getAlerts(persona.getId(), criteria);
            log.debugf("Alerts: %s ", alertList);
            if (isEmpty(alertList)) {
                return ResponseUtil.noContent();
            }
            return ResponseUtil.ok(alertList);
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @PUT
    @Path("/ack")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Set one or more alerts Acknowledged")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Alerts Acknowledged invoked successfully"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public Response ackAlerts(@ApiParam(required = true, value = "comma separated list of alertIds to Ack")
            @QueryParam("alertIds")
            final String alertIds,
            @ApiParam(required = false, value = "user acknowledging the alerts")
            @QueryParam("ackBy")
            final String ackBy,
            @ApiParam(required = false, value = "additional notes asscoiated with the acknowledgement")
            @QueryParam("ackNotes")
            final String ackNotes) {
        if (!checkPersona()) {
            return ResponseUtil.internalError("No persona found");
        }
        try {
            if (!isEmpty(alertIds)) {
                alertsService.ackAlerts(persona.getId(), Arrays.asList(alertIds.split(",")), ackBy, ackNotes);
                log.debugf("AlertsIds: %s ", alertIds);
                return ResponseUtil.ok();
            } else {
                return ResponseUtil.badRequest("AlertIds required for ack");
            }
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @PUT
    @Path("/resolve")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Set one or more alerts Resolved")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Alerts Resolution invoked successfully."),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public Response resolveAlerts(@ApiParam(required = true, value = "comma separated list of alertIds to set Resolved")
            @QueryParam("alertIds")
            final String alertIds,
            @ApiParam(required = false, value = "user resolving the alerts")
            @QueryParam("resolvedBy")
            final String resolvedBy,
            @ApiParam(required = false, value = "additional notes asscoiated with the resolution")
            @QueryParam("resolvedNotes")
            final String resolvedNotes) {
        if (!checkPersona()) {
            return ResponseUtil.internalError("No persona found");
        }
        try {
            if (!isEmpty(alertIds)) {
                alertsService.resolveAlerts(persona.getId(), Arrays.asList(alertIds.split(",")), resolvedBy,
                        resolvedNotes, null);
                log.debugf("AlertsIds: %s ", alertIds);
                return ResponseUtil.ok();
            } else {
                return ResponseUtil.badRequest("AlertsIds required for resolve");
            }
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @POST
    @Path("/data")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Send data for alert processing/condition evaluation.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, data added."),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public Response sendData(@ApiParam(required = true, name = "mixedData", value = "data to be processed by alerting")
            final MixedData mixedData) {
        if (!checkPersona()) {
            return ResponseUtil.internalError("No persona found");
        }
        try {
            if (isEmpty(mixedData)) {
                return ResponseUtil.badRequest("Data is empty");
            } else {
                alertsEngine.sendData(mixedData.asCollection());
                log.debugf("MixedData: %s ", mixedData);
                return ResponseUtil.ok();
            }
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @GET
    @Path("/reload")
    @ApiOperation(
            value = "Reload all definitions into the alerts service",
            notes = "This service is temporal for demos/poc, this functionality will be handled internally" +
                    "between definitions and alerts services")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success. Reload invoked successfully."),
            @ApiResponse(code = 500, message = "Internal server error") })
    public Response reloadAlerts() {
        if (!checkPersona()) {
            return ResponseUtil.internalError("No persona found");
        }
        try {
            alertsEngine.reload();
            return ResponseUtil.ok();
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @GET
    @Path("/reload/{triggerId}")
    @ApiOperation(value = "Reload a specific trigger into the alerts service")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success. Reload invoked successfully."),
            @ApiResponse(code = 500, message = "Internal server error") })
    public Response reloadTrigger(@PathParam("triggerId")
    final String triggerId) {
        if (!checkPersona()) {
            return ResponseUtil.internalError("No persona found");
        }
        try {
            alertsEngine.reloadTrigger(persona.getId(), triggerId);
            return ResponseUtil.ok();
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    private boolean checkPersona() {
        if (persona == null) {
            log.warn("Persona is null. Possible issue with accounts integration ? ");
            return false;
        }
        if (isEmpty(persona.getId())) {
            log.warn("Persona is empty. Possible issue with accounts integration ? ");
            return false;
        }
        return true;
    }

    private boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private boolean isEmpty(MixedData data) {
        return data == null || data.isEmpty();
    }

    private boolean isEmpty(Collection collection) {
        return collection == null || collection.isEmpty();
    }
}
