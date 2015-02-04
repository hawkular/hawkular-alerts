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
package org.hawkular.alerts.bus.init;

import org.hawkular.alerts.api.services.NotificationsService;
import org.hawkular.alerts.bus.sender.NotificationSender;
import org.jboss.logging.Logger;


import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.Singleton;
import javax.ejb.Startup;

/**
 * A helper class to initialize bus callbacks into the alerts engine.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Startup
@Singleton
public class AlertEngineRegister {
    private final Logger log = Logger.getLogger(AlertEngineRegister.class);

    @EJB
    NotificationsService notifications;

    @PostConstruct
    public void init() {
        NotificationSender sender = new NotificationSender();
        notifications.register(sender);
        log.debugf("Registering sender: [%s]", sender);
    }
}
