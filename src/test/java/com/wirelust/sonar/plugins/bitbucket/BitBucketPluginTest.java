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

import org.junit.Test;
import org.sonar.api.Plugin;
import org.sonar.api.utils.Version;

import static org.assertj.core.api.Assertions.assertThat;

public class BitBucketPluginTest {

  @Test
  public void uselessTest() {
    Plugin.Context context = new Plugin.Context(Version.parse("5.6"));
    new BitBucketPlugin().define(context);
    assertThat(context.getExtensions().size()).isGreaterThan(1);
  }

}
