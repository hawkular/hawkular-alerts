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
package org.hawkular.alerts.engine.impl.ispn;

import static org.hawkular.alerts.engine.tags.ExpressionTagQueryParser.ExpressionTagResolver.AND;
import static org.hawkular.alerts.engine.tags.ExpressionTagQueryParser.ExpressionTagResolver.OR;
import static org.hawkular.alerts.engine.tags.ExpressionTagQueryParser.ExpressionTagResolver.getTokens;

import java.util.Collections;
import java.util.List;

import org.hawkular.alerts.engine.tags.ExpressionTagQueryParser;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class IspnExpressionTagQueryParser extends ExpressionTagQueryParser {

    private IspnExpressionTagResolver resolver;

    public interface IspnExpressionTagResolver {

        default void resolveQuery(String tagExpression, StringBuilder query) {
            resolveQuery(getTokens(tagExpression), query);
        }

        void resolveQuery(List<String> tokens, StringBuilder query);
    }

    public IspnExpressionTagQueryParser(IspnExpressionTagResolver resolver) {
        super(prefix -> Collections.emptySet());
        if (resolver == null) {
            throw new IllegalArgumentException("Resolver must be not null");
        }
        this.resolver = resolver;
    }

    public void resolveQuery(String expression, StringBuilder query) throws Exception {
        if (expression == null) {
            throw new IllegalArgumentException("expression must be not null");
        }
        if (query == null) {
            throw new IllegalArgumentException("query must be not null");
        }
        String prefix = parse(expression);
        query.append("(");
        if (prefix.startsWith(AND)) {
            and(left(prefix), right(prefix), query);
        } else if (prefix.startsWith(OR)) {
            or(left(prefix), right(prefix), query);
        } else {
            resolver.resolveQuery(getTokens(prefix), query);
        }
        query.append(")");
    }

    private void and(String left, String right, StringBuilder query) throws Exception {
        andOr(true, left, right, query);
    }

    private void or(String left, String right, StringBuilder query) throws Exception {
        andOr(false, left, right, query);
    }

    private void andOr(boolean isAnd, String left, String right, StringBuilder query) throws Exception {
        query.append("(");
        if (left.startsWith(AND)) {
            andOr(true, left(left), right(left), query);
        } else if (left.startsWith(OR)) {
            andOr(false, left(left), right(left), query);
        } else {
            resolver.resolveQuery(left, query);
        }
        query.append(")");
        if (isAnd) {
            query.append(" and ");
        } else {
            query.append(" or ");
        }
        query.append("(");
        if (right.startsWith(AND)) {
            andOr(true, left(right), right(right), query);
        } else if (right.startsWith(OR)) {
            andOr(false, left(right), right(right), query);
        } else {
            resolver.resolveQuery(right, query);
        }
        query.append(")");
    }
}
