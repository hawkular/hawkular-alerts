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
package org.hawkular.alerts.engine.exception;

import javax.ejb.ApplicationException;

import org.hawkular.alerts.api.exception.FoundException;

/**
 * Indicates a query for expected data did not return any results. Declared an {@link ApplicationException} because
 * we don't want these to be wrapped or to rollback an ongoing transaction.
 */
@ApplicationException(rollback = false, inherited = true)
public class FoundApplicationException extends FoundException {
    private static final long serialVersionUID = 1L;

    // Default no-arg constructor required by JAXB
    public FoundApplicationException() {
    }

    public FoundApplicationException(String message) {
        super(message);
    }
}