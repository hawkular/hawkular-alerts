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

import java.util.List;

/**
 * A listener for reacting to definitions changes.
 *
 * {@code DefinitionsListener} are registered via {@code DefinitionsService}.
 *
 * {@code DefinitionsListener} are invoked locally on the node which performs the definitions operation,
 * in distributed scenarios these events are not propagated and others nodes are not aware of the changes.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public interface DefinitionsListener {

    /**
     * React to one or more definitions change events sent to {@link DefinitionsListener}.  Multiple events may be
     * received in one notification due to several updates being imported in a batch.
     *
     * @param events change events triggering the notification.
     */
    void onChange(List<DefinitionsEvent> events);
}
