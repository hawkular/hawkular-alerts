package org.hawkular.alerts.handlers;

import java.util.HashMap;
import java.util.Map;

import org.hawkular.alerts.api.services.StatusService;
import org.hawkular.alerts.engine.StandaloneAlerts;
import org.hawkular.alerts.handlers.util.ManifestUtil;
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
@RestEndpoint(path = "/status")
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
