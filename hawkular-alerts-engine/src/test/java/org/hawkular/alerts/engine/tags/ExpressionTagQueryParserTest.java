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

import static org.hawkular.alerts.engine.tags.ExpressionTagQueryParser.ExpressionTagResolver.getTokens;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Collections;

import org.junit.Test;


/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class ExpressionTagQueryParserTest {

    @SuppressWarnings("unchecked")
    ExpressionTagQueryParser parser = new ExpressionTagQueryParser(prefix -> Collections.EMPTY_SET);

    @Test
    public void t00_singleTagExpression() throws Exception {
        String e1 = "tagA";

        assertEquals("tagA", parser.parse(e1));
        assertEquals("[tagA]", getTokens(parser.parse(e1)).toString());

        String e2 = "not tagA";
        assertEquals("not tagA", parser.parse(e2));
        assertEquals("[not, tagA]", getTokens(parser.parse(e2)).toString());

        String e3 = "tagA  =      'abc'";
        assertEquals("tagA = 'abc'", parser.parse(e3));
        assertEquals("[tagA, =, 'abc']", getTokens(parser.parse(e3)).toString());

        String e4 = " tagA !=   'abc'";
        assertEquals("tagA != 'abc'", parser.parse(e4));
        assertEquals("[tagA, !=, 'abc']", getTokens(parser.parse(e4)).toString());

        String e5 = "tagA IN ['abc', 'def', 'ghi']";
        assertEquals("tagA in ['abc','def','ghi']", parser.parse(e5));
        assertEquals("[tagA, in, ['abc','def','ghi']]", getTokens(parser.parse(e5)).toString());

        String e5s1 = "tagA IN ['abc','def','ghi']";
        assertEquals("tagA in ['abc','def','ghi']", parser.parse(e5s1));
        assertEquals("[tagA, in, ['abc','def','ghi']]", getTokens(parser.parse(e5s1)).toString());

        String e6 = "tagA NOT IN ['abc', 'def', 'ghi']";
        assertEquals("tagA not in ['abc','def','ghi']", parser.parse(e6));
        assertEquals("[tagA, not, in, ['abc','def','ghi']]", getTokens(parser.parse(e6)).toString());

        String e7 = "tagA  =      '*'";
        assertEquals("tagA = '*'", parser.parse(e7));
        assertEquals("[tagA, =, '*']", getTokens(parser.parse(e7)).toString());

        String e8 = "tagA  =      abc";
        assertEquals("tagA = abc", parser.parse(e8));
        assertEquals("[tagA, =, abc]", getTokens(parser.parse(e8)).toString());

        String e9 = " tagA !=   abc";
        assertEquals("tagA != abc", parser.parse(e9));
        assertEquals("[tagA, !=, abc]", getTokens(parser.parse(e9)).toString());

        String e10 = "tagA IN [abc, def, ghi]";
        assertEquals("tagA in [abc,def,ghi]", parser.parse(e10));
        assertEquals("[tagA, in, [abc,def,ghi]]", getTokens(parser.parse(e10)).toString());

        String e11 = "tagA NOT IN [abc, def, ghi]";
        assertEquals("tagA not in [abc,def,ghi]", parser.parse(e11));
        assertEquals("[tagA, not, in, [abc,def,ghi]]", getTokens(parser.parse(e11)).toString());

        String e12 = "tagA  =      *";
        try {
            parser.parse(e12);
            fail("* should be used with single quotes");
        } catch (Exception e) {
            // Expected
        }

        String e13 = "tagA-01";
        assertEquals("tagA-01", parser.parse(e13));
        assertEquals("[tagA-01]", getTokens(parser.parse(e13)).toString());
    }

    @Test
    public void t01_twoTagExpressions() throws Exception {
        String e1 = "tagA and not tagB";
        assertEquals("and(tagA, not tagB)", parser.parse(e1));

        // not tag is heavy, so tagA is prioritized to shortcut the analysis if tagA is empty
        String e2 = "not tagB and tagA";
        assertEquals("and(tagA, not tagB)", parser.parse(e2));

        // not tag is heavy, in or is difficult to optimize but we put at the end of the queue
        String e3 = "not tagB or tagA";
        assertEquals("or(tagA, not tagB)", parser.parse(e3));

        String e4 = "tagA = 'abc' and tagB = 'def'";
        assertEquals("and(tagA = 'abc', tagB = 'def')", parser.parse(e4));
    }

    @Test
    public void t02_defaultGrouping() throws Exception {
        String e1 = "tagA and not tagB or tagC";
        assertEquals("or(tagC, and(tagA, not tagB))", parser.parse(e1));

        String e2 = "not tagA and tagB or tagC";
        assertEquals("or(tagC, and(tagB, not tagA))", parser.parse(e2));

        String e3 = "not tagA or tagB and tagC";
        assertEquals("and(tagC, or(tagB, not tagA))", parser.parse(e3));

    }

    @Test
    public void t03_simpleGrouping() throws Exception {
        String e1 = "tagA and (not tagB or tagC)";
        assertEquals("and(tagA, or(tagC, not tagB))", parser.parse(e1));

        String e2 = "tagA and (not tagB and tagC)";
        assertEquals("and(tagA, and(tagC, not tagB))", parser.parse(e2));

        String e3 = "(not tagB or tagC) and tagA";
        assertEquals("and(tagA, or(tagC, not tagB))", parser.parse(e3));
    }

    @Test
    public void t04_complexGrouping() throws Exception {
        String e1 = "(tagA and not tagB) and (not tagC or tagD)";
        assertEquals("and(and(tagA, not tagB), or(tagD, not tagC))", parser.parse(e1));

        String e2 = "(not tagB and tagA) and (not tagC or tagD)";
        assertEquals("and(and(tagA, not tagB), or(tagD, not tagC))", parser.parse(e2));

        String e3 = "(not tagA and (not tagB and tagC)) and tagD";
        assertEquals("and(tagD, and(and(tagC, not tagB), not tagA))", parser.parse(e3));

        String e4 = "((not tagA and (not tagB and tagC)) and tagD) and tagE";
        assertEquals("and(tagE, and(tagD, and(and(tagC, not tagB), not tagA)))", parser.parse(e4));
    }

    @Test
    public void t06_existUnnecessaryParentheses() throws Exception {
        String e1 = "((tagA and not tagB) and ((not tagC or tagD)))";
        assertEquals("and(and(tagA, not tagB), or(tagD, not tagC))", parser.parse(e1));
    }

    @Test
    public void t07_checkDotsInTags() throws Exception {
        String e1 = "tagA.subA.subsubA";

        assertEquals("tagA.subA.subsubA", parser.parse(e1));
        assertEquals("[tagA.subA.subsubA]", getTokens(parser.parse(e1)).toString());

        String e3 = "tagA.subA.subsubA  =      'abc.abc.abc'";
        assertEquals("tagA.subA.subsubA = 'abc.abc.abc'", parser.parse(e3));
        assertEquals("[tagA.subA.subsubA, =, 'abc.abc.abc']", getTokens(parser.parse(e3)).toString());
    }

    @Test
    public void t08_checkSpacesInTagValues() throws Exception {
        String e1 = "tagA = 'a b'";

        assertEquals("[tagA, =, 'a b']", getTokens(parser.parse(e1)).toString());

        String e2 = "tagA IN ['a b', 'c d'] ";

        assertEquals("[tagA, in, ['a b','c d']]", getTokens(parser.parse(e2)).toString());
    }

    @Test
    public void t09_checkSpecialCharsInValue() throws Exception {
        String e1 = "test_tag = '/t;hawkular/f;my-agent/r;Local%20DMR~~_Server Availability'";

        assertEquals("[test_tag, =, '/t;hawkular/f;my-agent/r;Local%20DMR~~_Server Availability']",
                getTokens(parser.parse(e1)).toString());

        String e2 = "test_tag = '\\/t;hawkular\\/f;my-agent\\/r;Local%20DMR\\~\\~_Server Availability'";

        assertEquals("[test_tag, =, '\\/t;hawkular\\/f;my-agent\\/r;Local%20DMR\\~\\~_Server Availability']",
                getTokens(parser.parse(e2)).toString());
    }
}
