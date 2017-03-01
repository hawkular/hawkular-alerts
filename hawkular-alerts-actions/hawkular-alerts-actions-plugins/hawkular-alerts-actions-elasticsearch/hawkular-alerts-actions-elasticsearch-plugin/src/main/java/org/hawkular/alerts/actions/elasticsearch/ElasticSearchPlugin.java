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
package org.hawkular.alerts.actions.elasticsearch;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.hawkular.alerts.actions.api.ActionMessage;
import org.hawkular.alerts.actions.api.ActionPluginListener;
import org.hawkular.alerts.actions.api.ActionPluginSender;
import org.hawkular.alerts.actions.api.ActionResponseMessage;
import org.hawkular.alerts.actions.api.MsgLogger;
import org.hawkular.alerts.actions.api.Plugin;
import org.hawkular.alerts.actions.api.Sender;
import org.hawkular.alerts.api.json.JsonUtil;
import org.hawkular.alerts.api.model.action.Action;

import com.bazaarvoice.jolt.Shiftr;
/**
 * Action ElasticSearch Plugin.
 *
 * It processes Actions writing Event/Alerts into an ElasticSearch system.
 *
 * It supports optional JOLT Shiftr transformations to process Events/Alerts into custom JSON formats.
 *
 * i.e.  {"tenantId":"tenant", "ctime":"timestamp", "dataId":"dataId","context":"context"}
 *
 * https://github.com/bazaarvoice/jolt/blob/master/jolt-core/src/main/java/com/bazaarvoice/jolt/Shiftr.java
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Plugin(name = "elasticsearch")
public class ElasticSearchPlugin implements ActionPluginListener {
    public static final String PLUGIN_NAME = "elasticsearch";

    /**
     * "host" property is used to indicate the ElasticSearch host to connect.
     */
    public static final String PROP_HOST = "host";

    /**
     * "port" property is used to define the ElasticSearch port to connect.
     */
    public static final String PROP_PORT = "port";

    /**
     * "index" property is used to indicate the index where the Events/Alerts will be written.
     */
    public static final String PROP_INDEX = "index";

    /**
     * "type" property is used to define the type under the inxed where the Events/Alerts will be written.
     */
    public static final String PROP_TYPE = "type";

    /**
     * "transform" defines an optional transformation expression based on JOLT Shiftr format to convert an
     * Event/Alert into a custom JSON format.
     */
    public static final String PROP_TRANSFORM = "transform";

    private static final String ELASTICSEARCH_HOST = "hawkular-alerts.elasticsearch-host";
    private static final String ELASTICSEARCH_HOST_ENV = "ELASTICSEARCH_HOST";
    private static final String ELASTICSEARCH_HOST_DEFAULT = "localhost";

    private static final String ELASTICSEARCH_PORT = "hawkular-alerts.elasticsearch-port";
    private static final String ELASTICSEARCH_PORT_ENV = "ELASTICSEARCH_PORT";
    private static final String ELASTICSEARCH_PORT_DEFAULT = "9300";

    private static final String ELASTICSEARCH_INDEX = "hawkular-alerts.elasticsearch-index";
    private static final String ELASTICSEARCH_INDEX_ENV = "ELASTICSEARCH_INDEX";
    private static final String ELASTICSEARCH_INDEX_DEFAULT = "alerts";

    private static final String ELASTICSEARCH_TYPE = "hawkular-alerts.elasticsearch-type";
    private static final String ELASTICSEARCH_TYPE_ENV = "ELASTICSEARCH_TYPE";
    private static final String ELASTICSEARCH_TYPE_DEFAULT = "hawkular";

    private final MsgLogger msgLog = MsgLogger.LOGGER;
    Map<String, String> defaultProperties = new HashMap<>();

    @Sender
    ActionPluginSender sender;

    private TransportClient client;

    private static final String MESSAGE_PROCESSED = "PROCESSED";
    private static final String MESSAGE_FAILED = "FAILED";

    public ElasticSearchPlugin() {
        defaultProperties.put(PROP_HOST, getProperty(ELASTICSEARCH_HOST, ELASTICSEARCH_HOST_ENV,
                ELASTICSEARCH_HOST_DEFAULT));
        defaultProperties.put(PROP_PORT, getProperty(ELASTICSEARCH_PORT, ELASTICSEARCH_PORT_ENV,
                ELASTICSEARCH_PORT_DEFAULT));
        defaultProperties.put(PROP_INDEX, getProperty(ELASTICSEARCH_INDEX, ELASTICSEARCH_INDEX_ENV,
                ELASTICSEARCH_INDEX_DEFAULT));
        defaultProperties.put(PROP_TYPE, getProperty(ELASTICSEARCH_TYPE, ELASTICSEARCH_TYPE_ENV,
                ELASTICSEARCH_TYPE_DEFAULT));
        defaultProperties.put(PROP_TRANSFORM, "");
    }

    private String getProperty(String property, String env, String defaultValue) {
        String value = System.getProperty(property);
        if (value != null) {
            return value;
        }
        value = System.getenv(env);
        if (value != null) {
            return value;
        }
        return defaultValue;
    }

    @Override
    public Set<String> getProperties() {
        return defaultProperties.keySet();
    }

    @Override
    public Map<String, String> getDefaultProperties() {
        return defaultProperties;
    }

    @Override
    public void process(ActionMessage msg) throws Exception {
        if (msg == null || msg.getAction() == null) {
            msgLog.warnMessageReceivedWithoutPayload(PLUGIN_NAME);
        }
        try {
            writeAlert(msg.getAction());
            msgLog.infoActionReceived(PLUGIN_NAME, msg.toString());
            Action successAction = msg.getAction();
            successAction.setResult(MESSAGE_PROCESSED);
            sendResult(successAction);
        } catch (Exception e) {
            msgLog.errorCannotProcessMessage(PLUGIN_NAME, e.getMessage());
            Action failedAction = msg.getAction();
            failedAction.setResult(MESSAGE_FAILED);
            sendResult(failedAction);
        }
    }

    protected String transform(Action a) {
        String spec = a.getProperties().get(PROP_TRANSFORM);
        if (spec == null || spec.isEmpty()) {
            return JsonUtil.toJson(a.getEvent());
        }
        try {
            Shiftr transformer = new Shiftr(JsonUtil.fromJson(spec, Map.class));
            return JsonUtil.toJson(transformer.transform(JsonUtil.getMap(a.getEvent())));
        } catch (Exception e) {
            msgLog.warnf("Plugin elasticsearch can not apply spec [%s]", spec);
            return JsonUtil.toJson(a.getEvent());
        }
    }

    protected void writeAlert(Action a) throws Exception {
        String host = a.getProperties().get(PROP_HOST);
        String port = a.getProperties().get(PROP_PORT);
        String index = a.getProperties().get(PROP_INDEX);
        String type = a.getProperties().get(PROP_TYPE);
        client = TransportClient.builder().build()
                .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(host), new Integer(port)));
        client.prepareIndex(index, type)
                .setCreate(true)
                .setSource(transform(a))
                .get();
        client.close();
    }

    private void sendResult(Action action) {
        if (sender == null) {
            throw new IllegalStateException("ActionPluginSender is not present in the plugin");
        }
        if (action == null) {
            throw new IllegalStateException("Action to update result must be not null");
        }
        ActionResponseMessage newMessage = sender.createMessage(ActionResponseMessage.Operation.RESULT);
        newMessage.getPayload().put("action", JsonUtil.toJson(action));
        try {
            sender.send(newMessage);
        } catch (Exception e) {
            msgLog.error("Error sending ActionResponseMessage", e);
        }
    }
}
