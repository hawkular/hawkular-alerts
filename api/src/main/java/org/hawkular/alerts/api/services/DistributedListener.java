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
package org.hawkular.alerts.api.services;

import java.util.Set;

/**
 * A listener for reacting to distribution triggers changes.
 *
 * {@code DistributedListener} are registered via {@code DefinitionsService}.
 *
 * On each node of the cluster, a {@code DistributedListener} is invoked with the {@code DistributedEvent}
 * represeting the changes on the triggers the node should load/unload.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public interface DistributedListener {

    /**
     * React to one or more distribution trigger changes sent to {@link DistributedListener}.
     * Multiple events may be received in one notification as result of a topology change.
     *
     * @param events distributed events triggering the notification.
     */
    void onChange(Set<DistributedEvent> events);
}
