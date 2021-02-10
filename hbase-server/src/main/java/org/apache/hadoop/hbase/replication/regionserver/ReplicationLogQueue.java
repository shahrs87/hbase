/*
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
package org.apache.hadoop.hbase.replication.regionserver;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.util.EnvironmentEdgeManager;
import org.apache.hadoop.hbase.wal.AbstractFSWALProvider;
import org.apache.yetus.audience.InterfaceAudience;
import org.apache.yetus.audience.InterfaceStability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
  Class that does enqueueing/dequeueing of wal at one place so that we can update the metrics
  just at one place.
 */
@InterfaceAudience.Private
@InterfaceStability.Evolving
public class ReplicationLogQueue {
  // Queues of logs to process, entry in format of walGroupId->queue,
  // each presents a queue for one wal group
  private Map<String, PriorityBlockingQueue<Path>> queues = new HashMap<>();
  private MetricsSource metrics;
  private Configuration conf;
  // per group queue size, keep no more than this number of logs in each wal group
  private int queueSizePerGroup;
  // WARN threshold for the number of queued logs, defaults to 2
  private int logQueueWarnThreshold;
  private ReplicationSource source;
  private static final Logger LOG = LoggerFactory.getLogger(ReplicationSource.class);


  public ReplicationLogQueue(Configuration conf, MetricsSource metrics, ReplicationSource source) {
    this.conf = conf;
    this.metrics = metrics;
    this.source = source;
    this.queueSizePerGroup = this.conf.getInt("hbase.regionserver.maxlogs", 32);
    this.logQueueWarnThreshold = this.conf.getInt("replication.source.log.queue.warn", 2);
  }

  /**
   * Enqueue the wal
   * @param wal wal to be enqueued
   * @param walGroupId Key for the wal in @queues map
   * @return boolean whether this is the first time we are seeing this walGroupId.
   */
  public boolean enqueueLog(Path wal, String walGroupId) {
    boolean exists = false;
    PriorityBlockingQueue<Path> queue = queues.get(walGroupId);
    if (queue == null) {
      queue = new PriorityBlockingQueue<>(queueSizePerGroup,
        new AbstractFSWALProvider.WALStartTimeComparator());
      // make sure that we do not use an empty queue when setting up a ReplicationSource, otherwise
      // the shipper may quit immediately
      queue.put(wal);
      queues.put(walGroupId, queue);
    } else {
      exists = true;
      queue.put(wal);
    }
    // Increment size of logQueue
    this.metrics.incrSizeOfLogQueue();
    // Compute oldest wal age
    setOldestWalAge();
    // This will wal a warning for each new wal that gets created above the warn threshold
    int queueSize = queue.size();
    if (queueSize > this.logQueueWarnThreshold) {
      LOG.warn("{} WAL group {} queue size: {} exceeds value of " +
          "replication.source.log.queue.warn {}", source.logPeerId(), walGroupId, queueSize,
        logQueueWarnThreshold);
    }
    return exists;
  }

  /**
   * Get the queue size for the given walGroupId.
   * @param walGroupId
   */
  public int getQueueSize(String walGroupId) {
    Queue queue = queues.get(walGroupId);
    if (queue == null) {
      return 0;
    }
    return queue.size();
  }

  /**
   * Returns number of queues.
   */
  public int getNumQueues() {
    return queues.size();
  }

  public Map<String, PriorityBlockingQueue<Path>> getQueues() {
    return queues;
  }

  /**
   * Return queue for the given walGroupId
   * Please don't add or remove elements from the returned queue.
   * Use @enqueueLog and @remove methods respectively.
   * @param walGroupId
   */
  public PriorityBlockingQueue<Path> getQueue(String walGroupId) {
    return queues.get(walGroupId);
  }

  /**
   * Remove head from the queue corresponding to given walGroupId.
   * @param walGroupId
   */
  public void remove(String walGroupId) {
    PriorityBlockingQueue<Path> queue = getQueue(walGroupId);
    if (queue == null || queue.isEmpty()) {
      return;
    }
    queue.remove();
    // Decrease size logQueue.
    metrics.decrSizeOfLogQueue();
    // Re-compute age of oldest wal metric.
    setOldestWalAge();
  }

  /**
   * Remove all the elements from the queue corresponding to walGroupId
   * @param walGroupId
   */
  public void clear(String walGroupId) {
    PriorityBlockingQueue<Path> queue = getQueue(walGroupId);
    while (!queue.isEmpty()) {
      // Need to iterate since metrics#decrSizeOfLogQueue decrements just by 1.
      queue.remove();
      metrics.decrSizeOfLogQueue();
    }
    setOldestWalAge();
  }

  private void setOldestWalAge() {
    long now = EnvironmentEdgeManager.currentTime();
    long timestamp = getOldestWalTimestamp();
    // TODO: Should we handle the case where getOldestWalTimestamp returns Long.MAX_VALUE ?
    long age = now - timestamp;
    this.metrics.setOldestWalAge(age);
  }

  /*
  Get the oldest wal timestamp from all the queues.
 */
  private long getOldestWalTimestamp() {
    long oldestWalTimestamp = Long.MAX_VALUE;
    for (Map.Entry<String, PriorityBlockingQueue<Path>> entry : queues.entrySet()) {
      PriorityBlockingQueue<Path> queue = entry.getValue();
      Path path = queue.peek();
      // Can path ever be null ?
      if (path != null) {
        oldestWalTimestamp = Math.min(oldestWalTimestamp,
          AbstractFSWALProvider.WALStartTimeComparator.getTS(path));
      }
    }
    return oldestWalTimestamp;
  }
}
