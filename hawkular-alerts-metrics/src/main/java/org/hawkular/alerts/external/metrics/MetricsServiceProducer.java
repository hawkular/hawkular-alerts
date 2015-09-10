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
package org.hawkular.alerts.external.metrics;

import javax.enterprise.inject.Produces;

import org.hawkular.metrics.core.api.MetricsService;
import org.hawkular.metrics.core.impl.DataAccessImpl;
import org.hawkular.metrics.core.impl.MetricsServiceImpl;

import com.codahale.metrics.MetricRegistry;
import com.datastax.driver.core.Session;

/**
 * @author jay shaughnessy
 * @author john sanda
 */
public class MetricsServiceProducer {

    private MetricsServiceImpl metricsService;

    @Produces
    public MetricsService getMetricsService() throws Exception {
        if (metricsService == null) {
            metricsService = new MetricsServiceImpl();
            Session session = CassCluster.getSession();
            boolean resetDB = false;
            boolean createSchema = false;
            // TODO: forcing the keyspace and explicitly creating/setting the DataAccessImpl are necessary using
            //       metrics 0.6.0.Final.  Change this code if metrics supports something cleaner in the future.
            String keyspace = "hawkular_metrics";
            session.execute("USE " + keyspace);
            metricsService.setDataAccess(new DataAccessImpl(session));
            metricsService.startUp(session, keyspace, resetDB, createSchema, new MetricRegistry());
        }

        return metricsService;
    }
}
