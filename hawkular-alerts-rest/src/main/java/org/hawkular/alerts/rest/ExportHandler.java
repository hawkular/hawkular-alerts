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

import static org.hawkular.alerts.rest.HawkularAlertsApp.TENANT_HEADER_NAME;

import javax.ejb.EJB;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.hawkular.alerts.api.model.export.Definitions;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.rest.ResponseUtil.ApiError;
import org.jboss.logging.Logger;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * REST endpoint for definitions export tasks.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Path("/export")
@Api(value = "/export", description = "Export of triggers and actions definitions")
public class ExportHandler {
    private final Logger log = Logger.getLogger(ExportHandler.class);

    @HeaderParam(TENANT_HEADER_NAME)
    String tenantId;

    @EJB
    DefinitionsService definitions;

    @GET
    @Path("/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Export a list of full triggers and action definitions.",
            response = Definitions.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully exported list of full triggers and action definitions."),
            @ApiResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public Response exportDefinitions() {
        try {
            Definitions definitions = this.definitions.exportDefinitions(tenantId);
            return ResponseUtil.ok(definitions);
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            return ResponseUtil.internalError(e);
        }
    }
}
