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

import javax.annotation.Nullable;
import org.kohsuke.github.GHCommitState;
import org.sonar.api.issue.Issue;
import org.sonar.api.rule.Severity;

public class GlobalReport {
  private final MarkDownUtils markDownUtils;
  private int[] newIssuesBySeverity = new int[Severity.ALL.size()];
  private StringBuilder notReportedOnDiff = new StringBuilder();
  private int notReportedIssueCount = 0;
  private int notReportedDisplayedIssueCount = 0;

  public GlobalReport(MarkDownUtils markDownUtils) {
    this.markDownUtils = markDownUtils;
  }

  private void increment(String severity) {
    this.newIssuesBySeverity[Severity.ALL.indexOf(severity)]++;
  }

  public String formatForMarkdown() {
    StringBuilder sb = new StringBuilder();
    printNewIssuesMarkdown(sb);
    if (hasNewIssue()) {
      sb.append("\nWatch the comments in this conversation to review them.");
    }
    if (notReportedOnDiff.length() > 0) {
      sb.append("\nNote: the following issues could not be reported as comments because they are located on lines that are not displayed in this pull request:\n")
        .append(notReportedOnDiff.toString());

      if (notReportedIssueCount >= BitBucketPluginConfiguration.MAX_GLOBAL_ISSUES) {
        sb.append("* ... ")
          .append(notReportedIssueCount - BitBucketPluginConfiguration.MAX_GLOBAL_ISSUES)
          .append(" more\n");
      }
    }
    return sb.toString();
  }

  public String getStatusDescription() {
    StringBuilder sb = new StringBuilder();
    printNewIssuesInline(sb);
    return sb.toString();
  }

  public GHCommitState getStatus() {
    return (newIssues(Severity.BLOCKER) > 0 || newIssues(Severity.CRITICAL) > 0) ? GHCommitState.ERROR : GHCommitState.SUCCESS;
  }

  private int newIssues(String s) {
    return newIssuesBySeverity[Severity.ALL.indexOf(s)];
  }

  private void printNewIssuesMarkdown(StringBuilder sb) {
    sb.append("SonarQube analysis reported ");
    int newIssues = newIssues(Severity.BLOCKER) + newIssues(Severity.CRITICAL) + newIssues(Severity.MAJOR) + newIssues(Severity.MINOR) + newIssues(Severity.INFO);
    if (newIssues > 0) {
      sb.append(newIssues).append(" issue" + (newIssues > 1 ? "s" : "")).append(":\n");
      printNewIssuesForMarkdown(sb, Severity.BLOCKER);
      printNewIssuesForMarkdown(sb, Severity.CRITICAL);
      printNewIssuesForMarkdown(sb, Severity.MAJOR);
      printNewIssuesForMarkdown(sb, Severity.MINOR);
      printNewIssuesForMarkdown(sb, Severity.INFO);
    } else {
      sb.append("no issues.");
    }
  }

  private void printNewIssuesInline(StringBuilder sb) {
    sb.append("SonarQube reported ");
    int newIssues = newIssues(Severity.BLOCKER) + newIssues(Severity.CRITICAL) + newIssues(Severity.MAJOR) + newIssues(Severity.MINOR) + newIssues(Severity.INFO);
    if (newIssues > 0) {
      sb.append(newIssues).append(" issue" + (newIssues > 1 ? "s" : "")).append(",");
      int newCriticalOrBlockerIssues = newIssues(Severity.BLOCKER) + newIssues(Severity.CRITICAL);
      if (newCriticalOrBlockerIssues > 0) {
        printNewIssuesInline(sb, Severity.CRITICAL);
        printNewIssuesInline(sb, Severity.BLOCKER);
      } else {
        sb.append(" no critical nor blocker");
      }
    } else {
      sb.append("no issues");
    }
  }

  private void printNewIssuesInline(StringBuilder sb, String severity) {
    int issueCount = newIssues(severity);
    if (issueCount > 0) {
      if (sb.charAt(sb.length() - 1) == ',') {
        sb.append(" with ");
      } else {
        sb.append(" and ");
      }
      sb.append(issueCount).append(" ").append(severity.toLowerCase());
    }
  }

  private void printNewIssuesForMarkdown(StringBuilder sb, String severity) {
    int issueCount = newIssues(severity);
    if (issueCount > 0) {
      sb.append("* ").append(MarkDownUtils.getImageMarkdownForSeverity(severity)).append(" ").append(issueCount).append(" ").append(severity.toLowerCase()).append("\n");
    }
  }

  public void process(Issue issue, @Nullable String githubUrl, boolean reportedOnDiff) {
    increment(issue.severity());
    if (!reportedOnDiff) {
      notReportedIssueCount++;

      if (notReportedDisplayedIssueCount < BitBucketPluginConfiguration.MAX_GLOBAL_ISSUES) {
        notReportedOnDiff
          .append("* ")
          .append(markDownUtils.globalIssue(issue.severity(), issue.message(), issue.ruleKey().toString(), githubUrl, issue.componentKey()))
          .append("\n");
        notReportedDisplayedIssueCount++;
      }
    }
  }

  public boolean hasNewIssue() {
    return newIssues(Severity.BLOCKER) + newIssues(Severity.CRITICAL) + newIssues(Severity.MAJOR) + newIssues(Severity.MINOR) + newIssues(Severity.INFO) > 0;
  }
}
