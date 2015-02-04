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

import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.condition.ThresholdRangeCondition;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;

/**
 * REST endpoint for threshold range conditions.
 *
 * @author Lucas Ponce
 */
@Path("/conditions/range")
public class ThresholdRangeConditionsHandler {
    private static final Logger log = LoggerFactory.getLogger(ThresholdRangeConditionsHandler.class);
    private boolean debug = false;

    @EJB
    DefinitionsService definitions;

    public ThresholdRangeConditionsHandler() {
        if (log.isDebugEnabled()) {
            log.debug("Creating instance.");
            debug = true;
        }
    }

    @GET
    @Path("/")
    @Produces(APPLICATION_JSON)
    public void findAllThresholdRangeConditions(@Suspended final AsyncResponse response) {
        Collection<Condition> conditionsList = definitions.getConditions();
        Collection<ThresholdRangeCondition> rangeConditions = new ArrayList<ThresholdRangeCondition>();
        for (Condition cond : conditionsList) {
            if (cond instanceof ThresholdRangeCondition) {
                rangeConditions.add((ThresholdRangeCondition)cond);
            }
        }
        if (rangeConditions.isEmpty()) {
            if (debug) {
                log.debug("GET - findAllThresholdRangeConditions - Empty");
            }
            response.resume(Response.status(Response.Status.NO_CONTENT).type(APPLICATION_JSON_TYPE).build());
        } else {
            if (debug) {
                log.debug("GET - findAllThresholdRangeConditions - " + rangeConditions.size() +
                          " compare conditions");
            }
            response.resume(Response.status(Response.Status.OK)
                    .entity(rangeConditions).type(APPLICATION_JSON_TYPE).build());
        }
    }

    @POST
    @Path("/")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public void createThresholdRangeCondition(@Suspended final AsyncResponse response,
                                              final ThresholdRangeCondition condition) {
        if (condition != null && condition.getConditionId() != null
                && definitions.getCondition(condition.getConditionId()) == null) {
            if (debug) {
                log.debug("POST - createThresholdRangeCondition - conditionId " + condition.getConditionId());
            }
            definitions.addCondition(condition);
            response.resume(Response.status(Response.Status.OK).entity(condition).type(APPLICATION_JSON_TYPE).build());
        } else {
            if (debug) {
                log.debug("POST - createThresholdRangeCondition - ID not valid or existing condition");
            }
            Map<String, String> errors = new HashMap<String, String>();
            errors.put("errorMsg", "Existing condition or invalid ID");
            response.resume(Response.status(Response.Status.BAD_REQUEST)
                    .entity(errors).type(APPLICATION_JSON_TYPE).build());
        }
    }

    @GET
    @Path("/{conditionId}")
    @Produces(APPLICATION_JSON)
    public void getThresholdRangeCondition(@Suspended final AsyncResponse response,
                                           @PathParam("conditionId") final String conditionId) {
        ThresholdRangeCondition found = null;
        if (conditionId != null && !conditionId.isEmpty()) {
            Condition c = definitions.getCondition(conditionId);
            if (c instanceof ThresholdRangeCondition) {
                found = (ThresholdRangeCondition)c;
            } else {
                if (debug) {
                    log.debug("GET - getThresholdRangeCondition - conditionId: " + found.getConditionId() + " found " +
                              "but not instance of ThresholdRangeCondition class");
                }
            }
        }
        if (found != null) {
            if (debug) {
                log.debug("GET - getThresholdRangeCondition - conditionId: " + found.getConditionId());
            }
            response.resume(Response.status(Response.Status.OK).entity(found).type(APPLICATION_JSON_TYPE).build());
        } else {
            if (debug) {
                log.debug("GET - getThresholdRangeCondition - conditionId: " + conditionId + " not found or invalid. ");
            }
            Map<String, String> errors = new HashMap<String, String>();
            errors.put("errorMsg", "Condition ID " + conditionId + " not found or invalid ID");
            response.resume(Response.status(Response.Status.NOT_FOUND)
                    .entity(errors).type(APPLICATION_JSON_TYPE).build());
        }
    }

    @PUT
    @Path("/{conditionId}")
    @Consumes(APPLICATION_JSON)
    public void updateThresholdRangeCondition(@Suspended final AsyncResponse response,
                                              @PathParam("conditionId") final String conditionId,
                                              final ThresholdRangeCondition condition) {
        if (conditionId != null && !conditionId.isEmpty() &&
                condition != null && condition.getConditionId() != null &&
                conditionId.equals(condition.getConditionId()) &&
                definitions.getCondition(conditionId) != null) {
            if (debug) {
                log.debug("PUT - updateThresholdRangeCondition - conditionId: " + conditionId);
            }
            definitions.updateCondition(condition);
            response.resume(Response.status(Response.Status.OK).build());
        } else {
            log.debug("PUT - updateThresholdRangeCondition - conditionId: " + conditionId + " not found or invalid. ");
            Map<String, String> errors = new HashMap<String, String>();
            errors.put("errorMsg", "Condition ID " + conditionId + " not found or invalid ID");
            response.resume(Response.status(Response.Status.NOT_FOUND)
                    .entity(errors).type(APPLICATION_JSON_TYPE).build());
        }
    }

    @DELETE
    @Path("/{conditionId}")
    public void deleteThresholdRangeCondition(@Suspended final AsyncResponse response,
                                              @PathParam("conditionId") final String conditionId) {
        if (conditionId != null && !conditionId.isEmpty() && definitions.getCondition(conditionId) != null) {
            if (debug) {
                log.debug("DELETE - deleteThresholdRangeCondition - conditionId: " + conditionId);
            }
            definitions.removeCondition(conditionId);
            response.resume(Response.status(Response.Status.OK).build());
        } else {
            if (debug) {
                log.debug("DELETE - deleteThresholdRangeCondition - conditionId: " + conditionId + " not found or " +
                          "invalid. ");
            }
            Map<String, String> errors = new HashMap<String, String>();
            errors.put("errorMsg", "Condition ID " + conditionId + " not found or invalid ID");
            response.resume(Response.status(Response.Status.NOT_FOUND)
                    .entity(errors).type(APPLICATION_JSON_TYPE).build());
        }
    }
}
