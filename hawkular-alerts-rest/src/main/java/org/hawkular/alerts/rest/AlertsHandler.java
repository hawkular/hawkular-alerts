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
import org.hawkular.alerts.api.model.condition.Alert;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.rest.log.MsgLogger;
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

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;

/**
 * REST endpoint for alerts
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Path("/")
@Api(value = "/", description = "Operations about alerts")
public class AlertsHandler {
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(AlertsHandler.class);

    public AlertsHandler() {
        log.debugf("Creating instance.");
    }

    @EJB
    AlertsService alerts;

    @GET
    @Path("/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Find all alerts",
                  responseClass = "Collection<org.hawkular.alerts.api.model.condition.Alert>",
                  notes = "Pagination is not yet implemented.")
    public void findAllAlerts(@Suspended final AsyncResponse response) {
        Collection<Alert> alertList = alerts.checkAlerts();
        if (alertList.isEmpty()) {
            log.debugf("GET - findAllAlerts - Empty");
            response.resume(Response.status(Response.Status.NO_CONTENT).type(APPLICATION_JSON_TYPE).build());
        } else {
            log.debugf("GET - findAllAlerts - %s alerts", alertList.size());
            response.resume(Response.status(Response.Status.OK).entity(alertList).type(APPLICATION_JSON_TYPE).build());
        }
    }

    @GET
    @Path("/reload")
    @ApiOperation(value = "Reload all definitions into the alerts service",
                  responseClass = "void",
                  notes = "This service is temporal for demos/poc, this functionality will be handled internally" +
                          "between definitions and alerts services")
    public void reloadAlerts(@Suspended final AsyncResponse response) {
        alerts.reload();
        response.resume(Response.status(Response.Status.OK).build());
    }

    @GET
    @Path("/reload/{triggerId}")
    @ApiOperation(value = "Reload a specific trigger into the alerts service",
                  responseClass = "void")
    public void reloadTrigger(@Suspended final AsyncResponse response,
                              @PathParam("triggerId") String triggerId) {
        alerts.reloadTrigger(triggerId);
        response.resume(Response.status(Response.Status.OK).build());
    }
}
