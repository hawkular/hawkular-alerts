package org.hawkular.alerts.netty.handlers;

import static org.hawkular.alerts.netty.util.ResponseUtil.checkTenant;
import static org.hawkular.alerts.netty.util.ResponseUtil.result;

import java.util.Collection;
import java.util.Set;
import java.util.function.BiFunction;

import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.engine.StandaloneAlerts;
import org.hawkular.alerts.log.MsgLogger;
import org.hawkular.alerts.netty.RestEndpoint;
import org.hawkular.alerts.netty.RestHandler;

import org.hawkular.alerts.netty.util.ResponseUtil.InternalServerException;
import org.hawkular.alerts.netty.util.ResponseUtil.NotFoundException;
import org.jboss.logging.Logger;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@RestEndpoint(path = "/plugins")
public class ActionPluginHandler implements RestHandler {
    private static final MsgLogger log = Logger.getMessageLogger(MsgLogger.class, ActionPluginHandler.class.getName());

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
                   checkTenant(routing);
                   try {
                       Collection<String> actionPlugins = definitionsService.getActionPlugins();
                       log.debugf("ActionPlugins: %s", actionPlugins);
                        future.complete(actionPlugins);
                   } catch (Exception e) {
                       log.errorf(e, "Error querying all plugins. Reason: %s", e.toString());
                       throw new InternalServerException(e.toString());
                   }
                }, res -> result(routing, res));
    }

    void getActionPlugin(RoutingContext routing) {
        String actionPlugin = routing.request().getParam("actionPlugin");
        routing.vertx()
                .executeBlocking(future -> {
                    checkTenant(routing);
                    Set<String> actionPluginProps;
                    try {
                        actionPluginProps = definitionsService.getActionPlugin(actionPlugin);
                        log.debugf("ActionPlugin: %s - Properties: %s", actionPlugin, actionPluginProps);
                        if (actionPluginProps == null) {
                            future.fail(new NotFoundException("Not found action plugin: " + actionPlugin));
                        } else {
                            future.complete(actionPluginProps);
                        }
                    } catch (Exception e) {
                        log.errorf(e, "Error querying plugin %s. Reason: %s", actionPlugin, e.toString());
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }
}
