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

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.jboss.logging.Logger;

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
 * REST endpoint for Notifiers
 *
 * @author Lucas Ponce
 */
@Path("/notifiers")
@Api(value = "/notifiers",
     description = "Create/Read/Update/Delete operations for notifiers")
public class NotifiersHandler {
    private final Logger log = Logger.getLogger(NotifiersHandler.class);

    @EJB
    DefinitionsService definitions;

    public NotifiersHandler() {
        log.debugf("Creating instance.");
    }

    @GET
    @Path("/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Find all notifiers ids",
                  responseClass = "Collection<String>",
                  notes = "Pagination is not yet implemented")
    public void findAllNotifiers(@Suspended final AsyncResponse response) {
        try {
            Collection<String> notifiers = definitions.getNotifiers();
            if (notifiers == null || notifiers.isEmpty()) {
                log.debugf("GET - findAllNotifiers - Empty");
                response.resume(Response.status(Response.Status.NO_CONTENT).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("GET - findAllNotifiers - %s notifiers ", notifiers);
                response.resume(Response.status(Response.Status.OK)
                        .entity(notifiers).type(APPLICATION_JSON_TYPE).build());
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
    @Path("/type/{notifierType}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Find all notifiers ids of an specific notifier type",
                  responseClass = "Collection<String>",
                  notes = "Pagination is not yet implemented")
    public void findAllNotifiersByType(@Suspended final AsyncResponse response,
                                       @ApiParam(value = "Notifier type to filter query for notifiers ids",
                                                 required = true)
                                       @PathParam("notifierType") final String notifierType) {
        try {
            Collection<String> notifiers = definitions.getNotifiers(notifierType);
            if (notifiers == null || notifiers.isEmpty()) {
                log.debugf("GET - findAllNotifiers - Empty");
                response.resume(Response.status(Response.Status.NO_CONTENT).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("GET - findAllNotifiers - %s notifiers ", notifiers);
                response.resume(Response.status(Response.Status.OK)
                                        .entity(notifiers).type(APPLICATION_JSON_TYPE).build());
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
    @ApiOperation(value = "Create a new notifier",
                  responseClass = "Map<String, String>",
                  notes = "Notifier properties are variable and depends on the notifier type. " +
                          "A user needs to request previously NotifierType API to get the list of properties to fill " +
                          "for a specific type. All notifier should have NotifierId and NotifierType as mandatory " +
                          "properties")
    public void createNotifier(@Suspended final AsyncResponse response,
                               @ApiParam(value = "Notifier properties. Properties depend of specific NotifierType.",
                                         name = "notifierProperties",
                                         required = true)
                               final Map<String, String> notifierProperties) {
        try {
            if (notifierProperties != null && !notifierProperties.isEmpty() &&
                    notifierProperties.containsKey("NotifierId") &&
                    definitions.getNotifier(notifierProperties.get("NotifierId")) == null) {
                String notifierId = notifierProperties.get("NotifierId");
                log.debugf("POST - createNotifier - NotifierId %s - Properties: ", notifierId, notifierProperties);
                definitions.addNotifier(notifierId, notifierProperties);
                response.resume(Response.status(Response.Status.OK)
                        .entity(notifierProperties).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("POST - createNotifier - ID not valid or existing condition");
                Map<String, String> errors = new HashMap<String, String>();
                errors.put("errorMsg", "Existing notifier or invalid ID");
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
    @Path("/{notifierId}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get an existing notifier",
                  responseClass = "Map<String, String>",
                  notes = "Notifier is represented as a map of properties.")
    public void getNotifier(@Suspended final AsyncResponse response,
                            @ApiParam(value = "Notifier id to be retrieved",
                                      required = true)
                            @PathParam("notifierId") final String notifierId) {
        try {
             Map<String, String> notifierProp = definitions.getNotifier(notifierId);
            if (notifierProp == null || notifierProp.isEmpty()) {
                log.debugf("GET - getNotifier - Empty");
                response.resume(Response.status(Response.Status.NO_CONTENT).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("GET - getNotifier - notifierId: %s - properties: %s ",
                        notifierId, notifierProp);
                response.resume(Response.status(Response.Status.OK)
                        .entity(notifierProp).type(APPLICATION_JSON_TYPE).build());
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
    @Path("/{notifierId}")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Update an existing notifier",
                  responseClass = "void",
                  notes = "Notifier properties are variable and depends on the notifier type. " +
                          "A user needs to request previously NotifierType API to get the list of properties to fill " +
                          "for a specific type. All notifier should have NotifierId and NotifierType as mandatory " +
                          "properties")
    public void updateNotifier(@Suspended final AsyncResponse response,
                               @ApiParam(value = "Notifier id to be updated",
                                         required = true)
                               @PathParam("notifierId") final String notifierId,
                               @ApiParam(value = "Notifier properties. Properties depend of specific NotifierType.",
                                         name = "notifierProperties",
                                         required = true)
                               final Map<String, String> notifierProperties) {
        try {
            if (notifierId != null && !notifierId.isEmpty() &&
                    notifierProperties != null && !notifierProperties.isEmpty() &&
                    notifierProperties.containsKey("NotifierId") &&
                    notifierProperties.get("NotifierId").equals(notifierId) &&
                    definitions.getNotifier(notifierId) != null) {
                log.debugf("POST - updateNotifier - notifierId %s - Properties: ", notifierId, notifierProperties);
                definitions.updateNotifier(notifierId, notifierProperties);
                response.resume(Response.status(Response.Status.OK)
                        .entity(notifierProperties).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("PUT - updateNotifier - notifierId: %s not found or invalid. ", notifierId);
                Map<String, String> errors = new HashMap<String, String>();
                errors.put("errorMsg", "Notifier ID " + notifierId + " not found or invalid ID");
                errors.put("errorMsg", "Existing notifier or invalid ID");
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

    @DELETE
    @Path("/{notifierId}")
    @ApiOperation(value = "Delete an existing notifier",
                  responseClass = "void")
    public void deleteNotifier(@Suspended final AsyncResponse response,
                               @ApiParam(value = "Notifier id to be deleted",
                                         required = true)
                               @PathParam("notifierId") final String notifierId) {
        try {
            if (notifierId != null && !notifierId.isEmpty() && definitions.getNotifier(notifierId) != null) {
                log.debugf("DELETE - deleteNotifier - notifierId: %s ", notifierId);
                definitions.removeNotifier(notifierId);
                response.resume(Response.status(Response.Status.OK).build());
            } else {
                log.debugf("DELETE - deleteNotifier - notifierId: %s not found or invalid. ", notifierId);
                Map<String, String> errors = new HashMap<String, String>();
                errors.put("errorMsg", "Notifier ID " + notifierId + " not found or invalid ID");
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
