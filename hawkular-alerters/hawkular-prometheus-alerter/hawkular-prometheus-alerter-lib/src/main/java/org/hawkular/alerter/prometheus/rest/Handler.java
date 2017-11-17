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
package org.hawkular.alerter.prometheus.rest;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

import static org.hawkular.alerter.prometheus.ServiceNames.Service.ALERTS_SERVICE;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.ejb.EJB;
import javax.naming.InitialContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.hawkular.alerter.prometheus.ConditionManager;
import org.hawkular.alerter.prometheus.ServiceNames;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.services.AlertsService;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * REST endpoint for Prometheus Handler
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Path("/")
public class Handler {
    private final Logger log = Logger.getLogger(Handler.class);

    @EJB
    ConditionManager conditionManager;

    public Handler() {
        log.debug("Creating instance.");
    }

    @GET
    @Path("/endpoint")
    @Produces(TEXT_PLAIN)
    public Response endpoint() {
        return ok(conditionManager.getPrometheusUrlDefault());
    }

    @GET
    @Path("/status")
    @Produces(APPLICATION_JSON)
    public Response status() {
        return ok("Running");
    }

    @POST
    @Path("/notification")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response handleNotification(
            final String jsonString) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Notification notification = mapper.readValue(jsonString, Notification.class);

            String tenantId = notification.getGroupLabels().getOrDefault("tenant", "prometheus");
            String dataId = notification.getGroupLabels().get("dataId");
            String category = "prometheus";
            String text = notification.getCommonAnnotations().get("description");
            text = null != text ? text : notification.getCommonAnnotations().get("summary");
            text = null != text ? text : notification.getGroupLabels().getOrDefault("alertname", "notification");
            Map<String, String> context = new HashMap<>();
            context.put("alertname", notification.getGroupLabels().getOrDefault("alertname", "unknown"));
            context.put("json", jsonString);
            Event event = new Event(tenantId, UUID.randomUUID().toString(), dataId, category, text, context);

            InitialContext ctx = new InitialContext();
            AlertsService alerts = (AlertsService) ctx.lookup(ServiceNames.getServiceName(ALERTS_SERVICE));

            log.debugf("Adding Prometheus Notification Event %s", event);
            alerts.addEvents(Collections.singleton(event));
            return ok("Success");

        } catch (Exception e) {
            log.warnf("Failure handling Prometheus Notification %s: %s", jsonString, e);
            return badRequest(e.getMessage());
        }
    }

    private static Response ok(Object entity) {
        return Response.status(Response.Status.OK).entity(entity).type(APPLICATION_JSON_TYPE).build();
    }

    public static Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST).entity(message).type(APPLICATION_JSON_TYPE).build();
    }

}
