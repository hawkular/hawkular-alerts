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
package org.hawkular.alerts.engine.impl.ispn.model;

import java.io.IOException;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;
import org.hibernate.search.bridge.ContainerBridge;
import org.hibernate.search.bridge.FieldBridge;
import org.hibernate.search.bridge.LuceneOptions;
import org.hibernate.search.bridge.StringBridge;

/**
 *
 * This class adds a custom mapping of Tags (represented as Map<String,String>) in Hibernate Search / ISPN.
 *
 * A "tags" field annotated with @FieldBridge(impl = TagsBridge.class) will index each tag as a plain string with
 * format
 *
 *      tagName<V>tagValue
 *
 * Also, if this field is analyzed with @Analyzer(impl = TagsBridge.TagsAnalyzer.class) will create the following terms
 * in the index engine
 *
 *      tags = { tagName }
 *      tags = { tagName_tagValue }
 *
 * This is particularly useful to allow queries per tagName or tagName_tagValue in a native way like this
 *
 *  from Event where (tags : 'tag1')                // All Events with tag1
 *  from Event where (tags : ('tag1' or 'tag2')     // All Events with tag1 or tag2
 *  from Event where (tags : ('tag1_value1')        // All Events with tag1 = 'value1'
 *  from Event where (tags : (not 'tag1_value1')    // All Events with tag1 != 'value1'
 *  from Event where (tags : (/tag1_v.*1/)          // All Events with tag1 = regular expression /v.*1/
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class TagsBridge implements FieldBridge, ContainerBridge, StringBridge {
    private static final MsgLogger log = MsgLogging.getMsgLogger(TagsBridge.class);

    public static final String VALUE = "<V>";
    public static final String SEPARATOR = "_";

    TagBridge bridge = new TagBridge();

    @Override
    public FieldBridge getElementBridge() {
        return bridge;
    }

    @Override
    public void set(String name, Object value, Document document, LuceneOptions luceneOptions) {
        Map<String, String> tags = (Map<String, String>)value;
        for (Map.Entry<String, String> tag : tags.entrySet()) {
            bridge.set(name, tag, document, luceneOptions);
        }
    }

    @Override
    public String objectToString(Object object) {
        return bridge.objectToString(object);
    }

    public static class TagBridge implements FieldBridge, StringBridge {
        @Override
        public void set(String name, Object value, Document document, LuceneOptions luceneOptions) {
            luceneOptions.addFieldToDocument(name, objectToString(value), document);
        }

        @Override
        public String objectToString(Object object) {
            if (object instanceof Map.Entry) {
                Map.Entry<String, String> tag = (Map.Entry<String, String>)object;
                return tag.getKey() + VALUE + tag.getValue();
            }
            return (String) object;
        }
    }

    public static class TagsAnalyzer extends Analyzer {

        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            return new TokenStreamComponents(new TagsTokenizer());
        }

    }

    public static class TagsTokenizer extends Tokenizer {

        private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

        String[] tokens;
        int iTokens = -1;

        public TagsTokenizer() {
        }

        @Override
        public final boolean incrementToken() throws IOException {
            clearAttributes();
            if (iTokens < 0) {
                readTokens();
            }
            if (iTokens < (tokens.length - 1)) {
                iTokens++;
                termAtt.resizeBuffer(tokens[iTokens].length());
                termAtt.append(tokens[iTokens]);
                return true;
            } else {
                iTokens = -1;
                tokens = null;
                return false;
            }

        }

        private void readTokens() {
            int nRead;
            String tags = null;
            try {
                char[] buff = new char[1 * 1024];
                StringBuilder buffer = new StringBuilder();
                while ((nRead = input.read(buff, 0, buff.length)) != -1) {
                    buffer.append(buff, 0, nRead);
                }
                tags = buffer.toString();
            } catch (IOException e) {
                log.error(e);
            }
            if (tags != null && !tags.isEmpty()) {
                int iValue = tags.indexOf(TagsBridge.VALUE);
                if (iValue > 0) {
                    String tag = tags.substring(0, iValue);
                    String value = tags.substring(iValue + TagsBridge.VALUE.length());
                    String token = tag + SEPARATOR + value;
                    tokens = new String[2];
                    tokens[0] = tag;
                    tokens[1] = token;
                } else {
                    tokens = new String[1];
                    tokens[0] = tags;
                }
            }
        }

    }

}
