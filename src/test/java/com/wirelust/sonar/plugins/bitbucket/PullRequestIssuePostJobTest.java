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

import java.util.Arrays;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.measure.Metric;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;
import org.sonar.api.issue.Issue;
import org.sonar.api.issue.ProjectIssues;
import org.sonar.api.measures.Metrics;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rule.Severity;

import static org.mockito.AdditionalMatchers.not;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PullRequestIssuePostJobTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private PullRequestIssuePostJob pullRequestIssuePostJob;
  private PullRequestFacade pullRequestFacade;
  private ProjectIssues issues;
  private InputFileCache cache;

  @Before
  public void prepare() throws Exception {
    pullRequestFacade = mock(PullRequestFacade.class);
    BitBucketPluginConfiguration config = mock(BitBucketPluginConfiguration.class);
    issues = mock(ProjectIssues.class);
    cache = mock(InputFileCache.class);
    Settings settings = new Settings(new PropertyDefinitions(PropertyDefinition.builder(CoreProperties.SERVER_BASE_URL)
      .name("Server base URL")
      .description("HTTP URL of this SonarQube server, such as <i>http://yourhost.yourdomain/sonar</i>. This value is used i.e. to create links in emails.")
      .category(CoreProperties.CATEGORY_GENERAL)
      .defaultValue(CoreProperties.SERVER_BASE_URL_DEFAULT_VALUE)
      .build()));

    settings.setProperty("sonar.host.url", "http://192.168.0.1");
    settings.setProperty(CoreProperties.SERVER_BASE_URL, "http://myserver");
    when(config.issueThreshold()).thenReturn(Severity.CRITICAL);

    when(config.reportNotInDiff()).thenReturn(true);


    pullRequestIssuePostJob = new PullRequestIssuePostJob(config, pullRequestFacade, issues, null,
      cache, new MarkDownUtils(settings));
  }

  @Test
  public void testPullRequestAnalysisWithNewIssues() {
    Issue newIssue = mock(Issue.class);
    DefaultInputFile inputFile1 = new DefaultInputFile("foo", "src/Foo.php");
    when(cache.byKey("foo:src/Foo.php")).thenReturn(inputFile1);
    when(newIssue.componentKey()).thenReturn("foo:src/Foo.php");
    when(newIssue.line()).thenReturn(1);
    when(newIssue.ruleKey()).thenReturn(RuleKey.of("repo", "rule"));
    when(newIssue.severity()).thenReturn(Severity.BLOCKER);
    when(newIssue.isNew()).thenReturn(true);
    when(newIssue.message()).thenReturn("msg1");
    when(pullRequestFacade.getWebUrl(inputFile1, 1)).thenReturn("http://github/blob/abc123/src/Foo.php#L1");

    Issue lineNotVisible = mock(Issue.class);
    when(cache.byKey("foo:src/Foo.php")).thenReturn(inputFile1);
    when(lineNotVisible.componentKey()).thenReturn("foo:src/Foo.php");
    when(lineNotVisible.line()).thenReturn(2);
    when(lineNotVisible.ruleKey()).thenReturn(RuleKey.of("repo", "rule"));
    when(lineNotVisible.severity()).thenReturn(Severity.BLOCKER);
    when(lineNotVisible.isNew()).thenReturn(true);
    when(lineNotVisible.message()).thenReturn("msg2");
    when(pullRequestFacade.getWebUrl(inputFile1, 2)).thenReturn("http://github/blob/abc123/src/Foo.php#L2");

    Issue fileNotInPR = mock(Issue.class);
    DefaultInputFile inputFile2 = new DefaultInputFile("foo", "src/Foo2.php");
    when(cache.byKey("foo:src/Foo2.php")).thenReturn(inputFile2);
    when(fileNotInPR.componentKey()).thenReturn("foo:src/Foo2.php");
    when(fileNotInPR.line()).thenReturn(1);
    when(fileNotInPR.ruleKey()).thenReturn(RuleKey.of("repo", "rule"));
    when(fileNotInPR.severity()).thenReturn(Severity.BLOCKER);
    when(fileNotInPR.isNew()).thenReturn(true);
    when(fileNotInPR.message()).thenReturn("msg3");

    Issue notNewIssue = mock(Issue.class);
    when(cache.byKey("foo:src/Foo.php")).thenReturn(inputFile1);
    when(notNewIssue.componentKey()).thenReturn("foo:src/Foo.php");
    when(notNewIssue.line()).thenReturn(1);
    when(notNewIssue.ruleKey()).thenReturn(RuleKey.of("repo", "rule"));
    when(notNewIssue.severity()).thenReturn(Severity.BLOCKER);
    when(notNewIssue.isNew()).thenReturn(false);
    when(notNewIssue.message()).thenReturn("msg");

    Issue issueOnDir = mock(Issue.class);
    when(cache.byKey("foo:src")).thenReturn(null);
    when(issueOnDir.componentKey()).thenReturn("foo:src");
    when(issueOnDir.ruleKey()).thenReturn(RuleKey.of("repo", "rule"));
    when(issueOnDir.severity()).thenReturn(Severity.BLOCKER);
    when(issueOnDir.isNew()).thenReturn(true);
    when(issueOnDir.message()).thenReturn("msg4");

    Issue issueOnProject = mock(Issue.class);
    when(issueOnProject.ruleKey()).thenReturn(RuleKey.of("repo", "rule"));
    when(issueOnProject.componentKey()).thenReturn("foo");
    when(issueOnProject.severity()).thenReturn(Severity.BLOCKER);
    when(issueOnProject.isNew()).thenReturn(true);
    when(issueOnProject.message()).thenReturn("msg");

    Issue globalIssue = mock(Issue.class);
    when(cache.byKey("foo:src/Foo.php")).thenReturn(inputFile1);
    when(globalIssue.componentKey()).thenReturn("foo:src/Foo.php");
    when(globalIssue.line()).thenReturn(null);
    when(globalIssue.ruleKey()).thenReturn(RuleKey.of("repo", "rule"));
    when(globalIssue.severity()).thenReturn(Severity.BLOCKER);
    when(globalIssue.isNew()).thenReturn(true);
    when(globalIssue.message()).thenReturn("msg5");

    when(issues.issues()).thenReturn(Arrays.<Issue>asList(newIssue, globalIssue, issueOnProject, issueOnDir, fileNotInPR, lineNotVisible, notNewIssue));
    when(pullRequestFacade.hasFile(inputFile1)).thenReturn(true);
    when(pullRequestFacade.hasFileLine(inputFile1, 1)).thenReturn(true);

    pullRequestIssuePostJob.executeOn(null, null);
    verify(pullRequestFacade).addGlobalComment(contains("SonarQube analysis reported 5 issues:"));
    verify(pullRequestFacade)
      .addGlobalComment(contains("* ![BLOCKER](https://raw.githubusercontent.com/teacurran/sonar-bitbucket/master/images/severity-blocker.png) 5 blocker"));
    verify(pullRequestFacade)
      .addGlobalComment(
        not(contains("* [msg]")));
    verify(pullRequestFacade)
      .addGlobalComment(
        contains(
          "* ![BLOCKER](https://raw.githubusercontent.com/teacurran/sonar-bitbucket/master/images/severity-blocker.png) [msg2](http://github/blob/abc123/src/Foo.php#L2) [![rule](https://raw.githubusercontent.com/teacurran/sonar-bitbucket/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule)"));

    verify(pullRequestFacade).unapprovePullRequest();
  }

  @Test
  public void testPullRequestAnalysisWithNewCriticalIssues() {
    Issue newIssue = mock(Issue.class);
    DefaultInputFile inputFile1 = new DefaultInputFile("foo", "src/Foo.php");
    when(cache.byKey("foo:src/Foo.php")).thenReturn(inputFile1);
    when(newIssue.componentKey()).thenReturn("foo:src/Foo.php");
    when(newIssue.line()).thenReturn(1);
    when(newIssue.ruleKey()).thenReturn(RuleKey.of("repo", "rule"));
    when(newIssue.severity()).thenReturn(Severity.CRITICAL);
    when(newIssue.isNew()).thenReturn(true);
    when(newIssue.message()).thenReturn("msg1");
    when(pullRequestFacade.getWebUrl(inputFile1, 1)).thenReturn("http://github/blob/abc123/src/Foo.php#L1");

    when(issues.issues()).thenReturn(Arrays.<Issue>asList(newIssue));
    when(pullRequestFacade.hasFile(inputFile1)).thenReturn(true);
    when(pullRequestFacade.hasFileLine(inputFile1, 1)).thenReturn(true);

    pullRequestIssuePostJob.executeOn(null, null);

    verify(pullRequestFacade).unapprovePullRequest();
  }

  @Test
  public void testPullRequestAnalysisWithNewIssuesNoBlockerNorCritical() {
    Issue newIssue = mock(Issue.class);
    DefaultInputFile inputFile1 = new DefaultInputFile("foo", "src/Foo.php");
    when(cache.byKey("foo:src/Foo.php")).thenReturn(inputFile1);
    when(newIssue.componentKey()).thenReturn("foo:src/Foo.php");
    when(newIssue.line()).thenReturn(1);
    when(newIssue.ruleKey()).thenReturn(RuleKey.of("repo", "rule"));
    when(newIssue.severity()).thenReturn(Severity.MAJOR);
    when(newIssue.isNew()).thenReturn(true);
    when(newIssue.message()).thenReturn("msg1");
    when(pullRequestFacade.getWebUrl(inputFile1, 1)).thenReturn("http://github/blob/abc123/src/Foo.php#L1");

    when(issues.issues()).thenReturn(Arrays.<Issue>asList(newIssue));
    when(pullRequestFacade.hasFile(inputFile1)).thenReturn(true);
    when(pullRequestFacade.hasFileLine(inputFile1, 1)).thenReturn(true);

    pullRequestIssuePostJob.executeOn(null, null);

    verify(pullRequestFacade).approvePullRequest();
  }

  @Test
  public void testPullRequestAnalysisWithNewBlockerAndCriticalIssues() {
    Issue newIssue = mock(Issue.class);
    DefaultInputFile inputFile1 = new DefaultInputFile("foo", "src/Foo.php");
    when(cache.byKey("foo:src/Foo.php")).thenReturn(inputFile1);
    when(newIssue.componentKey()).thenReturn("foo:src/Foo.php");
    when(newIssue.line()).thenReturn(1);
    when(newIssue.ruleKey()).thenReturn(RuleKey.of("repo", "rule"));
    when(newIssue.severity()).thenReturn(Severity.CRITICAL);
    when(newIssue.isNew()).thenReturn(true);
    when(newIssue.message()).thenReturn("msg1");
    when(pullRequestFacade.getWebUrl(inputFile1, 1)).thenReturn("http://github/blob/abc123/src/Foo.php#L1");

    Issue lineNotVisible = mock(Issue.class);
    when(cache.byKey("foo:src/Foo.php")).thenReturn(inputFile1);
    when(lineNotVisible.componentKey()).thenReturn("foo:src/Foo.php");
    when(lineNotVisible.line()).thenReturn(2);
    when(lineNotVisible.ruleKey()).thenReturn(RuleKey.of("repo", "rule"));
    when(lineNotVisible.severity()).thenReturn(Severity.BLOCKER);
    when(lineNotVisible.isNew()).thenReturn(true);
    when(lineNotVisible.message()).thenReturn("msg2");
    when(pullRequestFacade.getWebUrl(inputFile1, 2)).thenReturn("http://github/blob/abc123/src/Foo.php#L2");

    when(issues.issues()).thenReturn(Arrays.<Issue>asList(newIssue, lineNotVisible));
    when(pullRequestFacade.hasFile(inputFile1)).thenReturn(true);
    when(pullRequestFacade.hasFileLine(inputFile1, 1)).thenReturn(true);

    pullRequestIssuePostJob.executeOn(null, null);

    verify(pullRequestFacade).unapprovePullRequest();
  }
}
