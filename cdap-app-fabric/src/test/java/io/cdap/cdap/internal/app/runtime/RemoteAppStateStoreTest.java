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

package io.cdap.cdap.internal.app.runtime;

import io.cdap.cdap.common.ApplicationNotFoundException;
import io.cdap.cdap.common.NotFoundException;
import io.cdap.cdap.common.conf.CConfiguration;
import io.cdap.cdap.common.conf.Constants;
import io.cdap.cdap.common.discovery.URIScheme;
import io.cdap.cdap.common.http.CommonNettyHttpServiceBuilder;
import io.cdap.cdap.common.internal.remote.DefaultInternalAuthenticator;
import io.cdap.cdap.common.internal.remote.RemoteClientFactory;
import io.cdap.cdap.common.metrics.NoOpMetricsCollectionService;
import io.cdap.cdap.common.namespace.InMemoryNamespaceAdmin;
import io.cdap.cdap.common.namespace.NamespaceAdmin;
import io.cdap.cdap.gateway.handlers.AppStateHandler;
import io.cdap.cdap.internal.app.services.ApplicationLifecycleService;
import io.cdap.cdap.internal.app.store.state.AppStateKey;
import io.cdap.cdap.proto.NamespaceMeta;
import io.cdap.cdap.proto.id.ApplicationId;
import io.cdap.cdap.proto.id.NamespaceId;
import io.cdap.cdap.security.auth.context.AuthenticationTestContext;
import io.cdap.http.NettyHttpService;
import org.apache.twill.common.Cancellable;
import org.apache.twill.discovery.InMemoryDiscoveryService;
import org.hamcrest.CoreMatchers;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Optional;

/**
 * Tests for {@link RemoteAppStateStore}
 */
public class RemoteAppStateStoreTest {

  private static final String NAMESPACE = "ns1";

  private static NettyHttpService httpService;
  private static ApplicationLifecycleService applicationLifecycleService;
  private static CConfiguration cConf;
  private static RemoteClientFactory remoteClientFactory;
  private static Cancellable cancellable;

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @BeforeClass
  public static void setup() throws Exception {
    cConf = CConfiguration.create();
    cConf.set("app.state.retry.policy.base.delay.ms", "10");
    cConf.set("app.state.retry.policy.max.delay.ms", "2000");
    cConf.set("app.state.retry.policy.max.retries", "2147483647");
    cConf.set("app.state.retry.policy.max.time.secs", "60");
    cConf.set("app.state.retry.policy.type", "exponential.backoff");
    InMemoryDiscoveryService discoveryService = new InMemoryDiscoveryService();
    NamespaceAdmin namespaceAdmin = new InMemoryNamespaceAdmin();
    applicationLifecycleService = Mockito.mock(ApplicationLifecycleService.class);
    NamespaceMeta testNameSpace = new NamespaceMeta.Builder()
      .setName(NAMESPACE)
      .setDescription("This is the default namespace, which is automatically created, and is always available.")
      .build();
    namespaceAdmin.create(testNameSpace);
    remoteClientFactory = new RemoteClientFactory(discoveryService,
                                                  new DefaultInternalAuthenticator(new AuthenticationTestContext()));
    httpService = new CommonNettyHttpServiceBuilder(cConf, "appfabric", new NoOpMetricsCollectionService())
      .setHttpHandlers(new AppStateHandler(applicationLifecycleService, namespaceAdmin)).build();
    httpService.start();
    cancellable = discoveryService
      .register(URIScheme.createDiscoverable(Constants.Service.APP_FABRIC_HTTP, httpService));
  }

  @AfterClass
  public static void cleanUp() throws Exception {
    cancellable.cancel();
    httpService.stop();
  }

  @Test
  public void testSaveSuccess() throws IOException {
    RemoteAppStateStore remoteAppStateStore = new RemoteAppStateStore(cConf, remoteClientFactory, NAMESPACE, "app1");
    byte[] value = "testvalue".getBytes();
    remoteAppStateStore.saveState("key1", value);
  }

  @Test
  public void testSaveInvalidNamespace() throws IOException {
    expectedException.expectCause(CoreMatchers.isA(NotFoundException.class));
    RemoteAppStateStore remoteAppStateStore = new RemoteAppStateStore(cConf, remoteClientFactory, "invalid", "app1");
    byte[] value = "testvalue".getBytes();
    remoteAppStateStore.saveState("key1", value);
  }

  @Test
  public void testSaveInvalidApp() throws ApplicationNotFoundException, IOException {
    expectedException.expectCause(CoreMatchers.isA(NotFoundException.class));
    String testAppName = "app1";
    RemoteAppStateStore remoteAppStateStore = new RemoteAppStateStore(cConf, remoteClientFactory, NAMESPACE,
                                                                      testAppName);
    byte[] value = "testvalue".getBytes();
    String testKey = "key1";
    Mockito.doThrow(new ApplicationNotFoundException(new ApplicationId(
      NAMESPACE, testAppName))).when(applicationLifecycleService).saveState(Mockito.any());
    remoteAppStateStore.saveState(testKey, value);
  }

  @Test
  public void testSaveFail() throws ApplicationNotFoundException, IOException {
    expectedException.expectCause(CoreMatchers.isA(IOException.class));
    String testAppName = "app1";
    RemoteAppStateStore remoteAppStateStore = new RemoteAppStateStore(cConf, remoteClientFactory, NAMESPACE,
                                                                      testAppName);
    byte[] value = "testvalue".getBytes();
    String testKey = "key1";
    Mockito.doThrow(new RuntimeException("test")).when(applicationLifecycleService).saveState(Mockito.any());
    remoteAppStateStore.saveState(testKey, value);
  }

  @Test
  public void testGetSuccess() throws ApplicationNotFoundException, IOException {
    RemoteAppStateStore remoteAppStateStore = new RemoteAppStateStore(cConf, remoteClientFactory, NAMESPACE, "app1");
    byte[] value = "testvalue".getBytes();
    Mockito.when(applicationLifecycleService.getState(Mockito.any())).thenReturn(Optional.of(value));
    Optional<byte[]> state = remoteAppStateStore.getState("key1");
    Assert.assertEquals(new String(value), new String(state.get()));
  }

  @Test
  public void testGetInvalidNamespace() throws IOException {
    expectedException.expectCause(CoreMatchers.isA(NotFoundException.class));
    RemoteAppStateStore remoteAppStateStore = new RemoteAppStateStore(cConf, remoteClientFactory, "invalid", "app1");
    remoteAppStateStore.getState("key1");
  }

  @Test
  public void testGetInvalidApp() throws ApplicationNotFoundException, IOException {
    expectedException.expectCause(CoreMatchers.isA(NotFoundException.class));
    String testAppName = "app1";
    RemoteAppStateStore remoteAppStateStore = new RemoteAppStateStore(cConf, remoteClientFactory, NAMESPACE,
                                                                      testAppName);
    String testKey = "key1";
    AppStateKey appStateKey = new AppStateKey(new NamespaceId(NAMESPACE), testAppName, testKey);
    Mockito.doThrow(new ApplicationNotFoundException(new ApplicationId(
      NAMESPACE, testAppName))).when(applicationLifecycleService).getState(Mockito.refEq(appStateKey));
    remoteAppStateStore.getState(testKey);
  }

  @Test
  public void testGetInvalidKey() throws ApplicationNotFoundException, IOException {
    String testAppName = "app2";
    RemoteAppStateStore remoteAppStateStore = new RemoteAppStateStore(cConf, remoteClientFactory, NAMESPACE,
                                                                      testAppName);
    String testKey = "key1";
    AppStateKey appStateKey = new AppStateKey(new NamespaceId(NAMESPACE), testAppName, testKey);
    Mockito.when(applicationLifecycleService.getState(Mockito.refEq(appStateKey))).thenReturn(Optional.empty());
    Optional<byte[]> state = remoteAppStateStore.getState(testKey);
    Assert.assertEquals(Optional.empty(), state);
  }

  @Test
  public void testGetFail() throws ApplicationNotFoundException, IOException {
    expectedException.expectCause(CoreMatchers.isA(IOException.class));
    String testAppName = "app3";
    RemoteAppStateStore remoteAppStateStore = new RemoteAppStateStore(cConf, remoteClientFactory, NAMESPACE,
                                                                      testAppName);
    String testKey = "key1";
    AppStateKey appStateKey = new AppStateKey(new NamespaceId(NAMESPACE), testAppName, testKey);
    Mockito.doThrow(new RuntimeException("test")).when(applicationLifecycleService)
      .getState(Mockito.refEq(appStateKey));
    remoteAppStateStore.getState(testKey);
  }
}