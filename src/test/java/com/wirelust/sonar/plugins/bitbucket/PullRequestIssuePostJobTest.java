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
import java.util.Locale;
import javax.annotation.CheckForNull;

import org.junit.Before;
import org.junit.Test;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.postjob.PostJobContext;
import org.sonar.api.batch.postjob.issue.PostJobIssue;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;
import org.sonar.api.rule.RuleKey;

import static org.mockito.AdditionalMatchers.not;
import static org.mockito.Matchers.contains;
import static org.mockito.Mockito.*;

public class PullRequestIssuePostJobTest {

  private PullRequestIssuePostJob pullRequestIssuePostJob;
  private PullRequestFacade pullRequestFacade;
  private PostJobContext context;

  @Before
  public void prepare() throws Exception {
    pullRequestFacade = mock(PullRequestFacade.class);
    BitBucketPluginConfiguration config = mock(BitBucketPluginConfiguration.class);
    Settings settings = new Settings(new PropertyDefinitions(PropertyDefinition.builder(CoreProperties.SERVER_BASE_URL)
      .name("Server base URL")
      .description("HTTP URL of this SonarQube server, such as <i>http://yourhost.yourdomain/sonar</i>. This value is used i.e. to create links in emails.")
      .category(CoreProperties.CATEGORY_GENERAL)
      .defaultValue(CoreProperties.SERVER_BASE_URL_DEFAULT_VALUE)
      .build()));

    context = mock(PostJobContext.class);

    settings.setProperty("sonar.host.url", "http://192.168.0.1");
    settings.setProperty(CoreProperties.SERVER_BASE_URL, "http://myserver");
    when(config.issueThreshold()).thenReturn(Severity.CRITICAL.name());
    when(config.reportNotInDiff()).thenReturn(true);
    when(config.getLocale()).thenReturn(Locale.ENGLISH);

    pullRequestIssuePostJob = new PullRequestIssuePostJob(config, pullRequestFacade, null, new MarkDownUtils(settings));
  }

  @Test
  public void testPullRequestAnalysisWithNewIssues() {
    DefaultInputFile inputFile1 = new DefaultInputFile("foo", "src/Foo.php");
    PostJobIssue newIssue = newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, true, "msg1");
    when(pullRequestFacade.getWebUrl(inputFile1, 1)).thenReturn("http://github/blob/abc123/src/Foo.php#L1");

    PostJobIssue lineNotVisible = newMockedIssue("foo:src/Foo.php", inputFile1, 2, Severity.BLOCKER, true, "msg2");
    when(pullRequestFacade.getWebUrl(inputFile1, 2)).thenReturn("http://github/blob/abc123/src/Foo.php#L2");

    DefaultInputFile inputFile2 = new DefaultInputFile("foo", "src/Foo2.php");
    PostJobIssue fileNotInPR = newMockedIssue("foo:src/Foo2.php", inputFile2, 1, Severity.BLOCKER, true, "msg3");

    PostJobIssue notNewIssue = newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, false, "msg");

    PostJobIssue issueOnDir = newMockedIssue("foo:src", Severity.BLOCKER, true, "msg4");

    PostJobIssue issueOnProject = newMockedIssue("foo", Severity.BLOCKER, true, "msg");

    PostJobIssue globalIssue = newMockedIssue("foo:src/Foo.php", inputFile1, null, Severity.BLOCKER, true, "msg5");

    when(context.issues()).thenReturn(Arrays.<PostJobIssue>asList(newIssue, globalIssue, issueOnProject, issueOnDir, fileNotInPR, lineNotVisible, notNewIssue));
    when(pullRequestFacade.hasFile(inputFile1)).thenReturn(true);
    when(pullRequestFacade.hasFileLine(inputFile1, 1)).thenReturn(true);

    pullRequestIssuePostJob.execute(context);
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
    DefaultInputFile inputFile1 = new DefaultInputFile("foo", "src/Foo.php");
    PostJobIssue newIssue = newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.CRITICAL, true, "msg1");
    when(pullRequestFacade.getWebUrl(inputFile1, 1)).thenReturn("http://github/blob/abc123/src/Foo.php#L1");

    when(context.issues()).thenReturn(Arrays.<PostJobIssue>asList(newIssue));
    when(pullRequestFacade.hasFile(inputFile1)).thenReturn(true);
    when(pullRequestFacade.hasFileLine(inputFile1, 1)).thenReturn(true);

    pullRequestIssuePostJob.execute(context);

    verify(pullRequestFacade).unapprovePullRequest();
  }

  @Test
  public void testPullRequestAnalysisWithNewIssuesNoBlockerNorCritical() {
    DefaultInputFile inputFile1 = new DefaultInputFile("foo", "src/Foo.php");
    PostJobIssue newIssue = newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.MAJOR, true, "msg1");
    when(pullRequestFacade.getWebUrl(inputFile1, 1)).thenReturn("http://github/blob/abc123/src/Foo.php#L1");

    when(context.issues()).thenReturn(Arrays.<PostJobIssue>asList(newIssue));
    when(pullRequestFacade.hasFile(inputFile1)).thenReturn(true);
    when(pullRequestFacade.hasFileLine(inputFile1, 1)).thenReturn(true);

    pullRequestIssuePostJob.execute(context);

    verify(pullRequestFacade).approvePullRequest();
  }

  @Test
  public void testPullRequestAnalysisWithNewBlockerAndCriticalIssues() {
    DefaultInputFile inputFile1 = new DefaultInputFile("foo", "src/Foo.php");
    PostJobIssue newIssue = newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.CRITICAL, true, "msg1");
    when(pullRequestFacade.getWebUrl(inputFile1, 1)).thenReturn("http://github/blob/abc123/src/Foo.php#L1");

    PostJobIssue lineNotVisible = newMockedIssue("foo:src/Foo.php", inputFile1, 2, Severity.BLOCKER, true, "msg2");
    when(pullRequestFacade.getWebUrl(inputFile1, 2)).thenReturn("http://github/blob/abc123/src/Foo.php#L2");

    when(context.issues()).thenReturn(Arrays.<PostJobIssue>asList(newIssue, lineNotVisible));
    when(pullRequestFacade.hasFile(inputFile1)).thenReturn(true);
    when(pullRequestFacade.hasFileLine(inputFile1, 1)).thenReturn(true);

    pullRequestIssuePostJob.execute(context);

    verify(pullRequestFacade).unapprovePullRequest();
  }

  private PostJobIssue newMockedIssue(String componentKey, @CheckForNull DefaultInputFile inputFile, @CheckForNull Integer line, Severity severity,
                                      boolean isNew, String message) {
    PostJobIssue issue = mock(PostJobIssue.class);
    when(issue.inputComponent()).thenReturn(inputFile);
    when(issue.componentKey()).thenReturn(componentKey);
    if (line != null) {
      when(issue.line()).thenReturn(line);
    }
    when(issue.ruleKey()).thenReturn(RuleKey.of("repo", "rule"));
    when(issue.severity()).thenReturn(severity);
    when(issue.isNew()).thenReturn(isNew);
    when(issue.message()).thenReturn(message);

    return issue;
  }

  private PostJobIssue newMockedIssue(String componentKey, Severity severity, boolean isNew, String message) {
      return newMockedIssue(componentKey, null, null, severity, isNew, message);
  }

}
