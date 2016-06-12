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
import org.sonar.api.batch.AnalysisMode;
import org.sonar.api.batch.bootstrap.ProjectBuilder;
import org.sonar.api.utils.MessageException;

/**
 * Trigger load of pull request metadata at the very beginning of SQ analysis. Also
 * set "in progress" status on the pull request.
 *
 */
public class PullRequestProjectBuilder extends ProjectBuilder {

  private final BitBucketPluginConfiguration config;
  private final PullRequestFacade pullRequestFacade;
  private final AnalysisMode mode;

  public PullRequestProjectBuilder(BitBucketPluginConfiguration bitBucketPluginConfiguration, PullRequestFacade pullRequestFacade, AnalysisMode mode) {
    this.config = bitBucketPluginConfiguration;
    this.pullRequestFacade = pullRequestFacade;
    this.mode = mode;
  }

  @Override
  public void build(Context context) {
    if (!config.isEnabled()) {
      return;
    }
    checkMode();
    int pullRequestNumber = config.pullRequestNumber();
    pullRequestFacade.init(pullRequestNumber, context.projectReactor().getRoot().getBaseDir());

    pullRequestFacade.unapprovePullRequest();
  }

  private void checkMode() {
    if (!mode.isIssues()) {
      throw MessageException.of(config.message("error.issue_mode_only", CoreProperties.ANALYSIS_MODE));
    }
  }

}
