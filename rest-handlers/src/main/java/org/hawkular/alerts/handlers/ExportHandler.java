package org.hawkular.alerts.handlers;

import static org.hawkular.alerts.api.doc.DocConstants.GET;
import static org.hawkular.alerts.api.doc.DocConstants.POST;

import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.export.Definitions;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.engine.StandaloneAlerts;
import org.hawkular.alerts.api.doc.DocEndpoint;
import org.hawkular.alerts.api.doc.DocParameter;
import org.hawkular.alerts.api.doc.DocParameters;
import org.hawkular.alerts.api.doc.DocPath;
import org.hawkular.alerts.api.doc.DocResponse;
import org.hawkular.alerts.api.doc.DocResponses;
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
@RestEndpoint(path = "/export")
@DocEndpoint(value = "/export", description = "Export of triggers and actions definitions")
public class ExportHandler implements RestHandler {
    private static final MsgLogger log = MsgLogging.getMsgLogger(ExportHandler.class);
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

    @DocPath(method = GET,
            path = "/",
            name = "Export a list of full triggers and action definitions.")
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Successfully exported list of full triggers and action definitions.", response = Definitions.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ResponseUtil.ApiError.class)
    })
    public void exportDefinitions(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    try {
                        Definitions definitions = definitionsService.exportDefinitions(tenantId);
                        future.complete(definitions);
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                }, res -> ResponseUtil.result(routing, res));
    }
}
