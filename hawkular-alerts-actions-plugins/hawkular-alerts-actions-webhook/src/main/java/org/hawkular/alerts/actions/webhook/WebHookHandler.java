/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
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

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import static org.hawkular.alerts.actions.webhook.WebHookApp.TENANT_HEADER_NAME;

import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.jboss.logging.Logger;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Path("/")
public class WebHookHandler {
    private static final Logger log = Logger.getLogger(WebHookHandler.class);

    @HeaderParam(TENANT_HEADER_NAME)
    String tenantId;

    @GET
    @Path("/")
    @Produces(APPLICATION_JSON)
    public Response findWebHooks() {
        return WebHookApp.ok(WebHooks.getWebHooks(tenantId));
    }

    @PUT
    @Path("/")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response registerWebHook(final Map<String, String> webhook) {
        if (webhook == null || webhook.isEmpty()) {
            return WebHookApp.badRequest("webhook must be not null");
        }
        if (!webhook.containsKey("url")) {
            return WebHookApp.badRequest("webhook must contain an url");
        }
        String url = webhook.get("url");
        String filter = webhook.containsKey("filter") ? webhook.get("filter") : null;
        try {
            WebHooks.addWebHook(tenantId, filter, url);
            return WebHookApp.ok();
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return WebHookApp.internalError(e.getMessage());
        }
    }

    @DELETE
    @Path("/{url}")
    public Response unregisterWebHook(
            @PathParam("url")
            final String url) {
        if (url == null || url.isEmpty()) {
            return WebHookApp.badRequest("webhook url must be not null");
        }
        try {
            WebHooks.removeWebHook(tenantId, url);
            return WebHookApp.ok();
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return WebHookApp.internalError(e.getMessage());
        }
    }
}
