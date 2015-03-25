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

import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.services.ActionsService;
import org.hawkular.alerts.api.services.DefinitionsService;

import org.jboss.logging.Logger;

/**
 * REST endpoint for Actions
 *
 * @author Lucas Ponce
 */
@Path("/actions")
@Api(value = "/actions",
     description = "Operations for actions")
public class ActionsHandler {
    private final Logger log = Logger.getLogger(ActionsHandler.class);

    @EJB
    DefinitionsService definitions;

    @EJB
    ActionsService actions;


    public ActionsHandler() {
        log.debugf("Creating instance.");
    }

    @POST
    @Path("/send")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Send an action to the ActionService.",
                  notes = "ActionService should not be invoked directly. This method is for demo/poc purposes.")
    public void send(@Suspended final AsyncResponse response, Action action) {
        actions.send(action);
        response.resume(Response.status(Response.Status.OK).build());
    }

    @GET
    @Path("/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Find all action ids",
                  responseContainer = "Collection",
                  response = String.class,
                  notes = "Pagination is not yet implemented")
    public void findAllActions(@Suspended final AsyncResponse response) {
        try {
            Collection<String> actions = definitions.getAllActions();
            if (actions == null || actions.isEmpty()) {
                log.debugf("GET - findAllActions - Empty");
                response.resume(Response.status(Response.Status.NO_CONTENT).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("GET - findAllActions - %s actions ", actions);
                response.resume(Response.status(Response.Status.OK)
                        .entity(actions).type(APPLICATION_JSON_TYPE).build());
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
    @Path("/plugin/{actionPlugin}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Find all action ids of an specific action plugin",
                  responseContainer = "Collection",
                  response = String.class,
                  notes = "Pagination is not yet implemented")
    public void findAllActionsByPlugin(@Suspended final AsyncResponse response,
                                       @ApiParam(value = "Action plugin to filter query for action ids",
                                                 required = true)
                                       @PathParam("actionPlugin") final String actionPlugin) {
        try {
            Collection<String> actions = definitions.getActions(actionPlugin);
            if (actions == null || actions.isEmpty()) {
                log.debugf("GET - findAllActions - Empty");
                response.resume(Response.status(Response.Status.NO_CONTENT).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("GET - findAllActions - %s notifiers ", actions);
                response.resume(Response.status(Response.Status.OK)
                                        .entity(actions).type(APPLICATION_JSON_TYPE).build());
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
    @ApiOperation(value = "Create a new action",
                  responseContainer = "Map<String, String>",
                  response = String.class,
                  notes = "Action properties are variable and depends on the action plugin. " +
                          "A user needs to request previously ActionPlugin API to get the list of properties to fill " +
                          "for a specific type. All actions should have actionId and actionPlugin as mandatory " +
                          "properties")
    public void createAction(@Suspended final AsyncResponse response,
                             @ApiParam(value = "Action properties. Properties depend of specific ActionPlugin.",
                                       name = "actionProperties",
                                       required = true)
                             final Map<String, String> actionProperties) {
        try {
            if (actionProperties != null && !actionProperties.isEmpty() &&
                    actionProperties.containsKey("actionId") &&
                    definitions.getAction(actionProperties.get("actionId")) == null) {
                String actionId = actionProperties.get("actionId");
                log.debugf("POST - createAction - actionId %s - properties %s ", actionId, actionProperties);
                definitions.addAction(actionId, actionProperties);
                response.resume(Response.status(Response.Status.OK)
                        .entity(actionProperties).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("POST - createAction - ID not valid or existing condition");
                Map<String, String> errors = new HashMap<String, String>();
                errors.put("errorMsg", "Existing action or invalid actionId");
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
    @Path("/{actionId}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get an existing action",
                  responseContainer = "Map<String, String>",
                  response = String.class,
                  notes = "Action is represented as a map of properties.")
    public void getAction(@Suspended final AsyncResponse response,
                          @ApiParam(value = "Action id to be retrieved",
                                    required = true)
                          @PathParam("actionId") final String actionId) {
        try {
             Map<String, String> actionProps = definitions.getAction(actionId);
            if (actionProps == null || actionProps.isEmpty()) {
                log.debugf("GET - getAction - Empty");
                response.resume(Response.status(Response.Status.NO_CONTENT).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("GET - getAction - actionId: %s - properties: %s ",
                        actionId, actionProps);
                response.resume(Response.status(Response.Status.OK)
                        .entity(actionProps).type(APPLICATION_JSON_TYPE).build());
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
    @Path("/{actionId}")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Update an existing action",
                  notes = "Action properties are variable and depends on the action plugin. " +
                          "A user needs to request previously ActionPlugin API to get the list of properties to fill " +
                          "for a specific type. All actions should have actionId and actionPlugin as mandatory " +
                          "properties")
    public void updateAction(@Suspended final AsyncResponse response,
                             @ApiParam(value = "action id to be updated",
                                       required = true)
                             @PathParam("actionId") final String actionId,
                             @ApiParam(value = "Action properties. Properties depend of specific ActionPlugin.",
                                       name = "actionProperties",
                                       required = true)
                             final Map<String, String> actionProperties) {
        try {
            if (actionId != null && !actionId.isEmpty() &&
                    actionProperties != null && !actionProperties.isEmpty() &&
                    actionProperties.containsKey("actionId") &&
                    actionProperties.get("actionId").equals(actionId) &&
                    definitions.getAction(actionId) != null) {
                log.debugf("POST - updateAction - actionId %s - properties: %s ", actionId, actionProperties);
                definitions.updateAction(actionId, actionProperties);
                response.resume(Response.status(Response.Status.OK)
                        .entity(actionProperties).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("PUT - updateAction - actionId: %s not found or invalid. ", actionId);
                Map<String, String> errors = new HashMap<String, String>();
                errors.put("errorMsg", "actionId  " + actionId + " not found or invalid Id");
                errors.put("errorMsg", "Existing action or invalid Id");
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
    @Path("/{actionId}")
    @ApiOperation(value = "Delete an existing action")
    public void deleteAction(@Suspended final AsyncResponse response,
                             @ApiParam(value = "Action id to be deleted",
                                       required = true)
                             @PathParam("actionId") final String actionId) {
        try {
            if (actionId != null && !actionId.isEmpty() && definitions.getAction(actionId) != null) {
                log.debugf("DELETE - deleteAction - actionId: %s ", actionId);
                definitions.removeAction(actionId);
                response.resume(Response.status(Response.Status.OK).build());
            } else {
                log.debugf("DELETE - deleteAction - actionId: %s not found or invalid. ", actionId);
                Map<String, String> errors = new HashMap<String, String>();
                errors.put("errorMsg", "actionId " + actionId + " not found or invalid Id");
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
