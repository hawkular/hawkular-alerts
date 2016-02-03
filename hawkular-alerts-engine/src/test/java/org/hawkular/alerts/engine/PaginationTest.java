/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.alerts.engine;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Lucas Ponce
 */
public class PaginationTest {

    private static final Logger logger = LoggerFactory.getLogger(PaginationTest.class);

    @Test
    public void cassandraSmallTests() {

        /*
            Table creation:

            CREATE TABLE paging_table (
                partition       TEXT,   // partition key column
                cluster_01      TEXT,   // first clustering key column
                cluster_02      TEXT,   // second clustering key column
                cluster_03      TEXT,   // third clustering key column
                non_primary_key TEXT,   // column not part of primary key
                PRIMARY KEY (partition, cluster_01, cluster_02, cluster_03)
            ) WITH CLUSTERING ORDER BY (cluster_01 ASC, cluster_02 ASC, cluster_03 ASC);
         */

        /*
            Populate data
         */
        logger.info("insert into paging_table (partition, cluster_01, cluster_02, cluster_03, " +
                "non_primary_key) " +
                "values ('A01', 'B01', 'C01', 'D01', '01'); ");

        logger.info("insert into paging_table (partition, cluster_01, cluster_02, cluster_03, " +
                "non_primary_key) " +
                "values ('A01', 'B01', 'C01', 'D02', '02'); ");

        logger.info("insert into paging_table (partition, cluster_01, cluster_02, cluster_03, " +
                "non_primary_key) " +
                "values ('A01', 'B01', 'C02', 'D03', '03'); ");

        logger.info("insert into paging_table (partition, cluster_01, cluster_02, cluster_03, " +
                "non_primary_key) " +
                "values ('A01', 'B01', 'C02', 'D04', '04'); ");

        logger.info("insert into paging_table (partition, cluster_01, cluster_02, cluster_03, " +
                "non_primary_key) " +
                "values ('A01', 'B02', 'C03', 'D05', '05'); ");

        logger.info("insert into paging_table (partition, cluster_01, cluster_02, cluster_03, " +
                "non_primary_key) " +
                "values ('A01', 'B02', 'C03', 'D06', '06'); ");

        logger.info("insert into paging_table (partition, cluster_01, cluster_02, cluster_03, " +
                "non_primary_key) " +
                "values ('A02', 'B03', 'C04', 'D07', '07'); ");

        /*
            First page:

            SELECT *
            FROM paging_table
            WHERE partition = 'A01' AND cluster_01 = 'B01'
            ORDER BY cluster_01 ASC
            LIMIT 2;
         */


        /*
            Next page:

            SELECT *
            FROM paging_table
            WHERE partition = 'A01' AND
                (cluster_01, cluster_02, cluster_03) > ('B01', 'C01', 'D02')
            ORDER BY cluster_01 ASC
            LIMIT 2;
         */
    }
}
