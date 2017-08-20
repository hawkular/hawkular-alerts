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

import static org.hawkular.alerts.api.util.Util.isEmpty;
import static org.hawkular.alerts.engine.tags.ExpressionTagQueryParser.ExpressionTagResolver.AND;
import static org.hawkular.alerts.engine.tags.ExpressionTagQueryParser.ExpressionTagResolver.NOT;
import static org.hawkular.alerts.engine.tags.ExpressionTagQueryParser.ExpressionTagResolver.OR;
import static org.hawkular.alerts.engine.tags.ExpressionTagQueryParser.ExpressionTagResolver.getTokens;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.hawkular.alerts.engine.tags.parser.TagQueryBaseListener;
import org.hawkular.alerts.engine.tags.parser.TagQueryLexer;
import org.hawkular.alerts.engine.tags.parser.TagQueryParser;
import org.hawkular.alerts.engine.tags.parser.TagQueryParser.TagexpContext;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class ExpressionTagQueryParser extends TagQueryBaseListener implements ANTLRErrorListener {
    private static final MsgLogger log = MsgLogging.getMsgLogger(ExpressionTagQueryParser.class);

    private ExpressionTagResolver resolver;

    private boolean error;
    private String errorMsg;
    private List<String> evalsPostfix;
    private Stack<Stack<String>> stack;

    public interface ExpressionTagResolver {
        String AND = "and";
        String OR = "or";
        String NOT = "not";
        String EQ = "=";
        String NEQ = "!=";
        String IN = "in";
        char SPACE = ' ';
        char QUOTE = '\'';
        char LEFT_BRACKET =  '[';
        char RIGHT_BRACKET = ']';

        static List<String> getTokens(String tagExpression) {
            if (tagExpression == null || tagExpression.isEmpty()) {
                return null;
            }
            List<String> tokens = new ArrayList<>();
            StringBuilder token = new StringBuilder();

            int marker = 0;
            boolean separatorChanged = false;
            while (marker < tagExpression.length() && !separatorChanged) {
                char ch = tagExpression.charAt(marker);
                if (!separatorChanged && ch == QUOTE) {
                    separatorChanged = true;
                }
                if (!separatorChanged && ch == LEFT_BRACKET) {
                    separatorChanged = true;
                }

                if (ch != SPACE) {
                    token.append(ch);
                }

                if (token.toString().equals(NOT)) {
                    tokens.add(token.toString());
                    token = new StringBuilder();
                } else if (ch == SPACE) {
                    if (token.length() > 0) {
                        tokens.add(token.toString());
                    }
                    token = new StringBuilder();
                }
                marker++;
            }
            if (separatorChanged) {
                tokens.add(tagExpression.substring(marker - 1));
            } else if (token.toString().length() > 0) {
                tokens.add(token.toString());
            }
            return tokens;
        }

        default Set<String> resolve(String tagExpression) throws Exception {
            return resolve(getTokens(tagExpression));
        }

        Set<String> resolve(List<String> tokens) throws Exception;
    }

    public ExpressionTagQueryParser(ExpressionTagResolver resolver) {
        if (resolver == null) {
            throw new IllegalArgumentException("Resolver must be not null");
        }
        this.resolver = resolver;
    }

    public Set<String> resolve(String expression) throws Exception {
        String prefix = parse(expression);
        Set<String> result;
        if (prefix.startsWith(AND)) {
            result = and(left(prefix), right(prefix));
        } else if (prefix.startsWith(OR)) {
            result = or(left(prefix), right(prefix));
        } else {
            result = resolver.resolve(getTokens(prefix));
        }
        return result;
    }

    public String parse(String expression) throws Exception {

        evalsPostfix = new ArrayList<>();
        stack = new Stack<>();
        error = false;
        errorMsg = null;

        ANTLRInputStream input = new ANTLRInputStream(expression);
        TagQueryLexer tql = new TagQueryLexer(input);
        tql.addErrorListener(this);
        CommonTokenStream tokens = new CommonTokenStream(tql);
        TagQueryParser parser = new TagQueryParser(tokens);
        ParseTree parseTree = parser.tagquery();
        ParseTreeWalker.DEFAULT.walk(this, parseTree);

        if (error) {
            throw new IllegalArgumentException("Expression [" + expression + "] is malformed. Msg: " + errorMsg);
        }
        if (log.isDebugEnabled()) {
            log.debugf("Expression [%s] evaluated as [%s]", expression, evalsPostfix);
        }
        return prefix(evalsPostfix);
    }

    private String prefix(List<String> parsed) {
        Stack<String> stack = new Stack<>();
        for (String eval : parsed) {
            if (AND.equals(eval) || OR.equals(eval)) {
                String op2 = stack.pop();
                String op1 = stack.pop();
                stack.push(eval + "(" + op1 + ", " + op2 + ")");
            } else {
                stack.push(eval);
            }
        }
        return stack.pop();
    }

    // Lexer errors
    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
        error = true;
        errorMsg = msg;
    }

    @Override
    public void reportAmbiguity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, boolean exact, BitSet ambigAlts, ATNConfigSet configs) {
        error = true;
        errorMsg = "Ambiguity error";
    }

    @Override
    public void reportAttemptingFullContext(Parser recognizer, DFA dfa, int startIndex, int stopIndex, BitSet conflictingAlts, ATNConfigSet configs) {
        error = true;
        errorMsg = "Attempting Full Context error";
    }

    @Override
    public void reportContextSensitivity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, int prediction, ATNConfigSet configs) {
        error = true;
        errorMsg = "Context Sensitivity error";
    }

    // Grammar errors
    @Override
    public void visitErrorNode(ErrorNode node) {
        if (error) {
            errorMsg = "Error " + errorMsg + " on node " + node.getText();
        } else {
            errorMsg = "Error on node " + node.getText();
            error = true;
        }
    }

    // Grammar analysis
    @Override
    public void enterObject(TagQueryParser.ObjectContext ctx) {
        Stack<String> objectStack = new Stack<>();
        stack.push(objectStack);
        String eval;
        if (ctx.getParent().getParent() == null && ctx.tagexp() != null) {
            eval = getEval(ctx.tagexp());
            evalsPostfix.add(eval);
        }
        if (ctx.logical_operator() != null) {
            TagexpContext left = ctx.object(0).tagexp();
            TagexpContext right = ctx.object(1).tagexp();
            if (left != null && right != null) {
                String lEval = getEval(left);
                String rEval = getEval(right);
                if (isNot(lEval) && !isNot(rEval)) {
                    evalsPostfix.add(rEval);
                    evalsPostfix.add(lEval);
                } else {
                    evalsPostfix.add(lEval);
                    evalsPostfix.add(rEval);
                }
            } else if (left != null && right == null) {
                String lEval = getEval(left);
                if (!isNot(lEval)) {
                    evalsPostfix.add(lEval);
                } else {
                    objectStack.push(lEval);
                }
            }  else if (left == null && right != null) {
                String rEval = getEval(right);
                evalsPostfix.add(rEval);
            }
        }
    }

    @Override
    public void exitObject(TagQueryParser.ObjectContext ctx) {
        Stack<String> objectStack = stack.pop();
        if (ctx.logical_operator() != null) {
            String eval = ctx.logical_operator().getText().toLowerCase();
            if (!objectStack.isEmpty()) {
                evalsPostfix.add(objectStack.pop());
            }
            evalsPostfix.add(eval);
        }
    }

    private boolean isNot(String eval) {
        return eval.startsWith(NOT);
    }

    private String getEval(TagexpContext tagexp) {
        String eval;
        if (tagexp.array_operator() != null) {
            eval = tagexp.key().getText() + " " +
                    (tagexp.array_operator().NOT() != null ? "not in" : "in") + " " +
                    tagexp.array().getText();
        } else if (tagexp.boolean_operator() != null) {
            eval = tagexp.key().getText() + " " +
                    tagexp.boolean_operator().getText() + " " +
                    tagexp.value().getText();
        } else if (tagexp.NOT() != null) {
            eval = NOT + " " + tagexp.key().getText();
        } else {
            eval = tagexp.key().getText();
        }
        return eval;
    }

    // Analysis
    protected String left(String exp) {
        return getOperand(true, exp);
    }

    protected String right(String exp) {
        return getOperand(false, exp);
    }

    private String getOperand(boolean left, String exp) {
        if (exp.startsWith(AND)) {
            // and(<left>,<right>)
            int comma = getCommaIndex(exp.substring(AND.length() + 1, exp.length() - 1)) + AND.length() + 1;
            if (left) {
                return exp.substring(AND.length() + 1, comma).trim();
            } else {
                return exp.substring(comma + 1, exp.length() - 1).trim();
            }
        } else if (exp.startsWith(OR)) {
            // or(<left>,<right>)
            int comma = getCommaIndex(exp.substring(OR.length() + 1, exp.length() - 1)) + OR.length() + 1;
            if (left) {
                return exp.substring(OR.length() + 1, comma).trim();
            } else {
                return exp.substring(comma + 1, exp.length() - 1).trim();
            }
        } else {
            return exp.trim();
        }
    }

    private int getCommaIndex(String exp) {
        int open = 0;
        for (int i = 0; i < exp.length(); i++) {
            char c = exp.charAt(i);
            if (c == '(') {
                open++;
            }
            if (c == ')') {
                open--;
            }
            if (c == ',' && open == 0) {
                return i;
            }
        }
        return -1;
    }

    private Set<String> and(String left, String right) throws Exception {
        return andOr(true, left, right);
    }

    private Set<String> or(String left, String right) throws Exception {
        return andOr(false, left, right);
    }

    private Set<String> andOr(boolean isAnd, String left, String right) throws Exception {
        Set<String> leftResult;
        if (left.startsWith(AND)) {
            leftResult = andOr(true, left(left), right(left));
        } else if (left.startsWith(OR)) {
            leftResult = andOr(false, left(left), right(left));
        } else {
            leftResult = resolver.resolve(left);
        }
        // Shorcutting the AND operator
        if (isAnd && isEmpty(leftResult)) {
            return leftResult;
        }
        Set<String> rightResult;
        if (right.startsWith(AND)) {
            rightResult = andOr(true, left(right), right(right));
        } else if (right.startsWith(OR)) {
            rightResult = andOr(false, left(right), right(right));
        } else {
            rightResult = resolver.resolve(right);
        }
        if (isAnd) {
            leftResult.retainAll(rightResult);
        } else {
            leftResult.addAll(rightResult);
        }
        return leftResult;
    }

}
