/*
 * Copyright © 2017-2018 Cask Data, Inc.
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

package io.cdap.cdap.internal.app.services.http.handlers;

import com.google.gson.JsonObject;
import io.cdap.cdap.AppWithSchedule;
import io.cdap.cdap.api.Config;
import io.cdap.cdap.api.artifact.ArtifactSummary;
import io.cdap.cdap.common.conf.Constants;
import io.cdap.cdap.common.id.Id;
import io.cdap.cdap.internal.app.services.http.AppFabricTestBase;
import io.cdap.cdap.proto.ScheduleDetail;
import io.cdap.cdap.proto.artifact.AppRequest;
import io.cdap.cdap.proto.id.ApplicationId;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

import java.util.List;

public class AppScheduleUpdateTest extends AppFabricTestBase {
  @ClassRule
  public static final ExternalResource RESOURCE = new ExternalResource() {
    private String previousAppUpdateSchedules;
    @Override
    protected void before() {
      // store the previous value
      previousAppUpdateSchedules = System.getProperty(Constants.AppFabric.APP_UPDATE_SCHEDULES);
      // Set app schedule update to be false
      System.setProperty(Constants.AppFabric.APP_UPDATE_SCHEDULES, "false");
    }

    @Override
    protected void after() {
      // reset the system property
      if (previousAppUpdateSchedules == null) {
        System.clearProperty(Constants.AppFabric.APP_UPDATE_SCHEDULES);
      } else {
        System.setProperty(Constants.AppFabric.APP_UPDATE_SCHEDULES, previousAppUpdateSchedules);
      }
    }
  };

  @Test
  public void testUpdateSchedulesFlag() throws Exception {
    // deploy an app with schedule
    AppWithSchedule.AppConfig config = new AppWithSchedule.AppConfig(true, true, true);

    Id.Artifact artifactId = Id.Artifact.from(Id.Namespace.fromEntityId(TEST_NAMESPACE_META2.getNamespaceId()),
                                              AppWithSchedule.NAME, VERSION1);
    addAppArtifact(artifactId, AppWithSchedule.class);
    AppRequest<? extends Config> request = new AppRequest<>(
      new ArtifactSummary(artifactId.getName(), artifactId.getVersion().getVersion()), config);

    ApplicationId defaultAppId = TEST_NAMESPACE_META2.getNamespaceId().app(AppWithSchedule.NAME);
    Assert.assertEquals(200, deploy(defaultAppId, request).getResponseCode());
    JsonObject result = getAppDetails(TEST_NAMESPACE_META2.getNamespaceId().getNamespace(),
                                      defaultAppId.getApplication());

    List<ScheduleDetail> actualSchSpecs = listSchedules(TEST_NAMESPACE_META2.getNamespaceId().getNamespace(),
                                                        defaultAppId.getApplication(),
                                                        result.get("appVersion").getAsString());

    // none of the schedules will be added - by default we have set update schedules to be false as system property.
    Assert.assertEquals(0, actualSchSpecs.size());

    request = new AppRequest<>(
      new ArtifactSummary(artifactId.getName(), artifactId.getVersion().getVersion()), config, null, null, true);


    Assert.assertEquals(200, deploy(defaultAppId, request).getResponseCode());
    result = getAppDetails(TEST_NAMESPACE_META2.getNamespaceId().getNamespace(), defaultAppId.getApplication());

    actualSchSpecs = listSchedules(TEST_NAMESPACE_META2.getNamespaceId().getNamespace(), defaultAppId.getApplication(),
                                   result.get("appVersion").getAsString());

    // both the schedules will be added as now,
    // we have provided update schedules property to be true manually in appRequest
    Assert.assertEquals(2, actualSchSpecs.size());

    config = new AppWithSchedule.AppConfig(true, true, false);
    request = new AppRequest<>(
      new ArtifactSummary(artifactId.getName(), artifactId.getVersion().getVersion()), config);

    Assert.assertEquals(200, deploy(defaultAppId, request).getResponseCode());
    result = getAppDetails(TEST_NAMESPACE_META2.getNamespaceId().getNamespace(), defaultAppId.getApplication());

    actualSchSpecs = listSchedules(TEST_NAMESPACE_META2.getNamespaceId().getNamespace(), defaultAppId.getApplication(),
                                   result.get("appVersion").getAsString());

    // no changes will be made, as default behavior is dont update schedules, so both the schedules should be there
    Assert.assertEquals(2, actualSchSpecs.size());

    config = new AppWithSchedule.AppConfig(false, false, false);
    request = new AppRequest<>(
      new ArtifactSummary(artifactId.getName(), artifactId.getVersion().getVersion()), config);

    Assert.assertEquals(200, deploy(defaultAppId, request).getResponseCode());
    result = getAppDetails(TEST_NAMESPACE_META2.getNamespaceId().getNamespace(), defaultAppId.getApplication());

    actualSchSpecs = listSchedules(TEST_NAMESPACE_META2.getNamespaceId().getNamespace(), defaultAppId.getApplication(),
                                   result.get("appVersion").getAsString());

    // the schedules will not be deleted 
    Assert.assertEquals(2, actualSchSpecs.size());
  }
}
