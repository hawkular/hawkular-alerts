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

import org.hawkular.alerts.engine.log.MsgLogger;
import org.hawkular.alerts.engine.rules.RulesEngine;
import org.jboss.logging.Logger;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;

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
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(DroolsRulesEngineImpl.class);
    private static final String SESSION_NAME = "hawkular-alerts-engine-session";

    private KieServices ks;
    private KieContainer kc;
    private KieSession kSession;

    public DroolsRulesEngineImpl() {
        log.debugf("Creating instance.");
        ks = KieServices.Factory.get();
        kc = ks.getKieClasspathContainer();
        kSession = kc.newKieSession(SESSION_NAME);
    }

    @Override
    public void addFact(Object fact) {
        log.debugf("Insert %s ", fact);
        kSession.insert(fact);
    }

    @Override
    public void addFacts(Collection facts) {
        for (Object fact : facts) {
            log.debugf("Insert %s ", fact);
            kSession.insert(fact);
        }
    }

    @Override
    public void addGlobal(String name, Object global) {
        log.debugf("Add Global %s = %s", name, global);
        kSession.setGlobal(name, global);
    }

    @Override
    public void clear() {
        for (FactHandle factHandle : kSession.getFactHandles()) {
            log.debugf("Delete %s ", factHandle);
            kSession.delete(factHandle);
        }
    }

    @Override
    public void fire() {
        log.debugf("Firing rules !!");
        kSession.fireAllRules();
    }

    @Override
    public void removeFact(Object fact) {
        FactHandle factHandle = kSession.getFactHandle(fact);
        if (factHandle != null) {
            log.debugf("Delete %s ", factHandle);
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
        log.debugf("Remove Global %s ", name);
        kSession.setGlobal(name, null);
    }

    @Override
    public void reset() {
        log.debugf("Reset session");
        kSession.dispose();
        kSession = kc.newKieSession(SESSION_NAME);
    }
}
