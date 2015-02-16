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
 * REST endpoint for NotifierTypes
 *
 * @author Lucas Ponce
 */
@Path("/notifierType")
@Api(value = "/notifierType",
     description = "Query operations for notifier type plugins.")
public class NotifierTypeHandler {
    private final Logger log = Logger.getLogger(NotifierTypeHandler.class);

    @EJB
    DefinitionsService definitions;

    public NotifierTypeHandler() {
        log.debugf("Creating instance.");
    }

    @GET
    @Path("/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Find all notifiers types",
                  responseClass = "Collection<String>",
                  notes = "Pagination is not yet implemented")
    public void findAllNotifierTypes(@Suspended final AsyncResponse response) {
        try {
            Collection<String> notifierTypes = definitions.getNotifierTypes();
            if (notifierTypes == null || notifierTypes.isEmpty()) {
                log.debugf("GET - findAllNotifierTypes - Empty");
                response.resume(Response.status(Response.Status.NO_CONTENT).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("GET - findAllNotifierTypes - %s notifier types ", notifierTypes);
                response.resume(Response.status(Response.Status.OK)
                        .entity(notifierTypes).type(APPLICATION_JSON_TYPE).build());
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
    @Path("/{notifierType}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Find list of properties to fill for a specific notifier type",
                  responseClass = "Collection<String>",
                  notes = "Each notifier type can have a different and variable number of properties. " +
                          "This method should be invoked before of a creation of a new notifier.")
    public void getNotifierType(@Suspended final AsyncResponse response,
                                @ApiParam(value = "Notifier type to query",
                                          required = true)
                                @PathParam("notifierType") final String notifierType) {
        try {
            Set<String> notifierTypeProp = definitions.getNotifierType(notifierType);
            if (notifierTypeProp == null || notifierTypeProp.isEmpty()) {
                log.debugf("GET - getNotifierType - Empty");
                response.resume(Response.status(Response.Status.NO_CONTENT).type(APPLICATION_JSON_TYPE).build());
            } else {
                log.debugf("GET - getNotifierType - notifierType: %s - properties: %s ",
                        notifierType, notifierTypeProp);
                response.resume(Response.status(Response.Status.OK)
                        .entity(notifierTypeProp).type(APPLICATION_JSON_TYPE).build());
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
