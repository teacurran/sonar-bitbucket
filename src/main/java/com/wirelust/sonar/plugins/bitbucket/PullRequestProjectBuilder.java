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

import org.sonar.api.CoreProperties;
import org.sonar.api.batch.bootstrap.ProjectBuilder;
import org.sonar.api.config.Settings;
import org.sonar.api.utils.MessageException;

/**
 * Trigger load of pull request metadata at the very beginning of SQ analysis. Also
 * set "in progress" status on the pull request.
 *
 */
public class PullRequestProjectBuilder extends ProjectBuilder {

  private final BitBucketPluginConfiguration bitBucketPluginConfiguration;
  private final PullRequestFacade pullRequestFacade;
  private final Settings settings;

  public PullRequestProjectBuilder(BitBucketPluginConfiguration bitBucketPluginConfiguration, PullRequestFacade pullRequestFacade, Settings settings) {
    this.bitBucketPluginConfiguration = bitBucketPluginConfiguration;
    this.pullRequestFacade = pullRequestFacade;
    this.settings = settings;
  }

  @Override
  public void build(Context context) {
    if (!bitBucketPluginConfiguration.isEnabled()) {
      return;
    }
    checkMode();
    int pullRequestNumber = bitBucketPluginConfiguration.pullRequestNumber();
    pullRequestFacade.init(pullRequestNumber, context.projectReactor().getRoot().getBaseDir());

    pullRequestFacade.unapprovePullRequest();
  }

  private void checkMode() {
    String analysisMode = settings.getString(CoreProperties.ANALYSIS_MODE);
    boolean isIssues = CoreProperties.ANALYSIS_MODE_PREVIEW.equals(analysisMode)
      || CoreProperties.ANALYSIS_MODE_ISSUES.equals(analysisMode);
    if (!isIssues) {
      throw MessageException.of("The Bitbucket plugin is only intended to be used in preview or issues mode. Please set '" + CoreProperties.ANALYSIS_MODE + "'.");
    }

  }

}
