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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;

import org.sonar.api.BatchComponent;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.InstantiationStrategy;
import org.sonar.api.config.Settings;

@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
public class BitBucketPluginConfiguration implements BatchComponent {

  public static final int MAX_GLOBAL_ISSUES  = 10;

  private Settings settings;
  private Pattern gitSshPattern;
  private Pattern gitHttpPattern;

  public BitBucketPluginConfiguration(Settings settings) {
    this.settings = settings;
    this.gitSshPattern = Pattern.compile(".*@bitbucket\\.com:(.*/.*)\\.git");
    this.gitHttpPattern = Pattern.compile("https?://bitbucket\\.com/(.*/.*)\\.git");
  }

  public int pullRequestNumber() {
    return settings.getInt(BitBucketPlugin.BITBUCKET_PULL_REQUEST);
  }

  @CheckForNull
  public String repositoryRaw() {
    String repo = null;
    if (settings.hasKey(BitBucketPlugin.BITBUCKET_REPO)) {
      String urlOrRepo = settings.getString(BitBucketPlugin.BITBUCKET_REPO);
      repo = parseGitUrl(urlOrRepo);
      if (repo == null) {
        repo = urlOrRepo;
      }
    }
    if (repo == null && settings.hasKey(CoreProperties.LINKS_SOURCES_DEV)) {
      String url = settings.getString(CoreProperties.LINKS_SOURCES_DEV);
      repo = parseGitUrl(url);
    }
    if (repo == null && settings.hasKey(CoreProperties.LINKS_SOURCES)) {
      String url = settings.getString(CoreProperties.LINKS_SOURCES);
      repo = parseGitUrl(url);
    }
    return repo;
  }

  public String repository() {
    String repositoryRaw = repositoryRaw();
    if (repositoryRaw != null) {
      if (repositoryRaw.contains("/")) {
        String[] repoSplit = repositoryRaw.split("/");
        if (repoSplit.length > 1) {
          return repoSplit[1];
        }
      }
    }
    return null;
  }

  public String repositoryOwner() {
    String repositoryRaw = repositoryRaw();
    if (repositoryRaw != null) {
      if (repositoryRaw.contains("/")) {
        String[] repoSplit = repositoryRaw.split("/");
        if (repoSplit.length > 0) {
          return repoSplit[0];
        }
      }
    }
    return null;
  }

  @CheckForNull
  private String parseGitUrl(String urlOrRepo) {
    Matcher matcher = gitSshPattern.matcher(urlOrRepo);
    if (matcher.matches()) {
      return matcher.group(1);
    }
    matcher = gitHttpPattern.matcher(urlOrRepo);
    if (matcher.matches()) {
      return matcher.group(1);
    }
    return null;
  }

  public boolean reportNotInDiff() {
    return settings.getBoolean(BitBucketPlugin.BITBUCKET_REPORT_NOT_IN_PR);
  }

  @CheckForNull
  public String tokenEndpoint() {
    return settings.getString(BitBucketPlugin.BITBUCKET_TOKEN_ENDPOINT);
  }

  @CheckForNull
  public String password() {
    return settings.getString(BitBucketPlugin.BITBUCKET_PASSWORD);
  }

  @CheckForNull
  public String login() {
    return settings.getString(BitBucketPlugin.BITBUCKET_LOGIN);
  }

  @CheckForNull
  public String clientId() {
    return settings.getString(BitBucketPlugin.BITBUCKET_CLIENT_ID);
  }

  @CheckForNull
  public String clientSecret() {
    return settings.getString(BitBucketPlugin.BITBUCKET_CLIENT_SECRET);
  }

  public boolean isEnabled() {
    return settings.hasKey(BitBucketPlugin.BITBUCKET_PULL_REQUEST);
  }

  public String endpoint() {
    return settings.getString(BitBucketPlugin.BITBUCKET_ENDPOINT);
  }

  @CheckForNull
  public String issueThreshold() {
    return settings.getString(BitBucketPlugin.BITBUCKET_ISSUE_THRESHOLD);
  }

  public String ciKey() {
    String value = settings.getString(BitBucketPlugin.BITBUCKET_CI_KEY);
    if (value == null || value.isEmpty()) {
      value = BitBucketPlugin.DEFAULT_CI_KEY_NAME;
    }
    return value;
  }

  public String ciName() {
    String value = settings.getString(BitBucketPlugin.BITBUCKET_CI_NAME);
    if (value == null || value.isEmpty()) {
      value = BitBucketPlugin.DEFAULT_CI_KEY_NAME;
    }
    return value;
  }

  public String ciURL() {
    String value = settings.getString(BitBucketPlugin.BITBUCKET_CI_URL);
    if (value == null || value.isEmpty()) {
      value = BitBucketPlugin.DEFAULT_CI_URL;
    }
    return value;
  }
}
