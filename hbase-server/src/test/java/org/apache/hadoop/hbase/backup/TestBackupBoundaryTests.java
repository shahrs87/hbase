/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hbase.backup;

import static org.junit.Assert.assertTrue;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hbase.DoNotRetryIOException;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.testclassification.LargeTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.google.common.collect.Lists;

@Category(LargeTests.class)
public class TestBackupBoundaryTests extends TestBackupBase {

  private static final Log LOG = LogFactory.getLog(TestBackupBoundaryTests.class);

  /**
   * Verify that full backup is created on a single empty table correctly.
   * @throws Exception
   */
  @Test
  public void testFullBackupSingleEmpty() throws Exception {

    LOG.info("create full backup image on single table");
    List<TableName> tables = Lists.newArrayList(table3);
    String backupId = getBackupClient().create(BackupType.FULL, tables, BACKUP_ROOT_DIR);
    LOG.info("Finished Backup");
    assertTrue(checkSucceeded(backupId));
  }

  /**
   * Verify that full backup is created on multiple empty tables correctly.
   * @throws Exception
   */
  @Test
  public void testFullBackupMultipleEmpty() throws Exception {
    LOG.info("create full backup image on mulitple empty tables");

    List<TableName> tables = Lists.newArrayList(table3, table4);
    String backupId = getBackupClient().create(BackupType.FULL, tables, BACKUP_ROOT_DIR);
    assertTrue(checkSucceeded(backupId));
  }

  /**
   * Verify that full backup fails on a single table that does not exist.
   * @throws Exception
   */
  @Test(expected = DoNotRetryIOException.class)
  public void testFullBackupSingleDNE() throws Exception {

    LOG.info("test full backup fails on a single table that does not exist");
    List<TableName> tables = toList("tabledne");
    String backupId = getBackupClient().create(BackupType.FULL, tables, BACKUP_ROOT_DIR);
    assertTrue(checkSucceeded(backupId));
  }

  /**
   * Verify that full backup fails on multiple tables that do not exist.
   * @throws Exception
   */
  @Test(expected = DoNotRetryIOException.class)
  public void testFullBackupMultipleDNE() throws Exception {

    LOG.info("test full backup fails on multiple tables that do not exist");
    List<TableName> tables = toList("table1dne", "table2dne");
    String backupId = getBackupClient().create(BackupType.FULL, tables, BACKUP_ROOT_DIR);
    assertTrue(checkSucceeded(backupId));
  }

  /**
   * Verify that full backup fails on tableset containing real and fake tables.
   * @throws Exception
   */
  @Test(expected = DoNotRetryIOException.class)
  public void testFullBackupMixExistAndDNE() throws Exception {
    LOG.info("create full backup fails on tableset containing real and fake table");

    List<TableName> tables = toList(table1.getNameAsString(), "tabledne");
    String backupId = getBackupClient().create(BackupType.FULL, tables, BACKUP_ROOT_DIR);
    //assertTrue(checkSucceeded(backupId)); // TODO
  }
}