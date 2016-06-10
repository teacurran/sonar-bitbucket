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
import java.io.StringWriter;

import javax.ws.rs.core.Response;

import com.wirelust.bitbucket.client.BitbucketAuthClient;
import com.wirelust.bitbucket.client.BitbucketV2Client;
import com.wirelust.bitbucket.client.representations.BuildStatus;
import com.wirelust.bitbucket.client.representations.CommentList;
import com.wirelust.bitbucket.client.representations.Commit;
import com.wirelust.bitbucket.client.representations.PullRequest;
import com.wirelust.bitbucket.client.representations.User;
import com.wirelust.bitbucket.client.representations.auth.OauthAccessToken;
import com.wirelust.sonar.plugins.bitbucket.client.ApiClientFactory;
import com.wirelust.sonar.plugins.bitbucket.jackson.JacksonObjectMapper;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.patch.Patch;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.sonar.api.batch.fs.InputPath;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.config.Settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class PullRequestFacadeTest {
  public static final String MOCK_USERNAME = "evzijst";

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Rule
  public TemporaryFolder nonGitFolder = new TemporaryFolder();

  @Mock
  BitbucketAuthClient bitbucketAuthClient;

  @Mock
  BitbucketV2Client bitbucketV2Client;

  @Mock
  ApiClientFactory apiClientFactory;

  Settings settings;
  BitBucketPluginConfiguration configuration;

  @Mock
  Response authResponse;

  @Mock
  Response getUserResponse;

  @Mock
  Response pullRequestResponse;

  @Mock
  Response commitResponse;

  @Mock
  Response statusResponse;

  @Mock
  Response commentsResponse;

  @Mock
  Response diffResponse;

  @Spy
  PullRequest pullRequest;

  @Before
  public void init() throws Exception {
    when(apiClientFactory.getV2Client(any(String.class))).thenReturn(bitbucketV2Client);
    when(apiClientFactory.getAuthClient()).thenReturn(bitbucketAuthClient);

    // create a git file in the temporary folder because init will look for one
    temporaryFolder.newFile(".git");

    settings = new Settings();
    configuration = new BitBucketPluginConfiguration(settings);

    // getAccessToken Response
    OauthAccessToken accessToken = new OauthAccessToken();
    when(authResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
    when(authResponse.readEntity(OauthAccessToken.class)).thenReturn(accessToken);
    when(bitbucketAuthClient.getTokenByUsernamePassword(any(String.class), any(String.class), any(String.class)))
      .thenReturn(authResponse);

    // getUser Response
    User user = new User();
    user.setUsername(MOCK_USERNAME);
    when(getUserResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
    when(getUserResponse.readEntity(User.class)).thenReturn(user);
    when(bitbucketV2Client.getUser()).thenReturn(getUserResponse);

    // load a pull request from json
    InputStream pullRequestStream = getClass().getClassLoader().getResourceAsStream("mocks/pull_request_1.json");
    JacksonObjectMapper objectMapper = JacksonObjectMapper.get();
    pullRequest = objectMapper.readValue(pullRequestStream, PullRequest.class);

    // Pull Request Response
    when(pullRequestResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
    when(pullRequestResponse.readEntity(PullRequest.class)).thenReturn(pullRequest);
    when(bitbucketV2Client.getPullRequestById(any(String.class), any(String.class), any(Long.class)))
      .thenReturn(pullRequestResponse);

    // get Commit
    Commit commit = pullRequest.getDestination().getCommit();
    when(commitResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
    when(commitResponse.readEntity(Commit.class)).thenReturn(commit);
    when(bitbucketV2Client.getCommitByOwnerRepoRevision(any(String.class), any(String.class), any(String.class)))
      .thenReturn(commitResponse);

    // get Status
    when(statusResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
    when(bitbucketV2Client.postBuildStatus(any(String.class), any(String.class), any(String.class),
      any(BuildStatus.class))).thenReturn(statusResponse);

    // get Comments List
    InputStream commentsStream = getClass().getClassLoader()
      .getResourceAsStream("mocks/api/2.0/repositories/owner/repo/commit/revision/comments.json");
    CommentList commentList = objectMapper.readValue(commentsStream, CommentList.class);
    when(commentsResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
    when(commentsResponse.readEntity(CommentList.class)).thenReturn(commentList);
    when(bitbucketV2Client.getPullRequestCommentsWithPage(any(String.class), any(String.class), any(Long.class),
      any(Integer.class))).thenReturn(commentsResponse);

    // get Diff
    when(diffResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
    when(diffResponse.readEntity(String.class)).thenReturn("");
    when(bitbucketV2Client.getPullRequestDiff(any(String.class), any(String.class), any(Long.class)))
      .thenReturn(diffResponse);

  }

  @Test
  public void shouldBeAbleToInitPullRequestFacade() throws Exception {
    setDefaultConfig();

    PullRequestFacade pullRequestFacade = new PullRequestFacade(configuration, apiClientFactory);
    pullRequestFacade.init(123, temporaryFolder.getRoot());
  }

  @Test
  public void initShouldFailWithoutGitDirectory() {
    setDefaultConfig();

    try {
      PullRequestFacade pullRequestFacade = new PullRequestFacade(configuration, apiClientFactory);
      pullRequestFacade.init(123, nonGitFolder.getRoot());

      Assert.fail();
    } catch (Exception e) {
      Assert.assertTrue(e instanceof IllegalStateException);
      Assert.assertTrue(e.getMessage().contains("Unable to find Git root directory"));
    }
  }

  @Test
  public void initShouldFailWithoutLogin() {
    setDefaultConfig();
    settings.removeProperty(BitBucketPlugin.BITBUCKET_LOGIN);

    try {
      PullRequestFacade pullRequestFacade = new PullRequestFacade(configuration, apiClientFactory);
      pullRequestFacade.init(123, temporaryFolder.getRoot());

      Assert.fail();
    } catch (Exception e) {
      Assert.assertTrue(e instanceof IllegalStateException);
      Assert.assertTrue(e.getMessage().contains(BitBucketPlugin.BITBUCKET_LOGIN + " cannot be null"));
    }
  }

  @Test
  public void initShouldFailWithoutPassword() {
    setDefaultConfig();
    settings.removeProperty(BitBucketPlugin.BITBUCKET_PASS_KEY);

    try {
      PullRequestFacade pullRequestFacade = new PullRequestFacade(configuration, apiClientFactory);
      pullRequestFacade.init(123, temporaryFolder.getRoot());

      Assert.fail();
    } catch (Exception e) {
      Assert.assertTrue(e instanceof IllegalStateException);
      Assert.assertTrue(e.getMessage().contains(BitBucketPlugin.BITBUCKET_PASS_KEY + " cannot be null"));
    }
  }

  @Test
  public void initShouldFailWithoutRepository() {
    setDefaultConfig();
    settings.removeProperty(BitBucketPlugin.BITBUCKET_REPO);

    try {
      PullRequestFacade pullRequestFacade = new PullRequestFacade(configuration, apiClientFactory);
      pullRequestFacade.init(123, temporaryFolder.getRoot());

      Assert.fail();
    } catch (Exception e) {
      Assert.assertTrue(e instanceof IllegalStateException);
      Assert.assertTrue(e.getMessage().contains(BitBucketPlugin.BITBUCKET_REPO + " cannot be null"));
    }
  }

  @Test
  public void initShouldFailWithoutPullRequestNumber() {
    setDefaultConfig();

    try {
      PullRequestFacade pullRequestFacade = new PullRequestFacade(configuration, apiClientFactory);
      pullRequestFacade.init(0, temporaryFolder.getRoot());

      Assert.fail();
    } catch (Exception e) {
      Assert.assertTrue(e instanceof IllegalStateException);
      Assert.assertTrue(e.getMessage().contains(BitBucketPlugin.BITBUCKET_PULL_REQUEST + " cannot be null"));
    }
  }

  @Test
  public void shouldBeAbleToLoadPullRequestPatch() throws Exception {
    setDefaultConfig();

    InputStream diffStream = getClass().getClassLoader().getResourceAsStream("unified_diff.txt");
    StringWriter writer = new StringWriter();
    IOUtils.copy(diffStream, writer);

    when(diffResponse.readEntity(String.class)).thenReturn(writer.toString());

    DefaultInputFile inputFile = new DefaultInputFile("", "sonar-ws/pom.xml");
    inputFile.setModuleBaseDir(temporaryFolder.getRoot().toPath());
    PullRequestFacade pullRequestFacade = new PullRequestFacade(configuration, apiClientFactory);
    pullRequestFacade.init(123, temporaryFolder.getRoot());
    pullRequestFacade.loadPatch(pullRequest);
    Assert.assertTrue(pullRequestFacade.hasFile(inputFile));
  }

  @Test
  public void testGetWebUrl() throws Exception {

    File gitBasedir = temporaryFolder.newFolder();
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

  private void setDefaultConfig() {
    settings.setProperty(BitBucketPlugin.BITBUCKET_LOGIN, "login");
    settings.setProperty(BitBucketPlugin.BITBUCKET_PASS_KEY, "password");
    settings.setProperty(BitBucketPlugin.BITBUCKET_REPO, "test/repo");
  }
}
