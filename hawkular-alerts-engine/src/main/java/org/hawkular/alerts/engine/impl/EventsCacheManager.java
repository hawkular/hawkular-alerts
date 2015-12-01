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
package org.hawkular.alerts.engine.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.Singleton;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.condition.EventCondition;
import org.hawkular.alerts.api.services.DefinitionsEvent;
import org.hawkular.alerts.api.services.DefinitionsListener;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.jboss.logging.Logger;

/**
 * A helper class to cache active dataIds used for Events
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Singleton
@TransactionAttribute(value= TransactionAttributeType.NOT_SUPPORTED)
public class EventsCacheManager {
    private final Logger log = Logger.getLogger(EventsCacheManager.class);

    Set<String> activeDataIds;

    @EJB
    DefinitionsService definitions;

    @PostConstruct
    public void init() {

        updateActiveIds();

        definitions.registerListener(new DefinitionsListener() {
            @Override
            public void onChange(DefinitionsEvent event) {
                updateActiveIds();
            }
        }, DefinitionsEvent.Type.CONDITION_CHANGE);
    }

    public Set<String> getActiveDataIds() {
        return activeDataIds;
    }

    public void setActiveDataIds(Set<String> activeDataIds) {
        this.activeDataIds = activeDataIds;
    }

    private synchronized void updateActiveIds() {
        Set<String> dataIds = null;
        try {
            Collection<Condition> conditions = definitions.getAllConditions();
            dataIds = new HashSet<>();
            for (Condition c : conditions) {
                if (c instanceof EventCondition) {
                    dataIds.add(c.getDataId());
                    continue;
                }
            }
        } catch (Exception e) {
            log.error("FAILED to load conditions to create Id filters. All data being forwarded to alerting!", e);
            activeDataIds = null;
            return;
        }

        activeDataIds = Collections.unmodifiableSet(dataIds);

        if (log.isDebugEnabled()) {
            log.debug("Updated activeDataIds! " + activeDataIds);
        }
    }
}
