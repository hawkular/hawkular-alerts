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
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.services.ActionsCriteria;
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

    @HeaderParam(TENANT_HEADER_NAME)
    String tenantId;

    @EJB
    DefinitionsService definitions;

    @EJB
    ActionsService actions;

    public ActionsHandler() {
        log.debugf("Creating instance.");
    }

    @GET
    @Path("/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Find all action ids grouped by plugin",
            notes = "Pagination is not yet implemented")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success."),
            @ApiResponse(code = 500, message = "Internal server error") })
    public Response findActions() {
        try {
            Map<String, Set<String>> actions = definitions.getActions(tenantId);
            log.debugf("Actions: ", actions);
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
            @ApiResponse(code = 200, message = "Success"),
            @ApiResponse(code = 500, message = "Internal server error") })
    public Response findActionsByPlugin(@ApiParam(value = "Action plugin to filter query for action ids",
            required = true)
            @PathParam("actionPlugin")
            final String actionPlugin) {
        try {
            Collection<String> actions = definitions.getActions(tenantId, actionPlugin);
            log.debugf("Actions: %s ", actions);
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
        String actionPlugin = actionProperties.get("actionPlugin");
        String actionId = actionProperties.get("actionId");
        if (isEmpty(actionPlugin)) {
            return ResponseUtil.badRequest("actionPlugin must be not null");
        }
        if (isEmpty(actionId)) {
            return ResponseUtil.badRequest("actionId must be not null");
        }
        try {
            if (definitions.getAction(tenantId, actionPlugin, actionId) != null) {
                return ResponseUtil.badRequest("Existing action:  " + actionId);
            } else {
                definitions.addAction(tenantId, actionPlugin, actionId, actionProperties);
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
        try {
            Map<String, String> actionProperties = definitions.getAction(tenantId, actionPlugin, actionId);
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
            if (definitions.getAction(tenantId, actionPlugin, actionId) != null) {
                definitions.updateAction(tenantId, actionPlugin, actionId, actionProperties);
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
            if (definitions.getAction(tenantId, actionPlugin, actionId) != null) {
                definitions.removeAction(tenantId, actionPlugin, actionId);
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

    @GET
    @Path("/history")
    @Produces(APPLICATION_JSON)
    @ApiOperation(
            value = "Get actions from history with optional filtering")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success"),
            @ApiResponse(code = 500, message = "Internal server error") })
    public Response findActionsHistory(
            @ApiParam(required = false, value = "filter out actions created before this time, millisecond since epoch")
            @QueryParam("startTime")
            final Long startTime,
            @ApiParam(required = false, value = "filter out action created after this time, millisecond since epoch")
            @QueryParam("endTime")
            final Long endTime,
            @ApiParam(required = false, value = "filter out actions for unspecified actionPlugin, " +
                    "comma separated list of plugin names")
            @QueryParam("actionPlugins")
            final String actionPlugins,
            @ApiParam(required = false, value = "filter out actions for unspecified actionId, " +
                    "comma separated list of action IDs")
            @QueryParam("actionIds")
            final String actionIds,
            @ApiParam(required = false, value = "filter out actions for unspecified alertIds, " +
                    "comma separated list of alert IDs")
            @QueryParam("alertIds")
            final String alertIds,
            @ApiParam(required = false, value = "filter out alerts for unspecified result, " +
                    "comma separated list of action results")
            @QueryParam("results")
            final String results,
            @ApiParam(required = false, value = "return only thin actions, do not include full alert, only alertId")
            @QueryParam("thin")
            final Boolean thin,
            @Context
            final UriInfo uri) {
        Pager pager = RequestUtil.extractPaging(uri);
        try {
            ActionsCriteria criteria = buildCriteria(startTime, endTime, actionPlugins, actionIds, alertIds, results,
                    thin);
            Page<Action> actionPage = actions.getActions(tenantId, criteria, pager);
            log.debugf("Actions: %s ", actionPage);
            if (isEmpty(actionPage)) {
                return ResponseUtil.ok(actionPage);
            }
            return ResponseUtil.paginatedOk(actionPage, uri);
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @PUT
    @Path("/history/delete")
    @Produces(APPLICATION_JSON)
    @ApiOperation(
            value = "Delete actions from history with optional filtering")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success"),
            @ApiResponse(code = 500, message = "Internal server error") })
    public Response deleteActionsHistory(
            @ApiParam(required = false, value = "filter out actions created before this time, millisecond since epoch")
            @QueryParam("startTime")
            final Long startTime,
            @ApiParam(required = false, value = "filter out action created after this time, millisecond since epoch")
            @QueryParam("endTime")
            final Long endTime,
            @ApiParam(required = false, value = "filter out actions for unspecified actionPlugin, " +
                    "comma separated list of plugin names")
            @QueryParam("actionPlugins")
            final String actionPlugins,
            @ApiParam(required = false, value = "filter out actions for unspecified actionId, " +
                    "comma separated list of action IDs")
            @QueryParam("actionIds")
            final String actionIds,
            @ApiParam(required = false, value = "filter out actions for unspecified alertIds, " +
                    "comma separated list of alert IDs")
            @QueryParam("alertIds")
            final String alertIds,
            @ApiParam(required = false, value = "filter out alerts for unspecified result, " +
                    "comma separated list of action results")
            @QueryParam("results")
            final String results) {
        try {
            ActionsCriteria criteria = buildCriteria(startTime, endTime, actionPlugins, actionIds, alertIds, results,
                    false);
            int numDeleted = actions.deleteActions(tenantId, criteria);
            log.debugf("Actions deleted: %s ", numDeleted);
            return ResponseUtil.ok(numDeleted);
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
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
            criteria.setActionIds(Arrays.asList(actionPlugins.split(",")));
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

    private boolean isEmpty(Map map) {
        return map == null || map.isEmpty();
    }

    private boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private boolean isEmpty(Collection collection) {
        return collection == null || collection.isEmpty();
    }

}
