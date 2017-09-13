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
package org.hawkular.alerts.engine.service;

import java.util.Collection;

import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.event.Event;

/**
 * A listener for reacting to partition events related to data and events.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public interface PartitionDataListener {

    /**
     * Invoked when a new collection of Data has been received into the partition.
     *
     * @param data the new data received
     */
    void onNewData(Collection<Data> data);

    /**
     * Invoked when a new collection of Events has been received into the partition.
     *
     * @param events the new events received
     */
    void onNewEvents(Collection<Event> events);
}
