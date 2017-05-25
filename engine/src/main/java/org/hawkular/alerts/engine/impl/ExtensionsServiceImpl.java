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
package org.hawkular.alerts.engine.impl;

import java.util.HashSet;
import java.util.Set;

import org.hawkular.alerts.api.services.DataExtension;
import org.hawkular.alerts.api.services.EventExtension;
import org.hawkular.alerts.api.services.ExtensionsService;
import org.hawkular.alerts.log.MsgLogger;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class ExtensionsServiceImpl implements ExtensionsService {
    private final MsgLogger log = MsgLogger.getLogger(ExtensionsServiceImpl.class);

    Set<DataExtension> dataExtensions;
    Set<EventExtension> eventsExtensions;

    public void init() {
        dataExtensions = new HashSet<>();
        eventsExtensions = new HashSet<>();
    }

    @Override
    public void addExtension(DataExtension extension) {
        log.info("Adding DataExtension {}", extension);
        dataExtensions.add(extension);
    }

    @Override
    public void addExtension(EventExtension extension) {
        log.info("Adding EventExtension {}", extension);
        eventsExtensions.add(extension);
    }

    @Override
    public Set<DataExtension> getDataExtensions() {
        return dataExtensions;
    }

    @Override
    public Set<EventExtension> getEventExtensions() {
        return eventsExtensions;
    }
}
