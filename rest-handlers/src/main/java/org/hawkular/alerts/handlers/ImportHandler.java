package org.hawkular.alerts.handlers;

import static org.hawkular.alerts.api.json.JsonUtil.fromJson;

import org.hawkular.alerts.api.model.export.Definitions;
import org.hawkular.alerts.api.model.export.ImportType;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.engine.StandaloneAlerts;
import org.hawkular.alerts.handlers.util.ResponseUtil;
import org.hawkular.handlers.RestEndpoint;
import org.hawkular.handlers.RestHandler;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@RestEndpoint(path = "/import")
public class ImportHandler implements RestHandler {
    private static final MsgLogger log = MsgLogging.getMsgLogger(ImportHandler.class);

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
                    String tenantId = ResponseUtil.checkTenant(routing);
                    String json = routing.getBodyAsString();
                    String strategy = routing.request().getParam("strategy");
                    Definitions definitions;
                    try {
                        definitions = fromJson(json, Definitions.class);
                    } catch (Exception e) {
                        log.errorf("Error parsing Definitions json: %s. Reason: %s", json, e.toString());
                        throw new ResponseUtil.NotFoundException(e.toString());
                    }
                    try {
                        ImportType importType = ImportType.valueOf(strategy.toUpperCase());
                        Definitions imported = definitionsService.importDefinitions(tenantId, definitions, importType);
                        future.complete(imported);
                    } catch (IllegalArgumentException e) {
                        throw new ResponseUtil.BadRequestException(e.toString());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                }, res -> ResponseUtil.result(routing, res));
    }
}
