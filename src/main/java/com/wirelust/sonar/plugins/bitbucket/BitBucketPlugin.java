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

import com.wirelust.sonar.plugins.bitbucket.client.ApiClientFactory;
import org.sonar.api.CoreProperties;
import org.sonar.api.Plugin;
import org.sonar.api.Properties;
import org.sonar.api.Property;
import org.sonar.api.PropertyType;
import org.sonar.api.rule.Severity;

@Properties({
  @Property(
    key = BitBucketPlugin.BITBUCKET_ENDPOINT,
    name = "Bitbucket API Endpoint",
    description = "URL to access BitBucket API. Deafult value is fine for public BitBucket. Can be modified for BitBucket enterprise.",
    defaultValue = "https://bitbucket.org/api",
    global = true,
    module = true),
  @Property(
    key = BitBucketPlugin.BITBUCKET_TOKEN_ENDPOINT,
    name = "BitBucket OAuth Token Endpoint",
    description = "BitBucket OAuth Token Endpoint",
    defaultValue = "https://bitbucket.org/site",
    global = true,
    module = true),
  @Property(
    key = BitBucketPlugin.BITBUCKET_CLIENT_ID,
    name = "BitBucket API Client ID",
    description = "BitBucket API Client ID",
    defaultValue = "",
    global = false,
    module = true),
  @Property(
    key = BitBucketPlugin.BITBUCKET_CLIENT_SECRET,
    name = "BitBucket API Client Secret",
    description = "BitBucket API Client Secret",
    defaultValue = "",
    global = false,
    module = true),
  @Property(
    key = BitBucketPlugin.BITBUCKET_LOGIN,
    name = "BitBucket Login",
    description = "BitBucket account used to perform operations like adding comments on pull requests.",
    global = false),
  @Property(
    key = BitBucketPlugin.BITBUCKET_PASS_KEY,
    name = "BitBucket Password",
    description = "BitBucket Password",
    type = PropertyType.PASSWORD,
    global = false),
  @Property(
    key = BitBucketPlugin.BITBUCKET_REPO,
    name = "BitBucket repository",
    description = "BitBucket repository for this project. Will be guessed from '" + CoreProperties.LINKS_SOURCES_DEV + "' if present",
    global = false,
    project = true),
  @Property(
    key = BitBucketPlugin.BITBUCKET_PULL_REQUEST,
    name = "BitBucket Pull Request",
    description = "Pull request number",
    type = PropertyType.INTEGER,
    global = false,
    project = false,
    module = false),
  @Property(
    key = BitBucketPlugin.BITBUCKET_CI_KEY,
    name = "BitBucket CI Key",
    description = "Key for the CI job, must be unique across all your jobs. defaults to \"SonarQube\"",
    type = PropertyType.STRING,
    global = true,
    project = true
  ),
  @Property(
    key = BitBucketPlugin.BITBUCKET_CI_NAME,
    name = "BitBucket CI Name",
    description = "The name that appears on bitbucket for the build. defaults to \"" +
      BitBucketPlugin.DEFAULT_CI_KEY_NAME + "\"",
    type = PropertyType.STRING,
    global = true,
    project = true
  ),
  @Property(
    key = BitBucketPlugin.BITBUCKET_CI_URL,
    name = "BitBucket CI URL",
    description = "The url for the status. defaults to \"" + BitBucketPlugin.DEFAULT_CI_URL + "\"",
    type = PropertyType.STRING,
    global = true,
    project = true
  ),
  @Property(
    key = BitBucketPlugin.BITBUCKET_ISSUE_THRESHOLD,
    name = "BitBucket Issue Threshold",
    description = "Severity at which a pull request can be approved",
    type = PropertyType.SINGLE_SELECT_LIST,
    options = {Severity.BLOCKER, Severity.CRITICAL, Severity.MAJOR, Severity.MINOR, Severity.INFO},
    defaultValue = Severity.MAJOR,
    global = true,
    project = true,
    module = false),
  @Property(
    key = BitBucketPlugin.BITBUCKET_REPORT_NOT_IN_PR,
    name = "Report Issues Not In Diff",
    description = "Report on issues in code not changed in the Pull Request",
    type = PropertyType.BOOLEAN,
    defaultValue = "true",
    global = true,
    project = true,
    module = false)
})
public class BitBucketPlugin implements Plugin {

  public static final String BITBUCKET_CLIENT_ID = "sonar.bitbucket.client.id";
  public static final String BITBUCKET_CLIENT_SECRET = "sonar.bitbucket.client.secret";
  public static final String BITBUCKET_ENDPOINT = "sonar.bitbucket.endpoint";
  public static final String BITBUCKET_TOKEN_ENDPOINT = "sonar.bitbucket.token.endpoint";
  public static final String BITBUCKET_LOGIN = "sonar.bitbucket.login";
  public static final String BITBUCKET_PASS_KEY = "sonar.bitbucket.password";
  public static final String BITBUCKET_REPO = "sonar.bitbucket.repository";
  public static final String BITBUCKET_PULL_REQUEST = "sonar.bitbucket.pullRequest";
  public static final String BITBUCKET_ISSUE_THRESHOLD = "sonar.bitbucket.threshold";
  public static final String BITBUCKET_REPORT_NOT_IN_PR = "sonar.bitbucket.reportNotInDiff";
  public static final String BITBUCKET_CI_KEY = "sonar.bitbucket.ci.key";
  public static final String BITBUCKET_CI_NAME = "sonar.bitbucket.ci.name";
  public static final String BITBUCKET_CI_URL = "sonar.bitbucket.ci.url";

  public static final String DEFAULT_CI_KEY_NAME = "SonarQube";
  public static final String DEFAULT_CI_URL = "http://sonarqube.org";

  @Override
  public void define(Context context) {
    context.addExtensions(
      ApiClientFactory.class,
      PullRequestIssuePostJob.class,
      BitBucketPluginConfiguration.class,
      PullRequestProjectBuilder.class,
      PullRequestFacade.class,
      MarkDownUtils.class);
  }

}
