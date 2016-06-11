/*
 * SonarQube :: Bitbucket Plugin
 * Copyright (C) 2015-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
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
