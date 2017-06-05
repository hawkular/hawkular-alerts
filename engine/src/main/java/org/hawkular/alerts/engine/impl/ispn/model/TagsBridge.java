package org.hawkular.alerts.engine.impl.ispn.model;

import java.util.Map;

import org.apache.lucene.document.Document;
import org.hibernate.search.bridge.ContainerBridge;
import org.hibernate.search.bridge.FieldBridge;
import org.hibernate.search.bridge.LuceneOptions;
import org.hibernate.search.bridge.StringBridge;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class TagsBridge implements FieldBridge, ContainerBridge, StringBridge {

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
                return tag.getKey() + ":" + tag.getValue();
            }
            return (String) object;
        }
    }
}
