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
package org.hawkular.alerts.actions.tests;

import org.hawkular.alerts.api.model.event.Alert;

/**
 * Common variables for action tests scenarios
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public abstract class CommonData {

    public static final String TEST_TENANT = "test-tenant";
    public static final String ACK_BY = "ack-user";
    public static final String ACK_NOTES = "These ack notes are automatically generated";
    public static final String RESOLVED_BY = "resolve-user";
    public static final String RESOLVED_NOTES = "These resolved notes are automatically generated";

    /**
     * Modify the current alert with ACKNOWLEDGE status.
     * It doesnt clone the alert.
     *
     * @param openAlert the open Alert
     * @return the same alert with status modified to ACKNOWLEDGE
     */
    public static Alert ackAlert(Alert openAlert) {
        if (null == openAlert) return null;
        openAlert.setStatus(Alert.Status.ACKNOWLEDGED);
        openAlert.setAckBy(ACK_BY);
        openAlert.setAckNotes(ACK_NOTES);
        openAlert.setAckTime(System.currentTimeMillis());
        return openAlert;
    }

}
