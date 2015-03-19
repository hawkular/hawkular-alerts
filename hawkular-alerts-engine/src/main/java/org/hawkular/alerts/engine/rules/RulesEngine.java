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
import java.util.function.Predicate;

import org.hawkular.alerts.api.model.data.Data;

/**
 * Interface that defines an abstract API with the rules engine implementation. This is for internal use by the
 * public API implementation.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
/**
 * @author jshaughn
 */
public interface RulesEngine {

    void addGlobal(String name, Object global);

    void removeGlobal(String name);

    /**
     * Insert the provided <code>fact</code> into the rules engine. This method is not appropriate for
     * <code>Data</code>.  For <code>Data</code> use {@link #addData(Data)}.
     * @param facts
     * @throws IllegalArgumentExeption If <code>fact</code> instanceof <code>Data</code>.
     */
    void addFact(Object fact);

    /**
     * @param fact
     * @return The implementation-specific Fact representing <object>, or null if <object> is not a Fact in the
     * rules engine.
     */
    Object getFact(Object fact);

    /**
     * Retrieves the FactHandle for <code>fact</code> and then deletes the fact from the rules engine.
     * @param fact
     */
    void removeFact(Object fact);

    /**
     * Retrieves the FactHandle for <code>fact</code> and then updates the fact in the rules engine.
     * @param fact
     */
    void updateFact(Object fact);

    /**
     * Insert the provided <code>fact</code> into the rules engine. This method is not appropriate for
     * <code>Data</code>.  For <code>Data</code> use {@link #addData(Collection)}.
     * @param facts
     * @throws IllegalArgumentExeption If any <code>fact</code> instanceof <code>Data</code>.
     */
    void addFacts(Collection facts);

    /**
     * Retrieves the FactHandles for <code>facts</code> and then deletes the facts from the rules engine.
     * @param facts
     */
    void removeFacts(Collection facts);

    /**
     * Retrieves the FactHandles for <code>facts</code> matching the <code>factFilter</code> and then
     * deletes the facts from the rules engine.
     * @param factFilter
     */
    void removeFacts(Predicate<Object> factFilter);

    /**
     * Add to the accumulated <code>Data</code> to be processed the next time {@link #fire()} is called. After the
     * rules are fired on the accumulated <code>Data</code> it will be cleared.
     * @param data
     */
    void addData(Data data);

    /**
     * Add to the accumulated <code>Data</code> to be processed the next time {@link #fire()} is called. After the
     * rules are fired on the accumulated <code>Data</code> it will be cleared.
     * @param data
     */
    void addData(Collection<Data> data);

    /**
     * Fire all rules given the current set of added definitions and the currently accumulated <Data>.
     */
    void fire();

    /**
     * Fire all rules given the current set of added definitions and facts. Do not add the currently accumulated <Data>.
     * This is an advanced feature and used only when Facts are manually manipulated.
     */
    void fireNoData();

    /**
     * Deletes all Facts from the rules engine.
     */
    void clear();

    /**
     * Completely reset the rules engine session. Disposes of any existing session before creating a new session.
     */
    void reset();
}
