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
package org.hawkular.alerts.rest;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import static org.hawkular.alerts.rest.CommonUtil.isEmpty;
import static org.hawkular.alerts.rest.HawkularAlertsApp.TENANT_HEADER_NAME;

import java.util.Collection;
import java.util.Set;

import javax.ejb.EJB;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.rest.ResponseUtil.ApiError;
import org.jboss.logging.Logger;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * REST endpoint for ActionPlugins
 *
 * @author Lucas Ponce
 */
@Path("/plugins")
@Api(value = "/plugins",
     description = "Query operations for action plugins")
public class ActionPluginHandler {
    private final Logger log = Logger.getLogger(ActionPluginHandler.class);

    @HeaderParam(TENANT_HEADER_NAME)
    String tenantId;

    @EJB
    DefinitionsService definitions;

    public ActionPluginHandler() {
        log.debug("Creating instance.");
    }

    @GET
    @Path("/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Find all action plugins.",
            response = String.class, responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully fetched list of actions plugins."),
            @ApiResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public Response findActionPlugins() {
        try {
            Collection<String> actionPlugins = definitions.getActionPlugins();
            if (log.isDebugEnabled()) {
                log.debug("ActionPlugins: " + actionPlugins);
            }
            return ResponseUtil.ok(actionPlugins);
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            return ResponseUtil.internalError(e);
        }
    }

    @GET
    @Path("/{actionPlugin}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Find list of properties to fill for a specific action plugin.",
            notes = "Each action plugin can have a different and variable number of properties. + \n" +
                    "This method should be invoked before of a creation of a new action.",
            response = String.class, responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Action Plugin found."),
            @ApiResponse(code = 404, message = "Action Plugin not found.", response = ApiError.class),
            @ApiResponse(code = 500, message = "Internal server error", response = ApiError.class)
    })
    public Response getActionPlugin(@ApiParam(value = "Action plugin to query.", required = true)
            @PathParam ("actionPlugin")
            final String actionPlugin) {
        try {
            Set<String> actionPluginProps = definitions.getActionPlugin(actionPlugin);
            if (log.isDebugEnabled()) {
                log.debug("ActionPlugin: " + actionPlugin + " - Properties: " + actionPluginProps);
            }
            if (isEmpty(actionPluginProps)) {
                return ResponseUtil.notFound("actionPlugin: " + actionPlugin + " not found");
            }
            return ResponseUtil.ok(actionPluginProps);
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            return ResponseUtil.internalError(e);
        }
    }
}
