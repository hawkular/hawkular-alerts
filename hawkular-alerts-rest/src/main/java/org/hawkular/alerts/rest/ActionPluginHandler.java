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
package org.hawkular.alerts.rest;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import java.util.Collection;
import java.util.Set;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.hawkular.accounts.api.model.Persona;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.jboss.logging.Logger;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;

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

    @Inject
    Persona persona;

    @EJB
    DefinitionsService definitions;

    public ActionPluginHandler() {
        log.debugf("Creating instance.");
    }

    @GET
    @Path("/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Find all action plugins",
                  notes = "Pagination is not yet implemented")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success. Plugins found."),
            @ApiResponse(code = 204, message = "Success. No plugins found."),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Response findActionPlugins() {
        if (!checkPersona()) {
            return ResponseUtil.internalError("No persona found");
        }
        try {
            Collection<String> actionPlugins = definitions.getActionPlugins();
            log.debugf("ActionPlugins: %s ", actionPlugins);
            if (isEmpty(actionPlugins)) {
                return ResponseUtil.noContent();
            }
            return ResponseUtil.ok(actionPlugins);
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @GET
    @Path("/{actionPlugin}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Find list of properties to fill for a specific action plugin",
                  notes = "Each action plugin can have a different and variable number of properties. " +
                          "This method should be invoked before of a creation of a new action.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Action Plugin found."),
            @ApiResponse(code = 404, message = "Action Plugin not found."),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Response getActionPlugin(@ApiParam(value = "Action plugin to query", required = true)
                                        @PathParam ("actionPlugin")
                                        final String actionPlugin) {
        if (!checkPersona()) {
            return ResponseUtil.internalError("No persona found");
        }
        try {
            Set<String> actionPluginProps = definitions.getActionPlugin(actionPlugin);
            log.debugf("ActionPlugin: %s - Properties: %s ", actionPlugin, actionPluginProps);
            if (isEmpty(actionPluginProps)) {
                return ResponseUtil.notFound("actionPlugin: " + actionPlugin + " not found");
            }
            return ResponseUtil.ok(actionPluginProps);
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    private boolean checkPersona() {
        if (persona == null) {
            log.warn("Persona is null. Possible issue with accounts integration ? ");
            return false;
        } else if (persona.getId().trim().isEmpty()) {
            log.warn("Persona is empty. Possible issue with accounts integration ? ");
            return false;
        }
        return true;
    }

    private boolean isEmpty(Collection collection) {
        return collection == null || collection.isEmpty();
    }

}
