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

import javax.annotation.Nullable;
import org.sonar.api.batch.postjob.issue.PostJobIssue;
import org.sonar.api.batch.rule.Severity;

public class GlobalReport {
  private final MarkDownUtils markDownUtils;
  private int[] newIssuesBySeverity = new int[Severity.values().length];
  private StringBuilder notReportedOnDiff = new StringBuilder();
  private int notReportedIssueCount = 0;
  private int notReportedDisplayedIssueCount = 0;
  private BitBucketPluginConfiguration config;

  public GlobalReport(MarkDownUtils markDownUtils, BitBucketPluginConfiguration config) {
    this.markDownUtils = markDownUtils;
    this.config = config;
  }

  private void increment(Severity severity) {
    this.newIssuesBySeverity[severity.ordinal()]++;
  }

  public String formatForMarkdown() {
    StringBuilder sb = new StringBuilder();
    printNewIssuesMarkdown(sb);
    if (hasNewIssue()) {
      sb.append("\nWatch the comments in this conversation to review them.\n");
    }
    if (notReportedOnDiff.length() > 0) {
      sb.append("\nNote: The following issues were found on lines that were not modified in the pull request. "
             + "Because these issues can't be reported as line comments, they are summarized here:\n\n")
        .append(notReportedOnDiff.toString());

      if (notReportedIssueCount >= BitBucketPluginConfiguration.MAX_GLOBAL_ISSUES) {
        sb.append("* ... ")
          .append(notReportedIssueCount - BitBucketPluginConfiguration.MAX_GLOBAL_ISSUES)
          .append(" more\n");
      }
    }
    return sb.toString();
  }

  private int newIssues(Severity s) {
    return newIssuesBySeverity[s.ordinal()];
  }

  private void printNewIssuesMarkdown(StringBuilder sb) {
    sb.append("SonarQube analysis reported ");
    int newIssues = newIssues(Severity.BLOCKER) + newIssues(Severity.CRITICAL) + newIssues(Severity.MAJOR) + newIssues(Severity.MINOR) + newIssues(Severity.INFO);
    if (newIssues > 0) {
      sb.append(newIssues).append(" issue").append(newIssues > 1 ? "s" : "").append(":\n");
      printNewIssuesForMarkdown(sb, Severity.BLOCKER);
      printNewIssuesForMarkdown(sb, Severity.CRITICAL);
      printNewIssuesForMarkdown(sb, Severity.MAJOR);
      printNewIssuesForMarkdown(sb, Severity.MINOR);
      printNewIssuesForMarkdown(sb, Severity.INFO);
    } else {
      sb.append("no issues.");
    }
  }

  private void printNewIssuesForMarkdown(StringBuilder sb, Severity severity) {
    int issueCount = newIssues(severity);
    if (issueCount > 0) {
      sb.append("* ")
        .append(MarkDownUtils.getImageMarkdownForSeverity(severity))
        .append(" ")
        .append(issueCount)
        .append(" ").append(severity.name().toLowerCase(config.getLocale()))
        .append("\n");
    }
  }

  public void process(PostJobIssue issue, @Nullable String webUrl, boolean reportedOnDiff) {
    if (reportedOnDiff) {
      increment(issue.severity());
    } else if (config.reportNotInDiff()) {
      increment(issue.severity());
      notReportedIssueCount++;

      if (notReportedDisplayedIssueCount < BitBucketPluginConfiguration.MAX_GLOBAL_ISSUES) {
        notReportedOnDiff
          .append("* ")
          .append(markDownUtils.globalIssue(issue.severity(), issue.message(), issue.ruleKey().toString(), webUrl, issue.componentKey()))
          .append("\n");
        notReportedDisplayedIssueCount++;
      }
    }
  }

  public boolean hasNewIssue() {
    return newIssues(Severity.BLOCKER) + newIssues(Severity.CRITICAL) + newIssues(Severity.MAJOR) + newIssues(Severity.MINOR) + newIssues(Severity.INFO) > 0;
  }

  public boolean isApproved(String threshold) {

    boolean approved = true;
    boolean belowThreshold = true;
    for (Severity severity : Severity.values()) {
      if (severity.name().equalsIgnoreCase(threshold)) {
        belowThreshold = false;
      }
      if (!belowThreshold && newIssues(severity) > 0) {
        approved = false;
      }
    }
    return approved;
  }

}
