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

import org.junit.Before;
import org.junit.Test;
import org.sonar.api.CoreProperties;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class BitBucketPluginConfigurationTest {

  private Settings settings;
  private BitBucketPluginConfiguration config;

  @Before
  public void prepare() {
    settings = new Settings(new PropertyDefinitions(BitBucketPlugin.class));
    config = new BitBucketPluginConfiguration(settings);
  }

  @Test
  public void guessRepositoryFromScmUrl() {
    assertThat(config.repositoryRaw()).isNull();

    settings.setProperty(CoreProperties.LINKS_SOURCES, "do_not_match");
    settings.setProperty(CoreProperties.LINKS_SOURCES_DEV, "do_not_match");
    assertThat(config.repositoryRaw()).isNull();

    settings.setProperty(CoreProperties.LINKS_SOURCES, "scm:git:git@bitbucket.com:SonarCommunity/bitbucket-integration.git");
    assertThat(config.repositoryRaw()).isEqualTo("SonarCommunity/bitbucket-integration");
    assertThat(config.repositoryOwner()).isEqualTo("SonarCommunity");
    assertThat(config.repository()).isEqualTo("bitbucket-integration");

    settings.setProperty(CoreProperties.LINKS_SOURCES_DEV, "scm:git:git@bitbucket.com:SonarCommunity2/bitbucket-integration.git");
    assertThat(config.repositoryRaw()).isEqualTo("SonarCommunity2/bitbucket-integration");
    assertThat(config.repositoryOwner()).isEqualTo("SonarCommunity2");
    assertThat(config.repository()).isEqualTo("bitbucket-integration");

    settings.setProperty(BitBucketPlugin.BITBUCKET_REPO, "https://bitbucket.com/SonarCommunity/sonar-bitbucket.git");
    assertThat(config.repositoryRaw()).isEqualTo("SonarCommunity/sonar-bitbucket");
    assertThat(config.repositoryOwner()).isEqualTo("SonarCommunity");
    assertThat(config.repository()).isEqualTo("sonar-bitbucket");

    settings.setProperty(BitBucketPlugin.BITBUCKET_REPO, "http://bitbucket.com/SonarCommunity/sonar-bitbucket.git");
    assertThat(config.repositoryRaw()).isEqualTo("SonarCommunity/sonar-bitbucket");
    assertThat(config.repositoryOwner()).isEqualTo("SonarCommunity");
    assertThat(config.repository()).isEqualTo("sonar-bitbucket");

    settings.setProperty(BitBucketPlugin.BITBUCKET_REPO, "SonarCommunity3/bitbucket-integration");
    assertThat(config.repositoryRaw()).isEqualTo("SonarCommunity3/bitbucket-integration");
    assertThat(config.repositoryOwner()).isEqualTo("SonarCommunity3");
    assertThat(config.repository()).isEqualTo("bitbucket-integration");
  }

  @Test
  public void other() {
    settings.setProperty(BitBucketPlugin.BITBUCKET_LOGIN, "login");
    assertThat(config.login()).isEqualTo("login");

    settings.setProperty(BitBucketPlugin.BITBUCKET_PASS_KEY, "password");
    assertThat(config.password()).isEqualTo("password");

    assertThat(config.isEnabled()).isFalse();
    settings.setProperty(BitBucketPlugin.BITBUCKET_PULL_REQUEST, "3");
    assertThat(config.pullRequestNumber()).isEqualTo(3);
    assertThat(config.isEnabled()).isTrue();

    assertThat(config.endpoint()).isEqualTo("https://bitbucket.org/api");
    settings.setProperty(BitBucketPlugin.BITBUCKET_ENDPOINT, "http://myprivate-endpoint");
    assertThat(config.endpoint()).isEqualTo("http://myprivate-endpoint");
  }

  @Test
  public void shouldBeAbleToDefaultCiKey() {
    assertEquals(BitBucketPlugin.DEFAULT_CI_KEY_NAME, config.ciKey());

    settings.setProperty(BitBucketPlugin.BITBUCKET_CI_KEY, "repository_key");
    assertEquals("repository_key", config.ciKey());
  }

  @Test
  public void shouldBeAbleToDefaultCiName() {
    assertEquals(BitBucketPlugin.DEFAULT_CI_KEY_NAME, config.ciName());

    settings.setProperty(BitBucketPlugin.BITBUCKET_CI_NAME, "repository_name");
    assertEquals("repository_name", config.ciName());
  }

  @Test
  public void shouldBeAbleToDefaultCiUrl() {
    assertEquals(BitBucketPlugin.DEFAULT_CI_URL, config.ciURL());

    settings.setProperty(BitBucketPlugin.BITBUCKET_CI_URL, "ci_url");
    assertEquals("ci_url", config.ciURL());
  }
}
