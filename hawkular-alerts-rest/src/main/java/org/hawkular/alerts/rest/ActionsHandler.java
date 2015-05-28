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

import java.util.Collection;
import java.util.Map;

import java.util.Set;
import javax.ejb.EJB;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.hawkular.accounts.api.model.Persona;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.services.ActionsService;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.jboss.logging.Logger;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

/**
 * REST endpoint for Actions
 *
 * @author Lucas Ponce
 */
@Path("/actions")
@Api(value = "/actions", description = "Action Handling")
public class ActionsHandler {
    private final Logger log = Logger.getLogger(ActionsHandler.class);

    @Inject
    Persona persona;

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
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Action sent succesfully."),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Response send(Action action) {
        if (!checkPersona()) {
            return ResponseUtil.internalError("No persona found");
        }
        try {
            actions.send(action);
            return ResponseUtil.ok();
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @GET
    @Path("/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Find all action ids grouped by plugin",
            notes = "Pagination is not yet implemented")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success. Actions found."),
            @ApiResponse(code = 204, message = "Success. No actions found."),
            @ApiResponse(code = 500, message = "Internal server error") })
    public Response findActions() {
        if (!checkPersona()) {
            return ResponseUtil.internalError("No persona found");
        }
        try {
            Map<String, Set<String>> actions = definitions.getActions(persona.getId());
            log.debugf("Actions: ", actions);
            if (isEmpty(actions)) {
                return ResponseUtil.noContent();
            }
            return ResponseUtil.ok(actions);
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @GET
    @Path("/plugin/{actionPlugin}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Find all action ids of an specific action plugin",
            notes = "Pagination is not yet implemented")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Actions Found"),
            @ApiResponse(code = 204, message = "No Actions Found"),
            @ApiResponse(code = 500, message = "Internal server error") })
    public Response findActionsByPlugin(@ApiParam(value = "Action plugin to filter query for action ids",
            required = true)
            @PathParam("actionPlugin")
            final String actionPlugin) {
        if (!checkPersona()) {
            return ResponseUtil.internalError("No persona found");
        }
        try {
            Collection<String> actions = definitions.getActions(persona.getId(), actionPlugin);
            log.debugf("Actions: %s ", actions);
            if (isEmpty(actions)) {
                return ResponseUtil.noContent();
            }
            return ResponseUtil.ok(actions);
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @POST
    @Path("/")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Create a new action",
            notes = "Action properties are variable and depends on the action plugin. " +
                    "A user needs to request previously ActionPlugin API to get the list of properties to fill " +
                    "for a specific type. All actions should have actionId and actionPlugin as mandatory " +
                    "properties")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Action Created"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Existing action/Invalid Parameters") })
    public Response createAction(@ApiParam(value = "Action properties. Properties depend of specific ActionPlugin.",
                    name = "actionProperties",
                    required = true)
            final Map<String, String> actionProperties) {
        if (!checkPersona()) {
            return ResponseUtil.internalError("No persona found");
        }
        String actionPlugin = actionProperties.get("actionPlugin");
        String actionId = actionProperties.get("actionId");
        if (isEmpty(actionPlugin)) {
            return ResponseUtil.badRequest("actionPlugin must be not null");
        }
        if (isEmpty(actionId)) {
            return ResponseUtil.badRequest("actionId must be not null");
        }
        try {
            if (definitions.getAction(persona.getId(), actionPlugin, actionId) != null) {
                return ResponseUtil.badRequest("Existing action:  " + actionId);
            } else {
                definitions.addAction(persona.getId(), actionPlugin, actionId, actionProperties);
                log.debugf("ActionId: %s - Properties: %s ", actionId, actionProperties);
                return ResponseUtil.ok(actionProperties);
            }
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @GET
    @Path("/{actionPlugin}/{actionId}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get an existing action",
            responseContainer = "Map<String, String>",
            response = String.class,
            notes = "Action is represented as a map of properties.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Action Found"),
            @ApiResponse(code = 404, message = "No Action Found"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Response getAction(@ApiParam(value = "Action plugin", required = true)
            @PathParam("actionPlugin")
            final String actionPlugin,
            @ApiParam(value = "Action id to be retrieved", required = true)
            @PathParam("actionId")
            final String actionId) {
        if (!checkPersona()) {
            return ResponseUtil.internalError("No persona found");
        }
        try {
            Map<String, String> actionProperties = definitions.getAction(persona.getId(), actionPlugin, actionId);
            log.debugf("ActionId: %s - Properties: %s ", actionId, actionProperties);
            if (isEmpty(actionProperties)) {
                return ResponseUtil.notFound("Not action found for actionPlugin: " + actionPlugin + " and actionId: "
                        + actionId);
            }
            return ResponseUtil.ok(actionProperties);
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @PUT
    @Path("/{actionPlugin}/{actionId}")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Update an existing action",
            notes = "Action properties are variable and depends on the action plugin. " +
                    "A user needs to request previously ActionPlugin API to get the list of properties to fill " +
                    "for a specific type. All actions should have actionId and actionPlugin as mandatory " +
                    "properties")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Action Updated"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 404, message = "Action not found for update") })
    public Response updateAction(@ApiParam(value = "Action plugin", required = true)
            @PathParam("actionPlugin")
            final String actionPlugin,
            @ApiParam(value = "action id to be updated", required = true)
            @PathParam("actionId")
            final String actionId,
            @ApiParam(value = "Action properties. Properties depend of specific ActionPlugin.", required = true)
            final Map<String, String> actionProperties) {
        try {
            if (!checkPersona()) {
                return ResponseUtil.internalError("No persona found");
            }
            if (definitions.getAction(persona.getId(), actionPlugin, actionId) != null) {
                definitions.updateAction(persona.getId(), actionPlugin, actionId, actionProperties);
                log.debugf("ActionId: %s - Properties: %s ", actionId, actionProperties);
                return ResponseUtil.ok(actionProperties);
            } else {
                return ResponseUtil.notFound("ActionId: " + actionId + " not found for update");
            }
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @DELETE
    @Path("/{actionPlugin}/{actionId}")
    @ApiOperation(value = "Delete an existing action")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Action Deleted"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 404, message = "ActionId not found for delete") })
    public Response deleteAction(@ApiParam(value = "Action plugin", required = true)
            @PathParam("actionPlugin")
            final String actionPlugin,
            @ApiParam(value = "Action id to be deleted", required = true)
            @PathParam("actionId")
            final String actionId) {
        try {
            if (!checkPersona()) {
                return ResponseUtil.internalError("No persona found");
            }
            if (definitions.getAction(persona.getId(), actionPlugin, actionId) != null) {
                definitions.removeAction(persona.getId(), actionPlugin, actionId);
                log.debugf("ActionId: %s ", actionId);
                return ResponseUtil.ok();
            } else {
                return ResponseUtil.notFound("ActionId: " + actionId + " not found for delete");
            }
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    private boolean checkPersona() {
        if (persona == null) {
            log.warn("Persona is null. Possible issue with accounts integration ? ");
            return false;
        } else if (persona.getId().trim().isEmpty()) {
            log.warn("Persona is empty. Possible issue with accounts integration ? ");
            return false;
        }
        return true;
    }

    private boolean isEmpty(Map map) {
        return map == null || map.isEmpty();
    }

    private boolean isEmpty(Collection collection) {
        return collection == null || collection.isEmpty();
    }

    private boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

}
