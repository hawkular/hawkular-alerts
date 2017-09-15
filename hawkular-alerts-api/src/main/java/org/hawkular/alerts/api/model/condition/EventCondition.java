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
package org.hawkular.alerts.api.model.condition;

import static org.hawkular.alerts.api.util.Util.isEmpty;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.trigger.Mode;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * An <code>EventCondition</code> is used for condition evaluations over Event data using expressions.
 *
 * Expression is a comma separated list of the following 3 tokens structure:
 *
 * <event.field> <operator> <constant> [,<event.field> <operator> <constant>]*
 *
 * - <event.field> represent a fixed field of event structure or a key of tags.
 *   Supported fields are the following:
 *      - tenantId
 *      - id
 *      - ctime
 *      - text
 *      - category
 *      - tags.<key>
 *
 * - <operator> is a string representing a string/numeric operator, supported ones are:
 *   "==" equals
 *   "!=" not equals
 *   "starts" starts with String operator
 *   "ends" ends with String operator
 *   "contains" contains String operator
 *   "match" match String operator
 *   "<" less than
 *   "<=" less or equals than
 *   ">" greater than
 *   ">=" greater or equals than
 *   "==" equals
 *
 * - <constant> is a string that might be interpreted as a number if is not closed with single quotes or a string
 * constant if it is closed with single quotes
 * i.e. 23, 'test'
 *
 * A constant string can contain special character comma but escaped with backslash.
 * i.e. '\,test', 'test\,'
 *
 * So, putting everything together, a valid expression might look like:
 * event.id start 'IDXYZ', event.tag.category == 'Server', event.tag.from end '.com'
 *
 * A non valid expression will return false.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@ApiModel(description = "An EventCondition is used for condition evaluations over Event data using expressions. + \n" +
        " + \n" +
        "Expression is a comma separated list of the following 3 tokens structure: + \n" +
        " + \n" +
        "<event.field> <operator> <constant> [,<event.field> <operator> <constant>]* + \n" +
        " + \n" +
        "<event.field> represent a fixed field of event structure or a key of tags. + \n" +
        "Supported fields are the following: + \n" +
        "- tenantId + \n" +
        "- id + \n" +
        "- ctime + \n" +
        "- text + \n" +
        "- category + \n" +
        "- tags.<key> + \n" +
        " + \n" +
        "<operator> is a string representing a string/numeric operator, supported ones are: + \n" +
        "\"==\" equals + \n" +
        "\"!=\" not equals + \n" +
        "\"starts\" starts with String operator + \n" +
        "\"ends\" ends with String operator + \n" +
        "\"contains\" contains String operator + \n" +
        "\"match\" match String operator + \n" +
        "\"<\" less than + \n" +
        "\"<=\" less or equals than + \n" +
        "\">\" greater than + \n" +
        "\">=\" greater or equals than + \n" +
        "\"==\" equals + \n" +
        " + \n" +
        "<constant> is a string that might be interpreted as a number if is not closed with single quotes or a " +
        "string constant if it is closed with single quotes + \n" +
        "i.e. 23, 'test' + \n" +
        " + \n" +
        "A constant string can contain special character comma but escaped with backslash. + \n" +
        "i.e '\\,test', 'test\\,' + \n" +
        " + \n" +
        "So, putting everything together, a valid expression might look like: + \n" +
        "event.id starts 'IDXYZ', event.tag.category == 'Server', event.tag.from end '.com' + \n" +
        " + \n" +
        "A non valid expression will return false. + \n")
public class EventCondition extends Condition {

    private static final long serialVersionUID = 1L;

    @JsonInclude
    private String dataId;

    @ApiModelProperty(value = "Event expression used for this condition.",
            position = 0,
            required = true)
    @JsonInclude(Include.NON_NULL)
    private String expression;

    public EventCondition() {
        this("", "", Mode.FIRING, 1, 1, null, null);
    }

    public EventCondition(String tenantId, String triggerId, String dataId, String expression) {
        this(tenantId, triggerId, Mode.FIRING, 1, 1, dataId, expression);
    }

    public EventCondition(String tenantId, String triggerId, Mode triggerMode, String dataId) {
        this(tenantId, triggerId, triggerMode, 1, 1, dataId, null);
    }

    /**
     * This constructor requires the tenantId be assigned prior to persistence. It can be used when
     * creating triggers via Rest, as the tenant will be assigned automatically.
     */
    public EventCondition(String triggerId, Mode triggerMode, String dataId, String expression) {
        this("", triggerId, triggerMode, 1, 1, dataId, expression);
    }

    public EventCondition(String tenantId, String triggerId, Mode triggerMode, String dataId, String expression) {
        this(tenantId, triggerId, triggerMode, 1, 1, dataId, expression);
    }

    public EventCondition(String tenantId, String triggerId, int conditionSetSize, int conditionSetIndex,
            String dataId) {
        this(tenantId, triggerId, Mode.FIRING, conditionSetSize, conditionSetIndex, dataId, null);
    }

    public EventCondition(String tenantId, String triggerId, int conditionSetSize, int conditionSetIndex,
            String dataId, String expression) {
        this(tenantId, triggerId, Mode.FIRING, conditionSetSize, conditionSetIndex, dataId, expression);
    }

    public EventCondition(String tenantId, String triggerId, Mode triggerMode, int conditionSetSize,
            int conditionSetIndex, String dataId) {
        this(tenantId, triggerId, triggerMode, conditionSetSize, conditionSetIndex, dataId, null);
    }

    /**
     * This constructor requires the tenantId be assigned prior to persistence. It can be used when
     * creating triggers via Rest, as the tenant will be assigned automatically.
     */
    public EventCondition(String triggerId, Mode triggerMode, int conditionSetSize,
            int conditionSetIndex, String dataId, String expression) {
        this("", triggerId, triggerMode, conditionSetSize, conditionSetIndex, dataId, expression);
    }

    public EventCondition(String tenantId, String triggerId, Mode triggerMode, int conditionSetSize,
            int conditionSetIndex, String dataId, String expression) {
        super(tenantId, triggerId, triggerMode, conditionSetSize, conditionSetIndex, Type.EVENT);
        this.dataId = dataId;
        this.expression = expression;
        updateDisplayString();
    }

    public EventCondition(EventCondition condition) {
        super(condition);

        this.dataId = condition.getDataId();
        this.expression = condition.getExpression();
    }

    public void setDataId(String dataId) {
        this.dataId = dataId;
    }

    @Override
    public String getDataId() {
        return dataId;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    private static Pattern cleanComma = Pattern.compile("\\\\,");

    public boolean match(Event value) {
        if (null == value) {
            return false;
        }
        if (isEmpty(expression)) {
            return true;
        }
        List<String> expressions = new ArrayList<>();
        int j = 0;
        for (int i = 0; i < expression.length(); i++) {
            if (expression.charAt(i) == ','
                    && (i == 0 || (i > 0 && expression.charAt(i - 1) != '\\'))) {
                expressions.add(cleanComma.matcher(expression.substring(j, i).trim()).replaceAll(","));
                j = i + 1;
            }
        }
        expressions.add(cleanComma.matcher(expression.substring(j).trim()).replaceAll(","));
        for (String expression : expressions) {
            if (!processExpression(expression, value)) {
                return false;
            }
        }
        return true;
    }

    private static final String TENANT_ID = "tenantId";
    private static final String ID = "id";
    private static final String CTIME = "ctime";
    private static final String TEXT = "text";
    private static final String CATEGORY = "category";
    private static final String TAGS = "tags.";

    private static final String EQ = "==";
    private static final String NON_EQ = "!=";
    private static final String STARTS = "starts";
    private static final String ENDS = "ends";
    private static final String CONTAINS = "contains";
    private static final String MATCHES = "matches";
    private static final String LT = "<";
    private static final String LTE = "<=";
    private static final String GT = ">";
    private static final String GTE = ">=";

    private boolean processExpression(String expression, Event value) {
        if (isEmpty(expression) || null == value) {
            return false;
        }
        String[] tokens = expression.split(" ");
        if (tokens.length < 3) {
            return false;
        }
        String eventField = tokens[0];
        String operator = tokens[1];
        String constant = tokens[2];
        for (int i = 3; i < tokens.length; ++i) {
            constant += " ";
            constant += tokens[i];
        }
        String sEventValue = null;
        Long lEventValue = null;
        String sConstantValue = null;
        Double dConstantValue = null;

        if (isEmpty(eventField)) {
            return false;
        }
        if (TENANT_ID.equals(eventField)) {
            sEventValue = value.getTenantId();
        } else if (ID.equals(eventField)) {
            sEventValue = value.getId();
        } else if (CTIME.equals(eventField)) {
            lEventValue = value.getCtime();
        } else if (TEXT.equals(eventField)) {
            sEventValue = value.getText();
        } else if (CATEGORY.equals(eventField)) {
            sEventValue = value.getCategory();
        } else if (eventField.startsWith(TAGS)) {
            // We get the key from tags.<key> string
            String key = eventField.substring(5);
            sEventValue = value.getTags().get(key);
        }
        if (sEventValue == null && lEventValue == null) {
            return false;
        }
        if (constant == null) {
            return false;
        }
        int constantLength = constant.length();
        if (constant.charAt(0) == '\'' && constant.charAt(constantLength - 1) == '\'') {
            sConstantValue = constant.substring(1, constantLength - 1);
        } else if (constant.charAt(0) == '\'' && constant.charAt(constantLength - 1) != '\'') {
            return false;
        } else if (constant.charAt(0) != '\'' && constant.charAt(constantLength - 1) == '\'') {
            return false;
        } else {
            dConstantValue = Double.valueOf(constant);
        }

        if (EQ.equals(operator)) {
            if (sEventValue != null && sConstantValue != null) {
                return sEventValue.equals(sConstantValue);
            }
            if (lEventValue != null && dConstantValue != null) {
                return lEventValue.longValue() == dConstantValue.doubleValue();
            }
            return false;
        } else if (NON_EQ.equals(operator)) {
            if (sEventValue != null && sConstantValue != null) {
                return !sEventValue.equals(sConstantValue);
            }
            if (lEventValue != null && dConstantValue != null) {
                return lEventValue.longValue() != dConstantValue.doubleValue();
            }
            return false;
        } else if (STARTS.equals(operator)) {
            if (sEventValue != null && sConstantValue != null) {
                return sEventValue.startsWith(sConstantValue);
            }
            return false;
        } else if (ENDS.equals(operator)) {
            if (sEventValue != null && sConstantValue != null) {
                return sEventValue.endsWith(sConstantValue);
            }
            return false;
        } else if (CONTAINS.equals(operator)) {
            if (sEventValue != null && sConstantValue != null) {
                return sEventValue.contains(sConstantValue);
            }
            return false;
        } else if (MATCHES.equals(operator)) {
            if (sEventValue != null && sConstantValue != null) {
                return sEventValue.matches(sConstantValue);
            }
            return false;
        } else if (GT.equals(operator)) {
            Double dEventValue = lEventValue != null ? lEventValue.doubleValue() : null;
            dEventValue = sEventValue != null ? Double.valueOf(sEventValue) : dEventValue;
            if (dEventValue != null && dConstantValue != null) {
                return dEventValue > dConstantValue;
            }
            return false;
        } else if (GTE.equals(operator)) {
            Double dEventValue = lEventValue != null ? lEventValue.doubleValue() : null;
            dEventValue = sEventValue != null ? Double.valueOf(sEventValue) : dEventValue;
            if (dEventValue != null && dConstantValue != null) {
                return dEventValue >= dConstantValue;
            }
            return false;
        } else if (LT.equals(operator)) {
            Double dEventValue = lEventValue != null ? lEventValue.doubleValue() : null;
            dEventValue = sEventValue != null ? Double.valueOf(sEventValue) : dEventValue;
            if (dEventValue != null && dConstantValue != null) {
                return dEventValue < dConstantValue;
            }
            return false;
        } else if (LTE.equals(operator)) {
            Double dEventValue = lEventValue != null ? lEventValue.doubleValue() : null;
            dEventValue = sEventValue != null ? Double.valueOf(sEventValue) : dEventValue;
            if (dEventValue != null && dConstantValue != null) {
                return dEventValue <= dConstantValue;
            }
            return false;
        }
        return false;
    }

    @Override
    public void updateDisplayString() {
        String s = String.format("%s matches [%s]", this.dataId, this.expression);
        setDisplayString(s);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;

        EventCondition that = (EventCondition) o;

        return !(expression != null ? !expression.equals(that.expression) : that.expression != null);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (expression != null ? expression.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "EventCondition{" +
                "dataId='" + dataId + '\'' +
                ",expression='" + expression + '\'' +
                '}';
    }
}
