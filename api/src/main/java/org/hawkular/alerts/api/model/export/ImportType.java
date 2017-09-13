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
package org.hawkular.alerts.api.model.export;

/**
 * Define the strategy to apply in case of conflicts with existing data during an import operation of alerts
 * definitions.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public enum ImportType {
    /**
     * Existing data in the backend is DELETED before the import operation.
     * All FullTrigger and ActionDefinition objects defined in the Definitions parameter are imported.
     */
    DELETE,

    /**
     * Existing data in the backend is NOT DELETED before the import operation.
     * All FullTrigger and ActionDefinition objects defined in the Definitions parameter are imported.
     * Existing FullTrigger and ActionDefinition objects are overwritten with new values passed in the
     * Definitions parameter.
     */
    ALL,

    /**
     * Existing data in the backend is NOT DELETED before the import operation.
     * Only NEW FullTrigger and ActionDefinition objects defined in the Definitions parameters are imported.
     * Existing FullTrigger and ActionDefinition objects are maintained in the backend.
     */
    NEW,

    /**
     * Existing data in the backend is NOT DELETED before the import operation.
     * Only FullTrigger and ActionDefinition objects defined in the Definitions parameter that previously exist
     * in the backend are imported and overwritten.
     * New FullTrigger and ActionDefinition objects that don't exist previously in the backend are ignored.
     *
     */
    OLD
}
