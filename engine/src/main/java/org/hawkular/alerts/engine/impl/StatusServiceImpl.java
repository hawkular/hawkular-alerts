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
package org.hawkular.alerts.engine.impl;

import java.util.Map;

import org.hawkular.alerts.api.services.StatusService;
import org.hawkular.alerts.engine.service.PartitionManager;

/**
 * An implementation of {@link org.hawkular.alerts.api.services.StatusService}.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class StatusServiceImpl implements StatusService {

    PartitionManager partitionManager;

    public void setPartitionManager(PartitionManager partitionManager) {
        this.partitionManager = partitionManager;
    }

    @Override
    public boolean isStarted() {
        // TODO [lponce] this test is quite simple and with a different backend perhaps it doesnt give enough info
        // TODO Perhaps on this call is better to call backend and check which is working correctly
        // TODO i.e. SELECT 1 FROM Test or similar kind of test probe
        return true;
    }

    @Override
    public boolean isDistributed() {
        return partitionManager.isDistributed();
    }

    @Override
    public Map<String, String> getDistributedStatus() {
        return partitionManager.getStatus();
    }
}
