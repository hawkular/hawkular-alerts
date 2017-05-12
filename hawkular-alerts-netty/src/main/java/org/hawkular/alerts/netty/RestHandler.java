package org.hawkular.alerts.netty;

import io.vertx.ext.web.Router;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public interface RestHandler {

    void initRoutes(String baseUrl, Router router);
}
