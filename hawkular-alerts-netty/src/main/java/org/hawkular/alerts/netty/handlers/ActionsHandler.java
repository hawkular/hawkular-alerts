package org.hawkular.alerts.netty.handlers;

import static org.hawkular.alerts.api.json.JsonUtil.fromJson;
import static org.hawkular.alerts.netty.util.ResponseUtil.checkTenant;
import static org.hawkular.alerts.netty.util.ResponseUtil.extractPaging;
import static org.hawkular.alerts.netty.util.ResponseUtil.isEmpty;
import static org.hawkular.alerts.netty.util.ResponseUtil.result;

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
import org.hawkular.alerts.log.MsgLogger;
import org.hawkular.alerts.netty.RestEndpoint;
import org.hawkular.alerts.netty.RestHandler;
import org.hawkular.alerts.netty.util.ResponseUtil.BadRequestException;
import org.hawkular.alerts.netty.util.ResponseUtil.InternalServerException;
import org.hawkular.alerts.netty.util.ResponseUtil.NotFoundException;
import org.jboss.logging.Logger;

import io.vertx.core.MultiMap;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@RestEndpoint(path = "/actions")
public class ActionsHandler implements RestHandler {
    private static final MsgLogger log = Logger.getMessageLogger(MsgLogger.class, ActionsHandler.class.getName());
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
                    String tenantId = checkTenant(routing);
                    try {
                        Map<String, Set<String>> actions = definitionsService.getActionDefinitionIds(tenantId);
                        log.debugf("Actions: %s", actions);
                        future.complete(actions);
                    } catch (Exception e) {
                        log.errorf(e, "Error querying actions ids for tenantId %s. Reason: %s", tenantId, e.toString());
                        future.fail(new InternalServerException(e.toString()));
                    }
                }, res -> result(routing, res));
    }

    void createActionDefinition(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String json = routing.getBodyAsString();
                    ActionDefinition actionDefinition;
                    try {
                        actionDefinition = fromJson(json.toString(), ActionDefinition.class);
                    } catch (Exception e) {
                        log.errorf(e, "Error parsing ActionDefinition json: %s. Reason: %s", json, e.toString());
                        throw new BadRequestException(e.toString());
                    }
                    if (actionDefinition == null) {
                        throw new BadRequestException("actionDefinition must be not null");
                    }
                    if (isEmpty(actionDefinition.getActionPlugin())) {
                        throw new BadRequestException("actionPlugin must be not null");
                    }
                    if (isEmpty(actionDefinition.getActionId())) {
                        throw new BadRequestException("actionId must be not null");
                    }
                    if (isEmpty(actionDefinition.getProperties())) {
                        throw new BadRequestException("properties must be not null");
                    }
                    actionDefinition.setTenantId(tenantId);
                    ActionDefinition found;
                    try {
                        found = definitionsService.getActionDefinition(actionDefinition.getTenantId(),
                                actionDefinition.getActionPlugin(), actionDefinition.getActionId());
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                    if (found != null) {
                        throw new BadRequestException("Existing ActionDefinition: " + actionDefinition);
                    }
                    try {
                        definitionsService.addActionDefinition(actionDefinition.getTenantId(), actionDefinition);
                        log.debugf("ActionDefinition: %s", actionDefinition);
                        future.complete(actionDefinition);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    void updateActionDefinition(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String json = routing.getBodyAsString();
                    ActionDefinition actionDefinition;
                    try {
                        actionDefinition = fromJson(json, ActionDefinition.class);
                    } catch (Exception e) {
                        log.errorf(e, "Error parsing ActionDefinition json: %s. Reason: %s", json, e.toString());
                        throw new BadRequestException(e.toString());
                    }
                    if (actionDefinition == null) {
                        throw new BadRequestException("actionDefinition must be not null");
                    }
                    if (isEmpty(actionDefinition.getActionPlugin())) {
                        throw new BadRequestException("actionPlugin must be not null");
                    }
                    if (isEmpty(actionDefinition.getActionId())) {
                        throw new BadRequestException("actionId must be not null");
                    }
                    if (isEmpty(actionDefinition.getProperties())) {
                        throw new BadRequestException("properties must be not null");
                    }
                    actionDefinition.setTenantId(tenantId);
                    ActionDefinition found;
                    try {
                        found = definitionsService.getActionDefinition(actionDefinition.getTenantId(),
                                actionDefinition.getActionPlugin(), actionDefinition.getActionId());
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                    if (found == null) {
                        throw new NotFoundException("ActionDefinition: " + actionDefinition + " not found for update");
                    }
                    try {
                        definitionsService.updateActionDefinition(actionDefinition.getTenantId(), actionDefinition);
                        log.debugf("ActionDefinition: %s", actionDefinition);
                        future.complete(actionDefinition);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    void findActionsHistory(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    try {
                        Pager pager = extractPaging(routing.request().params());
                        ActionsCriteria criteria = buildCriteria(routing.request().params());
                        Page<Action> actionPage = actionsService.getActions(tenantId, criteria, pager);
                        log.debugf("Actions: %s", actionPage);
                        future.complete(actionPage);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    void deleteActionsHistory(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    try {
                        ActionsCriteria criteria = buildCriteria(routing.request().params());
                        int numDeleted = actionsService.deleteActions(tenantId, criteria);
                        log.debugf("Actions deleted: %s", numDeleted);
                        Map<String, String> deleted = new HashMap<>();
                        deleted.put("deleted", String.valueOf(numDeleted));
                        future.complete(deleted);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    void findActionIdsByPlugin(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String actionPlugin = routing.request().getParam("actionPlugin");
                    try {
                        Collection<String> actions = definitionsService.getActionDefinitionIds(tenantId, actionPlugin);
                        log.debugf("Actions: %s", actions);
                        future.complete(actions);
                    } catch (Exception e) {
                        log.errorf(e, "Error querying actions ids for tenantId %s and actionPlugin %s. Reason: %s", tenantId, actionPlugin, e.toString());
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    void getActionDefinition(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String actionPlugin = routing.request().getParam("actionPlugin");
                    String actionId = routing.request().getParam("actionId");
                    ActionDefinition actionDefinition;
                    try {
                        actionDefinition = definitionsService.getActionDefinition(tenantId, actionPlugin, actionId);
                        log.debugf("ActionDefinition: %s", actionDefinition);
                    } catch (Exception e) {
                        log.errorf("Error querying action definition for tenantId %s actionPlugin %s and actionId %s", tenantId, actionPlugin, actionId);
                        throw new InternalServerException(e.toString());
                    }
                    if (actionDefinition == null) {
                        throw new NotFoundException("Not action found for actionPlugin: " + actionPlugin + " and actionId: " + actionId);
                    }
                    future.complete(actionDefinition);
                }, res -> result(routing, res));
    }

    void deleteActionDefinition(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String actionPlugin = routing.request().getParam("actionPlugin");
                    String actionId = routing.request().getParam("actionId");
                    ActionDefinition found;
                    try {
                        found = definitionsService.getActionDefinition(tenantId, actionPlugin, actionId);
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                    if (found == null) {
                        throw new NotFoundException("ActionPlugin: " + actionPlugin + " ActionId: " + actionId + " not found for delete");
                    }
                    try {
                        definitionsService.removeActionDefinition(tenantId, actionPlugin, actionId);
                        log.debugf("ActionPlugin: %s ActionId: %s", actionPlugin, actionId);
                        future.complete(found);
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
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
