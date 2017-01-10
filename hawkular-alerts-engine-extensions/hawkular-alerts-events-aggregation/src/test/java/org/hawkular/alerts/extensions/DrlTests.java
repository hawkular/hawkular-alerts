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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.drools.core.ClockType;
import org.drools.core.time.impl.PseudoClockScheduler;
import org.hawkular.alerts.api.model.event.Event;
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
public class DrlTests {

    public static final String TEST_TENANT = "28026b36-8fe4-4332-84c8-524e173a68bf";
    public static final String DATA_ID = "data-id";

    KieBaseConfiguration kieBaseConfiguration;
    KieBase kieBase;
    KieSessionConfiguration kieSessionConf;
    KieSession kieSession;
    List<String> results;
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
        kieSession.setGlobal("results", results);
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
    public void marketingScenarioTest() {
        String drl = " import org.hawkular.alerts.api.model.event.Event; \n " +
                " import org.kie.api.time.SessionClock; \n " +
                " import java.util.List; \n " +
                " global java.util.List results; \n" +
                " global SessionClock clock; \n" +
                " declare Event \n" +
                "   @role( event ) \n" +
                "   @expires( 10m ) \n" +
                "   @timestamp( ctime ) \n" +
                " end \n\n" +
                " declare AccountId accountId : String end \n " +
                " rule \"Extract accountId\" \n " +
                " when \n " +
                "   Event( $accountId : context[\"accountId\"] != null ) \n " +
                "   not AccountId ( accountId == $accountId  ) \n " +
                " then \n" +
                "   insert( new AccountId ( $accountId ) ); \n " +
                " end \n " +
                " \n " +
                " rule \"Marketing Scenario\" \n " +
                " when" +
                "   AccountId ( $accountId : accountId ) \n " +
                "   accumulate( $event : Event( context[\"accountId\"] == $accountId) over window:time(10s); \n " +
                "               $sizeEvents : count( $event ), \n" +
                "               $eventList : collectList( $event ); \n" +
                "               $sizeEvents > 2) \n " +
                " then \n " +
                "   System.out.println(\"Clock: \" + clock.getCurrentTime()); \n " +
                "   System.out.println(\"Account: \" + $accountId + \" has \" + $sizeEvents + " +
                "                      \" events in 10s \"); \n " +
                "   System.out.println(\"Events: \"); \n " +
                "   $eventList.stream().forEach(e -> System.out.println(e));" +
                "   results.add( $accountId ); \n " +
                " end \n " +
                " \n ";

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
        Assert.assertTrue(results.contains("user1"));
    }

    @Test
    public void marketingScenarioDebugTest() {
        String drl = " import " + TaggedEvent.class.getCanonicalName() + "; \n " +
                " import org.kie.api.time.SessionClock; \n" +
                " global java.util.List results; \n" +
                " global SessionClock clock; \n " +
                " declare AccountId accountId : String end \n " +
                " declare TaggedEvent \n" +
                "   @role( event ) \n" +
                "   @expires( 10m ) \n" +
                "   @timestamp( ctime ) \n" +
                " end \n\n" +
                " rule \"Extract accountId\" \n " +
                " when \n " +
                "   TaggedEvent( $accountId : tags[\"accountId\"] != null ) \n " +
                "   not AccountId ( accountId == $accountId  ) \n " +
                " then \n" +
                "   insert( new AccountId ( $accountId ) ); \n " +
                " end \n " +
                " \n " +
                " rule \"Marketing Scenario\" \n " +
                " when" +
                "   AccountId ( $accountId : accountId ) \n " +
                "   accumulate( $event : TaggedEvent( tags[\"accountId\"] == $accountId) over window:time(10s); \n " +
                "               $sizeEvents : count( $event ), \n" +
                "               $eventList : collectList( $event ); \n" +
                "               $sizeEvents > 2) \n " +
                " then \n " +
                "   System.out.println(\"Clock: \" + clock.getCurrentTime()); \n" +
                "   System.out.println(\"Account: \" + $accountId + \" has \" + $sizeEvents + " +
                "                      \" events in 10s \"); \n " +
                "   System.out.println(\"Events: \" + $eventList); \n " +
                "   results.add( $accountId ); \n " +
                " end \n " +
                " \n ";

        KieSessionConfiguration sessionConfig = KnowledgeBaseFactory.newKnowledgeSessionConfiguration();
        sessionConfig.setOption( ClockTypeOption.get( ClockType.PSEUDO_CLOCK.getId() ) );

        KieSession kieSession = new KieHelper().addContent( drl, ResourceType.DRL )
                .build( EventProcessingOption.STREAM )
                .newKieSession(sessionConfig, null);

        PseudoClockScheduler clock = kieSession.getSessionClock();

        List<String> results = new ArrayList<String>();
        kieSession.setGlobal( "results", results );
        kieSession.setGlobal( "clock", clock );

        // Init t0
        clock.setStartupTime(1);
        long now = clock.getCurrentTime();

        // User1 buys 5 times in < 10 seconds
        TaggedEvent e1 = new TaggedEvent( "E1" , now ).addTag( "accountId", "user1" );

        // User2 buys 3 times > 10 seconds
        TaggedEvent e6 = new TaggedEvent( "E6", now ).addTag( "accountId", "user2" );

        kieSession.insert(e1);
        kieSession.insert(e6);

        kieSession.fireAllRules();

        // t0 + 1000
        now = clock.advanceTime(1000, TimeUnit.MILLISECONDS);

        TaggedEvent e2 = new TaggedEvent( "2", now ).addTag( "accountId", "user1" );

        kieSession.insert(e2);

        kieSession.fireAllRules();

        // t0 + 2000
        now = clock.advanceTime(1000, TimeUnit.MILLISECONDS);
        TaggedEvent e3 = new TaggedEvent( "3", now ).addTag( "accountId", "user1" );

        kieSession.insert(e3);

        kieSession.fireAllRules();

        // t0 + 3000
        now = clock.advanceTime(1000, TimeUnit.MILLISECONDS);
        TaggedEvent e4 = new TaggedEvent( "4", now ).addTag( "accountId", "user1" );

        kieSession.insert(e4);

        kieSession.fireAllRules();

        // t0 + 4000
        now = clock.advanceTime(1000, TimeUnit.MILLISECONDS);

        TaggedEvent e5 = new TaggedEvent( "5", now ).addTag( "accountId", "user1" );

        kieSession.insert(e5);

        kieSession.fireAllRules();

        // t0 + 5000
        now = clock.advanceTime(1000, TimeUnit.MILLISECONDS);

        TaggedEvent e7 = new TaggedEvent( "7", now ).addTag( "accountId", "user2" );

        kieSession.insert(e7);

        kieSession.fireAllRules();

        // t0 + 11000
        now = clock.advanceTime(6000, TimeUnit.MILLISECONDS);

        TaggedEvent e8 = new TaggedEvent( "8", now ).addTag( "accountId", "user2" );

        kieSession.insert(e8);

        kieSession.fireAllRules();

        kieSession.dispose();

        Assert.assertEquals(4, results.size());
        Assert.assertTrue(results.contains("user1"));
    }

    @Test
    public void fraudScenarioTest() {
        String drl = " import org.hawkular.alerts.api.model.event.Event; \n " +
                " import org.kie.api.time.SessionClock; \n " +
                " import java.util.List; \n " +
                " global java.util.List results; \n" +
                " global SessionClock clock; \n" +
                " declare Event \n" +
                "   @role( event ) \n" +
                "   @expires( 10m ) \n" +
                "   @timestamp( ctime ) \n" +
                " end \n\n" +
                " declare AccountId accountId : String end \n " +
                " rule \"Extract accountId\" \n " +
                " when \n " +
                "   Event( $accountId : tags[\"accountId\"] != null ) \n " +
                "   not AccountId ( accountId == $accountId  ) \n " +
                " then \n" +
                "   insert( new AccountId ( $accountId ) ); \n " +
                " end \n " +
                " \n " +
                " rule \"Fraud Scenario\" \n " +
                " when" +
                "   AccountId ( $accountId : accountId ) \n " +
                "   accumulate( $event : Event( tags[\"accountId\"] == $accountId, $location : tags[\"location\"] ) " +
                "over window:time(10s); \n " +
                "               $sizeEvents : count( $event ), \n" +
                "               $eventList : collectList( $event ), \n" +
                "               $locations : collectSet( $location ); \n" +
                "               $sizeEvents > 1, \n " +
                "               $locations.size > 1 ) \n " +
                " then \n " +
                "   System.out.println(\"Clock: \" + clock.getCurrentTime()); \n " +
                "   System.out.println(\"Account: \" + $accountId + \" has \" + $sizeEvents + " +
                "                      \" events in 10s from these locations: \" + $locations); \n " +
                "   System.out.println(\"Events: \"); \n " +
                "   $eventList.stream().forEach(e -> System.out.println(e));" +
                "   results.add( $accountId ); \n " +
                " end \n " +
                " \n ";

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
        Assert.assertTrue(results.contains("user1"));
    }

    @Test
    public void fraudScenarioDebugTest() {
        String drl = " import " + TaggedEvent.class.getCanonicalName() + "; \n " +
                " import org.kie.api.time.SessionClock; \n " +
                " import java.util.List; \n " +
                " global java.util.List results; \n" +
                " global SessionClock clock; \n" +
                " declare TaggedEvent \n" +
                "   @role( event ) \n" +
                "   @expires( 10m ) \n" +
                "   @timestamp( ctime ) \n" +
                " end \n\n" +
                " declare AccountId accountId : String end \n " +
                " rule \"Extract accountId\" \n " +
                " when \n " +
                "   TaggedEvent( $accountId : tags[\"accountId\"] != null ) \n " +
                "   not AccountId ( accountId == $accountId  ) \n " +
                " then \n" +
                "   insert( new AccountId ( $accountId ) ); \n " +
                " end \n " +
                " \n " +
                " rule \"Fraud Scenario\" \n " +
                " when" +
                "   AccountId ( $accountId : accountId ) \n " +
                "   accumulate( $event : TaggedEvent( tags[\"accountId\"] == $accountId, $location : tags[\"location\"] ) " +
                "over window:time(10s); \n " +
                "               $sizeEvents : count( $event ), \n" +
                "               $eventList : collectList( $event ), \n" +
                "               $locations : collectSet( $location ); \n" +
                "               $sizeEvents > 1, \n " +
                "               $locations.size > 1 ) \n " +
                " then \n " +
                "   System.out.println(\"Clock: \" + clock.getCurrentTime()); \n " +
                "   System.out.println(\"Account: \" + $accountId + \" has \" + $sizeEvents + " +
                "                      \" events in 10s from these locations: \" + $locations); \n " +
                "   System.out.println(\"Events: \"); \n " +
                "   $eventList.stream().forEach(e -> System.out.println(e));" +
                "   results.add( $accountId ); \n " +
                " end \n " +
                " \n ";

        KieSessionConfiguration sessionConfig = KnowledgeBaseFactory.newKnowledgeSessionConfiguration();
        sessionConfig.setOption( ClockTypeOption.get( ClockType.PSEUDO_CLOCK.getId() ) );

        KieSession kieSession = new KieHelper().addContent( drl, ResourceType.DRL )
                .build( EventProcessingOption.STREAM )
                .newKieSession(sessionConfig, null);

        PseudoClockScheduler clock = kieSession.getSessionClock();

        List<String> results = new ArrayList<String>();
        kieSession.setGlobal( "results", results );
        kieSession.setGlobal( "clock", clock );

        System.out.println("[" + new Date() + "] start!");

        // Init t0
        clock.setStartupTime(1);
        long now = clock.getCurrentTime();

        // User1 buys 5 times in < 10 seconds from different locations
        TaggedEvent e1 = new TaggedEvent( "E1" , now ).addTag( "accountId", "user1" ).addTag("location", "ip1");

        // User2 buys 3 times > 10 seconds from single location
        TaggedEvent e6 = new TaggedEvent( "E6" , now ).addTag( "accountId", "user2" ).addTag("location", "ip3");

        // User3 buys 5 times in < 10 seconds from single location
        TaggedEvent e11 = new TaggedEvent( "E11" , now ).addTag( "accountId", "user3" ).addTag("location", "ip10");

        kieSession.insert(e1);
        kieSession.insert(e6);
        kieSession.insert(e11);

        kieSession.fireAllRules();

        // t0 + 1000
        now = clock.advanceTime(1000, TimeUnit.MILLISECONDS);

        TaggedEvent e2 = new TaggedEvent( "E2" , now ).addTag( "accountId", "user1" ).addTag("location", "ip1");
        TaggedEvent e12 = new TaggedEvent( "E12" , now ).addTag( "accountId", "user3" ).addTag("location", "ip10");

        kieSession.insert(e2);
        kieSession.insert(e12);

        kieSession.fireAllRules();

        // t0 + 2000
        now = clock.advanceTime(1000, TimeUnit.MILLISECONDS);

        TaggedEvent e3 = new TaggedEvent( "E3" , now ).addTag( "accountId", "user1" ).addTag("location", "ip1");
        TaggedEvent e13 = new TaggedEvent( "E13" , now ).addTag( "accountId", "user3" ).addTag("location", "ip10");

        kieSession.insert(e3);
        kieSession.insert(e13);

        kieSession.fireAllRules();

        // t0 + 3000
        now = clock.advanceTime(1000, TimeUnit.MILLISECONDS);

        TaggedEvent e4 = new TaggedEvent( "E4" , now ).addTag( "accountId", "user1" ).addTag("location", "ip2");
        TaggedEvent e14 = new TaggedEvent( "E14" , now ).addTag( "accountId", "user3" ).addTag("location", "ip10");

        kieSession.insert(e4);
        kieSession.insert(e14);

        kieSession.fireAllRules();

        // t0 + 4000
        now = clock.advanceTime(1000, TimeUnit.MILLISECONDS);

        TaggedEvent e5 = new TaggedEvent( "E5" , now ).addTag( "accountId", "user1" ).addTag("location", "ip1");
        TaggedEvent e15 = new TaggedEvent( "E15" , now ).addTag( "accountId", "user3" ).addTag("location", "ip10");

        kieSession.insert(e5);
        kieSession.insert(e15);

        kieSession.fireAllRules();

        // t0 + 15000
        now = clock.advanceTime(11000, TimeUnit.MILLISECONDS);

        TaggedEvent e7 = new TaggedEvent( "E7" , now ).addTag( "accountId", "user2" ).addTag("location", "ip4");

        kieSession.insert(e7);

        kieSession.fireAllRules();

        // t0 + 20000
        now = clock.advanceTime(5000, TimeUnit.MILLISECONDS);

        TaggedEvent e8 = new TaggedEvent( "E8" , now ).addTag( "accountId", "user2" ).addTag("location", "ip5");

        kieSession.insert(e8);

        kieSession.fireAllRules();

        kieSession.dispose();

        Assert.assertEquals(3, results.size());
        Assert.assertTrue(results.contains("user1"));
    }

    @Test
    public void customerRetentionScenarioTest() {
        String drl = " import org.hawkular.alerts.api.model.event.Event; \n " +
                " import org.kie.api.time.SessionClock; \n " +
                " import java.util.List; \n " +
                " global java.util.List results; \n" +
                " global SessionClock clock; \n" +
                " declare Event \n" +
                "   @role( event ) \n" +
                "   @expires( 10m ) \n" +
                "   @timestamp( ctime ) \n" +
                " end \n\n" +
                " declare TraceId traceId : String end \n " +
                " rule \"Extract traceId\" \n " +
                " when \n " +
                "   Event( $traceId : tags[\"traceId\"] != null ) \n " +
                "   not TraceId ( traceId == $traceId  ) \n " +
                " then \n" +
                "   insert( new TraceId ( $traceId ) ); \n " +
                " end \n " +
                " \n " +
                " rule \"Custmer Retention Scenario\" \n " +
                " when" +
                "   TraceId ( $traceId : traceId ) \n " +
                "   accumulate( $event : Event( tags[\"traceId\"] == $traceId, \n" +
                "                               (category == \"Credit Check\" && text == \"Exceptionally Good\") || " +
                "                               (category == \"Stock Check\" && text == \"Out of Stock\") ); \n " +
                "               $sizeEvents : count( $event ), \n" +
                "               $eventList : collectList( $event )," +
                "               $users : collectSet( $event.getTags().get(\"accountId\") );" +
                "               $sizeEvents > 1," +
                "               $users.size == 1 ) \n " +
                " then \n " +
                "   System.out.println(\"Clock: \" + clock.getCurrentTime()); \n " +
                "   System.out.println(\"TraceId: \" + $traceId + " +
                "                      \" for user: \" + $users + \" deserves a special offer. \"); " +
                "   System.out.println(\"Events: \"); \n " +
                "   $eventList.stream().forEach(e -> System.out.println(e));" +
                "   results.add( $traceId ); \n " +
                " end \n " +
                " \n ";

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
        Assert.assertTrue(results.contains("trace1"));
        Assert.assertTrue(results.contains("trace4"));
    }

    public static class TaggedEvent {
        private final String id;
        private final long ctime;
        private final Map<String, String> tags = new HashMap<>();

        public TaggedEvent( String id , long ctime) {
            this.id = id;
            this.ctime = ctime;
        }

        public Map<String, String> getTags() {
            return tags;
        }

        public TaggedEvent addTag(String key, String value) {
            tags.put( key, value );
            return this;
        }

        public long getCtime() {
            return ctime;
        }

        @Override
        public String toString() {
            return "TaggedEvent{" +
                    "id='" + id + '\'' +
                    ", ctime=" + ctime +
                    '}';
        }
    }
}
