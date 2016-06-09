package com.wirelust.sonar.plugins.bitbucket;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import com.wirelust.sonar.plugins.bitbucket.client.ResteasyRegisterBuiltin;
import org.junit.Assert;
import org.junit.Test;

/**
 * Date: 11-Jun-2016
 *
 * @author T. Curran
 */
public class ConstantsTest {

  /**
   * This method simply instantiates a private constructor to ensure code coverage for it so the
   * coverage reports aren't diminished
   */
  @Test
  public void testConstantsConstructorIsPrivate() throws Exception {
    Constructor<Constants> constructor = Constants.class.getDeclaredConstructor();
    Assert.assertTrue(Modifier.isPrivate(constructor.getModifiers()));
    constructor.setAccessible(true);
    constructor.newInstance();
  }

}
