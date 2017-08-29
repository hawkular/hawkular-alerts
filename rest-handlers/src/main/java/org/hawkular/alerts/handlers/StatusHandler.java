package org.hawkular.alerts.handlers;

import static org.hawkular.alerts.api.doc.DocConstants.GET;

import java.util.HashMap;
import java.util.Map;

import org.hawkular.alerts.api.services.StatusService;
import org.hawkular.alerts.engine.StandaloneAlerts;
import org.hawkular.alerts.api.doc.DocEndpoint;
import org.hawkular.alerts.api.doc.DocPath;
import org.hawkular.alerts.api.doc.DocResponse;
import org.hawkular.alerts.api.doc.DocResponses;
import org.hawkular.alerts.handlers.util.ManifestUtil;
import org.hawkular.alerts.handlers.util.ResponseUtil;
import org.hawkular.alerts.handlers.util.ResponseUtil.ApiError;
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
@RestEndpoint(path = "/status")
@DocEndpoint(value = "/status", description = "Status of Alerting Service")
public class StatusHandler implements RestHandler {
    private static final MsgLogger log = MsgLogging.getMsgLogger(StatusHandler.class);
    static final String STATUS = "status";
    static final String STARTED = "STARTED";
    static final String FAILED = "FAILED";
    static final String DISTRIBUTED = "distributed";

    StatusService statusService;
    ManifestUtil manifestUtil;

    public StatusHandler() {
        manifestUtil = new ManifestUtil();
        statusService = StandaloneAlerts.getStatusService();
    }

    @Override
    public void initRoutes(String baseUrl, Router router) {
        String path = baseUrl + "/status";
        router.get(path).handler(this::status);
    }

    @DocPath(method = GET,
            path = "/",
            name = "Get status info of Alerting Service.",
            notes = "Status fields:" +
                    " + \n" +
                    "{ + \n" +
                    "\"status\":\"<STARTED>|<FAILED>\", + \n" +
                    "\"Implementation-Version\":\"<Version>\", + \n" +
                    "\"Built-From-Git-SHA1\":\"<Git-SHA1>\", + \n" +
                    "\"distributed\":\"<true|false>\", + \n" +
                    "\"members\":\"<comma list of nodes IDs>\" + \n" +
                    "}")
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Success, Event Created.", response = String.class, responseContainer = "Map"),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void status(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    Map<String, String> status = new HashMap<>();
                    status.putAll(manifestUtil.getFrom());
                    if (statusService.isStarted()) {
                        status.put(STATUS, STARTED);
                    } else {
                        status.put(STATUS, FAILED);
                    }
                    boolean distributed = statusService.isDistributed();
                    status.put(DISTRIBUTED, Boolean.toString(distributed));
                    if (distributed) {
                        status.putAll(statusService.getDistributedStatus());
                    }
                    future.complete(status);
                }, res -> ResponseUtil.result(routing, res));
    }
}
