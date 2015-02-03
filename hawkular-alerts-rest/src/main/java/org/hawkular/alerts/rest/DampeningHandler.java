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

import org.hawkular.alerts.api.model.dampening.Dampening;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;

/**
 * REST endpoint for string conditions.
 *
 * @author Lucas Ponce
 */
@Path("/trigger/dampening")
public class DampeningHandler {
    private static final Logger log = LoggerFactory.getLogger(DampeningHandler.class);
    private boolean debug = false;

    @EJB
    DefinitionsService definitions;

    public DampeningHandler() {
        if (log.isDebugEnabled()) {
            log.debug("Creating instance.");
            debug = true;
        }
    }

    @GET
    @Path("/")
    @Produces(APPLICATION_JSON)
    public void findAllDampenings(@Suspended final AsyncResponse response) {
        Collection<Dampening> dampeningList = definitions.getDampenings();
        if (dampeningList.isEmpty()) {
            if (debug) {
                log.debug("GET - findAllDampenings - Empty");
            }
            response.resume(Response.status(Response.Status.NO_CONTENT).type(APPLICATION_JSON_TYPE).build());
        } else {
            if (debug) {
                log.debug("GET - findAllDampenings - " + dampeningList.size() +
                          " compare conditions");
            }
            response.resume(Response.status(Response.Status.OK)
                    .entity(dampeningList).type(APPLICATION_JSON_TYPE).build());
        }
    }

    @POST
    @Path("/")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public void createDampening(@Suspended final AsyncResponse response,
                                final Dampening dampening) {
        if (dampening != null && dampening.getTriggerId() != null
                && definitions.getDampening(dampening.getTriggerId()) == null) {
            if (debug) {
                log.debug("POST - createDampening - triggerId " + dampening.getTriggerId());
            }
            definitions.addDampening(dampening);
            response.resume(Response.status(Response.Status.OK).entity(dampening).type(APPLICATION_JSON_TYPE).build());
        } else {
            if (debug) {
                log.debug("POST - createDampening - ID not valid or existing dampening");
            }
            Map<String, String> errors = new HashMap<String, String>();
            errors.put("errorMsg", "Existing dampening or invalid ID");
            response.resume(Response.status(Response.Status.BAD_REQUEST)
                    .entity(errors).type(APPLICATION_JSON_TYPE).build());
        }
    }

    @GET
    @Path("/{triggerId}")
    @Produces(APPLICATION_JSON)
    public void getDampening(@Suspended final AsyncResponse response,
                             @PathParam("triggerId") final String triggerId) {
        Dampening found = null;
        if (triggerId != null && !triggerId.isEmpty()) {
            found = definitions.getDampening(triggerId);
        }
        if (found != null) {
            if (debug) {
                log.debug("GET - getDampening - triggerId: " + found.getTriggerId());
            }
            response.resume(Response.status(Response.Status.OK).entity(found).type(APPLICATION_JSON_TYPE).build());
        } else {
            if (debug) {
                log.debug("GET - getDampening - triggerId: " + triggerId + " not found or invalid. ");
            }
            Map<String, String> errors = new HashMap<String, String>();
            errors.put("errorMsg", "Trigger ID " + triggerId + " not found or invalid ID");
            response.resume(Response.status(Response.Status.NOT_FOUND)
                    .entity(errors).type(APPLICATION_JSON_TYPE).build());
        }
    }

    @PUT
    @Path("/{triggerId}")
    @Consumes(APPLICATION_JSON)
    public void updateDampening(@Suspended final AsyncResponse response,
                                @PathParam("triggerId") final String triggerId,
                                final Dampening dampening) {
        if (triggerId != null && !triggerId.isEmpty() &&
                dampening != null && dampening.getTriggerId() != null &&
                triggerId.equals(dampening.getTriggerId()) &&
                definitions.getDampening(triggerId) != null) {
            if (debug) {
                log.debug("PUT - updateDampening - triggerId: " + triggerId);
            }
            definitions.updateDampening(dampening);
            response.resume(Response.status(Response.Status.OK).build());
        } else {
            log.debug("PUT - updateDampening - triggerId: " + triggerId + " not found or invalid. ");
            Map<String, String> errors = new HashMap<String, String>();
            errors.put("errorMsg", "Trigger ID " + triggerId + " not found or invalid ID");
            response.resume(Response.status(Response.Status.NOT_FOUND)
                    .entity(errors).type(APPLICATION_JSON_TYPE).build());
        }
    }

    @DELETE
    @Path("/{triggerId}")
    public void deleteDampening(@Suspended final AsyncResponse response,
                                @PathParam("triggerId") final String triggerId) {
        if (triggerId != null && !triggerId.isEmpty() && definitions.getDampening(triggerId) != null) {
            if (debug) {
                log.debug("DELETE - deleteDampening - triggerId: " + triggerId);
            }
            definitions.removeDampening(triggerId);
            response.resume(Response.status(Response.Status.OK).build());
        } else {
            if (debug) {
                log.debug("DELETE - deleteDampening - triggerId: " + triggerId + " not found or " +
                          "invalid. ");
            }
            Map<String, String> errors = new HashMap<String, String>();
            errors.put("errorMsg", "Trigger ID " + triggerId + " not found or invalid ID");
            response.resume(Response.status(Response.Status.NOT_FOUND)
                    .entity(errors).type(APPLICATION_JSON_TYPE).build());
        }
    }
}
