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

import org.hawkular.alerts.api.model.condition.Alert;
import org.hawkular.alerts.api.services.AlertsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.EJB;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
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
public class AlertsHandler {
    private static final Logger log = LoggerFactory.getLogger(AlertsHandler.class);
    private boolean debug = false;

    public AlertsHandler() {
        if (log.isDebugEnabled()) {
            log.debug("Creating instance.");
            debug = true;
        }
    }

    @EJB
    AlertsService alerts;

    @GET
    @Path("/")
    @Produces(APPLICATION_JSON)
    public void findAllAlerts(@Suspended final AsyncResponse response) {
        Collection<Alert> alertList = alerts.checkAlerts();
        if (alertList.isEmpty()) {
            if (debug) {
                log.debug("GET - findAllAlerts - Empty");
            }
            response.resume(Response.status(Response.Status.NO_CONTENT).type(APPLICATION_JSON_TYPE).build());
        } else {
            if (debug) {
                log.debug("GET - findAllAlerts - " + alertList.size() + " alerts");
            }
            response.resume(Response.status(Response.Status.OK).entity(alertList).type(APPLICATION_JSON_TYPE).build());
        }
    }

    @GET
    @Path("/reload")
    public void reloadAlerts(@Suspended final AsyncResponse response) {
        alerts.reload();
        response.resume(Response.status(Response.Status.OK).build());
    }
}
