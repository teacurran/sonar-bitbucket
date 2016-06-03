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

import javax.ws.rs.core.Response;

import com.wirelust.bitbucket.client.BitbucketAuthClient;
import com.wirelust.bitbucket.client.BitbucketV2Client;
import com.wirelust.bitbucket.client.representations.Branch;
import com.wirelust.bitbucket.client.representations.BuildStatus;
import com.wirelust.bitbucket.client.representations.CommentList;
import com.wirelust.bitbucket.client.representations.Commit;
import com.wirelust.bitbucket.client.representations.Link;
import com.wirelust.bitbucket.client.representations.PullRequest;
import com.wirelust.bitbucket.client.representations.User;
import com.wirelust.bitbucket.client.representations.auth.OauthAccessToken;
import com.wirelust.sonar.plugins.bitbucket.client.ApiClientFactory;
import com.wirelust.sonar.plugins.bitbucket.jackson.JacksonObjectMapper;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.patch.Patch;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.sonar.api.batch.fs.InputPath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class PullRequestFacadeTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Mock
  BitbucketAuthClient bitbucketAuthClient;

  @Mock
  BitbucketV2Client bitbucketV2Client;

  @Mock
  ApiClientFactory apiClientFactory;

  @Mock
  BitBucketPluginConfiguration configuration;

  @Test
  public void testInit() throws Exception {
    when(apiClientFactory.getV2Client(any(String.class))).thenReturn(bitbucketV2Client);
    when(apiClientFactory.getAuthClient()).thenReturn(bitbucketAuthClient);

    // create a git file in the temporary folder because init will look for one
    temp.newFile(".git");

    when(configuration.login()).thenReturn("login");
    when(configuration.password()).thenReturn("password");
    when(configuration.repository()).thenReturn("test");

    OauthAccessToken accessToken = new OauthAccessToken();

    Response authResponse = mock(Response.class);
    when(authResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
    when(authResponse.readEntity(OauthAccessToken.class)).thenReturn(accessToken);
    when(bitbucketAuthClient.getTokenByUsernamePassword(any(String.class), any(String.class), any(String.class)))
      .thenReturn(authResponse);

    User user = new User();
    Response getUserResponse = mock(Response.class);
    when(getUserResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
    when(getUserResponse.readEntity(User.class)).thenReturn(user);
    when(bitbucketV2Client.getUser()).thenReturn(getUserResponse);

    InputStream pullRequestStream = getClass().getClassLoader().getResourceAsStream("mocks/pull_request_1.json");
    JacksonObjectMapper objectMapper = JacksonObjectMapper.get();
    PullRequest pullRequest = objectMapper.readValue(pullRequestStream, PullRequest.class);

    Response pullRequestResponse = mock(Response.class);
    when(pullRequestResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
    when(pullRequestResponse.readEntity(PullRequest.class)).thenReturn(pullRequest);
    when(bitbucketV2Client.getPullRequestById(any(String.class), any(String.class), any(Long.class)))
      .thenReturn(pullRequestResponse);

    Commit commit = pullRequest.getDestination().getCommit();
    Response commitResponse = mock(Response.class);
    when(commitResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
    when(commitResponse.readEntity(Commit.class)).thenReturn(commit);
    when(bitbucketV2Client.getCommitByOwnerRepoRevision(any(String.class), any(String.class), any(String.class)))
      .thenReturn(commitResponse);

    Response statusResponse = mock(Response.class);
    when(statusResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
    when(bitbucketV2Client.postBuildStatus(any(String.class), any(String.class), any(String.class),
      any(BuildStatus.class))).thenReturn(statusResponse);

    CommentList commentList = new CommentList();
    Response commentsResponse = mock(Response.class);
    when(commentsResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
    when(commentsResponse.readEntity(CommentList.class)).thenReturn(commentList);

    when(bitbucketV2Client.getPullRequestCommentsWithPage(any(String.class), any(String.class), any(Long.class),
      any(Integer.class))).thenReturn(commentsResponse);

    Response diffResponse = mock(Response.class);
    when(diffResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
    when(diffResponse.readEntity(String.class)).thenReturn("");
    when(bitbucketV2Client.getPullRequestDiff(any(String.class), any(String.class), any(Long.class)))
      .thenReturn(diffResponse);

    PullRequestFacade pullRequestFacade = new PullRequestFacade(configuration, apiClientFactory);
    pullRequestFacade.init(123, temp.getRoot());
  }


  @Test
  public void testGetWebUrl() throws Exception {

    InputStream pullRequestStream = getClass().getClassLoader().getResourceAsStream("mocks/pull_request_1.json");

    JacksonObjectMapper objectMapper = JacksonObjectMapper.get();
    PullRequest pullRequest = objectMapper.readValue(pullRequestStream, PullRequest.class);

    File gitBasedir = temp.newFolder();
    PullRequestFacade facade = new PullRequestFacade(mock(BitBucketPluginConfiguration.class),
      mock(ApiClientFactory.class));
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
