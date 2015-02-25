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
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Response;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;

import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.CompareCondition;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.services.DefinitionsService;

import org.jboss.logging.Logger;

/**
 * REST endpoint for availability conditions.
 *
 * @author Lucas Ponce
 */
@Path("/conditions/availability")
@Api(value = "/conditions/availability",
     description = "Create/Read/Update/Delete operations for AvailabilityCondition type condition")
public class AvailabilityConditionsHandler {
    private final Logger log = Logger.getLogger(AvailabilityConditionsHandler.class);

    @EJB
    DefinitionsService definitions;

    public AvailabilityConditionsHandler() {
        log.debugf("Creating instance.");
    }

    @GET
    @Path("/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Find all availability conditions",
                  responseClass = "Collection<org.hawkular.alerts.api.model.condition.AvailabilityCondition>",
                  notes = "Pagination is not yet implemented")
    public void findAllAvailabilityConditions(@Suspended final AsyncResponse response) {
        try {
            Collection<Condition> conditionsList = definitions.getAllConditions();
            Collection<AvailabilityCondition> availabilityConditions = new ArrayList<AvailabilityCondition>();
            for (Condition cond : conditionsList) {
                if (cond instanceof AvailabilityCondition) {
                    availabilityConditions.add((AvailabilityCondition) cond);
                }
            }
            if (availabilityConditions.isEmpty()) {
                log.debugf("GET - findAllAvailabilityConditions - Empty");
                response.resume(Response.status(Response.Status.NO_CONTENT).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("GET - findAllAvailabilityConditions - %s availability conditions ",
                        availabilityConditions.size());

                response.resume(Response.status(Response.Status.OK)
                        .entity(availabilityConditions).type(APPLICATION_JSON_TYPE).build());
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
    @Path("/trigger/{triggerId}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Find all availability conditions for a specific trigger",
                  responseClass = "Collection<org.hawkular.alerts.api.model.condition.AvailabilityCondition>",
                  notes = "Pagination is not yet implemented")
    public void findAllAvailabilityConditionsByTrigger(@Suspended final AsyncResponse response,
                                                       @ApiParam(value = "Trigger id to get availability conditions",
                                                                 required = true)
                                                       @PathParam("triggerId") final String triggerId) {
        try {
            Collection<Condition> conditionsList = definitions.getTriggerConditions(triggerId, null);
            Collection<AvailabilityCondition> availabilityConditions = new ArrayList<AvailabilityCondition>();
            for (Condition cond : conditionsList) {
                if (cond instanceof AvailabilityCondition) {
                    availabilityConditions.add((AvailabilityCondition) cond);
                }
            }
            if (availabilityConditions.isEmpty()) {
                log.debugf("GET - findAllAvailabilityConditions - Empty");
                response.resume(Response.status(Response.Status.NO_CONTENT).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("GET - findAllAvailabilityConditions - %s availability conditions ",
                           availabilityConditions.size());

                response.resume(Response.status(Response.Status.OK)
                                        .entity(availabilityConditions).type(APPLICATION_JSON_TYPE).build());
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
    @ApiOperation(value = "Create a new availability condition",
                  responseClass = "org.hawkular.alerts.api.model.condition.AvailabilityCondition",
                  notes = "Returns AvailabilityCondition created if operation finished correctly")
    public void createAvailabilityCondition(@Suspended final AsyncResponse response,
            @ApiParam(value = "Trigger id for new availability condition",
                    required = true) @PathParam("triggerId") final String triggerId,
            @ApiParam(value = "Trigger mode for new availability condition",
                    required = true) @PathParam("triggerMode") final String triggerMode,
            @ApiParam(value = "Availability condition to be created",
                    name = "condition",
                    required = true) final CompareCondition condition) {
        try {
            if (condition != null && condition.getConditionId() != null
                    && definitions.getCondition(condition.getConditionId()) == null) {
                log.debugf("POST - createAvailabilityCondition - conditionId: %s ", condition.getConditionId());
                definitions.addCondition(triggerId, Trigger.Mode.valueOf(triggerMode), condition);
                response.resume(Response.status(Response.Status.OK)
                        .entity(condition).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("POST - createAvailabilityCondition - ID not valid or existing condition");
                Map<String, String> errors = new HashMap<String, String>();
                errors.put("errorMsg", "Existing condition or invalid ID");
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
    @Path("/{conditionId}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get an existing availability condition",
                  responseClass = "org.hawkular.alerts.api.model.condition.AvailabilityCondition")
    public void getAvailabilityCondition(@Suspended final AsyncResponse response,
                                         @ApiParam(value = "Availability condition id to be retrieved",
                                                   required = true)
                                         @PathParam("conditionId") final String conditionId) {
        try {
            AvailabilityCondition found = null;
            if (conditionId != null && !conditionId.isEmpty()) {
                Condition c = definitions.getCondition(conditionId);
                if (c instanceof AvailabilityCondition) {
                    found = (AvailabilityCondition) c;
                } else {
                    log.debugf("GET - getAvailabilityCondition - conditionId: %s found " +
                            "but not instance of AvailabilityCondition class", c.getConditionId());
                }
            }
            if (found != null) {
                log.debugf("GET - getAvailabilityCondition - conditionId: ", found.getConditionId());
                response.resume(Response.status(Response.Status.OK).entity(found).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("GET - getAvailabilityCondition - conditionId: %s not found or invalid. ", conditionId);
                Map<String, String> errors = new HashMap<String, String>();
                errors.put("errorMsg", "Condition ID " + conditionId + " not found or invalid ID");
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
    @Path("/{conditionId}")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Update an existing availability condition",
                  responseClass = "void")
    public void updateAvailabilityCondition(@Suspended final AsyncResponse response,
                                            @ApiParam(value = "Availability condition id to be updated",
                                                      required = true)
                                            @PathParam("conditionId") final String conditionId,
                                            @ApiParam(value = "Updated availability condition",
                                                      name = "condition",
                                                      required = true)
                                            final AvailabilityCondition condition) {
        try {
            if (conditionId != null && !conditionId.isEmpty() &&
                    condition != null && condition.getConditionId() != null &&
                    conditionId.equals(condition.getConditionId()) &&
                    definitions.getCondition(conditionId) != null) {
                log.debugf("PUT - updateAvailabilityCondition - conditionId: %s", conditionId);
                definitions.updateCondition(condition);
                response.resume(Response.status(Response.Status.OK).build());
            } else {
                log.debugf("PUT - updateAvailabilityCondition - conditionId: %s not found or invalid. ", conditionId);
                Map<String, String> errors = new HashMap<String, String>();
                errors.put("errorMsg", "Condition ID " + conditionId + " not found or invalid ID");
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
    @Path("/{conditionId}")
    @ApiOperation(value = "Delete an existing availability condition",
                  responseClass = "void")
    public void deleteAvailabilityCondition(@Suspended final AsyncResponse response,
                                            @ApiParam(value = "Availability condition id to be deleted",
                                                      required = true)
                                            @PathParam("conditionId") final String conditionId) {
        try {
            if (conditionId != null && !conditionId.isEmpty() && definitions.getCondition(conditionId) != null) {
                log.debugf("DELETE - deleteAvailabilityCondition - conditionId: %s", conditionId);
                definitions.removeCondition(conditionId);
                response.resume(Response.status(Response.Status.OK).build());
            } else {
                log.debugf("DELETE - deleteAvailabilityCondition - conditionId: %s not found or invalid.", conditionId);
                Map<String, String> errors = new HashMap<String, String>();
                errors.put("errorMsg", "Condition ID " + conditionId + " not found or invalid ID");
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
