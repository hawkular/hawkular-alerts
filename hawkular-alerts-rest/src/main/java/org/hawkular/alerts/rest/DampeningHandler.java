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
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Response;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;

import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.services.DefinitionsService;

import org.jboss.logging.Logger;

// TODO: Need to fully support Trigger.Mode in the API.

/**
 * REST endpoint for string conditions.
 *
 * @author Lucas Ponce
 */
@Path("/trigger/dampening")
@Api(value = "/trigger/dampening",
     description = "Create/Read/Update/Delete operations for dampenings definitions")
public class DampeningHandler {
    private final Logger log = Logger.getLogger(DampeningHandler.class);

    @EJB
    DefinitionsService definitions;

    public DampeningHandler() {
        log.debugf("Creating instance.");
    }

    @GET
    @Path("/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Find all dampenings",
                  responseContainer = "Collection",
                  response = Dampening.class,
                  notes = "Pagination is not yet implemented")
    public void findAllDampenings(@Suspended final AsyncResponse response) {
        try {
            Collection<Dampening> dampeningList = definitions.getAllDampenings();
            if (dampeningList.isEmpty()) {
                log.debugf("GET - findAllDampenings - Empty");
                response.resume(Response.status(Response.Status.NO_CONTENT).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("GET - findAllDampenings - %s compare conditions. ", dampeningList.size());
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

    @POST
    @Path("/")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Create a new dampening",
                  response = Dampening.class,
                  notes = "Returns Dampening created if operation finished correctly")
    public void createDampening(@Suspended final AsyncResponse response,
                                @ApiParam(value = "Dampening definition to be created",
                                          name = "dampening",
                                          required = true)
                                final Dampening dampening) {
        try {
            if (dampening != null && dampening.getTriggerId() != null
                    && definitions.getDampening(dampening.getDampeningId()) == null) {
                log.debugf("POST - createDampening - triggerId %s ", dampening.getTriggerId());
                definitions.addDampening(dampening);
                response.resume(Response.status(Response.Status.OK)
                        .entity(dampening).type(APPLICATION_JSON_TYPE).build());
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

    @GET
    @Path("/{dampeningId}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get an existing dampening",
                  response = Dampening.class)
    public void getDampening(@Suspended final AsyncResponse response,
                             @ApiParam(value = "Dampening id",
                                       required = true)
                             @PathParam("dampeningId") final String dampeningId) {
        try {
            Dampening found = null;
            if (dampeningId != null && !dampeningId.isEmpty()) {
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

    @PUT
    @Path("/{dampeningId}")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Update an existing dampening definition")
    public void updateDampening(@Suspended final AsyncResponse response,
                                @ApiParam(value = "Dampening id",
                                          required = true)
                                @PathParam("dampeningId") final String dampeningId,
                                @ApiParam(value = "Updated dampening definition",
                                          name = "dampening",
                                          required = true)
                                final Dampening dampening) {
        try {
            if (dampeningId != null && !dampeningId.isEmpty() &&
                    dampening != null && dampening.getDampeningId() != null &&
                    dampeningId.equals(dampening.getDampeningId()) &&
                    definitions.getDampening(dampeningId) != null) {
                log.debugf("PUT - updateDampening - dampeningId: %s ", dampeningId);
                definitions.updateDampening(dampening);
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
    @Path("/{dampeningId}")
    @ApiOperation(value = "Delete an existing dampening definition")
    public void deleteDampening(
            @Suspended final AsyncResponse response,
            @ApiParam(value = "Dampening id for dampening definition to be deleted", required = true)//
            @PathParam("dampeningId") final String dampeningId) {
        try {
            if (dampeningId != null && !dampeningId.isEmpty()
                    && definitions.getDampening(dampeningId) != null) {
                log.debugf("DELETE - deleteDampening - dampeningId: %s ", dampeningId);
                definitions.removeDampening(dampeningId);
                response.resume(Response.status(Response.Status.OK).build());
            } else {
                log.debugf("DELETE - deleteDampening - dampeningId: %s not found or invalid ", dampeningId);
                Map<String, String> errors = new HashMap<String, String>();
                errors.put("errorMsg", "Trigger ID " + dampeningId + " not found or invalid ID");
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
