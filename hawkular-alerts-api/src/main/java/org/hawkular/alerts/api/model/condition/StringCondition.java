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
package org.hawkular.alerts.api.model.condition;

import org.hawkular.alerts.api.log.MsgLogger;
import org.hawkular.alerts.api.model.trigger.Trigger.Mode;

/**
 * A string comparison condition.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class StringCondition extends Condition {
    private static final MsgLogger msgLog = MsgLogger.LOGGER;

    public enum Operator {
        EQUAL, NOT_EQUAL, STARTS_WITH, ENDS_WITH, CONTAINS, MATCH
    }

    private String dataId;
    private Operator operator;
    private String pattern;
    private boolean ignoreCase;

    public StringCondition() {
        /*
            Default constructor is needed for JSON libraries in JAX-RS context.
         */
        this("DefaultId", 1, 1, null, null, null, false);
    }

    public StringCondition(String triggerId, int conditionSetSize, int conditionSetIndex,
            String dataId, Operator operator, String pattern, boolean ignoreCase) {
        this(triggerId, Mode.FIRE, conditionSetSize, conditionSetIndex, dataId, operator, pattern, ignoreCase);
    }

    public StringCondition(String triggerId, Mode triggerMode, int conditionSetSize, int conditionSetIndex,
            String dataId, Operator operator, String pattern, boolean ignoreCase) {
        super(triggerId, triggerMode, conditionSetSize, conditionSetIndex);
        this.dataId = dataId;
        this.operator = operator;
        this.pattern = pattern;
        this.ignoreCase = ignoreCase;
    }

    public String getDataId() {
        return dataId;
    }

    public void setDataId(String dataId) {
        this.dataId = dataId;
    }

    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    public void setIgnoreCase(boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
    }

    public Operator getOperator() {
        return operator;
    }

    public void setOperator(Operator operator) {
        this.operator = operator;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getLog(String value) {
        return triggerId + " : " + value + " " + operator.name() + " " +
                pattern + " " + "ignoreCase=" + ignoreCase;
    }

    public boolean match(String value) {

        if (ignoreCase && operator != Operator.MATCH) {
            pattern = pattern.toLowerCase();
            value = value.toLowerCase();
        }
        switch (operator) {
            case EQUAL:
                return value.equals(pattern);
            case NOT_EQUAL:
                return !value.equals(pattern);
            case ENDS_WITH:
                return value.endsWith(pattern);
            case STARTS_WITH:
                return value.startsWith(pattern);
            case CONTAINS:
                return value.contains(pattern);
            case MATCH:
                return value.matches(ignoreCase ? ("(?i)" + pattern) : pattern);
            default:
                msgLog.warnUnknowOperatorOnCondition(operator.name(), this.getClass().getName());
                return false;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;

        StringCondition that = (StringCondition) o;

        if (ignoreCase != that.ignoreCase)
            return false;
        if (dataId != null ? !dataId.equals(that.dataId) : that.dataId != null)
            return false;
        if (operator != that.operator)
            return false;
        if (pattern != null ? !pattern.equals(that.pattern) : that.pattern != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (dataId != null ? dataId.hashCode() : 0);
        result = 31 * result + (operator != null ? operator.hashCode() : 0);
        result = 31 * result + (pattern != null ? pattern.hashCode() : 0);
        result = 31 * result + (ignoreCase ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "StringCondition [dataId=" + dataId + ", operator=" + operator + ", pattern=" + pattern
                + ", ignoreCase=" + ignoreCase + ", toString()=" + super.toString() + "]";
    }

}
