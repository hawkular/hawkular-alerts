/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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

import static org.hawkular.alerts.rest.HawkularAlertsApp.TENANT_HEADER_NAME;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.ejb.EJB;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.hawkular.alerts.api.model.Severity;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.services.AlertsCriteria;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.engine.service.AlertsEngine;
import org.hawkular.alerts.rest.ResponseUtil.ApiError;
import org.jboss.logging.Logger;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * REST endpoint for alerts
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Path("/")
@Api(tags = {"/"}, description = "Alerts Handling")
public class AlertsHandler {
    private final Logger log = Logger.getLogger(AlertsHandler.class);

    @HeaderParam(TENANT_HEADER_NAME)
    String tenantId;

    @EJB
    AlertsService alertsService;

    @EJB
    AlertsEngine alertsEngine;

    public AlertsHandler() {
        log.debug("Creating instance.");
    }

    @GET
    @Path("/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get alerts with optional filtering.",
            response = Alert.class, responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully fetched list of alerts."),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters"),
            @ApiResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public Response findAlerts(
            @ApiParam(required = false, value = "Filter out alerts created before this time, millisecond since epoch.")
            @QueryParam("startTime")
            final Long startTime,
            @ApiParam(required = false, value = "Filter out alerts created after this time, millisecond since epoch.")
            @QueryParam("endTime")
            final Long endTime,
            @ApiParam(required = false, value = "Filter out alerts for unspecified alertIds, " +
                    "comma separated list of alert IDs.")
            @QueryParam("alertIds")
            final String alertIds,
            @ApiParam(required = false, value = "Filter out alerts for unspecified triggers, " +
                    "comma separated list of trigger IDs.")
            @QueryParam("triggerIds")
            final String triggerIds,
            @ApiParam(required = false, value = "Filter out alerts for unspecified lifecycle status, " +
                    "comma separated list of status values.")
            @QueryParam("statuses")
            final String statuses,
            @ApiParam(required = false, value = "Filter out alerts for unspecified severity, " +
                    "comma separated list of severity values.")
            @QueryParam("severities")
            final String severities,
            @ApiParam(required = false, value = "Filter out events for unspecified tags, comma separated list of tags, "
                    + "each tag of format 'name|value'. Specify '*' for value to match all values.")
            @QueryParam("tags")
            final String tags,
            @ApiParam(required = false, value = "Return only thin alerts, do not include: evalSets, resolvedEvalSets.")
            @QueryParam("thin")
            final Boolean thin,
            @Context
            final UriInfo uri) {
        Pager pager = RequestUtil.extractPaging(uri);
        try {
            AlertsCriteria criteria = buildCriteria(startTime, endTime, alertIds, triggerIds, statuses, severities,
                    tags, thin);
            Page<Alert> alertPage = alertsService.getAlerts(tenantId, criteria, pager);
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

    @PUT
    @Path("/ack/{alertId}")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Set one alert Acknowledged.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Alert Acknowledged invoked successfully."),
            @ApiResponse(code = 500, message = "Internal server error.", response = ApiError.class),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters.", response = ApiError.class)
    })
    public Response ackAlert(@ApiParam(required = true, value = "The alertId to Ack.")
    @PathParam("alertId")
    final String alertId,
            @ApiParam(required = false, value = "User acknowledging the alerts.")
            @QueryParam("ackBy")
            final String ackBy,
            @ApiParam(required = false, value = "Additional notes associated with the acknowledgement.")
            @QueryParam("ackNotes")
            final String ackNotes) {
        try {
            if (!isEmpty(alertId)) {
                alertsService.ackAlerts(tenantId, Arrays.asList(alertId), ackBy, ackNotes);
                if (log.isDebugEnabled()) {
                    log.debug("AlertId: " + alertId);
                }
                return ResponseUtil.ok();
            } else {
                return ResponseUtil.badRequest("AlertId required for ack");
            }
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            if (e.getCause() != null && e.getCause() instanceof IllegalArgumentException) {
                return ResponseUtil.badRequest("Bad arguments: " + e.getMessage());
            }
            return ResponseUtil.internalError(e);
        }
    }

    @PUT
    @Path("/note/{alertId}")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Add a note into an existing Alert.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Alert Acknowledged invoked successfully."),
            @ApiResponse(code = 500, message = "Internal server error.", response = ApiError.class),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters.", response = ApiError.class)
    })
    public Response addAlertNote(@ApiParam(required = true, value = "The alertId to add the note.")
                             @PathParam("alertId")
                             final String alertId,
                             @ApiParam(required = false, value = "Author of the note.")
                             @QueryParam("user")
                             final String user,
                             @ApiParam(required = false, value = "Text of the note.")
                             @QueryParam("text")
                             final String text) {
        try {
            if (!isEmpty(alertId)) {
                alertsService.addNote(tenantId, alertId, user, text);
                if (log.isDebugEnabled()) {
                    log.debug("AlertId: " + alertId);
                }
                return ResponseUtil.ok();
            } else {
                return ResponseUtil.badRequest("AlertId required for adding notes");
            }
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            if (e.getCause() != null && e.getCause() instanceof IllegalArgumentException) {
                return ResponseUtil.badRequest("Bad arguments: " + e.getMessage());
            }
            return ResponseUtil.internalError(e);
        }
    }

    @PUT
    @Path("/tags")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Add tags to existing Alerts.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Alerts tagged successfully."),
            @ApiResponse(code = 500, message = "Internal server error.", response = ApiError.class),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters.", response = ApiError.class)
    })
    public Response addTags(
            @ApiParam(required = true, value = "Comma separated list of alertIds to tag.")
            @QueryParam("alertIds")
            final String alertIds,
            @ApiParam(required = true, value = "Comma separated list of tags to add, "
                    + "each tag of format 'name|value'.")
            @QueryParam("tags")
            final String tags) {
        try {
            if (!isEmpty(alertIds) || isEmpty(tags)) {
                // criteria just used for convenient type translation
                AlertsCriteria c = buildCriteria(null, null, alertIds, null, null, null, tags, false);
                alertsService.addAlertTags(tenantId, c.getAlertIds(), c.getTags());
                if (log.isDebugEnabled()) {
                    log.debugf("Tagged alertIds:%s, %s", c.getAlertIds(), c.getTags());
                }
                return ResponseUtil.ok();
            } else {
                return ResponseUtil.badRequest("AlertIds and Tags required for adding tags");
            }
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            if (e.getCause() != null && e.getCause() instanceof IllegalArgumentException) {
                return ResponseUtil.badRequest("Bad arguments: " + e.getMessage());
            }
            return ResponseUtil.internalError(e);
        }
    }

    @DELETE
    @Path("/tags")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Remove tags from existing Alerts.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Alerts untagged successfully."),
            @ApiResponse(code = 500, message = "Internal server error.", response = ApiError.class),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters.", response = ApiError.class)
    })
    public Response deleteTags(
            @ApiParam(required = true, value = "Comma separated list of alertIds to untag.")
            @QueryParam("alertIds")
            final String alertIds,
            @ApiParam(required = true, value = "Comma separated list of tag names to remove.")
            @QueryParam("tagNames")
            final String tagNames) {
        try {
            if (!isEmpty(alertIds) || isEmpty(tagNames)) {
                Collection<String> ids = Arrays.asList(alertIds.split(","));
                Collection<String> tags = Arrays.asList(tagNames.split(","));
                alertsService.removeAlertTags(tenantId, ids, tags);
                if (log.isDebugEnabled()) {
                    log.debugf("Untagged alertIds:%s, %s", ids, tags);
                }
                return ResponseUtil.ok();
            } else {
                return ResponseUtil.badRequest("AlertIds and Tags required for removing tags");
            }
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            if (e.getCause() != null && e.getCause() instanceof IllegalArgumentException) {
                return ResponseUtil.badRequest("Bad arguments: " + e.getMessage());
            }
            return ResponseUtil.internalError(e);
        }
    }

    @PUT
    @Path("/ack")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Set one or more alerts Acknowledged.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Alerts Acknowledged invoked successfully."),
            @ApiResponse(code = 500, message = "Internal server error.", response = ApiError.class),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters", response = ApiError.class)
    })
    public Response ackAlerts(
            @ApiParam(required = true, value = "Comma separated list of alertIds to Ack.")
            @QueryParam("alertIds")
            final String alertIds,
            @ApiParam(required = false, value = "User acknowledging the alerts.")
            @QueryParam("ackBy")
            final String ackBy,
            @ApiParam(required = false, value = "Additional notes asscoiated with the acknowledgement.")
            @QueryParam("ackNotes")
            final String ackNotes) {
        try {
            if (!isEmpty(alertIds)) {
                alertsService.ackAlerts(tenantId, Arrays.asList(alertIds.split(",")), ackBy, ackNotes);
                if (log.isDebugEnabled()) {
                    log.debug("Acked alertIds: " + alertIds);
                }
                return ResponseUtil.ok();
            } else {
                return ResponseUtil.badRequest("AlertIds required for ack");
            }
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            if (e.getCause() != null && e.getCause() instanceof IllegalArgumentException) {
                return ResponseUtil.badRequest("Bad arguments: " + e.getMessage());
            }
            return ResponseUtil.internalError(e);
        }
    }

    @DELETE
    @Path("/{alertId}")
    @ApiOperation(value = "Delete an existing Alert.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Alert deleted."),
            @ApiResponse(code = 500, message = "Internal server error.", response = ApiError.class),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters.", response = ApiError.class),
            @ApiResponse(code = 404, message = "Alert not found.", response = ApiError.class)
    })
    public Response deleteAlert(
            @ApiParam(required = true, value = "Alert id to be deleted.")
            @PathParam("alertId")
            final String alertId) {
        try {
            AlertsCriteria criteria = new AlertsCriteria();
            criteria.setAlertId(alertId);
            int numDeleted = alertsService.deleteAlerts(tenantId, criteria);
            if (1 == numDeleted) {
                if (log.isDebugEnabled()) {
                    log.debug("AlertId: " + alertId);
                }
                return ResponseUtil.ok();
            } else {
                return ResponseUtil.notFound("Alert " + alertId + " doesn't exist for delete");
            }
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            if (e.getCause() != null && e.getCause() instanceof IllegalArgumentException) {
                return ResponseUtil.badRequest("Bad arguments: " + e.getMessage());
            }
            return ResponseUtil.internalError(e);
        }
    }

    @PUT
    @Path("/delete")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Delete alerts with optional filtering.",
            notes = "Return number of alerts deleted.",
            response = Integer.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Alerts deleted."),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters"),
            @ApiResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public Response deleteAlerts(
            @ApiParam(required = false, value = "Filter out alerts created before this time, millisecond since epoch.")
            @QueryParam("startTime")
            final Long startTime,
            @ApiParam(required = false, value = "Filter out alerts created after this time, millisecond since epoch.")
            @QueryParam("endTime")
            final Long endTime,
            @ApiParam(required = false, value = "Filter out alerts for unspecified alertIds, " +
                    "comma separated list of alert IDs.")
            @QueryParam("alertIds")
            final String alertIds,
            @ApiParam(required = false, value = "Filter out alerts for unspecified triggers, " +
                    "comma separated list of trigger IDs.")
            @QueryParam("triggerIds")
            final String triggerIds,
            @ApiParam(required = false, value = "Filter out alerts for unspecified lifecycle status, " +
                    "comma separated list of status values.")
            @QueryParam("statuses")
            final String statuses,
            @ApiParam(required = false, value = "Filter out alerts for unspecified severity, " +
                    "comma separated list of severity values.")
            @QueryParam("severities")
            final String severities,
            @ApiParam(required = false, value = "Filter out alerts for unspecified tags, comma separated list of tags, "
                    + "each tag of format 'name|value'. Specify '*' for value to match all values.")
            @QueryParam("tags")
            final String tags
            ) {
        try {
            AlertsCriteria criteria = buildCriteria(startTime, endTime, alertIds, triggerIds, statuses, severities,
                    tags, null);
            int numDeleted = alertsService.deleteAlerts(tenantId, criteria);
            if (log.isDebugEnabled()) {
                log.debug("Alerts deleted: " + numDeleted);
            }
            return ResponseUtil.ok(numDeleted);
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            if (e.getCause() != null && e.getCause() instanceof IllegalArgumentException) {
                return ResponseUtil.badRequest("Bad arguments: " + e.getMessage());
            }
            return ResponseUtil.internalError(e);
        }
    }

    private AlertsCriteria buildCriteria(Long startTime, Long endTime, String alertIds, String triggerIds,
            String statuses, String severities, String tags, Boolean thin) {
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
        if (null != severities && !severities.trim().isEmpty()) {
            Set<Severity> severitySet = new HashSet<>();
            for (String s : severities.split(",")) {
                severitySet.add(Severity.valueOf(s));
            }
            criteria.setSeverities(severitySet);
        }
        if (!isEmpty(tags)) {
            String[] tagTokens = tags.split(",");
            Map<String, String> tagsMap = new HashMap<>(tagTokens.length);
            for (String tagToken : tagTokens) {
                String[] fields = tagToken.split("\\|");
                if (fields.length == 2) {
                    tagsMap.put(fields[0], fields[1]);
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("Invalid Tag Criteria " + Arrays.toString(fields));
                    }
                    throw new IllegalArgumentException("Invalid Tag Criteria " + Arrays.toString(fields));
                }
            }
            criteria.setTags(tagsMap);
        }
        if (null != thin) {
            criteria.setThin(thin.booleanValue());
        }

        return criteria;
    }

    @GET
    @Path("/alert/{alertId}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get an existing Alert.",
            response = Alert.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Alert found."),
            @ApiResponse(code = 404, message = "Alert not found.", response = ApiError.class),
            @ApiResponse(code = 500, message = "Internal server error", response = ApiError.class)
    })
    public Response getAlert(
            @ApiParam(value = "Id of alert to be retrieved", required = true)
            @PathParam("alertId")
            final String alertId,
            @ApiParam(required = false, value = "Return only a thin alert, do not include: evalSets, resolvedEvalSets.")
            @QueryParam("thin")
            final Boolean thin) {
        try {
            Alert found = alertsService.getAlert(tenantId, alertId, ((null == thin) ? false : thin.booleanValue()));
            if (found != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Alert: " + found);
                }
                return ResponseUtil.ok(found);
            } else {
                return ResponseUtil.notFound("alertId: " + alertId + " not found");
            }
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            return ResponseUtil.internalError(e);
        }
    }

    @PUT
    @Path("/resolve/{alertId}")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Set one alert Resolved.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Alerts Resolution invoked successfully."),
            @ApiResponse(code = 500, message = "Internal server error.", response = ApiError.class),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters", response = ApiError.class)
    })
    public Response resolveAlert(@ApiParam(required = true, value = "The alertId to set resolved.")
            @PathParam("alertId")
            final String alertId,
            @ApiParam(required = false, value = "User resolving the alerts.")
            @QueryParam("resolvedBy")
            final String resolvedBy,
            @ApiParam(required = false, value = "Additional notes associated with the resolution.")
            @QueryParam("resolvedNotes")
            final String resolvedNotes) {
        try {
            if (!isEmpty(alertId)) {
                alertsService.resolveAlerts(tenantId, Arrays.asList(alertId), resolvedBy,
                        resolvedNotes, null);
                if (log.isDebugEnabled()) {
                    log.debug("AlertId: " + alertId);
                }
                return ResponseUtil.ok();
            } else {
                return ResponseUtil.badRequest("AlertsId required for resolve");
            }
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            if (e.getCause() != null && e.getCause() instanceof IllegalArgumentException) {
                return ResponseUtil.badRequest("Bad arguments: " + e.getMessage());
            }
            return ResponseUtil.internalError(e);
        }
    }

    @PUT
    @Path("/resolve")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Set one or more alerts resolved.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Alerts Resolution invoked successfully."),
            @ApiResponse(code = 500, message = "Internal server error.", response = ApiError.class),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters", response = ApiError.class)
    })
    public Response resolveAlerts(
            @ApiParam(required = true, value = "Comma separated list of alertIds to set resolved.")
            @QueryParam("alertIds")
            final String alertIds,
            @ApiParam(required = false, value = "User resolving the alerts.")
            @QueryParam("resolvedBy")
            final String resolvedBy,
            @ApiParam(required = false, value = "Additional notes associated with the resolution.")
            @QueryParam("resolvedNotes")
            final String resolvedNotes) {
        try {
            if (!isEmpty(alertIds)) {
                alertsService.resolveAlerts(tenantId, Arrays.asList(alertIds.split(",")), resolvedBy,
                        resolvedNotes, null);
                if (log.isDebugEnabled()) {
                    log.debug("AlertsIds: " + alertIds);
                }
                return ResponseUtil.ok();
            } else {
                return ResponseUtil.badRequest("AlertsIds required for resolve");
            }
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            if (e.getCause() != null && e.getCause() instanceof IllegalArgumentException) {
                return ResponseUtil.badRequest("Bad arguments: " + e.getMessage());
            }
            return ResponseUtil.internalError(e);
        }
    }

    @POST
    @Path("/data")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Send data for alert processing/condition evaluation.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, data added."),
            @ApiResponse(code = 500, message = "Internal server error.", response = ApiError.class),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters", response = ApiError.class)
    })
    public Response sendData(
            @ApiParam(required = true, name = "datums", value = "Data to be processed by alerting.")
            final Collection<Data> datums) {
        try {
            if (isEmpty(datums)) {
                return ResponseUtil.badRequest("Data is empty");
            } else {
                for (Data d : datums) {
                    d.setTenantId(tenantId);
                }
                alertsService.sendData(datums);
                if (log.isDebugEnabled()) {
                    log.debug("Datums: " + datums);
                }
                return ResponseUtil.ok();
            }
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            if (e.getCause() != null && e.getCause() instanceof IllegalArgumentException) {
                return ResponseUtil.badRequest("Bad arguments: " + e.getMessage());
            }
            return ResponseUtil.internalError(e);
        }
    }

    private boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private boolean isEmpty(Collection collection) {
        return collection == null || collection.isEmpty();
    }
}
