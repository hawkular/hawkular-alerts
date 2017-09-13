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
package org.hawkular.alerts.engine.impl;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hawkular.alerts.engine.impl.PartitionManagerImpl.PartitionEntry;
import org.junit.Test;

/**
 * Testing redistribution of buckets on topology changes.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class BucketsTest {

    @Test
    public void emptyOldBuckets() {

        PartitionManagerImpl pm = new PartitionManagerImpl();
        try {
            pm.updateBuckets(null, null);
            fail("It should faild with null newMembers");
        } catch (Exception expected) {}

        try {
            pm.updateBuckets(null, new ArrayList<>());
            fail("It should faild with empty newMembers");
        } catch (Exception expected) {}

        List<Integer> members = Arrays.asList(2001, 3002, 4003, 5004);
        Map<Integer, Integer> newBuckets = pm.updateBuckets(null, members);
        assertEquals(newBuckets.get(0).intValue(), 2001);
        assertEquals(newBuckets.get(1).intValue(), 3002);
        assertEquals(newBuckets.get(2).intValue(), 4003);
        assertEquals(newBuckets.get(3).intValue(), 5004);
    }

    @Test
    public void reassignBuckets() {
        PartitionManagerImpl pm = new PartitionManagerImpl();

        /*
            Old: {0=server0, 1=server1, 2=server2, 3=server3, 4=server4, 5=server5, 6=server6, 7=server7, 8=server8}
            Drop server3
            New: {0=server0, 1=server1, 2=server2, 3=server8, 4=server4, 5=server5, 6=server6, 7=server7}
         */
        Map<Integer, Integer> oldBuckets = new HashMap<>();
        oldBuckets.put(0, 1000);
        oldBuckets.put(1, 1001);
        oldBuckets.put(2, 1002);
        oldBuckets.put(3, 1003);
        oldBuckets.put(4, 1004);
        oldBuckets.put(5, 1005);
        oldBuckets.put(6, 1006);
        oldBuckets.put(7, 1007);
        oldBuckets.put(8, 1008);

        List<Integer> members = Arrays.asList(1000, 1001, 1002, 1004, 1005, 1006, 1007, 1008);

        Map<Integer, Integer> newBuckets = pm.updateBuckets(oldBuckets, members);

        assertEquals(newBuckets.get(0).intValue(), 1000);
        assertEquals(newBuckets.get(1).intValue(), 1001);
        assertEquals(newBuckets.get(2).intValue(), 1002);
        assertEquals(newBuckets.get(3).intValue(), 1008);
        assertEquals(newBuckets.get(4).intValue(), 1004);
        assertEquals(newBuckets.get(5).intValue(), 1005);
        assertEquals(newBuckets.get(6).intValue(), 1006);
        assertEquals(newBuckets.get(7).intValue(), 1007);

        /*
            Old: {0=server0, 1=server1, 2=server2, 3=server8, 4=server4, 5=server5, 6=server6, 7=server7}
            Drop server0
            New: {0=server7, 1=server1, 2=server2, 3=server8, 4=server4, 5=server5, 6=server6}
         */
        oldBuckets = newBuckets;
        members = Arrays.asList(1001, 1002, 1004, 1005, 1006, 1007, 1008);

        newBuckets = pm.updateBuckets(oldBuckets, members);

        assertEquals(newBuckets.get(0).intValue(), 1007);
        assertEquals(newBuckets.get(1).intValue(), 1001);
        assertEquals(newBuckets.get(2).intValue(), 1002);
        assertEquals(newBuckets.get(3).intValue(), 1008);
        assertEquals(newBuckets.get(4).intValue(), 1004);
        assertEquals(newBuckets.get(5).intValue(), 1005);
        assertEquals(newBuckets.get(6).intValue(), 1006);

        /*
            Old: {0=server7, 1=server1, 2=server2, 3=server8, 4=server4, 5=server5, 6=server6}
            Drops server7, server2, server5
            New: {0=server4, 1=server1, 2=server6, 3=server8}
         */
        oldBuckets = newBuckets;
        members = Arrays.asList(1001, 1004, 1006, 1008);

        newBuckets = pm.updateBuckets(oldBuckets, members);

        assertEquals(newBuckets.get(0).intValue(), 1004);
        assertEquals(newBuckets.get(1).intValue(), 1001);
        assertEquals(newBuckets.get(2).intValue(), 1006);
        assertEquals(newBuckets.get(3).intValue(), 1008);

        /*
            Worst case
            Old: {0=server4, 1=server1, 2=server6, 3=server8}
            Drops everything with new nodes
            New: {0=new1, 1=new2, 2=new3}
         */
        oldBuckets = newBuckets;
        members = Arrays.asList(2001, 2002, 2003);

        newBuckets = pm.updateBuckets(oldBuckets, members);
        assertEquals(newBuckets.get(0).intValue(), 2001);
        assertEquals(newBuckets.get(1).intValue(), 2002);
        assertEquals(newBuckets.get(2).intValue(), 2003);

        /*
            Strange case
            Old: {0=new1, 1=new2, 2=new3}
            Drops new1 and server1 is back
            New: {0=server1, 1=new2, 2=new3}
         */
        oldBuckets = newBuckets;
        members = Arrays.asList(1001, 2002, 2003);

        newBuckets = pm.updateBuckets(oldBuckets, members);
        assertEquals(newBuckets.get(0).intValue(), 1001);
        assertEquals(newBuckets.get(1).intValue(), 2002);
        assertEquals(newBuckets.get(2).intValue(), 2003);

        /*
            Strange case
            Old: {0=server1, 1=new2, 2=new3}
            Drops new2 and new3 and server2 and server3 are back but in different order
            New: {0=server1, 1=server3, 2=server2}
         */
        oldBuckets = newBuckets;
        members = Arrays.asList(1001, 1003, 1002);

        newBuckets = pm.updateBuckets(oldBuckets, members);
        assertEquals(newBuckets.get(0).intValue(), 1001);
        assertEquals(newBuckets.get(1).intValue(), 1003);
        assertEquals(newBuckets.get(2).intValue(), 1002);

        /*
            Strange case
            No changes, but members order is different
            Result: bucket should not be affected
         */
        oldBuckets = newBuckets;
        members = Arrays.asList(1003, 1002, 1001);

        newBuckets = pm.updateBuckets(oldBuckets, members);
        assertEquals(newBuckets.get(0).intValue(), 1001);
        assertEquals(newBuckets.get(1).intValue(), 1003);
        assertEquals(newBuckets.get(2).intValue(), 1002);

        /*
            Normal case
            New nodes new1, new2, new2 joining
            New: {0=server1, 1=server3, 2=server2, 3=new1, 4=new2, 5=new3}
         */
        oldBuckets = newBuckets;
        members = Arrays.asList(1003, 1002, 1001, 2001, 2002, 2003);

        newBuckets = pm.updateBuckets(oldBuckets, members);
        assertEquals(newBuckets.get(0).intValue(), 1001);
        assertEquals(newBuckets.get(1).intValue(), 1003);
        assertEquals(newBuckets.get(2).intValue(), 1002);
        assertEquals(newBuckets.get(3).intValue(), 2001);
        assertEquals(newBuckets.get(4).intValue(), 2002);
        assertEquals(newBuckets.get(5).intValue(), 2003);
    }

    @Test
    public void distributeLocalPartitions() {
        PartitionManagerImpl pm = new PartitionManagerImpl();

        PartitionEntry[] entries = new PartitionEntry[10];
        Map<PartitionEntry, Integer> previous = new HashMap<>();
        Map<PartitionEntry, Integer> current = new HashMap<>();
        for (int i = 0; i < entries.length; i++) {
            entries[i] = new PartitionEntry("tenant", "t" + i);
            previous.put(entries[i], 1);
            if (i < 4) {
                current.put(entries[i], 1);
            } else {
                current.put(entries[i], 2);
            }
        }
        Map<String, Map<String, List<String>>> node1 = pm.getAddedRemovedPartition(previous, current, 1);
        Map<String, Map<String, List<String>>> node2 = pm.getAddedRemovedPartition(previous, current, 2);
        assertEquals(node1.get("added").size(), 0);
        assertEquals(node1.get("removed").get("tenant").size(), 6);
        assertEquals(node2.get("removed").size(), 0);
        assertEquals(node2.get("added").get("tenant").size(), 6);
    }

}
