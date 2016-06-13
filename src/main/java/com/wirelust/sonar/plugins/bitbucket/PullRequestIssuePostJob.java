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

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.postjob.PostJob;
import org.sonar.api.batch.postjob.PostJobContext;
import org.sonar.api.batch.postjob.PostJobDescriptor;
import org.sonar.api.batch.postjob.issue.PostJobIssue;
import org.sonar.api.measures.Metrics;

/**
 * Compute comments to be added on the pull request.
 */
public class PullRequestIssuePostJob implements PostJob {

  private static final Logger LOGGER = LoggerFactory.getLogger(PullRequestIssuePostJob.class);
  private static final Comparator<PostJobIssue> ISSUE_COMPARATOR = new IssueComparator();

  private final PullRequestFacade pullRequestFacade;
  private final Metrics metrics;
  private final BitBucketPluginConfiguration config;
  private final MarkDownUtils markDownUtils;

  public PullRequestIssuePostJob(BitBucketPluginConfiguration bitBucketPluginConfiguration,
                                 PullRequestFacade pullRequestFacade,
                                 Metrics metrics,
                                 MarkDownUtils markDownUtils) {
    this.config = bitBucketPluginConfiguration;
    this.pullRequestFacade = pullRequestFacade;
    this.metrics = metrics;
    this.markDownUtils = markDownUtils;
  }

  @Override
  public void describe(PostJobDescriptor descriptor) {
    descriptor.name(config.message("plugin.name"))
      .requireProperty(BitBucketPlugin.BITBUCKET_PULL_REQUEST);
  }

  @Override
  public void execute(PostJobContext context) {

    GlobalReport report = new GlobalReport(markDownUtils, config);
    Map<InputFile, Map<Integer, StringBuilder>> commentsToBeAddedByLine = processIssues(report, context.issues());

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

  private Map<InputFile, Map<Integer, StringBuilder>> processIssues(GlobalReport report, Iterable<PostJobIssue> issues) {
    LOGGER.debug("processing issues");
    Map<InputFile, Map<Integer, StringBuilder>> commentToBeAddedByFileAndByLine = new HashMap<>();

    StreamSupport.stream(issues.spliterator(), false)
      .filter(i -> i.isNew())
      // SONARGITUB-13 Ignore issues on files not modified by the P/R
      .filter(i -> {
        InputComponent inputComponent = i.inputComponent();
        return inputComponent == null ||
          !inputComponent.isFile() ||
          pullRequestFacade.hasFile((InputFile) inputComponent);
      })
      .sorted(ISSUE_COMPARATOR)
      .forEach(i -> processIssue(report, commentToBeAddedByFileAndByLine, i));
    return commentToBeAddedByFileAndByLine;

  }

  private boolean tryReportInline(Map<InputFile, Map<Integer, StringBuilder>> commentToBeAddedByFileAndByLine, PostJobIssue issue, InputFile inputFile) {
    Integer lineOrNull = issue.line();
    if (inputFile != null && lineOrNull != null) {
      int line = lineOrNull.intValue();
      if (pullRequestFacade.hasFileLine(inputFile, line)) {
        String message = issue.message();
        String ruleKey = issue.ruleKey().toString();
        if (!commentToBeAddedByFileAndByLine.containsKey(inputFile)) {
          commentToBeAddedByFileAndByLine.put(inputFile, new HashMap<Integer, StringBuilder>());
        }
        Map<Integer, StringBuilder> commentsByLine = commentToBeAddedByFileAndByLine.get(inputFile);
        if (!commentsByLine.containsKey(line)) {
          commentsByLine.put(line, new StringBuilder());
        }
        commentsByLine.get(line).append(markDownUtils.inlineIssue(issue.severity(), message, ruleKey)).append("\n");
        return true;
      }
    }
    return false;
  }

  private void processIssue(GlobalReport report,
                            Map<InputFile,Map<Integer, StringBuilder>>commentToBeAddedByFileAndByLine,
                            PostJobIssue issue) {
    boolean reportedInline = false;
    InputComponent inputComponent = issue.inputComponent();
    if (inputComponent != null
      && inputComponent.isFile()) {
      reportedInline = tryReportInline(commentToBeAddedByFileAndByLine, issue, (InputFile) inputComponent);
    }
    report.process(issue, pullRequestFacade.getWebUrl(inputComponent, issue.line()), reportedInline);
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
