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
package org.hawkular.alerter.prometheus.rest;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class Notification {

    @JsonInclude
    String receiver;

    @JsonInclude
    String status;

    @JsonInclude
    List<Alert> alerts;

    @JsonInclude
    Map<String, String> groupLabels;

    @JsonInclude
    Map<String, String> commonLabels;

    @JsonInclude
    Map<String, String> commonAnnotations;

    @JsonInclude
    String externalURL;

    @JsonInclude
    String version;

    @JsonInclude
    String groupKey;

    public Notification() {
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<Alert> getAlerts() {
        return alerts;
    }

    public void setAlerts(List<Alert> alerts) {
        this.alerts = alerts;
    }

    public Map<String, String> getGroupLabels() {
        return groupLabels;
    }

    public void setGroupLabels(Map<String, String> groupLabels) {
        this.groupLabels = groupLabels;
    }

    public Map<String, String> getCommonLabels() {
        return commonLabels;
    }

    public void setCommonLabels(Map<String, String> commonLabels) {
        this.commonLabels = commonLabels;
    }

    public Map<String, String> getCommonAnnotations() {
        return commonAnnotations;
    }

    public void setCommonAnnotations(Map<String, String> commonAnnotations) {
        this.commonAnnotations = commonAnnotations;
    }

    public String getExternalURL() {
        return externalURL;
    }

    public void setExternalURL(String externalURL) {
        this.externalURL = externalURL;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public void setGroupKey(String groupKey) {
        this.groupKey = groupKey;
    }

    @Override
    public String toString() {
        return "Notification [receiver=" + receiver + ", status=" + status + ", alerts=" + alerts + ", groupLabels="
                + groupLabels + ", commonLabels=" + commonLabels + ", commonAnnotations=" + commonAnnotations
                + ", externalURL=" + externalURL + ", version=" + version + ", groupKey=" + groupKey + "]";
    }

    public static class Alert {
        @JsonInclude
        String status;

        @JsonInclude
        Map<String, String> labels;

        @JsonInclude
        Map<String, String> annotations;

        @JsonInclude
        String startsAt;

        @JsonInclude
        String endsAt;

        @JsonInclude
        String generatorURL;

        public Alert() {
            super();
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Map<String, String> getLabels() {
            return labels;
        }

        public void setLabels(Map<String, String> labels) {
            this.labels = labels;
        }

        public Map<String, String> getAnnotations() {
            return annotations;
        }

        public void setAnnotations(Map<String, String> annotations) {
            this.annotations = annotations;
        }

        public String getStartsAt() {
            return startsAt;
        }

        public void setStartsAt(String startsAt) {
            this.startsAt = startsAt;
        }

        public String getEndsAt() {
            return endsAt;
        }

        public void setEndsAt(String endsAt) {
            this.endsAt = endsAt;
        }

        public String getGeneratorURL() {
            return generatorURL;
        }

        public void setGeneratorURL(String generatorURL) {
            this.generatorURL = generatorURL;
        }

        @Override
        public String toString() {
            return "Alert [status=" + status + ", labels=" + labels + ", annotations=" + annotations + ", startsAt="
                    + startsAt + ", endsAt=" + endsAt + ", generatorURL=" + generatorURL + "]";
        }

    }
}
