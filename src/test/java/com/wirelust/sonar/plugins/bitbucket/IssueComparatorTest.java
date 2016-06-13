package com.wirelust.sonar.plugins.bitbucket;

import junit.framework.Assert;
import org.junit.Test;
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.postjob.issue.PostJobIssue;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.rule.RuleKey;

/**
 * Date: 13-Jun-2016
 *
 * @author T. Curran
 */
public class IssueComparatorTest {

  PostJobIssue nonNullJob = new PostJobIssue() {
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
      return null;
    }

    @Override
    public InputComponent inputComponent() {
      return null;
    }

    @Override
    public Integer line() {
      return null;
    }

    @Override
    public String message() {
      return null;
    }

    @Override
    public Severity severity() {
      return null;
    }

    @Override
    public boolean isNew() {
      return false;
    }
  };

  IssueComparator issueComparator = new IssueComparator();

  @Test
  public void testLeftRightEqual() {
    Assert.assertEquals(0, issueComparator.compare(nonNullJob, nonNullJob));
  }

  @Test
  public void testLeftNull() {
    Assert.assertEquals(1, issueComparator.compare(null, nonNullJob));
  }
}
