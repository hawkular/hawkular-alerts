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
package org.hawkular.alerts.rest;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import java.util.HashMap;
import java.util.Map;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.jaxrs.filter.tenant.TenantRequired;

import io.swagger.annotations.Api;

/**
 * REST endpoint for status
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Path("/status")
@Api(value = "/status", description = "Status of Alerts Service")
@TenantRequired(false)
public class StatusHandler {
    private static final String STATUS = "status";
    private static final String STARTED = "STARTED";
    private static final String FAILED = "FAILED";

    @EJB
    DefinitionsService definitionsService;

    @Inject
    ManifestUtil manifestUtil;

    @GET
    @Path("/")
    @Produces(APPLICATION_JSON)
    public Response status(@Context ServletContext servletContext) {
        Map<String, String> status = new HashMap<>();
        status.putAll(manifestUtil.getFrom(servletContext));
        try {
            definitionsService.getActionPlugins();
            status.put(STATUS, STARTED);
        } catch (Exception e) {
            status.put(STATUS, FAILED);
        }
        return ResponseUtil.ok(status);
    }

}
