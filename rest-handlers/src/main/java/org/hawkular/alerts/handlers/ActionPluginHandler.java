package org.hawkular.alerts.handlers;

import java.util.Collection;
import java.util.Set;

import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.engine.StandaloneAlerts;
import org.hawkular.alerts.handlers.util.ResponseUtil;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;
import org.hawkular.handlers.RestEndpoint;
import org.hawkular.handlers.RestHandler;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@RestEndpoint(path = "/plugins")
public class ActionPluginHandler implements RestHandler {
    private static final MsgLogger log = MsgLogging.getMsgLogger(ActionPluginHandler.class);

    DefinitionsService definitionsService;

    public ActionPluginHandler() {
        definitionsService = StandaloneAlerts.getDefinitionsService();
    }

    @Override
    public void initRoutes(String baseUrl, Router router) {
        String path = baseUrl + "/plugins";
        router.get(path).handler(this::findActionPlugins);
        router.get(path + "/:actionPlugin").handler(this::getActionPlugin);
    }

    void findActionPlugins(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                   ResponseUtil.checkTenant(routing);
                   try {
                       Collection<String> actionPlugins = definitionsService.getActionPlugins();
                       log.debugf("ActionPlugins: %s", actionPlugins);
                        future.complete(actionPlugins);
                   } catch (Exception e) {
                       log.errorf("Error querying all plugins. Reason: %s", e.toString());
                       throw new ResponseUtil.InternalServerException(e.toString());
                   }
                }, res -> ResponseUtil.result(routing, res));
    }

    void getActionPlugin(RoutingContext routing) {
        String actionPlugin = routing.request().getParam("actionPlugin");
        routing.vertx()
                .executeBlocking(future -> {
                    ResponseUtil.checkTenant(routing);
                    Set<String> actionPluginProps;
                    try {
                        actionPluginProps = definitionsService.getActionPlugin(actionPlugin);
                        log.debugf("ActionPlugin: %s - Properties: %s", actionPlugin, actionPluginProps);
                        if (actionPluginProps == null) {
                            future.fail(new ResponseUtil.NotFoundException("Not found action plugin: " + actionPlugin));
                        } else {
                            future.complete(actionPluginProps);
                        }
                    } catch (Exception e) {
                        log.errorf("Error querying plugin %s. Reason: %s", actionPlugin, e.toString());
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                }, res -> ResponseUtil.result(routing, res));
    }
}
