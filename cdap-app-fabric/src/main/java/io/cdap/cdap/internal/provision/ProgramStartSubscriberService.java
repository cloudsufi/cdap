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
import io.cdap.cdap.common.ConflictException;
import io.cdap.cdap.common.TooManyRequestsException;
import io.cdap.cdap.common.conf.CConfiguration;
import io.cdap.cdap.common.conf.Constants;
import io.cdap.cdap.common.utils.ImmutablePair;
import io.cdap.cdap.internal.app.ApplicationSpecificationAdapter;
import io.cdap.cdap.internal.app.runtime.ProgramOptionConstants;
import io.cdap.cdap.internal.app.services.AbstractNotificationSubscriberService;
import io.cdap.cdap.internal.app.services.RunRecordMonitorService;
import io.cdap.cdap.internal.app.store.AppMetadataStore;
import io.cdap.cdap.internal.app.store.RunRecordDetail;
import io.cdap.cdap.messaging.MessagingService;
import io.cdap.cdap.proto.Notification;
import io.cdap.cdap.proto.ProgramRunClusterStatus;
import io.cdap.cdap.proto.ProgramRunStatus;
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
public class ProgramStartSubscriberService extends AbstractNotificationSubscriberService {
  private static final Logger LOG = LoggerFactory.getLogger(ProgramStartSubscriberService.class);

  private static final Gson GSON = ApplicationSpecificationAdapter.addTypeAdapters(new GsonBuilder()).create();

  private final int maxConcurrentRuns;
  private final int maxConcurrentLaunching;
  private final RunRecordMonitorService runRecordMonitorService;
  private final ProvisionerNotifier provisionerNotifier;

  @Inject
  ProgramStartSubscriberService(MessagingService messagingService, CConfiguration cConf,
                                MetricsCollectionService metricsCollectionService,
                                TransactionRunner transactionRunner,
                                RunRecordMonitorService runRecordMonitorService,
                                ProvisionerNotifier provisionerNotifier) {
    super("provision.pending", cConf,
          cConf.get(Constants.AppFabric.PROGRAM_START_EVENT_TOPIC),
          cConf.getInt(Constants.AppFabric.PROGRAM_START_EVENT_FETCH_SIZE),
          cConf.getLong(Constants.AppFabric.PROGRAM_START_EVENT_POLL_DELAY_MILLIS),
          messagingService, metricsCollectionService, transactionRunner);
    this.maxConcurrentRuns = cConf.getInt(Constants.AppFabric.MAX_CONCURRENT_RUNS);
    this.maxConcurrentLaunching = cConf.getInt(Constants.AppFabric.MAX_CONCURRENT_LAUNCHING);
    this.runRecordMonitorService = runRecordMonitorService;
    this.provisionerNotifier = provisionerNotifier;
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
      try {
        reserveLaunchingSlots();
      } catch (TooManyRequestsException e) {
        LOG.warn(e.getMessage());
        break;
      }

      ImmutablePair<String, Notification> messagePair = messages.next();
      ProgramRunId programRunId;
      try {
        programRunId = processNotification(messagePair.getSecond());
      } catch (IllegalArgumentException | ConflictException e) {
        LOG.warn(e.getMessage());
        releaseLaunchingReservation();
        continue;
      }
      commitLaunchingReservation(programRunId);
    }
  }

  /**
   * Process a {@link Notification} received from TMS.
   */
  private ProgramRunId processNotification(Notification notification) throws IllegalArgumentException, ConflictException {
    // Validate notification type
    if (!notification.getNotificationType().equals(Notification.Type.PROGRAM_STATUS)) {
      throw new IllegalArgumentException(String.format("Unexpected notification type %s. Should be %s",
                                                       notification.getNotificationType().toString(),
                                                       Notification.Type.PROGRAM_STATUS));
    }

    Map<String, String> properties = notification.getProperties();

    // Extract and validate ProgramRunId.
    String programRun = properties.get(ProgramOptionConstants.PROGRAM_RUN_ID);
    if (programRun == null) {
      throw new IllegalArgumentException(String.format("Unexpected notification: missing program run ID: %s",
                                                       notification));
    }
    ProgramRunId programRunId = GSON.fromJson(programRun, ProgramRunId.class);

    // Extract and validate ProgramRunClusterStatus is ENQUEUED
    String clusterStatusStr = properties.get(ProgramOptionConstants.CLUSTER_STATUS);
    if (clusterStatusStr == null) {
      throw new IllegalArgumentException(String.format("Unexpected notification: cluster status is missing: %s",
                                                       notification));
    }
    ProgramRunClusterStatus clusterStatus = null;
    clusterStatus = ProgramRunClusterStatus.valueOf(clusterStatusStr);
    if (clusterStatus != ProgramRunClusterStatus.ENQUEUED) {
      throw new IllegalArgumentException(String.format("Unexpected notification: cluster status is %s, expecting %s",
                                                       clusterStatus, ProgramRunClusterStatus.ENQUEUED));
    }

    // Start provisioning
    ProgramOptions programOptions = ProgramOptions.fromNotification(notification, GSON);
    ProgramDescriptor programDescriptor = GSON.fromJson(properties.get(ProgramOptionConstants.PROGRAM_DESCRIPTOR),
                                                        ProgramDescriptor.class);
    String userId = properties.get(ProgramOptionConstants.USER_ID);
    provisionerNotifier.provisioning(programRunId, programOptions, programDescriptor, userId);

    return programRunId;
  }

  private void reserveLaunchingSlots() throws TooManyRequestsException {
    RunRecordMonitorService.Counter cnt = runRecordMonitorService.reserveRequestAndGetCount();

    if (maxConcurrentRuns >= 0 &&
      cnt.getReservedLaunchingCount() + cnt.getLaunchingCount() + cnt.getRunningCount() > maxConcurrentRuns) {
      throw new TooManyRequestsException(
        String.format("Cannot start program because of %d reserved + %d launching + %d running > %d",
                      cnt.getReservedLaunchingCount(), cnt.getLaunchingCount(),
                      cnt.getRunningCount(), maxConcurrentRuns));
    }

    if (maxConcurrentLaunching >= 0 &&
      cnt.getReservedLaunchingCount() + cnt.getLaunchingCount() > maxConcurrentLaunching) {
      throw new TooManyRequestsException(
        String.format("Cannot start program because of %d reserved + %d launching > %d",
                      cnt.getReservedLaunchingCount(), cnt.getLaunchingCount(), maxConcurrentLaunching));
    }
  }

  private void releaseLaunchingReservation() {
    runRecordMonitorService.releaseReservedRequest();
  }

  private void commitLaunchingReservation(ProgramRunId programRunId) {
    runRecordMonitorService.commitReservedRequest(programRunId);
  }


  private AppMetadataStore getAppMetadataStore(StructuredTableContext context) {
    return AppMetadataStore.create(context);
  }
}