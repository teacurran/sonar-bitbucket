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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.patch.Patch;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.kohsuke.github.GHCommitStatus;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterable;
import org.mockito.Mockito;
import org.sonar.api.batch.fs.InputPath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class PullRequestFacadeTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  @Ignore
  public void testGetGithubUrl() throws Exception {

    File gitBasedir = temp.newFolder();

    PullRequestFacade facade = new PullRequestFacade(mock(BitBucketPluginConfiguration.class));
    facade.setGitBaseDir(gitBasedir);
    GHRepository ghRepo = mock(GHRepository.class);
    when(ghRepo.getHtmlUrl()).thenReturn(new URL("https://github.com/SonarSource/sonar-java"));
    //facade.setGhRepo(ghRepo);
    GHPullRequest pr = mock(GHPullRequest.class, withSettings().defaultAnswer(RETURNS_DEEP_STUBS));
    when(pr.getHead().getSha()).thenReturn("abc123");
    //facade.setPullRequest(pr);
    InputPath inputPath = mock(InputPath.class);
    when(inputPath.file()).thenReturn(new File(gitBasedir, "src/main/Foo.java"));
    assertThat(facade.getGithubUrl(inputPath, 10)).isEqualTo("https://github.com/SonarSource/sonar-java/blob/abc123/src/main/Foo.java#L10");
  }

  @Test
  public void testEmptyGetCommitStatusForContext() throws IOException {
    PullRequestFacade facade = new PullRequestFacade(mock(BitBucketPluginConfiguration.class));
    GHRepository ghRepo = mock(GHRepository.class);
    PagedIterable<GHCommitStatus> ghCommitStatuses = Mockito.mock(PagedIterable.class);
    GHPullRequest pr = mock(GHPullRequest.class, withSettings().defaultAnswer(RETURNS_DEEP_STUBS));
    when(pr.getRepository()).thenReturn(ghRepo);
    when(pr.getHead().getSha()).thenReturn("abc123");
    when(ghRepo.listCommitStatuses(pr.getHead().getSha())).thenReturn(ghCommitStatuses);
  }

  @Test
  public void testGetCommitStatusForContextWithOneCorrectStatus() throws IOException {
    PullRequestFacade facade = new PullRequestFacade(mock(BitBucketPluginConfiguration.class));
    GHRepository ghRepo = mock(GHRepository.class);
    PagedIterable<GHCommitStatus> ghCommitStatuses = Mockito.mock(PagedIterable.class);
    List<GHCommitStatus> ghCommitStatusesList = new ArrayList<>();
    GHCommitStatus ghCommitStatusGHPRHContext = Mockito.mock(GHCommitStatus.class);
    ghCommitStatusesList.add(ghCommitStatusGHPRHContext);
    GHPullRequest pr = mock(GHPullRequest.class, withSettings().defaultAnswer(RETURNS_DEEP_STUBS));
    when(pr.getRepository()).thenReturn(ghRepo);
    when(pr.getHead().getSha()).thenReturn("abc123");
    when(ghRepo.listCommitStatuses(pr.getHead().getSha())).thenReturn(ghCommitStatuses);
    when(ghCommitStatuses.asList()).thenReturn(ghCommitStatusesList);
    when(ghCommitStatusGHPRHContext.getContext()).thenReturn(PullRequestFacade.COMMIT_CONTEXT);
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

    assertThat(patch.getFiles().size() == 7);
  }

}
