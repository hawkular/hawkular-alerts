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

import org.hawkular.alerts.engine.rules.RulesEngine;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Singleton;
import java.util.Collection;

/**
 * An implementation of RulesEngine based on drools framework.
 *
 * This implementations has an approach of fixed rules based on filesystem.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Singleton
public class DroolsRulesEngineImpl implements RulesEngine {
    private static final Logger log = LoggerFactory.getLogger(DroolsRulesEngineImpl.class);
    private boolean debug = false;
    private static final String SESSION_NAME = "hawkular-alerts-engine-session";

    private KieServices ks;
    private KieContainer kc;
    private KieSession kSession;

    public DroolsRulesEngineImpl() {
        if (log.isDebugEnabled()) {
            debug = true;
            log.debug("Creating instance.");
        }
        ks = KieServices.Factory.get();
        kc = ks.getKieClasspathContainer();
        kSession = kc.newKieSession(SESSION_NAME);
    }

    @Override
    public void addFact(Object fact) {
        if (debug) {
            log.debug("Insert {} ", fact);
        }
        kSession.insert(fact);
    }

    @Override
    public void addFacts(Collection facts) {
        for (Object fact : facts) {
            if (debug) {
                log.debug("Insert {} ", fact);
            }
            kSession.insert(fact);
        }
    }

    @Override
    public void addGlobal(String name, Object global) {
        if (debug) {
            log.debug("Add Global {} = {}", name, global);
        }
        kSession.setGlobal(name, global);
    }

    @Override
    public void clear() {
        for (FactHandle factHandle : kSession.getFactHandles()) {
            if (debug) {
                log.debug("Delete {} ", factHandle);
            }
            kSession.delete(factHandle);
        }
    }

    @Override
    public void fire() {
        if (debug) {
            log.debug("Firing rules !!");
        }
        kSession.fireAllRules();
    }

    @Override
    public void removeFact(Object fact) {
        FactHandle factHandle = kSession.getFactHandle(fact);
        if (factHandle != null) {
            if (debug) {
                log.debug("Delete {} ", factHandle);
            }
            kSession.delete(factHandle);
        }
    }

    @Override
    public void removeFacts(Collection facts) {
        for (Object fact : facts) {
            removeFact(fact);
        }
    }

    @Override
    public void removeGlobal(String name) {
        if (debug) {
            log.debug("Remove Global {} ", name);
        }
        kSession.setGlobal(name, null);
    }

    @Override
    public void reset() {
        if (debug) {
            log.debug("Reset session");
        }
        kSession.dispose();
        kSession = kc.newKieSession(SESSION_NAME);
    }
}
