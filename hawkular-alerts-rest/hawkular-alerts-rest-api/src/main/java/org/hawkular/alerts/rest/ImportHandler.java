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
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.hawkular.alerts.api.model.export.Definitions;
import org.hawkular.alerts.api.model.export.ImportType;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.rest.ResponseUtil.ApiError;
import org.jboss.logging.Logger;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * REST endpoint for definitions import tasks.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Path("/import")
@Api(value = "/import", description = "Import of triggers and actions definitions")
public class ImportHandler {
    private final Logger log = Logger.getLogger(ImportHandler.class);

    @HeaderParam(TENANT_HEADER_NAME)
    String tenantId;

    @EJB
    DefinitionsService definitions;

    @POST
    @Path("/{strategy}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Import a list of full triggers and action definitions.",
            notes = "Return a list of effectively imported full triggers and action definitions. + \n" +
                    " + \n" +
                    "Import options: + \n" +
                    " + \n" +
                    "DELETE + \n" +
                    "" +
                    " + \n" +
                    "Existing data in the backend is DELETED before the import operation. + \n" +
                    "All <<FullTrigger>> and <<ActionDefinition objects>> defined in the <<Definitions>> parameter " +
                    "are imported. + \n" +
                    " + \n" +
                    "ALL + \n" +
                    " + \n" +
                    "Existing data in the backend is NOT DELETED before the import operation. + \n" +
                    "All <<FullTrigger>> and <<ActionDefinition>> objects defined in the <<Definitions>> parameter " +
                    "are imported. + \n" +
                    "Existing <<FullTrigger>> and <<ActionDefinition>> objects are overwritten with new values " +
                    "passed in the <<Definitions>> parameter." +
                    " + \n" +
                    "NEW + \n" +
                    " + \n" +
                    "Existing data in the backend is NOT DELETED before the import operation. + \n" +
                    "Only NEW <<FullTrigger>> and <<ActionDefinition>> objects defined in the <<Definitions>> " +
                    "parameters are imported. + \n" +
                    "Existing <<FullTrigger>> and <<ActionDefinition>> objects are maintained in the backend. + \n" +
                    " + \n" +
                    "OLD + \n" +
                    "Existing data in the backend is NOT DELETED before the import operation. + \n" +
                    "Only <<FullTrigger>> and <<ActionDefinition>> objects defined in the <<Definitions>> parameter " +
                    "that previously exist in the backend are imported and overwritten. + \n" +
                    "New <<FullTrigger>> and <<ActionDefinition>> objects that don't exist previously in the " +
                    "backend are ignored. + \n" +
                    " + \n",
            response = Definitions.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully exported list of full triggers and action definitions."),
            @ApiResponse(code = 500, message = "Internal server error.", response = ApiError.class),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters", response = ApiError.class)
    })
    public Response importDefinitions(
            @ApiParam(value = "Import strategy.", required = true,
                allowableValues = "DELETE,ALL,NEW,OLD")
            @PathParam("strategy")
            final String strategy,
            @ApiParam(value = "Collection of full triggers and action definitions to import.")
            final Definitions definitions) {
        try {
            ImportType importType = ImportType.valueOf(strategy.toUpperCase());
            Definitions imported = this.definitions.importDefinitions(tenantId, definitions, importType);
            return ResponseUtil.ok(imported);
        } catch (IllegalArgumentException e) {
            return ResponseUtil.badRequest("Bad argument: " + e.getMessage());
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            return ResponseUtil.internalError(e);
        }
    }

}
