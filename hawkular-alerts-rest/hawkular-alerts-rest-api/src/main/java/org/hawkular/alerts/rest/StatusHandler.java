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

import org.hawkular.alerts.api.services.StatusService;
import org.hawkular.jaxrs.filter.tenant.TenantRequired;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 * REST endpoint for status
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Path("/status")
@Api(value = "/status", description = "Status of Alerting Service")
@TenantRequired(false)
public class StatusHandler {
    private static final String STATUS = "status";
    private static final String STARTED = "STARTED";
    private static final String FAILED = "FAILED";
    private static final String DISTRIBUTED = "distributed";

    @EJB
    StatusService statusService;

    @Inject
    ManifestUtil manifestUtil;

    @GET
    @Path("/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get status info of Alerting Service.",
            notes = "Status fields:" +
                    " + \n" +
                    "{ + \n" +
                    "\"status\":\"<STARTED>|<FAILED>\", + \n" +
                    "\"Implementation-Version\":\"<Version>\", + \n" +
                    "\"Built-From-Git-SHA1\":\"<Git-SHA1>\", + \n" +
                    "\"distributed\":\"<true|false>\", + \n" +
                    "\"members\":\"<comma list of nodes IDs>\" + \n" +
                    "}",
            response = String.class, responseContainer = "Map")
    public Response status(@Context ServletContext servletContext) {
        Map<String, String> status = new HashMap<>();
        status.putAll(manifestUtil.getFrom(servletContext));
        try {
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
        } catch (Exception e) {
            status.put(STATUS, FAILED);
        }
        return ResponseUtil.ok(status);
    }

}
