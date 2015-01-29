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

import org.hawkular.alerts.api.model.trigger.Trigger;
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
 * REST endpoint for triggers
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Path("/triggers")
public class TriggersHandler {
    private static final Logger log = LoggerFactory.getLogger(TriggersHandler.class);
    private boolean debug = false;

    @EJB
    DefinitionsService definitions;

    public TriggersHandler() {
        if (log.isDebugEnabled()) {
            log.debug("Creating instance.");
            debug = true;
        }
    }

    @GET
    @Path("/")
    @Produces(APPLICATION_JSON)
    public void findAllTriggers(@Suspended final AsyncResponse response) {
        Collection<Trigger> triggerList = definitions.getTriggers();
        if (triggerList.isEmpty()) {
            if (debug) {
                log.debug("GET - findAllTriggers - Empty");
            }
            response.resume(Response.status(Response.Status.NO_CONTENT).type(APPLICATION_JSON_TYPE).build());
        } else {
            if (debug) {
                log.debug("GET - findAllTriggers - " + triggerList.size() + " triggers");
            }
            response.resume(Response.status(Response.Status.OK)
                    .entity(triggerList).type(APPLICATION_JSON_TYPE).build());
        }
    }

    @POST
    @Path("/")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public void createTrigger(@Suspended final AsyncResponse response, final Trigger trigger) {
        if (trigger != null && trigger.getId() != null && definitions.getTrigger(trigger.getId()) == null) {
            if (debug) {
                log.debug("POST - createTrigger - triggerId " + trigger.getId());
            }
            definitions.addTrigger(trigger);
            response.resume(Response.status(Response.Status.OK).entity(trigger).type(APPLICATION_JSON_TYPE).build());
        } else {
            if (debug) {
                log.debug("POST - createTrigger - ID not valid or existing trigger");
            }
            Map<String, String> errors = new HashMap<String, String>();
            errors.put("errorMsg", "Existing trigger or invalid ID");
            response.resume(Response.status(Response.Status.BAD_REQUEST)
                    .entity(errors).type(APPLICATION_JSON_TYPE).build());
        }
    }

    @GET
    @Path("/{triggerId}")
    @Produces(APPLICATION_JSON)
    public void getTrigger(@Suspended final AsyncResponse response, @PathParam("triggerId") final String triggerId) {
        Trigger found = null;
        if (triggerId != null && !triggerId.isEmpty()) {
            found = definitions.getTrigger(triggerId);
        }
        if (found != null) {
            if (debug) {
                log.debug("GET - getTrigger - triggerId: " + found.getId());
            }
            response.resume(Response.status(Response.Status.OK).entity(found).type(APPLICATION_JSON_TYPE).build());
        } else {
            if (debug) {
                log.debug("GET - getTrigger - triggerId : " + triggerId + " not found or invalid. ");
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
    public void updateTrigger(@Suspended final AsyncResponse response, @PathParam("triggerId") final String triggerId,
                              final Trigger trigger) {
        if (triggerId != null && !triggerId.isEmpty() &&
                trigger != null && trigger.getId() != null &&
                triggerId.equals(trigger.getId()) &&
                definitions.getTrigger(triggerId) != null) {
            if (debug) {
                log.debug("PUT - updateTrigger - triggerId: " + triggerId);
            }
            definitions.removeTrigger(triggerId);
            definitions.addTrigger(trigger);
            response.resume(Response.status(Response.Status.OK).build());
        } else {
            log.debug("PUT - updateTrigger - triggerId: " + triggerId + " not found or invalid. ");
            Map<String, String> errors = new HashMap<String, String>();
            errors.put("errorMsg", "Trigger ID " + triggerId + " not found or invalid ID");
            response.resume(Response.status(Response.Status.NOT_FOUND)
                    .entity(errors).type(APPLICATION_JSON_TYPE).build());
        }
    }

    @DELETE
    @Path("/{triggerId}")
    public void deleteTrigger(@Suspended final AsyncResponse response, @PathParam("triggerId") final String triggerId) {
        if (triggerId != null && !triggerId.isEmpty() && definitions.getTrigger(triggerId) != null) {
            if (debug) {
                log.debug("DELETE - deleteTrigger - triggerId: " + triggerId);
            }
            definitions.removeTrigger(triggerId);
            response.resume(Response.status(Response.Status.OK).build());
        } else {
            if (debug) {
                log.debug("DELETE - deleteTrigger - triggerId: " + triggerId + " not found or invalid. ");
            }
            Map<String, String> errors = new HashMap<String, String>();
            errors.put("errorMsg", "Trigger ID " + triggerId + " not found or invalid ID");
            response.resume(Response.status(Response.Status.NOT_FOUND)
                    .entity(errors).type(APPLICATION_JSON_TYPE).build());
        }
    }

}
