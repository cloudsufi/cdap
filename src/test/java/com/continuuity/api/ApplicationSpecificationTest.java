package com.continuuity.api;


import com.continuuity.WordCountApp;
import com.continuuity.api.io.UnsupportedTypeException;
import com.continuuity.internal.app.ApplicationSpecificationAdapter;
import com.continuuity.internal.io.SimpleQueueSpecificationGeneratorFactory;
import com.continuuity.internal.io.ReflectionSchemaGenerator;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class ApplicationSpecificationTest {

  @Test
  public void testConfigureApplication() throws NoSuchMethodException, UnsupportedTypeException {
    ApplicationSpecification appSpec = new WordCountApp().configure();

    ApplicationSpecificationAdapter adapter
      = ApplicationSpecificationAdapter.create(SimpleQueueSpecificationGeneratorFactory.create());

    ApplicationSpecification newSpec = adapter.fromJson(adapter.toJson(appSpec));

    Assert.assertEquals(1, newSpec.getDataSets().size());
  }
}
