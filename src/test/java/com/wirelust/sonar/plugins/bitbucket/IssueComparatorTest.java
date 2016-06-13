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

import org.junit.Test;
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.postjob.issue.PostJobIssue;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.rule.RuleKey;

import static org.junit.Assert.assertEquals;

/**
 * Date: 13-Jun-2016
 *
 * @author T. Curran
 */
public class IssueComparatorTest {

  private static final String COMPONENT = "foo";

  PostJobIssue nonNullJob = new PostIssueJobImpl(COMPONENT, 100, Severity.INFO);

  IssueComparator issueComparator = new IssueComparator();

  @Test
  public void testLeftRightEqual() {
    assertEquals(0, issueComparator.compare(nonNullJob, nonNullJob));
  }

  @Test
  public void testLeftNull() {
    assertEquals(1, issueComparator.compare(null, nonNullJob));
  }

  @Test
  public void testRightNull() {
    assertEquals(-1, issueComparator.compare(nonNullJob, null));
  }

  @Test
  public void testSeverityCompare() {
    assertEquals(1, issueComparator.compare(
      new PostIssueJobImpl(COMPONENT, 100, Severity.INFO),
      new PostIssueJobImpl(COMPONENT, 100, Severity.BLOCKER)
    ));

    assertEquals(1, issueComparator.compare(
      new PostIssueJobImpl(COMPONENT, 100, Severity.MINOR),
      new PostIssueJobImpl(COMPONENT, 100, Severity.MAJOR)
    ));

    assertEquals(-1, issueComparator.compare(
      new PostIssueJobImpl(COMPONENT, 100, Severity.BLOCKER),
      new PostIssueJobImpl(COMPONENT, 100, Severity.INFO)
    ));

    assertEquals(-1, issueComparator.compare(
      new PostIssueJobImpl(COMPONENT, 100, Severity.BLOCKER),
      new PostIssueJobImpl(COMPONENT, 100, Severity.CRITICAL)
    ));
  }

  @Test
  public void testComponentKeyCompare() {
    assertEquals(4, issueComparator.compare(
      new PostIssueJobImpl("foo", 100, Severity.INFO),
      new PostIssueJobImpl("bar", 100, Severity.INFO)
    ));

    assertEquals(1, issueComparator.compare(
      new PostIssueJobImpl("right", 100, Severity.INFO),
      new PostIssueJobImpl("rhode", 100, Severity.INFO)
    ));
  }

  @Test
  public void testLineCompare() {
    assertEquals(-1, issueComparator.compare(
      new PostIssueJobImpl(COMPONENT, 100, Severity.INFO),
      new PostIssueJobImpl(COMPONENT, 125, Severity.INFO)
    ));

    assertEquals(1, issueComparator.compare(
      new PostIssueJobImpl(COMPONENT, 125, Severity.INFO),
      new PostIssueJobImpl(COMPONENT, 100, Severity.INFO)
    ));

    assertEquals(0, issueComparator.compare(
      new PostIssueJobImpl(COMPONENT, 100, Severity.INFO),
      new PostIssueJobImpl(COMPONENT, 100, Severity.INFO)
    ));

    assertEquals(-1, issueComparator.compare(
      new PostIssueJobImpl(COMPONENT, null, Severity.INFO),
      new PostIssueJobImpl(COMPONENT, 100, Severity.INFO)
    ));

    assertEquals(1, issueComparator.compare(
      new PostIssueJobImpl(COMPONENT, 100, Severity.INFO),
      new PostIssueJobImpl(COMPONENT, null, Severity.INFO)
    ));
  }

  private class PostIssueJobImpl implements PostJobIssue {

    String componentKey;
    Integer line;
    Severity severity;

    public PostIssueJobImpl(String componentKey,
                            @Nullable
                            Integer line,
                            Severity severity) {
      this.componentKey = componentKey;
      this.line = line;
      this.severity = severity;
    }

    @Override
    public String key() {
      return null;
    }

    @Override
    public RuleKey ruleKey() {
      return null;
    }

    @Override
    public String componentKey() {
      return componentKey;
    }

    @Override
    public InputComponent inputComponent() {
      return null;
    }

    @Override
    public Integer line() {
      return line;
    }

    @Override
    public String message() {
      return null;
    }

    @Override
    public Severity severity() {
      return severity;
    }

    @Override
    public boolean isNew() {
      return false;
    }
  }
}
