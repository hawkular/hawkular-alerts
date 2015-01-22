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
package org.hawkular.alerts.api.services;

import org.hawkular.alerts.api.model.notification.Notification;

/**
 * A listener that will process a notification sent to the NotificationService.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public interface NotifierListener {

    /**
     * Process a notification sent to {@link org.hawkular.alerts.api.services.NotifierListener}.
     * It is responsability of NotifierListener implementation how this listener it will be invoked.
     *
     * @param notification Notification to be processed.
     */
    void process(Notification notification);
}
