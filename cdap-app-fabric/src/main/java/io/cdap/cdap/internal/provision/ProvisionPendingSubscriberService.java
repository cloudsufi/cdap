/*
 * Copyright © 2022 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.cdap.cdap.internal.provision;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import io.cdap.cdap.api.metrics.MetricsCollectionService;
import io.cdap.cdap.app.program.ProgramDescriptor;
import io.cdap.cdap.app.runtime.ProgramOptions;
import io.cdap.cdap.common.TooManyRequestsException;
import io.cdap.cdap.common.conf.CConfiguration;
import io.cdap.cdap.common.conf.Constants;
import io.cdap.cdap.common.utils.ImmutablePair;
import io.cdap.cdap.internal.app.ApplicationSpecificationAdapter;
import io.cdap.cdap.internal.app.runtime.ProgramOptionConstants;
import io.cdap.cdap.internal.app.services.AbstractNotificationSubscriberService;
import io.cdap.cdap.internal.app.services.RunRecordMonitorService;
import io.cdap.cdap.internal.app.store.AppMetadataStore;
import io.cdap.cdap.messaging.MessagingService;
import io.cdap.cdap.proto.Notification;
import io.cdap.cdap.proto.ProgramRunClusterStatus;
import io.cdap.cdap.proto.id.ProgramRunId;
import io.cdap.cdap.spi.data.StructuredTableContext;
import io.cdap.cdap.spi.data.TableNotFoundException;
import io.cdap.cdap.spi.data.transaction.TransactionRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Service that receives program start notification and launches provisioning when not hitting flow control limits.
 */
public class ProvisionPendingSubscriberService extends AbstractNotificationSubscriberService {
  private static final Logger LOG = LoggerFactory.getLogger(ProvisionPendingSubscriberService.class);

  private static final Gson GSON = ApplicationSpecificationAdapter.addTypeAdapters(new GsonBuilder()).create();

  private final RunRecordMonitorService runRecordMonitorService;
  private final ProvisionerNotifier provisionerNotifier;
  private final int maxConcurrentRuns;
  private final int maxConcurrentLaunching;

  @Inject
  ProvisionPendingSubscriberService(MessagingService messagingService, CConfiguration cConf,
                                    MetricsCollectionService metricsCollectionService,
                                    TransactionRunner transactionRunner,
                                    RunRecordMonitorService runRecordMonitorService,
                                    ProvisionerNotifier provisionerNotifier) {
    super("provision.pending", cConf,
          cConf.get(Constants.AppFabric.PROVISION_PENDING_EVENT_TOPIC),
          cConf.getInt(Constants.AppFabric.PROVISION_PENDING_EVENT_FETCH_SIZE),
          cConf.getLong(Constants.AppFabric.PROVISION_PENDING_POLL_DELAY_MILLIS),
          messagingService, metricsCollectionService, transactionRunner);
    this.runRecordMonitorService = runRecordMonitorService;
    this.provisionerNotifier = provisionerNotifier;
    this.maxConcurrentRuns = cConf.getInt(Constants.AppFabric.MAX_CONCURRENT_RUNS);
    this.maxConcurrentLaunching = cConf.getInt(Constants.AppFabric.MAX_CONCURRENT_LAUNCHING);
  }

  @Override
  protected void doStartUp() throws Exception {
    super.doStartUp();
  }

  @Nullable
  @Override
  protected String loadMessageId(StructuredTableContext context) throws IOException, TableNotFoundException {
    return getAppMetadataStore(context).retrieveSubscriberState(getTopicId().getTopic(), "");
  }

  @Override
  protected void storeMessageId(StructuredTableContext context, String messageId)
    throws IOException, TableNotFoundException {
    getAppMetadataStore(context).persistSubscriberState(getTopicId().getTopic(), "", messageId);
  }

  @Override
  protected void processMessages(StructuredTableContext structuredTableContext,
                                 Iterator<ImmutablePair<String, Notification>> messages) throws Exception {
    while (messages.hasNext()) {
      ImmutablePair<String, Notification> messagePair = messages.next();
      LOG.error("wyzhang: program start subscriber start");
      processNotification(messagePair.getSecond(),
                          structuredTableContext);
      LOG.error("wyzhang: program start subscriber end end");
    }
  }

  /**
   * Process a {@link Notification} received from TMS.
   */
  private void processNotification(Notification notification,
                                   StructuredTableContext context) throws Exception {
    if (!notification.getNotificationType().equals(Notification.Type.PROGRAM_STATUS)) {
      LOG.warn("Unexpected notification ignored. Type should be {}: {}",
               Notification.Type.PROGRAM_STATUS, notification);
      return;
    }

    Map<String, String> properties = notification.getProperties();

    // Extract and validate ProgramRunId. Ignore notification if ProgramRunId is missing, which should never happen.
    String programRun = properties.get(ProgramOptionConstants.PROGRAM_RUN_ID);
    if (programRun == null) {
      LOG.warn("Unexpected notification ignored. Program run ID is missing: {}", notification);
      return;
    }
    ProgramRunId programRunId = GSON.fromJson(programRun, ProgramRunId.class);

    // Extract and validate ProgramRunClusterStatus.
    String clusterStatusStr = properties.get(ProgramOptionConstants.CLUSTER_STATUS);
    if (clusterStatusStr == null) {
      LOG.warn("Unexpected notification ignored. Cluster status is missing: {}", notification);
      return;
    }

    ProgramRunClusterStatus clusterStatus = null;
    try {
      clusterStatus = ProgramRunClusterStatus.valueOf(clusterStatusStr);
    } catch (IllegalArgumentException e) {
      LOG.warn("Unexpected notification ignored. Cluster status string {} cannot be parsed: {}",
               clusterStatusStr, e);
      return;
    }

    handleClusterEvent(programRunId, clusterStatus, notification);

    return;
  }

  /**
   * Handles a notification related to cluster operations.
   *
   * @param programRunId program run id from the event
   * @param clusterStatus cluster status from the event
   * @param notification the notification to process
   * @param messageIdBytes the unique ID for the notification message
   * @param appMetadataStore the data table to use
   * @param context the table context for performing table operations
   * @return an {@link Optional} of {@link Runnable} to carry a task to execute after handling of this event completed.
   * See {@link #postProcess()} for details.
   * @throws IOException if failed to read/write to the app metadata store.
   */
  private void handleClusterEvent(ProgramRunId programRunId, ProgramRunClusterStatus clusterStatus,
                                  Notification notification) throws Exception {
    LOG.error("wyzhang: program start subscriber: handle event");
    Map<String, String> properties = notification.getProperties();

    switch (clusterStatus) {
      case ENQUEUED:
        RunRecordMonitorService.Counter counter = runRecordMonitorService.addRequestAndGetCount(programRunId);
        boolean done = false;
        LOG.error("wyzhang: program start subscriber: launching = {}, running = {}",
                  counter.getLaunchingCount(), counter.getRunningCount());
        try {
          if (maxConcurrentRuns >= 0 &&
            counter.getLaunchingCount() + counter.getRunningCount() > maxConcurrentRuns) {
            String msg =
              String.format("Program %s cannot start because " +
                              "the maximums of %d outstanding runs are allowed",
                            programRunId, maxConcurrentRuns);
            LOG.info(msg);

            TooManyRequestsException e = new TooManyRequestsException(msg);
            LOG.error("wyzhang: program start subscriber: reject. Too many running");
            throw e;
          }

          if (maxConcurrentLaunching >= 0 &&
            counter.getLaunchingCount() > maxConcurrentLaunching) {
            String msg = String.format("Program %s cannot start because " +
                                         "the maximums of %d concurrent provisioning/starting runs " +
                                         "are allowed",
                                       programRunId, maxConcurrentLaunching);
            LOG.info(msg);

            TooManyRequestsException e = new TooManyRequestsException(msg);
            LOG.error("wyzhang: program start subscriber: reject. Too many launching");
            throw e;
          }


          LOG.error("wyzhang: program start subscriber: accept. do provision");

          ProgramOptions programOptions = ProgramOptions.fromNotification(notification, GSON);
          ProgramDescriptor programDescriptor =
            GSON.fromJson(properties.get(ProgramOptionConstants.PROGRAM_DESCRIPTOR), ProgramDescriptor.class);
          String userId = properties.get(ProgramOptionConstants.USER_ID);
          provisionerNotifier.provisioning(programRunId, programOptions, programDescriptor, userId);

          done = true;
        } finally {
          if (!done) {
            runRecordMonitorService.removeRequest(programRunId, false);
          }
        }
        break;
      default:
        LOG.error("wyzhang: program start subscriber: ignore. Unexpected cluster status");
    }
    return;
  }

  private AppMetadataStore getAppMetadataStore(StructuredTableContext context) {
    return AppMetadataStore.create(context);
  }
}