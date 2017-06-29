package org.hawkular.alerts.handlers;

import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;
import org.hawkular.handlers.RestEndpoint;
import org.hawkular.handlers.RestHandler;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
// [lponce] It is not really a Rest endpoint but we can reuse the way we define the route in our hawkular frameworks
@RestEndpoint(path = "/ui")
public class UIHandler implements RestHandler {
    private static final MsgLogger log = MsgLogging.getMsgLogger(UIHandler.class);

    @Override
    public void initRoutes(String baseUrl, Router router) {
        String path = baseUrl + "/ui/*";
        router.route(path).handler(StaticHandler.create()::handle);
    }
}
