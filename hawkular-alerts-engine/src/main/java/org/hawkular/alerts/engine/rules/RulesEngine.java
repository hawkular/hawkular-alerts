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
package org.hawkular.alerts.engine.rules;

import java.util.Collection;

/**
 * Interface that defines an abstract API with the rules engine implementation
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public interface RulesEngine {

    void addGlobal(String name, Object global);
    void removeGlobal(String name);

    void addFact(Object fact);
    void removeFact(Object fact);
    void addFacts(Collection facts);
    void removeFacts(Collection facts);

    void fire();

    void clear();

    void reset();
}
