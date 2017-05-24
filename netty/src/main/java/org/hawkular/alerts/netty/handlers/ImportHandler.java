package org.hawkular.alerts.netty.handlers;

import static org.hawkular.alerts.api.json.JsonUtil.fromJson;
import static org.hawkular.alerts.netty.util.ResponseUtil.checkTenant;
import static org.hawkular.alerts.netty.util.ResponseUtil.result;

import org.hawkular.alerts.api.model.export.Definitions;
import org.hawkular.alerts.api.model.export.ImportType;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.engine.StandaloneAlerts;
import org.hawkular.alerts.log.MsgLogger;
import org.hawkular.alerts.netty.RestEndpoint;
import org.hawkular.alerts.netty.RestHandler;
import org.hawkular.alerts.netty.util.ResponseUtil;
import org.hawkular.alerts.netty.util.ResponseUtil.BadRequestException;
import org.hawkular.alerts.netty.util.ResponseUtil.InternalServerException;
import org.jboss.logging.Logger;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@RestEndpoint(path = "/import")
public class ImportHandler implements RestHandler {
    private static final MsgLogger log = Logger.getMessageLogger(MsgLogger.class, ImportHandler.class.getName());

    DefinitionsService definitionsService;

    public ImportHandler() {
        definitionsService = StandaloneAlerts.getDefinitionsService();
    }

    @Override
    public void initRoutes(String baseUrl, Router router) {
        String path = baseUrl + "/import";
        router.post(path + "/:strategy").handler(this::importDefinitions);
    }

    void importDefinitions(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String json = routing.getBodyAsString();
                    String strategy = routing.request().getParam("strategy");
                    Definitions definitions;
                    try {
                        definitions = fromJson(json, Definitions.class);
                    } catch (Exception e) {
                        log.errorf(e, "Error parsing Definitions json: %s. Reason: %s", json, e.toString());
                        throw new ResponseUtil.NotFoundException(e.toString());
                    }
                    try {
                        ImportType importType = ImportType.valueOf(strategy.toUpperCase());
                        Definitions imported = definitionsService.importDefinitions(tenantId, definitions, importType);
                        future.complete(imported);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException(e.toString());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }
}
