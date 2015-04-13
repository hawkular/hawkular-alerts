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
package org.hawkular.actions.aerogear.listener;

import java.util.Collections;
import java.util.List;

import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import org.jboss.aerogear.unifiedpush.message.UnifiedMessage;
import org.jboss.aerogear.unifiedpush.message.UnifiedPushMessage;

public class UnifiedMessageMatcher extends TypeSafeMatcher<UnifiedMessage> {
    private final String expectedAlias;
    private final String expectedContent;

    public UnifiedMessageMatcher(String expectedAlias, String expectedContent) {
        this.expectedAlias = expectedAlias;
        this.expectedContent = expectedContent;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("has alias ").appendText(expectedAlias).appendText(" and content ")
            .appendText(expectedContent);
    }

    @Override
    protected void describeMismatchSafely(UnifiedMessage item, Description mismatchDescription) {
        UnifiedPushMessage unifiedPushMessage = item.getObject();
        String alert = unifiedPushMessage.getMessage().getAlert();
        List<String> aliases = unifiedPushMessage.getCriteria().getAliases();
        mismatchDescription.appendText("has aliases ").appendText(String.valueOf(aliases)).appendText(" and content ")
            .appendText(alert);
    }

    @Override
    protected boolean matchesSafely(UnifiedMessage item) {
        UnifiedPushMessage unifiedPushMessage = item.getObject();
        String alert = unifiedPushMessage.getMessage().getAlert();
        List<String> aliases = unifiedPushMessage.getCriteria().getAliases();
        return contentMatches(alert) && aliasMatches(aliases);
    }

    private boolean contentMatches(String alert) {
        return alert == null ? expectedContent == null : alert.equals(expectedContent);
    }

    private boolean aliasMatches(List<String> aliases) {
        if (expectedAlias == null) {
            return aliases == null || aliases.isEmpty();
        }
        return aliases != null && Collections.singletonList(expectedAlias).containsAll(aliases);
    }

    @Factory
    public static Matcher<UnifiedMessage> matchesUnifiedMessage(String expectedAlias, String expectedContent) {
        return new UnifiedMessageMatcher(expectedAlias, expectedContent);
    }
}
