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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.CheckForNull;

import org.junit.Before;
import org.junit.Test;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.postjob.issue.PostJobIssue;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;
import org.sonar.api.issue.internal.DefaultIssue;
import org.sonar.api.rule.RuleKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GlobalReportTest {

  private static final String GITHUB_URL = "https://github.com/teacurran/sonar-bitbucket";

  private List<DefaultIssue> issues = new ArrayList<>();

  private Settings settings;
  BitBucketPluginConfiguration config;

  @Before
  public void setup() {
    settings = new Settings(new PropertyDefinitions(PropertyDefinition.builder(CoreProperties.SERVER_BASE_URL)
      .name("Server base URL")
      .description("HTTP URL of this SonarQube server, such as <i>http://yourhost.yourdomain/sonar</i>. This value is used i.e. to create links in emails.")
      .category(CoreProperties.CATEGORY_GENERAL)
      .defaultValue(CoreProperties.SERVER_BASE_URL_DEFAULT_VALUE)
      .build()));

    settings.setProperty("sonar.host.url", "http://myserver");

    config = mock(BitBucketPluginConfiguration.class);
    when(config.reportNotInDiff()).thenReturn(true);
    when(config.getLocale()).thenReturn(Locale.ENGLISH);
  }

  @Test
  public void shouldFormatIssuesForMarkdown() {

    GlobalReport globalReport = new GlobalReport(new MarkDownUtils(settings), config);
    globalReport.process(newMockedIssue("component", null, null, Severity.INFO, true, "Issue", "rule"), GITHUB_URL, true);
    globalReport.process(newMockedIssue("component", null, null, Severity.MINOR, true, "Issue", "rule"), GITHUB_URL, true);
    globalReport.process(newMockedIssue("component", null, null, Severity.MAJOR, true, "Issue", "rule"), GITHUB_URL, true);
    globalReport.process(newMockedIssue("component", null, null, Severity.CRITICAL, true, "Issue", "rule"), GITHUB_URL, true);
    globalReport.process(newMockedIssue("component", null, null, Severity.BLOCKER, true, "Issue", "rule"), GITHUB_URL, true);

    String desiredMarkdown = "SonarQube analysis reported 5 issues:\n" +
      "* ![BLOCKER](https://raw.githubusercontent.com/teacurran/sonar-bitbucket/master/images/severity-blocker.png) 1 blocker\n" +
      "* ![CRITICAL](https://raw.githubusercontent.com/teacurran/sonar-bitbucket/master/images/severity-critical.png) 1 critical\n" +
      "* ![MAJOR](https://raw.githubusercontent.com/teacurran/sonar-bitbucket/master/images/severity-major.png) 1 major\n" +
      "* ![MINOR](https://raw.githubusercontent.com/teacurran/sonar-bitbucket/master/images/severity-minor.png) 1 minor\n" +
      "* ![INFO](https://raw.githubusercontent.com/teacurran/sonar-bitbucket/master/images/severity-info.png) 1 info\n" +
      "\nWatch the comments in this conversation to review them.\n";

    String formattedGlobalReport = globalReport.formatForMarkdown();

    assertThat(formattedGlobalReport).isEqualTo(desiredMarkdown);
  }

  @Test
  public void shouldLimitGlobalIssues() {
    GlobalReport globalReport = new GlobalReport(new MarkDownUtils(settings), config);
    for (int i = 0; i < 17; i++) {
      globalReport.process(newMockedIssue("component", null, null, Severity.MAJOR, true, "Issue number:" + i, "rule" + i), GITHUB_URL, false);
    }

    String desiredMarkdown = "SonarQube analysis reported 17 issues:\n" +
      "* ![MAJOR](https://raw.githubusercontent.com/teacurran/sonar-bitbucket/master/images/severity-major.png) 17 major\n" +
      "\nWatch the comments in this conversation to review them.\n\n" +
      "Note: The following issues were found on lines that were not modified in the pull request. " +
      "Because these issues can't be reported as line comments, they are summarized here:\n\n" +
      "* ![MAJOR](https://raw.githubusercontent.com/teacurran/sonar-bitbucket/master/images/severity-major.png) [Issue number:0](https://github.com/teacurran/sonar-bitbucket) [![rule](https://raw.githubusercontent.com/teacurran/sonar-bitbucket/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule0)\n"
      +
      "* ![MAJOR](https://raw.githubusercontent.com/teacurran/sonar-bitbucket/master/images/severity-major.png) [Issue number:1](https://github.com/teacurran/sonar-bitbucket) [![rule](https://raw.githubusercontent.com/teacurran/sonar-bitbucket/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule1)\n"
      +
      "* ![MAJOR](https://raw.githubusercontent.com/teacurran/sonar-bitbucket/master/images/severity-major.png) [Issue number:2](https://github.com/teacurran/sonar-bitbucket) [![rule](https://raw.githubusercontent.com/teacurran/sonar-bitbucket/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule2)\n"
      +
      "* ![MAJOR](https://raw.githubusercontent.com/teacurran/sonar-bitbucket/master/images/severity-major.png) [Issue number:3](https://github.com/teacurran/sonar-bitbucket) [![rule](https://raw.githubusercontent.com/teacurran/sonar-bitbucket/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule3)\n"
      +
      "* ![MAJOR](https://raw.githubusercontent.com/teacurran/sonar-bitbucket/master/images/severity-major.png) [Issue number:4](https://github.com/teacurran/sonar-bitbucket) [![rule](https://raw.githubusercontent.com/teacurran/sonar-bitbucket/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule4)\n"
      +
      "* ![MAJOR](https://raw.githubusercontent.com/teacurran/sonar-bitbucket/master/images/severity-major.png) [Issue number:5](https://github.com/teacurran/sonar-bitbucket) [![rule](https://raw.githubusercontent.com/teacurran/sonar-bitbucket/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule5)\n"
      +
      "* ![MAJOR](https://raw.githubusercontent.com/teacurran/sonar-bitbucket/master/images/severity-major.png) [Issue number:6](https://github.com/teacurran/sonar-bitbucket) [![rule](https://raw.githubusercontent.com/teacurran/sonar-bitbucket/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule6)\n"
      +
      "* ![MAJOR](https://raw.githubusercontent.com/teacurran/sonar-bitbucket/master/images/severity-major.png) [Issue number:7](https://github.com/teacurran/sonar-bitbucket) [![rule](https://raw.githubusercontent.com/teacurran/sonar-bitbucket/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule7)\n"
      +
      "* ![MAJOR](https://raw.githubusercontent.com/teacurran/sonar-bitbucket/master/images/severity-major.png) [Issue number:8](https://github.com/teacurran/sonar-bitbucket) [![rule](https://raw.githubusercontent.com/teacurran/sonar-bitbucket/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule8)\n"
      +
      "* ![MAJOR](https://raw.githubusercontent.com/teacurran/sonar-bitbucket/master/images/severity-major.png) [Issue number:9](https://github.com/teacurran/sonar-bitbucket) [![rule](https://raw.githubusercontent.com/teacurran/sonar-bitbucket/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule9)\n"
      +
      "* ... 7 more\n";

    String formattedGlobalReport = globalReport.formatForMarkdown();

    assertThat(formattedGlobalReport).isEqualTo(desiredMarkdown);
  }

  private PostJobIssue newMockedIssue(String componentKey, @CheckForNull DefaultInputFile inputFile, @CheckForNull Integer line, Severity severity, boolean isNew, String message, String rule) {
    PostJobIssue issue = mock(PostJobIssue.class);
    when(issue.inputComponent()).thenReturn(inputFile);
    when(issue.componentKey()).thenReturn(componentKey);
    if (line != null) {
      when(issue.line()).thenReturn(line);
    }
    when(issue.ruleKey()).thenReturn(RuleKey.of("repo", rule));
    when(issue.severity()).thenReturn(severity);
    when(issue.isNew()).thenReturn(isNew);
    when(issue.message()).thenReturn(message);

    return issue;
  }

}
