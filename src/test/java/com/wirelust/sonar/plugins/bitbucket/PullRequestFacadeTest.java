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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.wirelust.bitbucket.client.representations.Branch;
import com.wirelust.bitbucket.client.representations.Link;
import com.wirelust.bitbucket.client.representations.PullRequest;
import com.wirelust.sonar.plugins.bitbucket.jackson.JacksonObjectMapper;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.patch.Patch;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.fs.InputPath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class PullRequestFacadeTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void testGetWebUrl() throws Exception {

    InputStream pullRequestStream = getClass().getClassLoader().getResourceAsStream("mocks/pull_request_1.json");

    JacksonObjectMapper objectMapper = JacksonObjectMapper.get();
    PullRequest pullRequest = objectMapper.readValue(pullRequestStream, PullRequest.class);

    File gitBasedir = temp.newFolder();
    PullRequestFacade facade = new PullRequestFacade(mock(BitBucketPluginConfiguration.class));
    facade.setGitBaseDir(gitBasedir);
    facade.setPullRequest(pullRequest);

    //facade.setPullRequest(pr);
    InputPath inputPath = mock(InputPath.class);
    when(inputPath.file()).thenReturn(new File(gitBasedir, "src/main/Foo.java"));

    assertThat(facade.getWebUrl(inputPath, 10))
      .isEqualTo("https://staging.bitbucket.org/api/2.0/repositories/bitbucket/bitbucket/pullrequests/3767/dev/diff/#chg-src/main/Foo.javaT10");
  }

  @Test
  public void testLoadingUnifiedDiff() throws IOException {

    InputStream diffStream = getClass().getClassLoader().getResourceAsStream("unified_diff.txt");

    Patch patch = new Patch();
    patch.parse(diffStream);

    for (FileHeader fileHeader : patch.getFiles()) {
      for (HunkHeader hunk : fileHeader.getHunks()) {
        hunk.toEditList();
      }
    }

    assertThat(patch.getFiles().size() == 7).isTrue();
  }

}
