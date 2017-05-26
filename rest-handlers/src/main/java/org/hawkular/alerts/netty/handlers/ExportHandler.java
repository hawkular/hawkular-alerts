package org.hawkular.alerts.netty.handlers;

import static org.hawkular.alerts.netty.util.ResponseUtil.checkTenant;
import static org.hawkular.alerts.netty.util.ResponseUtil.result;

import org.hawkular.alerts.api.model.export.Definitions;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.engine.StandaloneAlerts;
import org.hawkular.alerts.log.MsgLogger;
import org.hawkular.alerts.netty.RestEndpoint;
import org.hawkular.alerts.netty.RestHandler;
import org.hawkular.alerts.netty.util.ResponseUtil.InternalServerException;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@RestEndpoint(path = "/export")
public class ExportHandler implements RestHandler {
    private static final MsgLogger log = MsgLogger.getLogger(ExportHandler.class);
    private static final String ROOT = "/";

    DefinitionsService definitionsService;

    public ExportHandler() {
        definitionsService = StandaloneAlerts.getDefinitionsService();
    }

    @Override
    public void initRoutes(String baseUrl, Router router) {
        String path = baseUrl + "/export";
        router.get(path).handler(this::exportDefinitions);
    }

    void exportDefinitions(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    try {
                        Definitions definitions = definitionsService.exportDefinitions(tenantId);
                        future.complete(definitions);
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }
}
