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
import java.util.TreeSet;
import java.util.function.Predicate;

import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.event.Event;

/**
 * Interface that defines an abstract API with the rules engine implementation. This is for internal use by the
 * public API implementation.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public interface RulesEngine {

    String MIN_REPORTING_INTERVAL_DATA = "hawkular-alerts.min-reporting-interval-data";
    String MIN_REPORTING_INTERVAL_DATA_ENV = "HAWKULAR_MIN_REPORTING_INTERVAL_DATA";
    String MIN_REPORTING_INTERVAL_DATA_DEFAULT = "1000";

    String MIN_REPORTING_INTERVAL_EVENTS = "hawkular-alerts.min-reporting-interval-events";
    String MIN_REPORTING_INTERVAL_EVENTS_ENV = "HAWKULAR_MIN_REPORTING_INTERVAL_EVENTS";
    String MIN_REPORTING_INTERVAL_EVENTS_DEFAULT = "0";

    void addGlobal(String name, Object global);

    void removeGlobal(String name);

    /**
     * Insert the provided <code>fact</code> into the rules engine. This method is not appropriate for
     * <code>Data</code>.  For <code>Data</code> use {@link #addData(Data)}.
     * @param fact the fact
     * @throws IllegalArgumentException If <code>fact</code> instanceof <code>Data</code>.
     */
    void addFact(Object fact);

    /**
     * @param fact the fact
     * @return The Fact Object representing <code>object</code>, or null if <code>object</code> is
     * not a Fact in the rules engine.
     */
    Object getFact(Object fact);

    /**
     * Retrieves the FactHandle for <code>fact</code> and then deletes the fact from the rules engine.
     * @param fact the fact
     */
    void removeFact(Object fact);

    /**
     * Retrieves the FactHandle for <code>fact</code> and then updates the fact in the rules engine.
     * @param fact the fact
     */
    void updateFact(Object fact);

    /**
     * Insert the provided <code>fact</code> into the rules engine. This method is not appropriate for
     * <code>Data</code>.  For <code>Data</code> use {@link #addData(Collection)}.
     * @param facts the facts
     * @throws IllegalArgumentException If any <code>fact</code> instanceof <code>Data</code>.
     */
    void addFacts(Collection facts);

    /**
     * Retrieves the FactHandles for <code>facts</code> and then deletes the facts from the rules engine.
     * @param facts the facts
     */
    void removeFacts(Collection facts);

    /**
     * Retrieves the FactHandles for <code>facts</code> matching the <code>factFilter</code> and then
     * deletes the facts from the rules engine.
     * @param factFilter the factFilter
     */
    void removeFacts(Predicate<Object> factFilter);

    /**
     * Add to the accumulated <code>Data</code> to be processed the next time {@link #fire()} is called. After the
     * rules are fired on the accumulated <code>Data</code> it will be cleared.
     * @param data the data
     */
    void addData(TreeSet<Data> data);

    /**
     * Add to the accumulated <code>Event</code> to be processed the next time {@link #fire()} is called. After the
     * rules are fired on the accumulated <code>Event</code> it will be cleared.
     * @param events the events
     */
    void addEvents(TreeSet<Event> events);

    /**
     * Fire all rules given the current set of added definitions and the currently accumulated <code>Data</code>.
     */
    void fire();

    /**
     * Fire all rules given the current set of added definitions and facts. Do not add the currently accumulated
     * <code>Data</code>. This is an advanced feature and used only when Facts are manually manipulated.
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
