/*
 * Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
