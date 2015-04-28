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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.ejb.EJB;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Response;

import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.CompareCondition;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.condition.StringCondition;
import org.hawkular.alerts.api.model.condition.ThresholdCondition;
import org.hawkular.alerts.api.model.condition.ThresholdRangeCondition;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.trigger.Tag;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

/**
 * REST endpoint for triggers
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Path("/triggers")
@Api(value = "/triggers",
        description = "Hawkular-Alerts REST API for Trigger Handling")
public class TriggersHandler {
    private static final Logger log = Logger.getLogger(TriggersHandler.class);

    @EJB
    DefinitionsService definitions;

    ObjectMapper objectMapper;

    public TriggersHandler() {
        log.debugf("Creating instance.");
        objectMapper = new ObjectMapper();
    }

    @GET
    @Path("/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(
            value = "Find all Trigger definitions",
            responseContainer = "Collection<Trigger>",
            response = Trigger.class,
            notes = "Pagination is not yet implemented")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Triggers Found"),
            @ApiResponse(code = 204, message = "Success, No Triggers Found"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public void findAllTriggers(@Suspended
    final AsyncResponse response) {
        try {
            Collection<Trigger> triggerList = definitions.getAllTriggers();
            if (triggerList.isEmpty()) {
                log.debugf("GET - findAllTriggers - Empty");
                response.resume(Response.status(Response.Status.NO_CONTENT).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("GET - findAllTriggers - %s triggers ", triggerList.size());
                response.resume(Response.status(Response.Status.OK)
                        .entity(triggerList).type(APPLICATION_JSON_TYPE).build());
            }
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            Map<String, String> errors = new HashMap<String, String>();
            errors.put("errorMsg", "Internal Error: " + e.getMessage());
            response.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errors).type(APPLICATION_JSON_TYPE).build());
        }
    }

    @POST
    @Path("/")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(
            value = "Create a new trigger definitions. If trigger ID is null, a (likely) unique ID will be generated",
            response = Trigger.class,
            notes = "Returns Trigger created if operation finished correctly")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Trigger Created"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public void createTrigger(
            @Suspended
            final AsyncResponse response,
            @ApiParam(value = "Trigger definition to be created", name = "trigger", required = true)
            final Trigger trigger) {
        try {
            if (null != trigger) {
                if (isEmpty(trigger.getId())) {
                    trigger.setId(Trigger.generateId());

                } else if (definitions.getTrigger(trigger.getId()) != null) {
                    String errorMsg = "POST - Trigger with ID [" + trigger.getId() + "] exists.";
                    log.debugf(errorMsg);
                    Map<String, String> errors = new HashMap<String, String>();
                    errors.put("errorMsg", errorMsg);
                    response.resume(Response.status(Response.Status.BAD_REQUEST)
                            .entity(errors).type(APPLICATION_JSON_TYPE).build());
                    return;
                }

                log.debugf("POST - createTrigger - triggerId %s ", trigger.getId());
                definitions.addTrigger(trigger);
                response.resume(Response.status(Response.Status.OK)
                        .entity(trigger).type(APPLICATION_JSON_TYPE).build());

            } else {
                String errorMsg = "POST - Trigger is null";
                log.debugf(errorMsg);
                Map<String, String> errors = new HashMap<String, String>();
                errors.put("errorMsg", errorMsg);
                response.resume(Response.status(Response.Status.BAD_REQUEST)
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

    @GET
    @Path("/{triggerId}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get an existing trigger definition",
            response = Trigger.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Trigger Found"),
            @ApiResponse(code = 404, message = "Success, No Trigger Found"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public void getTrigger(
            @Suspended
            final AsyncResponse response,
            @ApiParam(value = "Trigger definition id to be retrieved",
                    required = true)
            @PathParam("triggerId")
            final String triggerId) {
        try {
            Trigger found = null;
            if (triggerId != null && !triggerId.isEmpty()) {
                found = definitions.getTrigger(triggerId);
            }
            if (found != null) {
                log.debugf("GET - getTrigger - triggerId: %s ", found.getId());
                response.resume(Response.status(Response.Status.OK).entity(found).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("GET - getTrigger - triggerId: %s not found or invalid. ", triggerId);
                Map<String, String> errors = new HashMap<String, String>();
                errors.put("errorMsg", "Trigger ID " + triggerId + " not found or invalid ID");
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
    @Path("/{triggerId}")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Update an existing trigger definition")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Trigger Updated"),
            @ApiResponse(code = 404, message = "No Trigger Found"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public void updateTrigger(
            @Suspended
            final AsyncResponse response,
            @ApiParam(value = "Trigger definition id to be updated",
                    required = true)
            @PathParam("triggerId")
            final String triggerId,
            @ApiParam(value = "Updated trigger definition",
                    name = "trigger",
                    required = true)
            final Trigger trigger) {
        try {
            if (triggerId != null && !triggerId.isEmpty() &&
                    trigger != null && trigger.getId() != null &&
                    triggerId.equals(trigger.getId()) &&
                    definitions.getTrigger(triggerId) != null) {
                log.debugf("PUT - updateTrigger - triggerId: %s ", triggerId);
                definitions.updateTrigger(trigger);
                response.resume(Response.status(Response.Status.OK).build());
            } else {
                log.debugf("PUT - updateTrigger - triggerId: %s not found or invalid. ", triggerId);
                Map<String, String> errors = new HashMap<String, String>();
                errors.put("errorMsg", "Trigger ID " + triggerId + " not found or invalid ID");
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

    @DELETE
    @Path("/{triggerId}")
    @ApiOperation(value = "Delete an existing trigger definition")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Trigger Deleted"),
            @ApiResponse(code = 404, message = "No Trigger Found"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public void deleteTrigger(
            @Suspended
            final AsyncResponse response,
            @ApiParam(value = "Trigger definition id to be deleted",
                    required = true)
            @PathParam("triggerId")
            final String triggerId) {
        try {
            if (triggerId != null && !triggerId.isEmpty() && definitions.getTrigger(triggerId) != null) {
                log.debugf("DELETE - deleteTrigger - triggerId: %s ", triggerId);
                definitions.removeTrigger(triggerId);

                response.resume(Response.status(Response.Status.OK).build());
            } else {
                log.debugf("DELETE - deleteTrigger - triggerId: %s not found or invalid. ", triggerId);
                Map<String, String> errors = new HashMap<String, String>();
                errors.put("errorMsg", "Trigger ID " + triggerId + " not found or invalid ID");
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

    @GET
    @Path("/{triggerId}/dampenings")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get all Dampenings for a Trigger (1 Dampening per mode).",
            responseContainer = "Collection<Dampening>",
            response = Dampening.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Dampenings Found"),
            @ApiResponse(code = 204, message = "Success, No Dampenings Found"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public void getTriggerDampenings(
            @Suspended
            final AsyncResponse response,
            @ApiParam(value = "Trigger definition id to be retrieved",
                    required = true)
            @PathParam("triggerId")
            final String triggerId) {
        try {
            Collection<Dampening> dampeningList = definitions.getTriggerDampenings(triggerId, null);
            if (dampeningList.isEmpty()) {
                log.debugf("GET - getTriggerDampenings - Empty");
                response.resume(Response.status(Response.Status.NO_CONTENT).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("GET - getTriggerDampenings - %s conditions ", dampeningList.size());

                response.resume(Response.status(Response.Status.OK)
                        .entity(dampeningList).type(APPLICATION_JSON_TYPE).build());
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
    @Path("/{triggerId}/dampenings/mode/{triggerMode}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get a dampening using triggerId and triggerMode",
            response = Dampening.class,
            notes = "Similar as getDampening(dampeningId)")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Dampening Found"),
            @ApiResponse(code = 204, message = "Success, No Dampening Found"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public void getTriggerModeDampenings(
            @Suspended
            final AsyncResponse response,
            @ApiParam(value = "Trigger definition id to be retrieved",
                    required = true)
            @PathParam("triggerId")
            final String triggerId,
            @ApiParam(value = "Trigger mode",
                    required = true)
            @PathParam("triggerMode")
            final Trigger.Mode triggerMode) {
        try {
            Collection<Dampening> dampeningList = definitions.getTriggerDampenings(triggerId, triggerMode);
            if (dampeningList.isEmpty()) {
                log.debugf("GET - getTriggerDampenings - Empty");
                response.resume(Response.status(Response.Status.NO_CONTENT).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("GET - getTriggerDampenings - %s dampenings ", dampeningList.size());
                Dampening first = dampeningList.iterator().next();
                response.resume(Response.status(Response.Status.OK)
                        .entity(first).type(APPLICATION_JSON_TYPE).build());
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
    @Path("/{triggerId}/dampenings/{dampeningId}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get an existing dampening",
            response = Dampening.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Dampening Found"),
            @ApiResponse(code = 404, message = "No Dampening Found"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public void getDampening(
            @Suspended
            final AsyncResponse response,
            @ApiParam(value = "Trigger definition id to be retrieved",
                    required = true)
            @PathParam("triggerId")
            final String triggerId,
            @ApiParam(value = "Dampening id",
                    required = true)
            @PathParam("dampeningId")
            final String dampeningId) {
        try {
            Dampening found = null;
            if (dampeningId != null && !dampeningId.isEmpty() && dampeningId.startsWith(triggerId)) {
                found = definitions.getDampening(dampeningId);
            }
            if (found != null) {
                log.debugf("GET - getDampening - dampeningId: %s ", found.getDampeningId());
                response.resume(Response.status(Response.Status.OK).entity(found).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("GET - getDampening - dampeningId: %s not found or invalid. ", dampeningId);
                Map<String, String> errors = new HashMap<String, String>();
                errors.put("errorMsg", "Dampening ID " + dampeningId + " not found or invalid ID");
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

    @POST
    @Path("/{triggerId}/dampenings")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Create a new dampening",
            response = Dampening.class,
            notes = "Returns Dampening created if operation finished correctly")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Dampening Created"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public void createDampening(
            @Suspended
            final AsyncResponse response,
            @ApiParam(value = "Trigger definition id attached to dampening",
                    required = true)
            @PathParam("triggerId")
            final String triggerId,
            @ApiParam(value = "Dampening definition to be created",
                    name = "dampening",
                    required = true)
            final Dampening dampening) {
        try {
            if (dampening != null && dampening.getTriggerId() != null &&
                    triggerId != null && dampening.getTriggerId().equals(triggerId) &&
                    definitions.getDampening(dampening.getDampeningId()) == null) {

                // make sure we have the best chance of clean data..
                Dampening d = getCleanDampening(dampening);
                log.debugf("POST - createDampening - triggerId %s ", d.getTriggerId());
                definitions.addDampening(d);

                response.resume(Response.status(Response.Status.OK)
                        .entity(d).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("POST - createDampening - ID not valid or existing dampening");
                Map<String, String> errors = new HashMap<String, String>();
                errors.put("errorMsg", "Existing dampening or invalid ID");
                response.resume(Response.status(Response.Status.BAD_REQUEST)
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

    private Dampening getCleanDampening(Dampening dampening) throws Exception {
        switch (dampening.getType()) {
            case STRICT:
                return Dampening.forStrict(dampening.getTriggerId(), dampening.getTriggerMode(),
                        dampening.getEvalTrueSetting());

            case STRICT_TIME:
                return Dampening.forStrictTime(dampening.getTriggerId(), dampening.getTriggerMode(),
                        dampening.getEvalTimeSetting());

            case STRICT_TIMEOUT:
                return Dampening.forStrictTimeout(dampening.getTriggerId(), dampening.getTriggerMode(),
                        dampening.getEvalTimeSetting());
            case RELAXED_COUNT:
                return Dampening.forRelaxedCount(dampening.getTriggerId(), dampening.getTriggerMode(),
                        dampening.getEvalTrueSetting(),
                        dampening.getEvalTotalSetting());
            case RELAXED_TIME:
                return Dampening.forRelaxedTime(dampening.getTriggerId(), dampening.getTriggerMode(),
                        dampening.getEvalTrueSetting(), dampening.getEvalTimeSetting());

            default:
                throw new Exception("Unhandled Dampening Type: " + dampening.toString());
        }
    }

    @PUT
    @Path("/{triggerId}/dampenings/{dampeningId}")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Update an existing dampening definition")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Dampening Updated"),
            @ApiResponse(code = 404, message = "No Dampening Found"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public void updateDampening(
            @Suspended
            final AsyncResponse response,
            @ApiParam(value = "Trigger definition id to be retrieved", required = true)
            @PathParam("triggerId")
            final String triggerId,
            @ApiParam(value = "Dampening id", required = true)
            @PathParam("dampeningId")
            final String dampeningId,
            @ApiParam(value = "Updated dampening definition", name = "dampening", required = true)
            final Dampening dampening) {
        try {
            if (dampeningId != null && !dampeningId.isEmpty() &&
                    dampening != null && dampening.getDampeningId() != null &&
                    dampeningId.equals(dampening.getDampeningId()) &&
                    dampeningId.startsWith(triggerId) &&
                    definitions.getDampening(dampeningId) != null) {

                log.debugf("PUT - updateDampening - dampeningId: %s ", dampeningId);

                // make sure we have the best chance of clean data..
                Dampening d = getCleanDampening(dampening);
                definitions.updateDampening(d);

                response.resume(Response.status(Response.Status.OK).build());

            } else {
                log.debugf("PUT - updateDampening - dampeningId: %s not found or invalid. ", dampeningId);
                Map<String, String> errors = new HashMap<String, String>();
                errors.put("errorMsg", "Dampening ID " + dampeningId + " not found or invalid ID");
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

    @DELETE
    @Path("/{triggerId}/dampenings/{dampeningId}")
    @ApiOperation(value = "Delete an existing dampening definition")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Dampening Deleted"),
            @ApiResponse(code = 404, message = "No Dampening Found"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public void deleteDampening(
            @Suspended
            final AsyncResponse response,
            @ApiParam(value = "Trigger definition id to be retrieved",
                    required = true)
            @PathParam("triggerId")
            final String triggerId,
            @ApiParam(value = "Dampening id for dampening definition to be deleted", required = true)
            @PathParam("dampeningId")
            final String dampeningId) {
        try {
            if (dampeningId != null && !dampeningId.isEmpty() &&
                    definitions.getDampening(dampeningId) != null &&
                    dampeningId.startsWith(triggerId)) {
                log.debugf("DELETE - deleteDampening - dampeningId: %s ", dampeningId);
                definitions.removeDampening(dampeningId);

                response.resume(Response.status(Response.Status.OK).build());
            } else {
                log.debugf("DELETE - deleteDampening - dampeningId: %s not found or invalid ", dampeningId);
                Map<String, String> errors = new HashMap<String, String>();
                errors.put("errorMsg", "Dampening ID " + dampeningId + " not found or invalid ID");
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

    @GET
    @Path("/{triggerId}/conditions")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get a map with all conditions for a specific trigger.",
            responseContainer = "Collection<Condition>",
            response = Condition.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Conditions Found"),
            @ApiResponse(code = 204, message = "Success, No Conditions Found"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public void getTriggerConditions(@Suspended
    final AsyncResponse response,
            @ApiParam(value = "Trigger definition id to be retrieved",
                    required = true)
            @PathParam("triggerId")
            final String triggerId) {
        try {
            Collection<Condition> conditionsList = definitions.getTriggerConditions(triggerId, null);
            if (conditionsList.isEmpty()) {
                log.debugf("GET - getTriggerConditions - Empty");
                response.resume(Response.status(Response.Status.NO_CONTENT).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("GET - getTriggerConditions - %s conditions ", conditionsList.size());

                response.resume(Response.status(Response.Status.OK)
                        .entity(conditionsList).type(APPLICATION_JSON_TYPE).build());
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
    @Path("/{triggerId}/conditions/{conditionId}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get a condition for a specific trigger id.",
            response = Condition.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Condition Found"),
            @ApiResponse(code = 404, message = "No Condition Found"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public void getTriggerCondition(
            @Suspended
            final AsyncResponse response,
            @ApiParam(value = "Trigger definition id to be retrieved", required = true)
            @PathParam("triggerId")
            final String triggerId,
            @PathParam("conditionId")
            final String conditionId) {
        try {
            Condition condition = null;
            if (conditionId.startsWith(triggerId)) {
                condition = definitions.getCondition(conditionId);
            }
            if (condition == null) {
                log.debugf("GET - getTriggerCondition - Empty");
                response.resume(Response.status(Response.Status.NOT_FOUND).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("GET - getTriggerCondition - %s condition ", conditionId);
                response.resume(Response.status(Response.Status.OK)
                        .entity(condition).type(APPLICATION_JSON_TYPE).build());
            }
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            Map<String, String> errors = new HashMap<String, String>();
            errors.put("errorMsg", "Internal Error: " + e.getMessage());
            response.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errors).type(APPLICATION_JSON_TYPE).build());
        }
    }

    @POST
    @Path("/{triggerId}/conditions")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Create a new condition for a specific trigger",
            responseContainer = "Collection",
            response = Condition.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Condition Created"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public void createCondition(
            @Suspended
            final AsyncResponse response,
            @ApiParam(value = "Trigger definition id to be retrieved",
                    required = true)
            @PathParam("triggerId")
            final String triggerId,
            @ApiParam(value = "Json representation of a condition. For examples of Condition types, See "
                    + "https://github.com/hawkular/hawkular-alerts/blob/master/hawkular-alerts-rest-tests/"
                    + "src/test/groovy/org/hawkular/alerts/rest/ConditionsITest.groovy")
            String jsonCondition) {
        try {
            if (jsonCondition == null || jsonCondition.isEmpty() || !jsonCondition.contains("type")) {
                log.debugf("POST - createCondition - json condition empty or without type");
                Map<String, String> errors = new HashMap<String, String>();
                errors.put("errorMsg", "Condition empty or without type");
                response.resume(Response.status(Response.Status.BAD_REQUEST)
                        .entity(errors).type(APPLICATION_JSON_TYPE).build());
            } else {
                Condition.Type conditionType = conditionType(jsonCondition);
                if (conditionType == null) {
                    log.debugf("POST - createCondition - bad type ");
                    Map<String, String> errors = new HashMap<String, String>();
                    errors.put("errorMsg", "Condition with bad type");
                    response.resume(Response.status(Response.Status.BAD_REQUEST)
                            .entity(errors).type(APPLICATION_JSON_TYPE).build());
                } else {
                    Condition condition = null;
                    if (conditionType.equals(Condition.Type.AVAILABILITY)) {
                        condition = objectMapper.readValue(jsonCondition, AvailabilityCondition.class);
                    } else if (conditionType.equals(Condition.Type.COMPARE)) {
                        condition = objectMapper.readValue(jsonCondition, CompareCondition.class);
                    } else if (conditionType.equals(Condition.Type.STRING)) {
                        condition = objectMapper.readValue(jsonCondition, StringCondition.class);
                    } else if (conditionType.equals(Condition.Type.THRESHOLD)) {
                        condition = objectMapper.readValue(jsonCondition, ThresholdCondition.class);
                    } else if (conditionType.equals(Condition.Type.RANGE)) {
                        condition = objectMapper.readValue(jsonCondition, ThresholdRangeCondition.class);
                    }
                    Collection<Condition> newConditions = definitions.addCondition(condition.getTriggerId(),
                            condition.getTriggerMode(),
                            condition);

                    response.resume(Response.status(Response.Status.OK)
                            .entity(newConditions).type(APPLICATION_JSON_TYPE).build());
                }
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
    @Path("/{triggerId}/conditions/{conditionId}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Update an existing condition for a specific trigger")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Condition Updated"),
            @ApiResponse(code = 404, message = "No Condition Found"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public void updateCondition(
            @Suspended
            final AsyncResponse response,
            @ApiParam(value = "Trigger definition id to be retrieved",
                    required = true)
            @PathParam("triggerId")
            final String triggerId,
            @PathParam("conditionId")
            final String conditionId,
            @ApiParam(value = "Json representation of a condition")
            String jsonCondition) {
        try {
            if (jsonCondition == null || jsonCondition.isEmpty() || !jsonCondition.contains("type")) {
                log.debugf("POST - updateCondition - json condition empty or without type");
                Map<String, String> errors = new HashMap<String, String>();
                errors.put("errorMsg", "Condition empty or without type");
                response.resume(Response.status(Response.Status.BAD_REQUEST)
                        .entity(errors).type(APPLICATION_JSON_TYPE).build());
            } else {
                Condition.Type conditionType = conditionType(jsonCondition);
                if (conditionType == null) {
                    log.debugf("POST - createCondition - bad type ");
                    Map<String, String> errors = new HashMap<String, String>();
                    errors.put("errorMsg", "Condition with bad type");
                    response.resume(Response.status(Response.Status.BAD_REQUEST)
                            .entity(errors).type(APPLICATION_JSON_TYPE).build());
                } else {
                    Condition condition = null;
                    if (conditionType.equals(Condition.Type.AVAILABILITY)) {
                        condition = objectMapper.readValue(jsonCondition, AvailabilityCondition.class);
                    } else if (conditionType.equals(Condition.Type.COMPARE)) {
                        condition = objectMapper.readValue(jsonCondition, CompareCondition.class);
                    } else if (conditionType.equals(Condition.Type.STRING)) {
                        condition = objectMapper.readValue(jsonCondition, StringCondition.class);
                    } else if (conditionType.equals(Condition.Type.THRESHOLD)) {
                        condition = objectMapper.readValue(jsonCondition, ThresholdCondition.class);
                    } else if (conditionType.equals(Condition.Type.RANGE)) {
                        condition = objectMapper.readValue(jsonCondition, ThresholdRangeCondition.class);
                    }
                    Condition test = definitions.getCondition(condition.getConditionId());
                    if (test == null) {
                        log.debugf("PUT - updateCondition - Condition " + condition.getConditionId() +
                                " doesn't exist");
                        Map<String, String> errors = new HashMap<String, String>();
                        errors.put("errorMsg", "Condition " + condition.getConditionId() + " doesn't exist");
                        response.resume(Response.status(Response.Status.NOT_FOUND)
                                .entity(errors).type(APPLICATION_JSON_TYPE).build());
                    } else {
                        Collection<Condition> updatedConditions = definitions.updateCondition(condition);

                        response.resume(Response.status(Response.Status.OK)
                                .entity(updatedConditions).type(APPLICATION_JSON_TYPE).build());
                    }
                }
            }
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            Map<String, String> errors = new HashMap<String, String>();
            errors.put("errorMsg", "Internal Error: " + e.getMessage());
            response.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errors).type(APPLICATION_JSON_TYPE).build());
        }
    }

    @DELETE
    @Path("/{triggerId}/conditions/{conditionId}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Delete an existing condition for a specific trigger",
            responseContainer = "Collection<Condition>",
            response = Condition.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Condition Deleted"),
            @ApiResponse(code = 404, message = "No Condition Found"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public void deleteCondition(
            @Suspended
            final AsyncResponse response,
            @ApiParam(value = "Trigger definition id to be retrieved",
                    required = true)
            @PathParam("triggerId")
            final String triggerId,
            @PathParam("conditionId")
            final String conditionId) {
        try {
            Condition test = definitions.getCondition(conditionId);
            if (test == null) {
                log.debugf("POST - deleteCondition - Condition " + conditionId + " not found or invalid");
                Map<String, String> errors = new HashMap<String, String>();
                errors.put("errorMsg", "Condition " + conditionId + " not found or invalid");
                response.resume(Response.status(Response.Status.NOT_FOUND)
                        .entity(errors).type(APPLICATION_JSON_TYPE).build());
            } else {
                Collection<Condition> updatedConditions = definitions.removeCondition(conditionId);

                response.resume(Response.status(Response.Status.OK)
                        .entity(updatedConditions).type(APPLICATION_JSON_TYPE).build());
            }
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            Map<String, String> errors = new HashMap<String, String>();
            errors.put("errorMsg", "Internal Error: " + e.getMessage());
            response.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errors).type(APPLICATION_JSON_TYPE).build());
        }
    }

    private Condition.Type conditionType(String jsonCondition) {
        int startType = jsonCondition.indexOf("\"type\"") + 6;
        int endType = jsonCondition.indexOf(",", startType);
        if (endType == -1) {
            endType = jsonCondition.indexOf("}", startType);
            if (endType == -1) {
                return null;
            }
        }
        String type = jsonCondition.substring(startType, endType);
        startType = type.indexOf('"') + 1;
        endType = type.indexOf('"', startType);
        type = type.substring(startType, endType);
        try {
            return Condition.Type.valueOf(type);
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
        }
        return null;
    }

    @POST
    @Path("/tags")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Create a new trigger tag",
            response = Tag.class,
            notes = "Returns Tag created if operation finished correctly")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Tag Created"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public void createTag(
            @Suspended
            final AsyncResponse response,
            @ApiParam(value = "Tag to be created", name = "tag", required = true)
            final Tag tag) {
        try {
            if (!isEmpty(tag.getTriggerId()) && !isEmpty(tag.getName())) {
                log.debugf("POST - createTag: %s ", tag);
                definitions.addTag(tag);
                response.resume(Response.status(Response.Status.OK)
                        .entity(tag).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("POST - createTag - Invalid Tag, trigger-id, name required");
                Map<String, String> errors = new HashMap<String, String>();
                errors.put("errorMsg", "Invalid Tag, no trigger or missing required field");
                response.resume(Response.status(Response.Status.BAD_REQUEST)
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

    @POST
    @Path("/{triggerId}/tags")
    @ApiOperation(value = "Delete existing Tags from a Trigger")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Tags Deleted"),
            @ApiResponse(code = 404, message = "No Trigger Found"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public void deleteTags(
            @Suspended
            final AsyncResponse response,
            @ApiParam(value = "Trigger id of tags to be deleted", required = true)
            @PathParam("triggerId")
            final String triggerId,
            @ApiParam(value = "Category of tags to be deleted", required = false)
            @QueryParam("category")
            final String category,
            @ApiParam(value = "Name of tags to be deleted", required = false)
            @QueryParam("name")
            final String name) {
        try {
            if (!isEmpty(triggerId)) {
                log.debugf("DELETE - deleteTags - triggerId: %s, category: %s, name: %s ", triggerId, category, name);
                definitions.removeTags(triggerId, category, name);
                response.resume(Response.status(Response.Status.OK).build());
            } else {
                log.debugf("DELETE - deleteTags - triggerId: %s not found or invalid. ", triggerId);
                Map<String, String> errors = new HashMap<String, String>();
                errors.put("errorMsg", "Trigger ID " + triggerId + " not found or invalid ID");
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

    @GET
    @Path("/{triggerId}/tags")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get tags for a trigger.",
            responseContainer = "Collection<Tag>",
            response = Tag.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Tags Found"),
            @ApiResponse(code = 204, message = "Success, No Tags Found"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public void getTriggerTags(
            @Suspended
            final AsyncResponse response,
            @ApiParam(value = "Trigger id for the retrieved Tags", required = true)
            @PathParam("triggerId")
            final String triggerId,
            @ApiParam(value = "Category of tags to be retrieved", required = false)
            @QueryParam("category")
            final String category) {
        try {
            Collection<Tag> tagsList = definitions.getTriggerTags(triggerId, category);
            if (tagsList.isEmpty()) {
                log.debugf("GET - getTriggerTags - Empty");
                response.resume(Response.status(Response.Status.NO_CONTENT).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("GET - getTriggerTags - %s tags ", tagsList.size());

                response.resume(Response.status(Response.Status.OK).entity(tagsList).type(APPLICATION_JSON_TYPE)
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

    private boolean isEmpty(String s) {
        return null == s || s.trim().isEmpty();
    }

}
