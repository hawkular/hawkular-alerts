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
package org.hawkular.alerts.engine.tags;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.google.common.collect.Sets;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class AnalyzeTagExpressionTest {
    /*
        Mock data between evaluations and alertIds.
        It will represent a "resolution" of a evaluation.
     */
    Map<String, Set<String>> data = new HashMap<>();
    /*
        Count the number of resolutions done for an expression.
     */
    int count;

    ExpressionTagQueryParser parser = new ExpressionTagQueryParser(new ExpressionTagQueryParser.ExpressionTagResolver() {
        public Set<String> resolve(String prefix) throws Exception {
            count++;
            return data.get(prefix);
        }
        @Override
        public Set<String> resolve(List<String> tokens) throws Exception {
            // Unused for this test
            return null;
        }
    });


    @Test
    public void t00_onlyOneEvaluation() throws Exception {
        // Prepare test data
        data.clear();
        data.put("tagA", Sets.newHashSet());
        data.put("not tagB", Sets.newHashSet("a1", "a2"));
        data.put("tagD", Sets.newHashSet("a1, a2, a3"));
        data.put("not tagC", Sets.newHashSet("a2, a3, a4"));
        count = 0;

        String e1 = "(tagA and not tagB) and (not tagC or tagD)";
        assertTrue(parser.resolve(e1).isEmpty());
        assertEquals(1, count);

        // Prepare test data
        data.clear();
        data.put("tagA", Sets.newHashSet());
        data.put("not tagB", Sets.newHashSet("a1", "a2"));
        data.put("tagC", Sets.newHashSet("a1, a2, a3"));
        count = 0;

        String e2 = "(not tagB or tagC) and tagA";
        assertTrue(parser.resolve(e2).isEmpty());
        assertEquals(1, count);

        // Prepare test data
        data.clear();
        data.put("tagA", Sets.newHashSet());
        data.put("not tagB", Sets.newHashSet("a1", "a2"));
        data.put("not tagC", Sets.newHashSet("a2, a3, a4"));
        data.put("tagD", Sets.newHashSet("a1, a2, a3"));
        count = 0;

        String e3 = "(not tagB and tagA) and (not tagC or tagD)";
        assertTrue(parser.resolve(e3).isEmpty());
        assertEquals(1, count);
    }

    @Test
    public void t01_onlyTwoEvaluations() throws Exception {

        // Prepare test data
        data.clear();
        data.put("tagA", Sets.newHashSet());
        data.put("not tagB", Sets.newHashSet("a1", "a2"));
        count = 0;

        String e1 = "not tagB or tagA";
        assertTrue(parser.resolve(e1).containsAll(Arrays.asList("a1", "a2")));
        assertEquals(2, count);

        // Prepare test data
        data.clear();
        data.put("tagC", Sets.newHashSet("a1"));
        data.put("tagB", Sets.newHashSet());
        data.put("not tagA", Sets.newHashSet("a2"));
        count = 0;

        String e2 = "not tagA and tagB or tagC";
        assertTrue(parser.resolve(e2).containsAll(Arrays.asList("a1")));
        assertEquals(2, count);

        // Prepare test data
        data.clear();
        data.put("tagA = 'abc'", Sets.newHashSet("a1", "a3"));
        data.put("tagB = 'def'", Sets.newHashSet("a2", "a3"));
        count = 0;

        String e3 = "tagA = 'abc' and tagB = 'def'";
        assertTrue(parser.resolve(e3).containsAll(Arrays.asList("a3")));
        assertEquals(2, count);

        data.clear();
        data.put("tagE", Sets.newHashSet("a1", "a3"));
        data.put("tagD", Sets.newHashSet());
        data.put("tagC", Sets.newHashSet("a2", "a3"));
        data.put("not tagA", Sets.newHashSet("a1", "a3"));
        data.put("not tagB", Sets.newHashSet("a1", "a3"));
        count = 0;

        String e4 = "((not tagA and (not tagB and tagC)) and tagD) and tagE";
        assertTrue(parser.resolve(e4).isEmpty());
        assertEquals(2, count);
    }
}
