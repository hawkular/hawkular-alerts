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

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hawkular.alerts.api.model.condition.ExternalCondition;
import org.hawkular.alerts.api.model.trigger.FullTrigger;
import org.hawkular.alerts.api.model.trigger.Trigger;

/**
 * Represent a DSL expression coming from an ExternalCondition which is parsed into a DRL format understandable
 * by the CEP engine.
 *
 * Expression syntax:
 *
 *  <expression> ::= "event:groupBy(" <field> ")" [ ":window(" <window> ")" ] [ ":filter(" <filter> ]
 *      [ ":having(" <having> ")" ]
 *  <field> ::= [ "tag." | "context." ] <field name>
 *  <window> ::= ( "time," <time_value> | "length," <numeric_value> )
 *  <time_value> ::= [ <numeric_value> "d" ][ <numeric_value> "h" ][ <numeric_value> "m" ][ <numeric_value> "s" ]
 *      [ <numeric_value> [ "ms" ]]
 *  <filter> ::= <drools_expression>
 *  <having> ::= <drools_expression>
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class Expression {

    private static final String DRL_HEADER = "  package org.hawkular.alerts.extension \n" +
            "  import org.hawkular.alerts.api.model.event.Event; \n" +
            "  import org.hawkular.alerts.api.json.JsonUtil; \n" +
            "  import org.hawkular.alerts.extensions.CepEngine; \n" +
            "  import org.kie.api.time.SessionClock; \n" +
            "  import org.jboss.logging.Logger; \n" +
            "  import java.util.List; \n" +
            "  import java.util.UUID; \n\n" +
            "  global Logger log; \n" +
            "  global CepEngine results; \n" +
            "  global SessionClock clock;\n" +
            "  \n";

    private static final String BLANK = "                ";

    private static final String CONTEXT = "context";
    private static final String DEFAULT_EXPIRATION = "30m";

    private static final String FUNCTION_COUNT = "$count : count( $event )";
    private static final String FUNCTION_EVENTS = "$events : collectList( $event )";

    private static final int GROUP_INDEX = 1;

    private static final Pattern SEARCH_CONTEXT = Pattern.compile("context\\.(\\w+)\\s");
    private static final Pattern SEARCH_TAGS = Pattern.compile("tags\\.(\\w+)\\s");

    private static final String TAGS = "tags";

    private static final String TOKEN_COMMA = ",";
    private static final String TOKEN_CONTEXT = CONTEXT + ".";
    private static final String TOKEN_COUNT = "count ";
    private static final String TOKEN_COUNT_CONTEXT = "count.context.";
    private static final String TOKEN_COUNT_TAGS = "count.tags.";
    private static final int    TOKEN_END_PARENTHESIS = ')';
    private static final String TOKEN_EVENT = "event";
    private static final String TOKEN_FILTER = "filter(";
    private static final String TOKEN_GROUP_BY = "groupBy(";
    private static final String TOKEN_HAVING = "having(";
    private static final String TOKEN_LENGTH = "length,";
    private static final String TOKEN_SEPARATOR = ":";
    private static final String TOKEN_TAGS = TAGS + ".";
    private static final String TOKEN_TIME = "time,";
    private static final String TOKEN_WINDOW = "window(";

    private static final String VARIABLE_COUNT = "\\$count ";

    private String expRuleName;
    private String alerterId;
    private String expression;
    private String tenantId;
    private String source;
    private String dataId;

    private Set<String> declareFields = new HashSet<>();
    private Set<String> ruleNames = new HashSet<>();

    private String drlGroupByDeclare;
    private String drlGroupByObject;
    private String drlGroupByConstraint;
    private String drlGroupByResult;
    private String drlWindow;
    private Set<String> drlEventConstraints = new HashSet<>();
    private Set<String> drlFunctions = new HashSet<>();
    private Set<String> drlFunctionsConstraints = new HashSet<>();

    private String drl;

    public Expression(Collection<FullTrigger> activeTriggers) {
        this(null, activeTriggers);
    }

    public Expression(String expiration, Collection<FullTrigger> activeTriggers) {
        if (isEmpty(expiration)) {
            expiration = DEFAULT_EXPIRATION;
        }
        if (isEmpty(activeTriggers)) {
            throw new IllegalArgumentException("ActiveTriggers must be not empty");
        }
        drl = DRL_HEADER + "\n";
        drl += "  declare Event \n" +
               "    @role( event ) \n" +
               "    @expires( " + expiration + " ) \n" +
               "    @timestamp( ctime ) \n" +
               "  end \n\n";
        activeTriggers.stream().forEach(fullTrigger -> {
            fullTrigger.getConditions().forEach(condition -> {
                if (condition instanceof ExternalCondition) {
                    buildTriggerDrl(fullTrigger.getTrigger(), (ExternalCondition) condition);
                    drl += "\n";
                    drlEventConstraints.clear();
                    drlFunctions.clear();
                    drlFunctionsConstraints.clear();
                }
            });
        });
    }

    private void buildTriggerDrl(Trigger trigger, ExternalCondition condition) {
        if (trigger == null || condition == null) {
            throw new IllegalArgumentException("Trigger or Condition must be not null");
        }
        expRuleName = trigger.getName() + "-" + condition.getConditionId();
        alerterId = condition.getAlerterId();
        expression = condition.getExpression();
        tenantId = trigger.getTenantId();
        source = trigger.getSource();
        dataId = condition.getDataId();
        drlWindow = "";

        if (isEmpty(expression)) {
            throw new IllegalArgumentException("Expression must be not null");
        }
        String[] section = expression.split(TOKEN_SEPARATOR);
        if (section.length < 2 || section.length > 5) {
            throw new IllegalArgumentException("Wrong sections for expression [" + expression + "]");
        }
        if (!section[0].equals(TOKEN_EVENT)) {
            throw new IllegalArgumentException("Expression [" + expression + "] must start with 'event'");
        }
        if (!section[1].startsWith(TOKEN_GROUP_BY)) {
            throw new IllegalArgumentException("Expression [" + expression + "] must contain a 'groupBy()' section");
        }
        parseGroupBy(section[1]);

        drlFunctions.add(FUNCTION_EVENTS);

        for (int i = 2; i < section.length; i++) {
            if (section[i].startsWith(TOKEN_WINDOW)) {
                parseWindow(section[i]);
            } else if (section[i].startsWith(TOKEN_FILTER)) {
                parseFilter(section[i]);
            } else if (section[i].startsWith(TOKEN_HAVING)) {
                parseHaving(section[i]);
            } else {
                throw new IllegalArgumentException("Expression [" + expression + "] contains an invalid '" + section[i]
                        + "' section");
            }
        }

        if (!ruleNames.contains(expRuleName)) {
            ruleNames.add(expRuleName);
            addTriggerDrl(expRuleName);
        }
    }

    private void parseGroupBy(String section) {
        int endSection = section.lastIndexOf(TOKEN_END_PARENTHESIS);
        if (endSection == -1) {
            throw new IllegalArgumentException("Expression [" + section + " must contain a valid 'groupBy()'");
        }
        String innerSection = section.substring(TOKEN_GROUP_BY.length(), endSection).trim();
        boolean tags = false;
        boolean context = false;
        if (innerSection.startsWith(TOKEN_TAGS)) {
            tags = true;
        }
        if (innerSection.startsWith(TOKEN_CONTEXT)) {
            context = true;
        }
        String field;
        if (tags) {
            field = innerSection.substring(TOKEN_TAGS.length());
        } else if (context) {
            field = innerSection.substring(TOKEN_CONTEXT.length());
        } else {
            field = innerSection;
        }
        String type = makeType(field);
        drlGroupByObject = type + " ( $tenantId : tenantId == \"" + tenantId + "\"," +
                "$source : source == \"" + source + "\", " +
                "$dataId : dataId == \"" + dataId + "\", $" + field + " : " + field + " )";
        if (tags) {
            drlGroupByConstraint = " tags[ \"" + field + "\" ] == $" + field + " ";
        } else if (context) {
            drlGroupByConstraint = " context[ \"" + field + "\" ] == $" + field + " ";
        } else {
            drlGroupByConstraint = " " + field + " == $" + field + " ";
        }
        drlGroupByResult = "    result.addContext(\"" + field + "\", $" + field + "); \n";
        drlEventConstraints.add(drlGroupByConstraint);
        if (declareFields.contains(field)) {
            drlGroupByDeclare =  "";
        } else {
            declareFields.add(field);
            drlGroupByDeclare =  "  declare " + type + " \n" +
                    "    tenantId : String \n" +
                    "    source : String \n" +
                    "    dataId : String \n" +
                    "    " + field + " : String \n" +
                    "  end \n\n";
        }
        String extractRuleName = "Extract " + field + " from " + tenantId + "-" + source +"-" + dataId;
        if (!ruleNames.contains(extractRuleName)) {
            ruleNames.add(extractRuleName);
            drlGroupByDeclare += "  rule \"" + extractRuleName + "\" \n" +
                    "  when \n" +
                    "    Event ( $tenantId : tenantId == \"" + tenantId + "\", \n" +
                    "            $dataSource : dataSource == \"" + source + "\", \n" +
                    "            $dataId : dataId == \"" + dataId + "\", \n" +
                    "            $" + field + " : ";
            if (tags) {
                drlGroupByDeclare += "tags[ \"" + field + "\" ] != null ) \n";
            } else if (context) {
                drlGroupByDeclare += "context[ \"" + field + "\" ] != null ) \n";
            } else {
                drlGroupByDeclare += field + " != null ) \n";
            }
            drlGroupByDeclare += "   not " + type + " ( tenantId == $tenantId, " +
                    "source == $dataSource, dataId == $dataId, " +
                    "" + field + " == $" + field + " ) \n" +
                    "  then \n" +
                    "    insert ( new " + type + " ( $tenantId, $dataSource, $dataId, $" + field + " ) ); \n" +
                    "  end \n\n";
        }
    }

    private void parseWindow(String section) {
        int endSection = section.lastIndexOf(TOKEN_END_PARENTHESIS);
        if (endSection == -1) {
            throw new IllegalArgumentException("Expression [" + section + " must contain a valid 'window()'");
        }
        String innerSection = section.substring(TOKEN_WINDOW.length(), endSection).trim();
        if (innerSection.startsWith(TOKEN_TIME)) {
            drlWindow += " over window:time(" + innerSection.substring(TOKEN_TIME.length()) + ")";
        } else if (innerSection.startsWith(TOKEN_LENGTH)) {
            drlWindow += " over window:length(" + innerSection.substring(TOKEN_LENGTH.length()) + ")";
        } else {
            new IllegalArgumentException("Expresion [" + section + " must contain a valid 'time' or 'length' token");
        }
    }

    private void parseFilter(String section) {
        int endSection = section.lastIndexOf(TOKEN_END_PARENTHESIS);
        if (endSection == -1) {
            throw new IllegalArgumentException("Expression [" + section + " must contain a valid 'filter()'");
        }
        String innerSection = section.substring(TOKEN_FILTER.length(), endSection).trim();
        String[] filterConstraints = innerSection.split(TOKEN_COMMA);
        for (int i = 0; i < filterConstraints.length; i++) {
            if (filterConstraints[i].contains(TOKEN_CONTEXT)) {
                filterConstraints[i] = replaceMap(filterConstraints[i], SEARCH_CONTEXT, CONTEXT);
            }
            if (filterConstraints[i].contains(TOKEN_TAGS)) {
                filterConstraints[i] = replaceMap(filterConstraints[i], SEARCH_TAGS, TAGS);
            }
            drlEventConstraints.add(filterConstraints[i]);
        }
    }

    private void parseHaving(String section) {
        int endSection = section.lastIndexOf(')');
        if (endSection == -1) {
            throw new IllegalArgumentException("Expression [" + section + " must contain a valid 'having()'");
        }
        String innerSection = section.substring(TOKEN_HAVING.length(), endSection).trim();
        String[] havingConstraints = innerSection.split(TOKEN_COMMA);
        for (int i = 0; i < havingConstraints.length; i++) {
            if (havingConstraints[i].contains(TOKEN_COUNT)) {
                havingConstraints[i] = havingConstraints[i].replaceAll(TOKEN_COUNT, VARIABLE_COUNT);
                drlFunctions.add(FUNCTION_COUNT);
            }
            if (havingConstraints[i].contains(TOKEN_COUNT_CONTEXT)) {
                havingConstraints[i] = processCountContext(havingConstraints[i]);
            }
            if (havingConstraints[i].contains(TOKEN_COUNT_TAGS)) {
                havingConstraints[i] = processCountTags(havingConstraints[i]);
            }
            drlFunctionsConstraints.add(havingConstraints[i].trim());
        }
    }

    private void addTriggerDrl(String name) {
        drl += drlGroupByDeclare +
                "  rule \"" + name + "\" \n" +
                "  when \n " +
                "   " + drlGroupByObject + " \n" +
                "    accumulate( $event : Event( tenantId == $tenantId, \n" +
                "                                dataSource == $source, \n" +
                "                                dataId == $dataId, \n";
        Iterator<String> it = drlEventConstraints.iterator();
        while (it.hasNext()) {
            String eventConstraint = it.next();
            drl += BLANK + BLANK + eventConstraint;
            if (it.hasNext()) {
                drl += ", \n";
            }
        }
        drl += ") " + drlWindow + "; \n";
        it = drlFunctions.iterator();
        while (it.hasNext()) {
            drl += BLANK + it.next();
            if (it.hasNext()) {
                drl += ", \n";
            }
        }
        drl += "; \n";
        it = drlFunctionsConstraints.iterator();
        while (it.hasNext()) {
            drl += BLANK + it.next();
            if (it.hasNext()) {
                drl += ", \n";
            }
        }
        drl += ") \n";
        drl +=  "  then \n" +
                "    Event result = new Event(\"" + tenantId + "\", \n" +
                "                             UUID.randomUUID().toString(), \n" +
                "                             \"" + dataId +"\", \n" +
                "                             \"" + alerterId + "\", \n" +
                "                             \"" + expression.replaceAll("\"", "'") + "\"); \n" +
                "    result.addContext(\"events\", JsonUtil.toJson($events)); \n" +
                "    result.addContext(\"processed\", \"true\"); \n" +
                drlGroupByResult +
                "    results.sendResult( result ); \n" +
                "  end \n";
    }

    public String getDrl() {
        return drl;
    }

    private String processCountContext(String str) {
        int start = str.indexOf(TOKEN_COUNT_CONTEXT);
        int end = str.indexOf(' ', start);
        String countContext = str.substring(start, end);
        String field = countContext.substring(TOKEN_COUNT_CONTEXT.length());
        drlFunctions.add("$" + field + "ContextSet : collectSet($event.getContext().get(\"" + field + "\") )");
        return str.replaceAll(countContext, "\\$" + field + "ContextSet.size");
    }


    private String processCountTags(String str) {
        int start = str.indexOf(TOKEN_COUNT_TAGS);
        int end = str.indexOf(' ', start);
        String countTags = str.substring(start, end);
        String field = countTags.substring(TOKEN_COUNT_TAGS.length());
        drlFunctions.add("$" + field + "TagsSet : collectSet($event.getTags().get(\"" + field + "\") )");
        return str.replaceAll(countTags, "\\$" + field + "TagsSet.size");
    }

    private static String makeType(String field) {
        return field.substring(0, 1).toUpperCase() + field.substring(1);
    }

    private static String replaceMap(String str, Pattern pattern, String map) {
        String newStr = str;
        Matcher matcher = pattern.matcher(str);
        int index = 0;
        while (matcher.find(index)) {
            int end = matcher.end();
            String original = matcher.group();
            String field = matcher.group(GROUP_INDEX);
            index = end;
            newStr = newStr.replaceAll(original, map + "[\"" + field + "\"]");
        }
        return newStr;
    }

    private static boolean isEmpty(String s) {
        return null == s || s.trim().isEmpty();
    }

    private static boolean isEmpty(Collection c) {
        return null == c || c.isEmpty();
    }

    @Override
    public String toString() {
        return getDrl();
    }
}
