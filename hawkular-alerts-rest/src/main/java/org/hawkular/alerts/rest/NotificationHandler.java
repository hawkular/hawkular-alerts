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

import org.hawkular.alerts.api.model.notification.Notification;
import org.hawkular.alerts.api.services.NotificationsService;
import org.jboss.logging.Logger;

import javax.ejb.EJB;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * REST endpoint for notifications
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Path("/notifications")
public class NotificationHandler {
    private final Logger log = Logger.getLogger(NotificationHandler.class);

    @EJB
    NotificationsService notifications;

    public NotificationHandler() {
        log.debug("Creating instance.");
    }

    @POST
    @Path("/")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public void notify(@Suspended final AsyncResponse response, Notification notification) {
        notifications.send(notification);
        response.resume(Response.status(Response.Status.OK).build());
    }
}
