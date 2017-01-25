/*
 * Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
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

import static org.hawkular.alerts.rest.CommonUtil.isEmpty;
import static org.hawkular.alerts.rest.HawkularAlertsApp.TENANT_HEADER_NAME;

import java.util.Arrays;
import java.util.Collection;
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

import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.model.action.ActionDefinition;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.services.ActionsCriteria;
import org.hawkular.alerts.api.services.ActionsService;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.rest.ResponseUtil.ApiDeleted;
import org.hawkular.alerts.rest.ResponseUtil.ApiError;
import org.jboss.logging.Logger;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * REST endpoint for Actions
 *
 * @author Lucas Ponce
 */
@Path("/actions")
@Api(value = "/actions", description = "Actions Handling")
public class ActionsHandler {
    private final Logger log = Logger.getLogger(ActionsHandler.class);

    @HeaderParam(TENANT_HEADER_NAME)
    String tenantId;

    @EJB
    DefinitionsService definitions;

    @EJB
    ActionsService actions;

    public ActionsHandler() {
        log.debug("Creating instance.");
    }

    @GET
    @Path("/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Find all action ids grouped by plugin.",
            notes = "Return a map[string, array of string]] where key is the plugin id and value " +
                    "a collection of actionIds.",
            response = Collection.class, responseContainer = "Map")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully fetched map of action ids grouped by plugin."),
            @ApiResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public Response findActionIds() {
        try {
            Map<String, Set<String>> actions = definitions.getActionDefinitionIds(tenantId);
            if (log.isDebugEnabled()) {
                log.debug("Actions: " + actions);
            }
            return ResponseUtil.ok(actions);
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            return ResponseUtil.internalError(e);
        }
    }

    @GET
    @Path("/plugin/{actionPlugin}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Find all action ids of an specific action plugin.",
            response = String.class, responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully fetched list of action ids."),
            @ApiResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public Response findActionIdsByPlugin(@ApiParam(value = "Action plugin to filter query for action ids.",
            required = true)
            @PathParam("actionPlugin")
            final String actionPlugin) {
        try {
            Collection<String> actions = definitions.getActionDefinitionIds(tenantId, actionPlugin);
            if (log.isDebugEnabled()) {
                log.debug("Actions: " + actions);
            }
            return ResponseUtil.ok(actions);
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            return ResponseUtil.internalError(e);
        }
    }

    @GET
    @Path("/{actionPlugin}/{actionId}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get an existing action definition.",
            response = ActionDefinition.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Action found."),
            @ApiResponse(code = 404, message = "No Action found.", response = ApiError.class),
            @ApiResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public Response getActionDefinition(@ApiParam(value = "Action plugin.", required = true)
            @PathParam("actionPlugin")
            final String actionPlugin,
            @ApiParam(value = "Action id to be retrieved.", required = true)
            @PathParam("actionId")
            final String actionId) {
        try {
            ActionDefinition actionDefinition = definitions.getActionDefinition(tenantId, actionPlugin, actionId);
            if (log.isDebugEnabled()) {
                log.debug("ActionDefinition: " + actionDefinition);
            }
            if (actionDefinition == null) {
                return ResponseUtil.notFound("Not action found for actionPlugin: " + actionPlugin + " and actionId: "
                        + actionId);
            }
            return ResponseUtil.ok(actionDefinition);
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            return ResponseUtil.internalError(e);
        }
    }

    @POST
    @Path("/")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Create a new ActionDefinition.",
            notes = "Returns created ActionDefinition",
            response = ActionDefinition.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, ActionDefinition Created."),
            @ApiResponse(code = 500, message = "Internal server error.", response = ApiError.class),
            @ApiResponse(code = 400, message = "Existing ActionDefinition/Invalid Parameters",
                    response = ApiError.class)
    })
    public Response createActionDefinition(
           @ApiParam(value = "ActionDefinition to be created.",
           name = "actionDefinition",
           required = true)
           final ActionDefinition actionDefinition) {
        if (actionDefinition == null) {
            return ResponseUtil.badRequest("actionDefinition must be not null");
        }
        if (isEmpty(actionDefinition.getActionPlugin())) {
            return ResponseUtil.badRequest("actionPlugin must be not null");
        }
        if (isEmpty(actionDefinition.getActionId())) {
            return ResponseUtil.badRequest("actionId must be not null");
        }
        if (isEmpty(actionDefinition.getProperties())) {
            return ResponseUtil.badRequest("properties must be not null");
        }
        try {
            if (definitions.getActionDefinition(tenantId, actionDefinition.getActionPlugin(),
                    actionDefinition.getActionId()) != null) {
                return ResponseUtil.badRequest("Existing ActionDefinition:  " + actionDefinition);
            } else {
                definitions.addActionDefinition(tenantId, actionDefinition);
                if (log.isDebugEnabled()) {
                    log.debug("ActionDefinition: " + actionDefinition);
                }
                return ResponseUtil.ok(actionDefinition);
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
    @Path("/")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Update an existing ActionDefinition.",
            notes = "Returns updated ActionDefinition.",
            response = ActionDefinition.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, ActionDefinition Updated."),
            @ApiResponse(code = 500, message = "Internal server error.", response = ApiError.class),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters.", response = ApiError.class),
            @ApiResponse(code = 404, message = "ActionDefinition not found for update.", response = ApiError.class)
    })
    public Response updateActionDefinition(
           @ApiParam(value = "ActionDefinition to be updated.",
           name = "actionDefinition",
           required = true)
           final ActionDefinition actionDefinition) {
        if (actionDefinition == null) {
            return ResponseUtil.badRequest("actionDefinition must be not null");
        }
        if (isEmpty(actionDefinition.getActionPlugin())) {
            return ResponseUtil.badRequest("actionPlugin must be not null");
        }
        if (isEmpty(actionDefinition.getActionId())) {
            return ResponseUtil.badRequest("actionId must be not null");
        }
        try {
            actionDefinition.setTenantId(tenantId);
            if (definitions.getActionDefinition(tenantId, actionDefinition.getActionPlugin(),
                    actionDefinition.getActionId()) != null) {
                definitions.updateActionDefinition(tenantId, actionDefinition);
                if (log.isDebugEnabled()) {
                    log.debug("ActionDefinition: " + actionDefinition);
                }
                return ResponseUtil.ok(actionDefinition);
            } else {
                return ResponseUtil.notFound("ActionDefinition: " + actionDefinition + " not found for update");
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
    @Path("/{actionPlugin}/{actionId}")
    @ApiOperation(value = "Delete an existing ActionDefinition.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, ActionDefinition Deleted."),
            @ApiResponse(code = 500, message = "Internal server error.", response = ApiError.class),
            @ApiResponse(code = 404, message = "ActionDefinition not found for delete.", response = ApiError.class)
    })
    public Response deleteActionDefinition(
           @ApiParam(value = "Action plugin.", required = true)
           @PathParam("actionPlugin")
           final String actionPlugin,
           @ApiParam(value = "Action id to be deleted.", required = true)
           @PathParam("actionId")
           final String actionId) {
        try {
            if (definitions.getActionDefinition(tenantId, actionPlugin, actionId) != null) {
                definitions.removeActionDefinition(tenantId, actionPlugin, actionId);
                if (log.isDebugEnabled()) {
                    log.debug("ActionPlugin: " + actionPlugin + " ActionId: " + actionId);
                }
                return ResponseUtil.ok();
            } else {
                return ResponseUtil.notFound("ActionPlugin: " + actionPlugin + " ActionId: " + actionId +
                        " not found for delete");
            }
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            return ResponseUtil.internalError(e);
        }
    }

    @GET
    @Path("/history")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get actions from history with optional filtering.",
            notes = "If not criteria defined, it fetches all actions stored in the system.",
            response = Action.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully fetched list of actions."),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters.", response = ApiError.class),
            @ApiResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public Response findActionsHistory(
            @ApiParam(required = false, value = "Filter out actions created before this time.",
                allowableValues = "Timestamp in millisecond since epoch.")
            @QueryParam("startTime")
            final Long startTime,
            @ApiParam(required = false, value = "Filter out actions created after this time.",
                allowableValues = "Timestamp in millisecond since epoch.")
            @QueryParam("endTime")
            final Long endTime,
            @ApiParam(required = false, value = "Filter out actions for unspecified actionPlugin.",
                allowableValues = "Comma separated list of plugin names.")
            @QueryParam("actionPlugins")
            final String actionPlugins,
            @ApiParam(required = false, value = "Filter out actions for unspecified actionId.",
                allowableValues = "Comma separated list of actions IDs.")
            @QueryParam("actionIds")
            final String actionIds,
            @ApiParam(required = false, value = "Filter out actions for unspecified alertIds.",
                allowableValues = "Comma separated list of alert IDs")
            @QueryParam("alertIds")
            final String alertIds,
            @ApiParam(required = false, value = "Filter out alerts for unspecified result.",
                allowableValues = "Comma separated list of action results.")
            @QueryParam("results")
            final String results,
            @ApiParam(required = false, value = "Return only thin actions, do not include full alert, only alertId.")
            @QueryParam("thin")
            final Boolean thin,
            @Context
            final UriInfo uri) {
        Pager pager = RequestUtil.extractPaging(uri);
        try {
            ActionsCriteria criteria = buildCriteria(startTime, endTime, actionPlugins, actionIds, alertIds, results,
                    thin);
            Page<Action> actionPage = actions.getActions(tenantId, criteria, pager);
            if (log.isDebugEnabled()) {
                log.debug("Actions: " + actionPage);
            }
            if (isEmpty(actionPage)) {
                return ResponseUtil.ok(actionPage);
            }
            return ResponseUtil.paginatedOk(actionPage, uri);
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            if (e.getCause() != null && e.getCause() instanceof IllegalArgumentException) {
                return ResponseUtil.badRequest("Bad arguments: " + e.getMessage());
            }
            return ResponseUtil.internalError(e);
        }
    }

    @PUT
    @Path("/history/delete")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Delete actions from history with optional filtering.",
            notes = "WARNING: If not criteria defined, it deletes all actions history stored in the system.",
            response = ApiDeleted.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Actions deleted."),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters.", response = ApiError.class),
            @ApiResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public Response deleteActionsHistory(
            @ApiParam(required = false, value = "Filter out actions created before this time.",
                allowableValues = "Timestamp in millisecond since epoch.")
            @QueryParam("startTime")
            final Long startTime,
            @ApiParam(required = false, value = "Filter out action created after this time.",
                allowableValues = "Timestamp in millisecond since epoch.")
            @QueryParam("endTime")
            final Long endTime,
            @ApiParam(required = false, value = "Filter out actions for unspecified actionPlugin.",
                allowableValues = "Comma separated list of plugin names.")
            @QueryParam("actionPlugins")
            final String actionPlugins,
            @ApiParam(required = false, value = "Filter out actions for unspecified actionId.",
                allowableValues = "Comma separated list of action IDs.")
            @QueryParam("actionIds")
            final String actionIds,
            @ApiParam(required = false, value = "Filter out actions for unspecified alertIds. ",
                allowableValues = "Comma separated list of alert IDs.")
            @QueryParam("alertIds")
            final String alertIds,
            @ApiParam(required = false, value = "Filter out alerts for unspecified result. ",
                allowableValues = "Comma separated list of action results.")
            @QueryParam("results")
            final String results) {
        try {
            ActionsCriteria criteria = buildCriteria(startTime, endTime, actionPlugins, actionIds, alertIds, results,
                    false);
            int numDeleted = actions.deleteActions(tenantId, criteria);
            if (log.isDebugEnabled()) {
                log.debug("Actions deleted: " + numDeleted);
            }
            return ResponseUtil.ok(new ApiDeleted(numDeleted));
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            if (e.getCause() != null && e.getCause() instanceof IllegalArgumentException) {
                return ResponseUtil.badRequest("Bad arguments: " + e.getMessage());
            }
            return ResponseUtil.internalError(e);
        }
    }

    private ActionsCriteria buildCriteria(Long startTime, Long endTime, String actionPlugins, String actionIds,
                                          String alertIds, String results, Boolean thin) {
        ActionsCriteria criteria = new ActionsCriteria();
        criteria.setStartTime(startTime);
        criteria.setEndTime(endTime);
        if (!isEmpty(actionPlugins)) {
            criteria.setActionPlugins(Arrays.asList(actionPlugins.split(",")));
        }
        if (!isEmpty(actionIds)) {
            criteria.setActionIds(Arrays.asList(actionIds.split(",")));
        }
        if (!isEmpty(alertIds)) {
            criteria.setAlertIds(Arrays.asList(alertIds.split(",")));
        }
        if (!isEmpty(results)) {
            criteria.setResults(Arrays.asList(results.split(",")));
        }
        if (thin != null) {
            criteria.setThin(thin);
        } else {
            criteria.setThin(false);
        }
        return criteria;
    }
}
