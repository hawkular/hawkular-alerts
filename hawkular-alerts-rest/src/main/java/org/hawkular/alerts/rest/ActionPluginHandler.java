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

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.jboss.logging.Logger;

import javax.ejb.EJB;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Response;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;

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

    @EJB
    DefinitionsService definitions;

    public ActionPluginHandler() {
        log.debugf("Creating instance.");
    }

    @GET
    @Path("/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Find all action plugins",
                  responseContainer = "Collection<String>",
                  response = String.class,
                  notes = "Pagination is not yet implemented")
    public void findAllActionPlugins(@Suspended final AsyncResponse response) {
        try {
            Collection<String> actionPlugins = definitions.getActionPlugins();
            if (actionPlugins == null || actionPlugins.isEmpty()) {
                log.debugf("GET - findAllActionPlugins - Empty");
                response.resume(Response.status(Response.Status.NO_CONTENT).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("GET - findAllActionPlugins - %s action plugins ", actionPlugins);
                response.resume(Response.status(Response.Status.OK)
                        .entity(actionPlugins).type(APPLICATION_JSON_TYPE).build());
            }
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            Map<String, String> errors = new HashMap<String, String>();
            errors.put("errorMsg", "Internal Error: " + e.getMessage());
            response.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errors).type(APPLICATION_JSON_TYPE).build());
        }
    }

    @GET
    @Path("/{actionPlugin}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Find list of properties to fill for a specific action plugin",
                  responseContainer = "Collection<String>",
                  response = String.class,
                  notes = "Each action plugin can have a different and variable number of properties. " +
                          "This method should be invoked before of a creation of a new action.")
    public void getActionPlugin(@Suspended final AsyncResponse response,
                                @ApiParam(value = "Action plugin to query",
                                          required = true)
                                @PathParam("actionPlugin") final String actionPlugin) {
        try {
            Set<String> actionPluginProps = definitions.getActionPlugin(actionPlugin);
            if (actionPluginProps == null || actionPluginProps.isEmpty()) {
                log.debugf("GET - getActionPlugin - Empty");
                response.resume(Response.status(Response.Status.NO_CONTENT).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("GET - getActionPlugin - actionPlugin: %s - properties: %s ",
                        actionPlugin, actionPluginProps);
                response.resume(Response.status(Response.Status.OK)
                        .entity(actionPluginProps).type(APPLICATION_JSON_TYPE).build());
            }
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            Map<String, String> errors = new HashMap<String, String>();
            errors.put("errorMsg", "Internal Error: " + e.getMessage());
            response.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errors).type(APPLICATION_JSON_TYPE).build());
        }
    }



}
