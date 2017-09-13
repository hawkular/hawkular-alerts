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
package org.hawkular.alerts.handlers;

import static org.hawkular.alerts.api.doc.DocConstants.POST;
import static org.hawkular.alerts.api.json.JsonUtil.fromJson;

import org.hawkular.alerts.api.doc.DocEndpoint;
import org.hawkular.alerts.api.doc.DocParameter;
import org.hawkular.alerts.api.doc.DocParameters;
import org.hawkular.alerts.api.doc.DocPath;
import org.hawkular.alerts.api.doc.DocResponse;
import org.hawkular.alerts.api.doc.DocResponses;
import org.hawkular.alerts.api.model.export.Definitions;
import org.hawkular.alerts.api.model.export.ImportType;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.engine.StandaloneAlerts;
import org.hawkular.alerts.handlers.util.ResponseUtil;
import org.hawkular.alerts.handlers.util.ResponseUtil.ApiError;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;
import org.hawkular.handlers.RestEndpoint;
import org.hawkular.handlers.RestHandler;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@RestEndpoint(path = "/import")
@DocEndpoint(value = "/import", description = "Import of triggers and actions definitions")
public class ImportHandler implements RestHandler {
    private static final MsgLogger log = MsgLogging.getMsgLogger(ImportHandler.class);

    DefinitionsService definitionsService;

    public ImportHandler() {
        definitionsService = StandaloneAlerts.getDefinitionsService();
    }

    @Override
    public void initRoutes(String baseUrl, Router router) {
        String path = baseUrl + "/import";
        router.post(path + "/:strategy").handler(this::importDefinitions);
    }

    @DocPath(method = POST,
            path = "/{strategy}",
            name = "Import a list of full triggers and action definitions.",
            notes = "Return a list of effectively imported full triggers and action definitions. + \n" +
                    " + \n" +
                    "Import options: + \n" +
                    " + \n" +
                    "DELETE + \n" +
                    "" +
                    " + \n" +
                    "Existing data in the backend is DELETED before the import operation. + \n" +
                    "All <<FullTrigger>> and <<ActionDefinition>> objects defined in the <<Definitions>> parameter " +
                    "are imported. + \n" +
                    " + \n" +
                    "ALL + \n" +
                    " + \n" +
                    "Existing data in the backend is NOT DELETED before the import operation. + \n" +
                    "All <<FullTrigger>> and <<ActionDefinition>> objects defined in the <<Definitions>> parameter " +
                    "are imported. + \n" +
                    "Existing <<FullTrigger>> and <<ActionDefinition>> objects are overwritten with new values " +
                    "passed in the <<Definitions>> parameter." +
                    " + \n" +
                    "NEW + \n" +
                    " + \n" +
                    "Existing data in the backend is NOT DELETED before the import operation. + \n" +
                    "Only NEW <<FullTrigger>> and <<ActionDefinition>> objects defined in the <<Definitions>> " +
                    "parameters are imported. + \n" +
                    "Existing <<FullTrigger>> and <<ActionDefinition>> objects are maintained in the backend. + \n" +
                    " + \n" +
                    "OLD + \n" +
                    "Existing data in the backend is NOT DELETED before the import operation. + \n" +
                    "Only <<FullTrigger>> and <<ActionDefinition>> objects defined in the <<Definitions>> parameter " +
                    "that previously exist in the backend are imported and overwritten. + \n" +
                    "New <<FullTrigger>> and <<ActionDefinition>> objects that don't exist previously in the " +
                    "backend are ignored. + \n" +
                    " + \n")
    @DocParameters(value = {
            @DocParameter(name = "strategy", required = true, path = true,
                    description = "Import strategy.",
                    allowableValues = "DELETE,ALL,NEW,OLD"),
            @DocParameter(required = true, body = true, type = Definitions.class,
                    description = "Collection of full triggers and action definitions to import.")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Successfully exported list of full triggers and action definitions.", response = Definitions.class),
            @DocResponse(code = 400, message = "Bad Request/Invalid Parameters.", response = ApiError.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void importDefinitions(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    String json = routing.getBodyAsString();
                    String strategy = routing.request().getParam("strategy");
                    Definitions definitions;
                    try {
                        definitions = fromJson(json, Definitions.class);
                    } catch (Exception e) {
                        log.errorf("Error parsing Definitions json: %s. Reason: %s", json, e.toString());
                        throw new ResponseUtil.NotFoundException(e.toString());
                    }
                    try {
                        ImportType importType = ImportType.valueOf(strategy.toUpperCase());
                        Definitions imported = definitionsService.importDefinitions(tenantId, definitions, importType);
                        future.complete(imported);
                    } catch (IllegalArgumentException e) {
                        throw new ResponseUtil.BadRequestException(e.toString());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                }, res -> ResponseUtil.result(routing, res));
    }
}
