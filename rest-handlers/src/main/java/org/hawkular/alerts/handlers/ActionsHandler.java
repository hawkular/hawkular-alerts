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
package org.hawkular.alerts.handlers;

import static org.hawkular.alerts.api.doc.DocConstants.DELETE;
import static org.hawkular.alerts.api.doc.DocConstants.GET;
import static org.hawkular.alerts.api.doc.DocConstants.POST;
import static org.hawkular.alerts.api.doc.DocConstants.PUT;
import static org.hawkular.alerts.api.json.JsonUtil.fromJson;
import static org.hawkular.alerts.api.util.Util.isEmpty;
import static org.hawkular.alerts.handlers.util.ResponseUtil.PARAMS_PAGING;
import static org.hawkular.alerts.handlers.util.ResponseUtil.checkForUnknownQueryParams;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.api.doc.DocEndpoint;
import org.hawkular.alerts.api.doc.DocParameter;
import org.hawkular.alerts.api.doc.DocParameters;
import org.hawkular.alerts.api.doc.DocPath;
import org.hawkular.alerts.api.doc.DocResponse;
import org.hawkular.alerts.api.doc.DocResponses;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.model.action.ActionDefinition;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.services.ActionsCriteria;
import org.hawkular.alerts.api.services.ActionsService;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.engine.StandaloneAlerts;
import org.hawkular.alerts.handlers.util.ResponseUtil;
import org.hawkular.alerts.handlers.util.ResponseUtil.ApiDeleted;
import org.hawkular.alerts.handlers.util.ResponseUtil.ApiError;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;
import org.hawkular.handlers.RestEndpoint;
import org.hawkular.handlers.RestHandler;

import io.vertx.core.MultiMap;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@RestEndpoint(path = "/actions")
@DocEndpoint(value = "/actions", description = "Actions Handling")
public class ActionsHandler implements RestHandler {
    private static final MsgLogger log = MsgLogging.getMsgLogger(ActionsHandler.class);
    private static final String PARAM_START_TIME = "startTime";
    private static final String PARAM_END_TIME = "endTime";
    private static final String PARAM_ACTION_PLUGINS = "actionPlugins";
    private static final String PARAM_ACTION_IDS = "actionIds";
    @Deprecated
    private static final String PARAM_ALERTS_IDS = "alertIds";
    private static final String PARAM_EVENT_IDS = "eventIds";
    private static final String PARAM_RESULTS = "results";
    private static final String COMMA = ",";

    private static final String DELETE_ACTIONS_HISTORY = "deleteActionsHistory";
    private static final String FIND_ACTIONS_HISTORY = "findActionsHistory";
    private static final Map<String, Set<String>> queryParamValidationMap = new HashMap<>();
    static {
        Collection<String> ACTIONS_CRITERIA = Arrays.asList(PARAM_START_TIME,
                PARAM_END_TIME,
                PARAM_ACTION_PLUGINS,
                PARAM_ACTION_IDS,
                PARAM_ALERTS_IDS,
                PARAM_EVENT_IDS,
                PARAM_RESULTS);
        queryParamValidationMap.put(FIND_ACTIONS_HISTORY, new HashSet<>(ACTIONS_CRITERIA));
        queryParamValidationMap.get(FIND_ACTIONS_HISTORY).addAll(PARAMS_PAGING);
        queryParamValidationMap.put(DELETE_ACTIONS_HISTORY, new HashSet<>(ACTIONS_CRITERIA));
    }

    ActionsService actionsService;
    DefinitionsService definitionsService;

    public ActionsHandler() {
        actionsService = StandaloneAlerts.getActionsService();
        definitionsService = StandaloneAlerts.getDefinitionsService();
    }

    @Override
    public void initRoutes(String baseUrl, Router router) {
        String path = baseUrl + "/actions";
        router.get(path).handler(this::findActionIds);
        router.post(path).handler(this::createActionDefinition);
        router.put(path).handler(this::updateActionDefinition);
        router.get(path + "/history").handler(this::findActionsHistory);
        router.put(path + "/history/delete").handler(this::deleteActionsHistory);
        router.get(path + "/plugin/:actionPlugin").handler(this::findActionIdsByPlugin);
        router.get(path + "/:actionPlugin/:actionId").handler(this::getActionDefinition);
        router.delete(path + "/:actionPlugin/:actionId").handler(this::deleteActionDefinition);
    }

    @DocPath(method = GET,
            path = "/",
            name = "Find all action ids grouped by plugin.",
            notes = "Return a map[string, array of string]] where key is the plugin id and description " +
                    "a collection of actionIds.")
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Successfully fetched map of action ids grouped by plugin.", response = Collection.class, responseContainer = "Map"),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void findActionIds(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    try {
                        Map<String, Set<String>> actions = definitionsService.getActionDefinitionIds(tenantId);
                        log.debugf("Actions: %s", actions);
                        future.complete(actions);
                    } catch (Exception e) {
                        log.errorf("Error querying actions ids for tenantId %s. Reason: %s", tenantId, e.toString());
                        future.fail(new ResponseUtil.InternalServerException(e.toString()));
                    }
                }, res -> ResponseUtil.result(routing, res));
    }

    @DocPath(method = POST,
            path = "/",
            name = "Create a new ActionDefinition.",
            notes = "Returns created ActionDefinition")
    @DocParameters(value = {
            @DocParameter(required = true, body = true, type = ActionDefinition.class,
                    description = "ActionDefinition to be created.")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Success, ActionDefinition Created.", response = String.class, responseContainer = "List"),
            @DocResponse(code = 400, message = "Existing ActionDefinition/Invalid Parameters.", response = ApiError.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void createActionDefinition(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    String json = routing.getBodyAsString();
                    ActionDefinition actionDefinition;
                    try {
                        actionDefinition = fromJson(json.toString(), ActionDefinition.class);
                    } catch (Exception e) {
                        log.errorf("Error parsing ActionDefinition json: %s. Reason: %s", json, e.toString());
                        throw new ResponseUtil.BadRequestException(e.toString());
                    }
                    if (actionDefinition == null) {
                        throw new ResponseUtil.BadRequestException("actionDefinition must be not null");
                    }
                    if (isEmpty(actionDefinition.getActionPlugin())) {
                        throw new ResponseUtil.BadRequestException("actionPlugin must be not null");
                    }
                    if (isEmpty(actionDefinition.getActionId())) {
                        throw new ResponseUtil.BadRequestException("actionId must be not null");
                    }
                    if (isEmpty(actionDefinition.getProperties())) {
                        throw new ResponseUtil.BadRequestException("properties must be not null");
                    }
                    actionDefinition.setTenantId(tenantId);
                    ActionDefinition found;
                    try {
                        found = definitionsService.getActionDefinition(actionDefinition.getTenantId(),
                                actionDefinition.getActionPlugin(), actionDefinition.getActionId());
                    } catch (IllegalArgumentException e) {
                        throw new ResponseUtil.BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                    if (found != null) {
                        throw new ResponseUtil.BadRequestException("Existing ActionDefinition: " + actionDefinition);
                    }
                    try {
                        definitionsService.addActionDefinition(actionDefinition.getTenantId(), actionDefinition);
                        log.debugf("ActionDefinition: %s", actionDefinition);
                        future.complete(actionDefinition);
                    } catch (IllegalArgumentException e) {
                        throw new ResponseUtil.BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                }, res -> ResponseUtil.result(routing, res));
    }

    @DocPath(method = PUT,
            path = "/",
            name = "Update an existing ActionDefinition.",
            notes = "Returns updated ActionDefinition.")
    @DocParameters(value = {
            @DocParameter(required = true, body = true, type = ActionDefinition.class,
                    description = "ActionDefinition to be created.")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Success, ActionDefinition Updated.", response = ActionDefinition.class),
            @DocResponse(code = 400, message = "Bad Request/Invalid Parameters.", response = ApiError.class),
            @DocResponse(code = 404, message = "ActionDefinition not found for update.", response = ApiError.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void updateActionDefinition(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    String json = routing.getBodyAsString();
                    ActionDefinition actionDefinition;
                    try {
                        actionDefinition = fromJson(json, ActionDefinition.class);
                    } catch (Exception e) {
                        log.errorf("Error parsing ActionDefinition json: %s. Reason: %s", json, e.toString());
                        throw new ResponseUtil.BadRequestException(e.toString());
                    }
                    if (actionDefinition == null) {
                        throw new ResponseUtil.BadRequestException("actionDefinition must be not null");
                    }
                    if (isEmpty(actionDefinition.getActionPlugin())) {
                        throw new ResponseUtil.BadRequestException("actionPlugin must be not null");
                    }
                    if (isEmpty(actionDefinition.getActionId())) {
                        throw new ResponseUtil.BadRequestException("actionId must be not null");
                    }
                    if (isEmpty(actionDefinition.getProperties())) {
                        throw new ResponseUtil.BadRequestException("properties must be not null");
                    }
                    actionDefinition.setTenantId(tenantId);
                    ActionDefinition found;
                    try {
                        found = definitionsService.getActionDefinition(actionDefinition.getTenantId(),
                                actionDefinition.getActionPlugin(), actionDefinition.getActionId());
                    } catch (IllegalArgumentException e) {
                        throw new ResponseUtil.BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                    if (found == null) {
                        throw new ResponseUtil.NotFoundException("ActionDefinition: " + actionDefinition + " not found for update");
                    }
                    try {
                        definitionsService.updateActionDefinition(actionDefinition.getTenantId(), actionDefinition);
                        log.debugf("ActionDefinition: %s", actionDefinition);
                        future.complete(actionDefinition);
                    } catch (IllegalArgumentException e) {
                        throw new ResponseUtil.BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                }, res -> ResponseUtil.result(routing, res));
    }

    @DocPath(method = GET,
            path = "/history",
            name = "Get actions from history with optional filtering.",
            notes = "If not criteria defined, it fetches all actions stored in the system.")
    @DocParameters(value = {
            @DocParameter(name = "startTime",
                    description = "Filter out actions created before this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "endTime",
                    description = "Filter out actions created after this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "actionPlugins",
                    description = "Filter out actions for unspecified actionPlugin.",
                    allowableValues = "Comma separated list of plugin names."),
            @DocParameter(name = "actionIds",
                    description = "Filter out actions for unspecified actionId.",
                    allowableValues = "Comma separated list of actions IDs."),
            @DocParameter(name = "results",
                    description = "Filter out alerts for unspecified result.",
                    allowableValues = "Comma separated list of action results."),
            @DocParameter(name = "thin", type = Boolean.class,
                    description = "Return only thin actions, do not include full alert, only alertId.")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Successfully fetched list of actions.", response = Action.class, responseContainer = "List"),
            @DocResponse(code = 400, message = "Bad Request/Invalid Parameters.", response = ApiError.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void findActionsHistory(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    try {
                        checkForUnknownQueryParams(routing.request().params(), queryParamValidationMap.get(FIND_ACTIONS_HISTORY));
                        Pager pager = ResponseUtil.extractPaging(routing.request().params());
                        ActionsCriteria criteria = buildCriteria(routing.request().params());
                        Page<Action> actionPage = actionsService.getActions(tenantId, criteria, pager);
                        log.debugf("Actions: %s", actionPage);
                        future.complete(actionPage);
                    } catch (IllegalArgumentException e) {
                        throw new ResponseUtil.BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                }, res -> ResponseUtil.result(routing, res));
    }

    @DocPath(method = PUT,
            path = "/history/delete",
            name = "Delete actions from history with optional filtering.",
            notes = "WARNING: If not criteria defined, it deletes all actions history stored in the system.")
    @DocParameters(value = {
            @DocParameter(name = "startTime",
                    description = "Filter out actions created before this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "endTime",
                    description = "Filter out actions created after this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "actionPlugins",
                    description = "Filter out actions for unspecified actionPlugin.",
                    allowableValues = "Comma separated list of plugin names."),
            @DocParameter(name = "actionIds",
                    description = "Filter out actions for unspecified actionId.",
                    allowableValues = "Comma separated list of actions IDs."),
            @DocParameter(name = "results",
                    description = "Filter out alerts for unspecified result.",
                    allowableValues = "Comma separated list of action results."),
            @DocParameter(name = "results",
                    description = "Filter out alerts for unspecified result.",
                    allowableValues = "Comma separated list of action results.")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Success, Actions deleted.", response = ApiDeleted.class),
            @DocResponse(code = 400, message = "Bad Request/Invalid Parameters.", response = ApiError.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void deleteActionsHistory(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    try {
                        checkForUnknownQueryParams(routing.request().params(), queryParamValidationMap.get(DELETE_ACTIONS_HISTORY));
                        ActionsCriteria criteria = buildCriteria(routing.request().params());
                        int numDeleted = actionsService.deleteActions(tenantId, criteria);
                        log.debugf("Actions deleted: %s", numDeleted);
                        Map<String, String> deleted = new HashMap<>();
                        deleted.put("deleted", String.valueOf(numDeleted));
                        future.complete(deleted);
                    } catch (IllegalArgumentException e) {
                        throw new ResponseUtil.BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                }, res -> ResponseUtil.result(routing, res));
    }

    @DocPath(method = GET,
            path = "/plugin/{actionPlugin}",
            name = "Find all action ids of an specific action plugin.")
    @DocParameters(value = {
            @DocParameter(name = "actionPlugin", required = true, path = true,
                    description = "Action plugin to filter query for action ids.")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Successfully fetched list of action ids.", response = ApiDeleted.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void findActionIdsByPlugin(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    String actionPlugin = routing.request().getParam("actionPlugin");
                    try {
                        Collection<String> actions = definitionsService.getActionDefinitionIds(tenantId, actionPlugin);
                        log.debugf("Actions: %s", actions);
                        future.complete(actions);
                    } catch (Exception e) {
                        log.errorf("Error querying actions ids for tenantId %s and actionPlugin %s. Reason: %s", tenantId, actionPlugin, e.toString());
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                }, res -> ResponseUtil.result(routing, res));
    }

    @DocPath(method = GET,
            path = "/{actionPlugin}/{actionId}",
            name = "Get an existing action definition.")
    @DocParameters(value = {
            @DocParameter(name = "actionPlugin", required = true, path = true,
                    description = "Action plugin."),
            @DocParameter(name = "actionId", required = true, path = true,
                    description = "Action id to be retrieved")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Success, ActionDefinition found.", response = ActionDefinition.class),
            @DocResponse(code = 404, message = "No ActionDefinition found.", response = ApiError.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void getActionDefinition(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    String actionPlugin = routing.request().getParam("actionPlugin");
                    String actionId = routing.request().getParam("actionId");
                    ActionDefinition actionDefinition;
                    try {
                        actionDefinition = definitionsService.getActionDefinition(tenantId, actionPlugin, actionId);
                        log.debugf("ActionDefinition: %s", actionDefinition);
                    } catch (Exception e) {
                        log.errorf("Error querying action definition for tenantId %s actionPlugin %s and actionId %s", tenantId, actionPlugin, actionId);
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                    if (actionDefinition == null) {
                        throw new ResponseUtil.NotFoundException("Not action found for actionPlugin: " + actionPlugin + " and actionId: " + actionId);
                    }
                    future.complete(actionDefinition);
                }, res -> ResponseUtil.result(routing, res));
    }

    @DocPath(method = DELETE,
            path = "/{actionPlugin}/{actionId}",
            name = "Delete an existing action definition.")
    @DocParameters(value = {
            @DocParameter(name = "actionPlugin", required = true, path = true,
                    description = "Action plugin."),
            @DocParameter(name = "actionId", required = true, path = true,
                    description = "Action id to be retrieved")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "ActionDefinition Deleted.", response = ActionDefinition.class),
            @DocResponse(code = 404, message = "No Action found.", response = ApiError.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void deleteActionDefinition(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    String actionPlugin = routing.request().getParam("actionPlugin");
                    String actionId = routing.request().getParam("actionId");
                    ActionDefinition found;
                    try {
                        found = definitionsService.getActionDefinition(tenantId, actionPlugin, actionId);
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                    if (found == null) {
                        throw new ResponseUtil.NotFoundException("ActionPlugin: " + actionPlugin + " ActionId: " + actionId + " not found for delete");
                    }
                    try {
                        definitionsService.removeActionDefinition(tenantId, actionPlugin, actionId);
                        log.debugf("ActionPlugin: %s ActionId: %s", actionPlugin, actionId);
                        future.complete(found);
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                }, res -> ResponseUtil.result(routing, res));
    }

    ActionsCriteria buildCriteria(MultiMap params) {
        ActionsCriteria criteria = new ActionsCriteria();
        if (params.get(PARAM_START_TIME) != null) {
            criteria.setStartTime(Long.valueOf(params.get(PARAM_START_TIME)));
        }
        if (params.get(PARAM_END_TIME) != null) {
            criteria.setEndTime(Long.valueOf(params.get(PARAM_END_TIME)));
        }
        if (params.get(PARAM_ACTION_PLUGINS) != null) {
            criteria.setActionPlugins(Arrays.asList(params.get(PARAM_ACTION_PLUGINS).split(COMMA)));
        }
        if (params.get(PARAM_ACTION_IDS) != null) {
            criteria.setActionIds(Arrays.asList(params.get(PARAM_ACTION_IDS).split(COMMA)));
        }
        if (params.get(PARAM_ALERTS_IDS) != null) {
            criteria.setEventIds(Arrays.asList(params.get(PARAM_ALERTS_IDS).split(COMMA)));
        }
        if (params.get(PARAM_EVENT_IDS) != null) {
            criteria.setEventIds(Arrays.asList(params.get(PARAM_EVENT_IDS).split(COMMA)));
        }
        if (params.get(PARAM_RESULTS) != null) {
            criteria.setResults(Arrays.asList(params.get(PARAM_RESULTS).split(COMMA)));
        }
        return criteria;
    }
}
