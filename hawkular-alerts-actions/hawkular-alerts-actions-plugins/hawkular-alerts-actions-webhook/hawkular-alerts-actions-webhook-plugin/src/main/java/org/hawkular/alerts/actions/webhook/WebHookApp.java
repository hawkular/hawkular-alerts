/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.alerts.actions.webhook;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;

import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;

import org.jboss.logging.Logger;

/**
 * Base class for REST WebHook plugin
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@ApplicationPath("/")
public class WebHookApp extends Application {
    private static final Logger log = Logger.getLogger(WebHookApp.class);

    public WebHookApp() {
        log.debug("Hawkular Alerts WebHook starting...");
    }

    public static Response internalError(String message) {
        Map<String, String> errors = new HashMap<>();
        errors.put("errorMsg", "Internal error: " + message);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(errors).type(APPLICATION_JSON_TYPE).build();
    }

    public static Response ok(Object entity) {
        return Response.status(Response.Status.OK).entity(entity).type(APPLICATION_JSON_TYPE).build();
    }
}
