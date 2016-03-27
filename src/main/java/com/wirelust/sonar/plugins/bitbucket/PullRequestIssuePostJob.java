/*
 * SonarQube :: Bitbucket Plugin
 * Copyright (C) 2015 SonarSource
 * sonarqube@googlegroups.com
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package com.wirelust.sonar.plugins.bitbucket;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.CheckProject;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.issue.Issue;
import org.sonar.api.issue.ProjectIssues;
import org.sonar.api.resources.Project;

/**
 * Compute comments to be added on the pull request.
 */
public class PullRequestIssuePostJob implements org.sonar.api.batch.PostJob, CheckProject {

  private static final Logger LOGGER = LoggerFactory.getLogger(PullRequestIssuePostJob.class);

  private final PullRequestFacade pullRequestFacade;
  private final ProjectIssues projectIssues;
  private final BitBucketPluginConfiguration config;
  private final InputFileCache inputFileCache;
  private final MarkDownUtils markDownUtils;

  public PullRequestIssuePostJob(BitBucketPluginConfiguration bitBucketPluginConfiguration,
                                 PullRequestFacade pullRequestFacade, ProjectIssues projectIssues,
                                 InputFileCache inputFileCache, MarkDownUtils markDownUtils) {
    this.config = bitBucketPluginConfiguration;
    this.pullRequestFacade = pullRequestFacade;
    this.projectIssues = projectIssues;
    this.inputFileCache = inputFileCache;
    this.markDownUtils = markDownUtils;
  }

  @Override
  public boolean shouldExecuteOnProject(Project project) {
    return config.isEnabled();
  }

  @Override
  public void executeOn(Project project, SensorContext context) {
    LOGGER.info("PullRequestIssuePostJob:{}", project != null ? project.toString() : "null");

    GlobalReport report = new GlobalReport(markDownUtils, config);
    Map<InputFile, Map<Integer, StringBuilder>> commentsToBeAddedByLine = processIssues(report);

    updateReviewComments(commentsToBeAddedByLine);

    pullRequestFacade.deleteOutdatedComments();

    LOGGER.info("report: hasNewIssues:{}", report.hasNewIssue());
    if (report.hasNewIssue()) {
      pullRequestFacade.addGlobalComment(report.formatForMarkdown());
    } else {
      pullRequestFacade.addGlobalComment("SonarQube reported no issues");
    }

    if (report.isApproved(config.issueThreshold())) {
      pullRequestFacade.approvePullRequest();
    } else {
      pullRequestFacade.unapprovePullRequest();
    }
  }

  @Override
  public String toString() {
    return "Bitbucket Pull Request Issue Publisher";
  }

  private Map<InputFile, Map<Integer, StringBuilder>> processIssues(GlobalReport report) {

    LOGGER.debug("processing issues");

    Map<InputFile, Map<Integer, StringBuilder>> commentToBeAddedByFileAndByLine = new HashMap<>();
    for (Issue issue : projectIssues.issues()) {
      String severity = issue.severity();
      boolean isNew = issue.isNew();
      if (!isNew) {
        continue;
      }

      LOGGER.debug("processing issue:{}, severity:{}, isNew:{}", issue.line(), issue.severity(), issue.isNew());

      Integer issueLine = issue.line();
      InputFile inputFile = inputFileCache.byKey(issue.componentKey());
      if (inputFile != null && !pullRequestFacade.hasFile(inputFile)) {
        // SONARGITUB-13 Ignore issues on files no modified by the P/R
        continue;
      }

      boolean reportedInline = false;
      if (inputFile != null && issueLine != null) {
        if (pullRequestFacade.hasFileLine(inputFile, issueLine)) {
          String message = issue.message();
          String ruleKey = issue.ruleKey().toString();
          if (!commentToBeAddedByFileAndByLine.containsKey(inputFile)) {
            commentToBeAddedByFileAndByLine.put(inputFile, new HashMap<Integer, StringBuilder>());
          }
          Map<Integer, StringBuilder> commentsByLine = commentToBeAddedByFileAndByLine.get(inputFile);
          if (!commentsByLine.containsKey(issueLine)) {
            commentsByLine.put(issueLine, new StringBuilder());
          }
          commentsByLine.get(issueLine).append(markDownUtils.inlineIssue(severity, message, ruleKey)).append("\n");
          reportedInline = true;
        }
      }

      report.process(issue, pullRequestFacade.getWebUrl(inputFile, issueLine), reportedInline);

    }
    return commentToBeAddedByFileAndByLine;
  }

  private void updateReviewComments(Map<InputFile, Map<Integer, StringBuilder>> commentsToBeAddedByLine) {
    for (Map.Entry<InputFile, Map<Integer, StringBuilder>> entry : commentsToBeAddedByLine.entrySet()) {
      for (Map.Entry<Integer, StringBuilder> entryPerLine : entry.getValue().entrySet()) {
        String body = entryPerLine.getValue().toString();
        pullRequestFacade.createOrUpdateReviewComment(entry.getKey(), entryPerLine.getKey(), body);
      }
    }
  }

}
