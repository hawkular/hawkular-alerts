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
package org.hawkular.alerts.extensions;

import static org.hawkular.alerts.api.model.trigger.Mode.FIRING;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.drools.core.time.impl.PseudoClockScheduler;
import org.hawkular.alerts.api.json.JsonUtil;
import org.hawkular.alerts.api.model.condition.ExternalCondition;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.trigger.FullTrigger;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.junit.Assert;
import org.junit.Test;
import org.kie.api.KieBase;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.kie.internal.KnowledgeBaseFactory;
import org.kie.internal.utils.KieHelper;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class ExpressionTest {

    public static final String TEST_TENANT = "28026b36-8fe4-4332-84c8-524e173a68bf";
    public static final String DATA_ID = "data-id";
    public static final String ALERTER_ID = "EventsAggregation";

    KieBaseConfiguration kieBaseConfiguration;
    KieBase kieBase;
    KieSessionConfiguration kieSessionConf;
    KieSession kieSession;
    List<Event> results;
    PseudoClockScheduler clock;

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    private void startSession(String drl) {
        kieBaseConfiguration = KnowledgeBaseFactory.newKnowledgeBaseConfiguration();
        kieBaseConfiguration.setOption( EventProcessingOption.STREAM );
        kieBase = new KieHelper().addContent(drl, ResourceType.DRL).build(kieBaseConfiguration);
        kieSessionConf = KnowledgeBaseFactory.newKnowledgeSessionConfiguration();
        kieSessionConf.setOption( ClockTypeOption.get( "pseudo" ) );
        kieSession = kieBase.newKieSession(kieSessionConf, null);
        clock = kieSession.getSessionClock();
        // kieSession.addEventListener(new DebugAgendaEventListener());
        // kieSession.addEventListener(new DebugRuleRuntimeEventListener());
        results = new ArrayList<>();
        kieSession.setGlobal("clock", clock);
        kieSession.setGlobal("results", new CepEngine() {
            @Override
            public void sendResult(Event event) {
                results.add(event);
            }

            @Override
            public void updateConditions(String expiration, Collection<FullTrigger> activeTriggers) { }

            @Override
            public void processEvents(TreeSet<Event> events) { }

            @Override
            public void stop() { }
        });
    }

    private void stopSession() {

        kieSession.dispose();

    }

    private void insert(Object... objs) {
        for (Object o : objs) {
            kieSession.insert(o);
        }
        kieSession.fireAllRules();
    }

    @Test
    public void marketingScenarioDsl() {
        Trigger trigger = new Trigger(TEST_TENANT, "marketing-scenario","Marketing Scenario");
        String expression = "event:groupBy(context.accountId):window(time,10s):having(count > 2)";
        ExternalCondition condition = new ExternalCondition(trigger.getId(), FIRING, DATA_ID, ALERTER_ID, expression);
        Expression exp = new Expression(Arrays.asList(new FullTrigger(trigger, null, Arrays.asList(condition))));

        String drl = exp.getDrl();

        System.out.println(drl);

        startSession(drl);

        // Init t0
        clock.setStartupTime(1);
        long now = clock.getCurrentTime();

        // User1 buys 5 times in < 10 seconds
        Event e1 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "E1 - Buy Book");
        e1.addContext("duration", "1000");
        e1.addContext("accountId", "user1");

        // User2 buys 3 times > 10 seconds
        Event e6 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "E6 - Buy Book");
        e6.addContext("duration", "1000");
        e6.addContext("accountId", "user2");

        insert(e1, e6);

        // t0 + 1000
        now = clock.advanceTime(1000, TimeUnit.MILLISECONDS);

        Event e2 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "E2 - Buy Music");
        e2.addContext("duration", "2000");
        e2.addContext("accountId", "user1");

        insert(e2);

        // t0 + 2000
        now = clock.advanceTime(1000, TimeUnit.MILLISECONDS);

        Event e3 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "E3 - Buy Groceries");
        e3.addContext("duration", "1500");
        e3.addContext("accountId", "user1");

        insert(e3);

        // t0 + 3000
        now = clock.advanceTime(1000, TimeUnit.MILLISECONDS);

        Event e4 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "E4 - Buy VideoGames");
        e4.addContext("duration", "3000");
        e4.addContext("accountId", "user1");

        insert(e4);

        // t0 + 4000
        now = clock.advanceTime(1000, TimeUnit.MILLISECONDS);

        Event e5 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "E5 - Buy VideoGames");
        e5.addContext("duration", "3000");
        e5.addContext("accountId", "user1");

        insert(e5);

        // t0 + 5000
        now = clock.advanceTime(1000, TimeUnit.MILLISECONDS);

        Event e7 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "E7 - Buy Music");
        e7.addContext("duration", "2000");
        e7.addContext("accountId", "user2");

        insert(e7);

        // t0 + 11000
        now = clock.advanceTime(6000, TimeUnit.MILLISECONDS);

        Event e8 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "E8 - Buy Groceries");
        e8.addContext("duration", "1500");
        e8.addContext("accountId", "user2");

        insert(e8);

        stopSession();

        Assert.assertEquals(4, results.size());
        results.stream().forEach(e -> {
            Assert.assertEquals("user1", e.getContext().get("accountId"));
            System.out.println("Event: ");
            System.out.println(e);
            System.out.println("Source events:");
            extractEvents(e).stream().forEach(extracted -> {
                System.out.println(extracted);
            });
            System.out.println("\n");
        });
    }

    @Test
    public void fraudScenarioDsl() {
        Trigger trigger = new Trigger(TEST_TENANT, "fraud-scenario", "Fraud Scenario");
        String expression = "event:groupBy(tags.accountId):window(time,10s):having(count > 1, count.tags.location > 1)";
        ExternalCondition condition = new ExternalCondition(trigger.getId(), FIRING, DATA_ID, ALERTER_ID, expression);
        Expression exp = new Expression(Arrays.asList(new FullTrigger(trigger, null, Arrays.asList(condition))));

        String drl = exp.getDrl();

        System.out.println(drl);

        startSession(drl);

        // Init t0
        clock.setStartupTime(1);
        long now = clock.getCurrentTime();

        // User1 buys 5 times in < 10 seconds from different locations
        Event e1 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "Buy Book");
        e1.addTag("duration", "1000");
        e1.addTag("accountId", "user1");
        e1.addTag("location", "ip1");

        // User2 buys 3 times > 10 seconds from single location
        Event e6 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "Buy Book");
        e6.addTag("duration", "1000");
        e6.addTag("accountId", "user2");
        e6.addTag("location", "ip3");

        // User3 buys 5 times in < 10 seconds from single location
        Event e11 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "Buy Book");
        e11.addTag("duration", "1000");
        e11.addTag("accountId", "user3");
        e11.addTag("location", "ip10");

        insert(e1, e6, e11);

        // t0 + 1000
        now = clock.advanceTime(1000, TimeUnit.MILLISECONDS);

        Event e2 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "Buy Music");
        e2.addTag("duration", "2000");
        e2.addTag("accountId", "user1");
        e2.addTag("location", "ip1");

        Event e12 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "Buy Music");
        e12.addTag("duration", "2000");
        e12.addTag("accountId", "user3");
        e12.addTag("location", "ip10");

        insert(e2, e12);

        // t0 + 2000
        now = clock.advanceTime(1000, TimeUnit.MILLISECONDS);

        Event e3 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "Buy Groceries");
        e3.addTag("duration", "1500");
        e3.addTag("accountId", "user1");
        e3.addTag("location", "ip1");

        Event e13 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "Buy Groceries");
        e13.addTag("duration", "1500");
        e13.addTag("accountId", "user3");
        e13.addTag("location", "ip10");

        insert(e3, e13);

        // t0 + 3000
        now = clock.advanceTime(1000, TimeUnit.MILLISECONDS);

        Event e4 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "Buy VideoGames");
        e4.addTag("duration", "3000");
        e4.addTag("accountId", "user1");
        e4.addTag("location", "ip2");

        Event e14 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "Buy VideoGames");
        e14.addTag("duration", "3000");
        e14.addTag("accountId", "user3");
        e14.addTag("location", "ip10");

        insert(e4, e14);

        // t0 + 4000
        now = clock.advanceTime(1000, TimeUnit.MILLISECONDS);

        Event e5 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "Buy VideoGames");
        e5.addTag("duration", "3000");
        e5.addTag("accountId", "user1");
        e5.addTag("location", "ip1");

        Event e15 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "Buy VideoGames");
        e15.addTag("duration", "3000");
        e15.addTag("accountId", "user3");
        e15.addTag("location", "ip10");

        insert(e5, e15);

        // t0 + 15000
        now = clock.advanceTime(11000, TimeUnit.MILLISECONDS);

        Event e7 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "Buy Music");
        e7.addTag("duration", "2000");
        e7.addTag("accountId", "user2");
        e7.addTag("location", "ip4");

        insert(e7);

        // t0 + 20000
        now = clock.advanceTime(5000, TimeUnit.MILLISECONDS);

        Event e8 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "Buy Groceries");
        e8.addTag("duration", "1500");
        e8.addTag("accountId", "user2");
        e8.addTag("location", "ip5");

        insert(e8);

        stopSession();

        Assert.assertEquals(3, results.size());
        results.stream().forEach(e -> {
            String accountId = e.getContext().get("accountId");
            Assert.assertTrue(accountId.equals("user1") || accountId.equals("user2"));
            System.out.println("Event: ");
            System.out.println(e);
            System.out.println("Source events:");
            extractEvents(e).stream().forEach(extracted -> {
                System.out.println(extracted);
            });
            System.out.println("\n");
        });

    }

    @Test
    public void customerRetentionScenarioDsl() {
        Trigger trigger = new Trigger(TEST_TENANT, "customer-retention-scenario", "Customer Retention Scenario");
        String expression = "event:groupBy(tags.traceId):" +
                "filter((category == \"Credit Check\" && text == \"Exceptionally Good\") || " +
                       "(category == \"Stock Check\" && text == \"Out of Stock\")):" +
                "having(count > 1, count.tags.accountId == 1)";
        ExternalCondition condition = new ExternalCondition(trigger.getId(), FIRING, DATA_ID, ALERTER_ID, expression);
        Expression exp = new Expression(Arrays.asList(new FullTrigger(trigger, null, Arrays.asList(condition))));

        String drl = exp.getDrl();

        System.out.println(drl);

        startSession(drl);

        // Init t0
        clock.setStartupTime(1);
        long now = clock.getCurrentTime();

        Event e1 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "Credit Check", "Exceptionally Good");
        e1.addTag("duration", "1000");
        e1.addTag("traceId", "trace1");
        e1.addTag("accountId", "user1");

        Event e11 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "Credit Check", "Exceptionally Good");
        e11.addTag("duration", "1000");
        e11.addTag("traceId", "trace4");
        e11.addTag("accountId", "user2");

        insert(e1, e11);

        // t0 + 1000
        now = clock.advanceTime(1000, TimeUnit.MILLISECONDS);

        Event e2 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "Stock Check", "Out of Stock");
        e2.addTag("duration", "2000");
        e2.addTag("traceId", "trace1");
        e2.addTag("accountId", "user1");

        Event e12 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "Stock Check", "Out of Stock");
        e12.addTag("duration", "2000");
        e12.addTag("traceId", "trace4");
        e12.addTag("accountId", "user2");

        insert(e2, e12);

        // t0 + 2000
        now = clock.advanceTime(1000, TimeUnit.MILLISECONDS);

        Event e3 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "Credit Check", "Good");
        e3.addTag("duration", "1500");
        e3.addTag("traceId", "trace2");
        e3.addTag("accountId", "user1");

        Event e13 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "Credit Check", "Good");
        e13.addTag("duration", "1500");
        e13.addTag("traceId", "trace5");
        e13.addTag("accountId", "user2");

        insert(e3, e13);

        // t0 + 3000
        now = clock.advanceTime(1000, TimeUnit.MILLISECONDS);

        Event e4 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "Stock Check", "Out of Stock");
        e4.addTag("duration", "2000");
        e4.addTag("traceId", "trace2");
        e4.addTag("accountId", "user1");

        Event e14 = new Event(TEST_TENANT, uuid(), now + 3, DATA_ID, "Stock Check", "Out of Stock");
        e14.addTag("duration", "2000");
        e14.addTag("traceId", "trace5");
        e14.addTag("accountId", "user2");

        insert(e4, e14);

        // t0 + 4000
        now = clock.advanceTime(1000, TimeUnit.MILLISECONDS);

        Event e5 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "Credit Check", "Exceptionally Good");
        e5.addTag("duration", "1500");
        e5.addTag("traceId", "trace3");
        e5.addTag("accountId", "user1");

        Event e15 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "Credit Check", "Exceptionally Good");
        e15.addTag("duration", "1500");
        e15.addTag("traceId", "trace6");
        e15.addTag("accountId", "user2");

        insert(e5, e15);

        // t0 + 5000
        now = clock.advanceTime(1000, TimeUnit.MILLISECONDS);

        Event e6 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "Stock Check", "Available");
        e6.addTag("duration", "2000");
        e6.addTag("traceId", "trace3");
        e6.addTag("accountId", "user1");

        Event e16 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "Stock Check", "Available");
        e16.addTag("duration", "2000");
        e16.addTag("traceId", "trace6");
        e16.addTag("accountId", "user2");

        insert(e6, e16);

        stopSession();

        Assert.assertEquals(2, results.size());
        results.stream().forEach(e -> {
            String traceId = e.getContext().get("traceId");
            Assert.assertTrue(traceId.equals("trace1") || traceId.equals("trace4"));
            System.out.println("Event: ");
            System.out.println(e);
            System.out.println("Source events:");
            extractEvents(e).stream().forEach(extracted -> {
                System.out.println(extracted);
            });
            System.out.println("\n");
        });
    }

    @Test
    public void combinedScenarios() {
        List<FullTrigger> activeTriggers = new ArrayList<>();

        // Marketing
        Trigger trigger = new Trigger(TEST_TENANT, "marketing-scenario","Marketing Scenario");
        String expression = "event:groupBy(tags.accountId):window(time,10s):having(count > 2)";
        ExternalCondition condition = new ExternalCondition(trigger.getId(), FIRING, DATA_ID, ALERTER_ID, expression);
        activeTriggers.add(new FullTrigger(trigger, null, Arrays.asList(condition)));

        // Fraud
        trigger = new Trigger(TEST_TENANT, "fraud-scenario", "Fraud Scenario");
        expression = "event:groupBy(tags.accountId):window(time,10s):having(count > 1, count.tags.location > 1)";
        condition = new ExternalCondition(trigger.getId(), FIRING, DATA_ID, ALERTER_ID, expression);
        activeTriggers.add(new FullTrigger(trigger, null, Arrays.asList(condition)));

        // Customer retention
        trigger = new Trigger(TEST_TENANT, "customer-retention-scenario", "Customer Retention Scenario");
        expression = "event:groupBy(tags.traceId):" +
                "filter((category == \"Credit Check\" && text == \"Exceptionally Good\") || " +
                "(category == \"Stock Check\" && text == \"Out of Stock\")):" +
                "having(count > 1, count.tags.accountId == 1)";
        condition = new ExternalCondition(trigger.getId(), FIRING, DATA_ID, ALERTER_ID, expression);
        activeTriggers.add(new FullTrigger(trigger, null, Arrays.asList(condition)));

        Expression exp = new Expression(activeTriggers);

        String drl = exp.getDrl();

        System.out.println(drl);

        startSession(drl);

        // Init t0
        clock.setStartupTime(1);
        long now = clock.getCurrentTime();

        // User1 buys 5 times in < 10 seconds
        Event a1 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "A1 - Buy Book");
        a1.addContext("duration", "1000");
        a1.addContext("accountId", "user1");

        // User2 buys 3 times > 10 seconds
        Event a6 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "A6 - Buy Book");
        a6.addContext("duration", "1000");
        a6.addContext("accountId", "user2");

        // User1 buys 5 times in < 10 seconds from different locations
        Event b1 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "B1 - Buy Book");
        b1.addTag("duration", "1000");
        b1.addTag("accountId", "user1");
        b1.addTag("location", "ip1");

        // User2 buys 3 times > 10 seconds from single location
        Event b6 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "B6 - Buy Book");
        b6.addTag("duration", "1000");
        b6.addTag("accountId", "user2");
        b6.addTag("location", "ip3");

        // User3 buys 5 times in < 10 seconds from single location
        Event b11 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "B11 - Buy Book");
        b11.addTag("duration", "1000");
        b11.addTag("accountId", "user3");
        b11.addTag("location", "ip10");

        // User1 buys 5 times in < 10 seconds from different locations
        Event e1 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "E1 - Buy Book");
        e1.addTag("duration", "1000");
        e1.addTag("accountId", "user1");
        e1.addTag("location", "ip1");

        // User2 buys 3 times > 10 seconds from single location
        Event e6 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "E6 - Buy Book");
        e6.addTag("duration", "1000");
        e6.addTag("accountId", "user2");
        e6.addTag("location", "ip3");

        // User3 buys 5 times in < 10 seconds from single location
        Event e11 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "E11 - Buy Book");
        e11.addTag("duration", "1000");
        e11.addTag("accountId", "user3");
        e11.addTag("location", "ip10");


        insert(a1, a6, b1, b6, b11, e1, e6, e11);

        // t0 + 1000
        now = clock.advanceTime(1000, TimeUnit.MILLISECONDS);

        Event a2 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "A2 - Buy Music");
        a2.addContext("duration", "2000");
        a2.addContext("accountId", "user1");

        Event b2 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "B2 - Buy Music");
        b2.addTag("duration", "2000");
        b2.addTag("accountId", "user1");
        b2.addTag("location", "ip1");

        Event b12 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "B12 - Buy Music");
        b12.addTag("duration", "2000");
        b12.addTag("accountId", "user3");
        b12.addTag("location", "ip10");

        Event e2 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "E2 - Buy Music");
        e2.addTag("duration", "2000");
        e2.addTag("accountId", "user1");
        e2.addTag("location", "ip1");

        Event e12 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "E12 - Buy Music");
        e12.addTag("duration", "2000");
        e12.addTag("accountId", "user3");
        e12.addTag("location", "ip10");

        insert(a2, b2, b12, e2, e12);

        // t0 + 2000
        now = clock.advanceTime(1000, TimeUnit.MILLISECONDS);

        Event a3 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "A3 - Buy Groceries");
        a3.addContext("duration", "1500");
        a3.addContext("accountId", "user1");

        Event b3 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "B3 - Buy Groceries");
        b3.addTag("duration", "1500");
        b3.addTag("accountId", "user1");
        b3.addTag("location", "ip1");

        Event b13 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "B13 - Buy Groceries");
        b13.addTag("duration", "1500");
        b13.addTag("accountId", "user3");
        b13.addTag("location", "ip10");

        Event e3 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "E3 - Buy Groceries");
        e3.addTag("duration", "1500");
        e3.addTag("accountId", "user1");
        e3.addTag("location", "ip1");

        Event e13 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "E13 - Buy Groceries");
        e13.addTag("duration", "1500");
        e13.addTag("accountId", "user3");
        e13.addTag("location", "ip10");

        insert(a3, b3, b13, e3, e13);

        // t0 + 3000
        now = clock.advanceTime(1000, TimeUnit.MILLISECONDS);

        Event a4 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "A4 - Buy VideoGames");
        a4.addContext("duration", "3000");
        a4.addContext("accountId", "user1");

        Event b4 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "B4 - Buy VideoGames");
        b4.addTag("duration", "3000");
        b4.addTag("accountId", "user1");
        b4.addTag("location", "ip2");

        Event b14 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "B14 - Buy VideoGames");
        b14.addTag("duration", "3000");
        b14.addTag("accountId", "user3");
        b14.addTag("location", "ip10");

        Event e4 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "E4 - Buy VideoGames");
        e4.addTag("duration", "3000");
        e4.addTag("accountId", "user1");
        e4.addTag("location", "ip2");

        Event e14 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "E14 - Buy VideoGames");
        e14.addTag("duration", "3000");
        e14.addTag("accountId", "user3");
        e14.addTag("location", "ip10");

        insert(a4, b4, b14, e4, e14);

        // t0 + 4000
        now = clock.advanceTime(1000, TimeUnit.MILLISECONDS);

        Event a5 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "A5 - Buy VideoGames");
        a5.addContext("duration", "3000");
        a5.addContext("accountId", "user1");

        Event b5 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "B5 - Buy VideoGames");
        b5.addTag("duration", "3000");
        b5.addTag("accountId", "user1");
        b5.addTag("location", "ip1");

        Event b15 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "B15 - Buy VideoGames");
        b15.addTag("duration", "3000");
        b15.addTag("accountId", "user3");
        b15.addTag("location", "ip10");

        Event e5 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "E5 - Buy VideoGames");
        e5.addTag("duration", "3000");
        e5.addTag("accountId", "user1");
        e5.addTag("location", "ip1");

        Event e15 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "E15 - Buy VideoGames");
        e15.addTag("duration", "3000");
        e15.addTag("accountId", "user3");
        e15.addTag("location", "ip10");

        insert(a5, b5, b15, e5, e15);

        // t0 + 5000
        now = clock.advanceTime(1000, TimeUnit.MILLISECONDS);

        Event a7 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "A7 - Buy Music");
        a7.addContext("duration", "2000");
        a7.addContext("accountId", "user2");

        insert(a7);

        // t0 + 11000
        now = clock.advanceTime(6000, TimeUnit.MILLISECONDS);

        Event a8 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "A8 - Buy Groceries");
        a8.addContext("duration", "1500");
        a8.addContext("accountId", "user2");

        insert(a8);

        // t0 + 15000
        now = clock.advanceTime(4000, TimeUnit.MILLISECONDS);

        Event b7 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "B7 - Buy Music");
        b7.addTag("duration", "2000");
        b7.addTag("accountId", "user2");
        b7.addTag("location", "ip4");

        Event e7 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "E7 - Buy Music");
        e7.addTag("duration", "2000");
        e7.addTag("accountId", "user2");
        e7.addTag("location", "ip4");

        insert(b7, e7);

        // t0 + 20000
        now = clock.advanceTime(5000, TimeUnit.MILLISECONDS);

        Event b8 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "B8 - Buy Groceries");
        b8.addTag("duration", "1500");
        b8.addTag("accountId", "user2");
        b8.addTag("location", "ip5");

        Event e8 = new Event(TEST_TENANT, uuid(), now, DATA_ID, "TraceCompletion", "E8 - Buy Groceries");
        e8.addTag("duration", "1500");
        e8.addTag("accountId", "user2");
        e8.addTag("location", "ip5");

        insert(b8, e8);

        stopSession();

        Assert.assertEquals(15, results.size());
        results.stream().forEach(e -> {
            System.out.println("Event: ");
            System.out.println(e);
            System.out.println("Source events:");
            extractEvents(e).stream().forEach(extracted -> {
                System.out.println(extracted);
            });
            System.out.println("\n");
        });
    }

    public static List<Event> extractEvents(Event e) {
        List<Event> events = new ArrayList<>();
        JsonUtil.fromJson(e.getContext().get("events"), ArrayList.class).stream()
                .forEach(o -> {
                    events.add(JsonUtil.fromJson(JsonUtil.toJson(o), Event.class));
                });
        return events;
    }

}
