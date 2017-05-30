package org.hawkular.alerts.handlers;

import static org.hawkular.alerts.api.json.JsonUtil.fromJson;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.model.action.ActionDefinition;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.services.ActionsCriteria;
import org.hawkular.alerts.api.services.ActionsService;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.engine.StandaloneAlerts;
import org.hawkular.alerts.handlers.util.ResponseUtil;
import org.hawkular.handlers.RestEndpoint;
import org.hawkular.handlers.RestHandler;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;

import io.vertx.core.MultiMap;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@RestEndpoint(path = "/actions")
public class ActionsHandler implements RestHandler {
    private static final MsgLogger log = MsgLogging.getMsgLogger(ActionsHandler.class);
    private static final String PARAM_START_TIME = "startTime";
    private static final String PARAM_END_TIME = "endTime";
    private static final String PARAM_ACTION_PLUGINS = "actionPlugins";
    private static final String PARAM_ACTION_IDS = "actionIds";
    private static final String PARAM_ALERTS_IDS = "alertIds";
    private static final String PARAM_RESULTS = "results";
    private static final String COMMA = ",";

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

    void findActionIds(RoutingContext routing) {
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

    void createActionDefinition(RoutingContext routing) {
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
                    if (ResponseUtil.isEmpty(actionDefinition.getActionPlugin())) {
                        throw new ResponseUtil.BadRequestException("actionPlugin must be not null");
                    }
                    if (ResponseUtil.isEmpty(actionDefinition.getActionId())) {
                        throw new ResponseUtil.BadRequestException("actionId must be not null");
                    }
                    if (ResponseUtil.isEmpty(actionDefinition.getProperties())) {
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

    void updateActionDefinition(RoutingContext routing) {
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
                    if (ResponseUtil.isEmpty(actionDefinition.getActionPlugin())) {
                        throw new ResponseUtil.BadRequestException("actionPlugin must be not null");
                    }
                    if (ResponseUtil.isEmpty(actionDefinition.getActionId())) {
                        throw new ResponseUtil.BadRequestException("actionId must be not null");
                    }
                    if (ResponseUtil.isEmpty(actionDefinition.getProperties())) {
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

    void findActionsHistory(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    try {
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

    void deleteActionsHistory(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    try {
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

    void findActionIdsByPlugin(RoutingContext routing) {
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

    void getActionDefinition(RoutingContext routing) {
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

    void deleteActionDefinition(RoutingContext routing) {
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
            criteria.setAlertIds(Arrays.asList(params.get(PARAM_ALERTS_IDS).split(COMMA)));
        }
        if (params.get(PARAM_RESULTS) != null) {
            criteria.setResults(Arrays.asList(params.get(PARAM_RESULTS).split(COMMA)));
        }
        return criteria;
    }
}
