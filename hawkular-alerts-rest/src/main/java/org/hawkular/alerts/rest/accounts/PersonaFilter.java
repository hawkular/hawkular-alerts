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
package org.hawkular.alerts.rest.accounts;

import java.io.IOException;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.ext.Provider;

import org.hawkular.accounts.api.model.Persona;
import org.hawkular.alerts.rest.HawkularAlertsApp;
import org.hawkular.alerts.rest.ResponseUtil;
import org.jboss.logging.Logger;

/**
 * A Filter to include the integration code with accounts.
 *
 * @author Lucas Ponce
 */
@Provider
public class PersonaFilter implements ContainerRequestFilter {
    private final Logger log = Logger.getLogger(PersonaFilter.class);

    @Inject
    Instance<Persona> personaInstance;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (!checkPersona()) {
            requestContext.abortWith(ResponseUtil.internalError("No persona found"));
        }
        requestContext.getHeaders().putSingle(HawkularAlertsApp.TENANT_HEADER_NAME,
                personaInstance.get().getIdAsUUID().toString());
    }

    private boolean checkPersona() {
        if (personaInstance == null || personaInstance.get() == null) {
            log.warn("Persona is null. Possible issue with accounts integration ? ");
            return false;
        }
        if (personaInstance.get().getIdAsUUID() == null) {
            log.warn("Persona is empty. Possible issue with accounts integration ? ");
            return false;
        }
        return true;
    }
}
