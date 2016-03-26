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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.xml.bind.DatatypeConverter;

import com.google.common.annotations.VisibleForTesting;
import com.wirelust.bitbucket.client.BitbucketAuthClient;
import com.wirelust.bitbucket.client.BitbucketV2Client;
import com.wirelust.bitbucket.client.representations.Comment;
import com.wirelust.bitbucket.client.representations.CommentList;
import com.wirelust.bitbucket.client.representations.PullRequest;
import com.wirelust.bitbucket.client.representations.User;
import com.wirelust.bitbucket.client.representations.auth.AccessToken;
import com.wirelust.bitbucket.client.representations.v1.V1Comment;
import com.wirelust.sonar.plugins.bitbucket.client.JacksonConfigurationProvider;
import com.wirelust.sonar.plugins.bitbucket.client.ResteasyClientBuilder;
import com.wirelust.sonar.plugins.bitbucket.client.ResteasyRegisterBuiltin;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.patch.Patch;
import org.jboss.resteasy.client.jaxrs.ProxyBuilder;
import org.jboss.resteasy.client.jaxrs.ProxyConfig;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.BatchComponent;
import org.sonar.api.batch.InstantiationStrategy;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputPath;
import org.sonar.api.scan.filesystem.PathResolver;

/**
 * Facade for all WS interaction with GitHub.
 */
@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
public class PullRequestFacade implements BatchComponent {

  private static final Logger LOGGER = LoggerFactory.getLogger(PullRequestFacade.class);

  @VisibleForTesting
  static final String COMMIT_CONTEXT = "sonarqube";

  private final BitBucketPluginConfiguration config;
  private Map<String, List<Integer>> modifiedLinesByFile;
  private Map<String, Map<Integer, Comment>> existingReviewCommentsByLocationByFile = new HashMap<>();

  private PullRequest pullRequest;

  private List<Long> commentsToBeDeleted = new ArrayList<>();
  private File gitBaseDir;
  private String authenticatedUser;
  private BitbucketV2Client bitbucketClient;
  ResteasyProviderFactory resteasyProviderFactory;

  ProxyConfig resteasyProxyConfig = new ProxyConfig(this.getClass().getClassLoader(), null, null);

  public PullRequestFacade(BitBucketPluginConfiguration config) {
    this.config = config;
  }

  public void init(int pullRequestNumber, File projectBaseDir) {
    if (findGitBaseDir(projectBaseDir) == null) {
      throw new IllegalStateException("Unable to find Git root directory. Is " + projectBaseDir + " part of a Git repository?");
    }

    // We need to register Resteasy providers ourselves to make sure they are bound to the correct classloader
    resteasyProviderFactory = ResteasyProviderFactory.getInstance();
    try {
      ResteasyRegisterBuiltin.registerDefaultProviders(resteasyProviderFactory);
    } catch (IOException e) {
      LOGGER.error("unable to register jax-rs providers", e);
    }

    if (config.login() == null || config.login().isEmpty()) {
      throw new IllegalStateException(BitBucketPlugin.BITBUCKET_LOGIN + " cannot be null");
    }

    if (config.password() == null || config.password().isEmpty()) {
      throw new IllegalStateException(BitBucketPlugin.BITBUCKET_PASSWORD + " cannot be null");
    }

    if (config.repository() == null || config.repository().isEmpty()) {
      throw new IllegalStateException(BitBucketPlugin.BITBUCKET_REPO + " cannot be null");
    }

    if (pullRequestNumber == 0) {
      throw new IllegalStateException(BitBucketPlugin.BITBUCKET_PULL_REQUEST + " cannot be null");
    }

    try {

      BitbucketAuthClient authClient = getAuthClient();
      Response response = authClient.getTokenByUsernamePassword("password", config.login(), config.password());
      LOGGER.info("received bitbucket response to login:{}", response.getStatus());

      if (response.getStatus() != Response.Status.OK.getStatusCode()) {
        LOGGER.error("error logging into bitbucket, response received:{}", response.getStatus());
        return;
      }

      AccessToken accessToken = response.readEntity(AccessToken.class);
      LOGGER.debug("bitbucket Access token:{}", accessToken.getAccessToken());

      bitbucketClient = getV2Client(accessToken.getAccessToken());

      Response userResponse = bitbucketClient.getUser();
      User user = userResponse.readEntity(User.class);
      authenticatedUser = user.getUsername();
      LOGGER.debug("authenticated bitbucket as username:{}", authenticatedUser);

      Response pullRequestResponse = bitbucketClient.getPullRequestById(
        config.repositoryOwner(), config.repository(), (long)pullRequestNumber);
      setPullRequest(pullRequestResponse.readEntity(PullRequest.class));
      pullRequestResponse.close();

      LOGGER.info("Starting analysis of pull request: " + pullRequest.getId());

      loadExistingReviewComments();
      loadPatch(pullRequest);

    } catch (Exception e) {
      throw new IllegalStateException("Unable to perform Bitbucket WS operation", e);
    }
  }

  public BitbucketAuthClient getAuthClient() {
    ResteasyClient client = new ResteasyClientBuilder()
      .providerFactory(resteasyProviderFactory)
      .build();

    client.register(JacksonConfigurationProvider.class);

    client.register(new ClientRequestFilter() {
      @Override
      public void filter(ClientRequestContext clientRequestContext) throws IOException {
        MultivaluedMap<String, Object> headers = clientRequestContext.getHeaders();

        String basicAuthentication;
        String token = config.clientId() + ":" + config.clientSecret();
        try {
            basicAuthentication =  "BASIC " + DatatypeConverter.printBase64Binary(token.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException("Cannot encode with UTF-8", ex);
        }

        LOGGER.debug("basic auth:{}", basicAuthentication);

        headers.add("Authorization", basicAuthentication);
      }
    });

    ResteasyWebTarget target = client.target(config.tokenEndpoint());

    return ProxyBuilder.proxy(BitbucketAuthClient.class, target, resteasyProxyConfig);
  }


  public BitbucketV2Client getV2Client(final String authToken) {

    ResteasyClient client = new ResteasyClientBuilder()
      .providerFactory(resteasyProviderFactory)
      .build();

    client.register(JacksonConfigurationProvider.class);

    if (authToken != null) {
      client.register(new ClientRequestFilter() {
        @Override
        public void filter(ClientRequestContext requestContext) throws IOException {

          //String base64Token = Base64.encodeBase64String(token.getBytes(StandardCharsets.UTF_8));
          requestContext.getHeaders().add("Authorization", "Bearer " + authToken);
        }
      });
    }

    ResteasyWebTarget target = client.target(config.endpoint());

    return ProxyBuilder.proxy(BitbucketV2Client.class, target, resteasyProxyConfig);
  }

  @VisibleForTesting
  void setPullRequest(PullRequest pullRequest) {
    this.pullRequest = pullRequest;
  }

  public File findGitBaseDir(@Nullable File baseDir) {
    if (baseDir == null) {
      return null;
    }
    if (new File(baseDir, ".git").exists()) {
      setGitBaseDir(baseDir);
      return baseDir;
    }
    return findGitBaseDir(baseDir.getParentFile());
  }

  @VisibleForTesting
  void setGitBaseDir(File gitBaseDir) {
    this.gitBaseDir = gitBaseDir;
  }

  private void loadPatch(PullRequest pullRequest) throws IOException {

    Response response = bitbucketClient.getPullRequestDiff(config.repositoryOwner(), config.repository(), pullRequest.getId());
    LOGGER.debug("received bitbucket response getPullRequestDiff:{}", response.getStatus());

    String diffString = response.readEntity(String.class);
    InputStream diffStream = new ByteArrayInputStream(diffString.getBytes(StandardCharsets.UTF_8));

    Patch patch = new Patch();
    patch.parse(diffStream);

    modifiedLinesByFile = new HashMap<>();

    for (FileHeader fileHeader : patch.getFiles()) {
      List<Integer> patchLocationMapping = new ArrayList<>();
      modifiedLinesByFile.put(fileHeader.getNewPath(), patchLocationMapping);

      if (fileHeader.getHunks() == null) {
        continue;
      }

      for (HunkHeader hunk : fileHeader.getHunks()) {
        for (Edit edit : hunk.toEditList()) {
          if (!edit.getType().equals(Edit.Type.DELETE)) {
            for (int line=edit.getBeginB(); line<edit.getEndB(); line++) {
              patchLocationMapping.add(line+1);
            }
          }
        }
      }
    }
  }

  /**
   * Load all previous comments made by provided bitbucket account.
   */
  private void loadExistingReviewComments() throws IOException {
    loadExistingReviewCommentsWithPage(1);
  }

  private void loadExistingReviewCommentsWithPage(int page) throws IOException {
    if (page <= 0) {
      throw new IllegalArgumentException("page must be 1 or greater");
    }
    Response commentResponse = bitbucketClient.getPullRequestCommentsWithPage(
      config.repositoryOwner(), config.repository(), pullRequest.getId(), page);
    CommentList commentList = commentResponse.readEntity(CommentList.class);

    for (Comment comment : commentList.getValues()) {
      if (!authenticatedUser.equals(comment.getUser().getUsername())) {
        // Ignore comments from other users
        continue;
      }
      String commentPath = null;
      if (comment.getInline() != null) {
        commentPath = comment.getInline().getPath();
      }
      if (commentPath != null) {
        if (!existingReviewCommentsByLocationByFile.containsKey(commentPath)) {
          existingReviewCommentsByLocationByFile.put(commentPath, new HashMap<Integer, Comment>());
        }
        existingReviewCommentsByLocationByFile.get(commentPath).put(comment.getInline().getTo(), comment);
      }

      // By default all previous comments will be marked for deletion
      commentsToBeDeleted.add(comment.getId());
    }

    // if we have more comments, load the next page.
    if (commentList.getNext() != null) {
      loadExistingReviewCommentsWithPage(page+1);
    }
  }

  private String getPath(InputPath inputPath) {
    return new PathResolver().relativePath(gitBaseDir, inputPath.file());
  }

  /**
   * Test if the P/R contains the provided file path (ie this file was added/modified/updated)
   */
  public boolean hasFile(InputFile inputFile) {
    return modifiedLinesByFile.containsKey(getPath(inputFile));
  }

  /**
   * Test if the P/R contains the provided line for the file path (ie this line is "visible" in diff)
   */
  public boolean hasFileLine(InputFile inputFile, int line) {
    return modifiedLinesByFile.get(getPath(inputFile)).contains(line);
  }

  public void createOrUpdateReviewComment(InputFile inputFile, Integer line, String body) {

    String fullpath = getPath(inputFile);
    LOGGER.info("creating comment:{} line:{}", fullpath, line, body);

    try {
      if (existingReviewCommentsByLocationByFile.containsKey(fullpath)
        && existingReviewCommentsByLocationByFile.get(fullpath).containsKey(line)) {
        Comment existingReview = existingReviewCommentsByLocationByFile.get(fullpath).get(line);

        if (existingReview.getContent() != null && existingReview.getContent().getRaw() != null) {
          if (!existingReview.getContent().getRaw().equals(body)) {
            existingReview.getContent().setMarkup(body);

            V1Comment comment = new V1Comment();
            comment.setCommentId(existingReview.getId());
            comment.setContent(body);
            comment.setFilename(fullpath);
            comment.setLineTo(line);
            Response response = bitbucketClient.postPullRequestComment(
              config.repositoryOwner(), config.repository(), pullRequest.getId(), comment);

            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
              String responseBody = response.readEntity(String.class);
              throw new IllegalStateException(
                String.format("Unable to update review comment file:%s, expected:%d, got:%d, body:%s",
                  fullpath, 200, response.getStatus(), responseBody));
            }

            response.close();

          }
        }
        commentsToBeDeleted.remove(existingReview.getId());
      } else {
        V1Comment comment = new V1Comment();
        comment.setContent(body);
        comment.setFilename(fullpath);
        comment.setLineTo(line);
        Response response = bitbucketClient.postPullRequestComment(
          config.repositoryOwner(), config.repository(), pullRequest.getId(), comment);

        if (response.getStatus() != Response.Status.OK.getStatusCode()) {
          String responseBody = response.readEntity(String.class);
          throw new IllegalStateException(
            String.format("Unable to create review comment file:%s, expected:%d, got:%d, body:%s",
              fullpath, 200, response.getStatus(), responseBody));
        }
        response.close();

      }
    } catch (Exception e) {
      throw new IllegalStateException("Unable to create or update review comment in file " + fullpath + " at line " + line, e);
    }
  }

  public void deleteOutdatedComments() {
    for (Long commentId : commentsToBeDeleted) {
      LOGGER.info("deleting outdated comment:{}", commentId);

      Response response = bitbucketClient.deletePullRequestComment(
        config.repositoryOwner(), config.repository(), pullRequest.getId(), commentId);
      response.close();

      if (response.getStatus() != Response.Status.OK.getStatusCode()) {
        throw new IllegalStateException(
          String.format("Unable to delete review comment id:%d, expected:%d, got:%d",
            commentId, 200, response.getStatus()));
      }
    }
  }

  public void addGlobalComment(String comment) {
    LOGGER.debug("global comment:{}", comment);

    Response response = bitbucketClient.postPullRequestComment(
      config.repositoryOwner(),
      config.repository(),
      pullRequest.getId(),
      comment);

    if (response.getStatus() < Response.Status.OK.getStatusCode()
      || response.getStatus() > Response.Status.NO_CONTENT.getStatusCode()) {

      throw new IllegalStateException(String.format("Unable to comment the pull request:%d", pullRequest.getId()));
    }

  }

  public void approvePullRequest() {
    LOGGER.info("approving pull request");
    createOrUpdateApproval(true);
  }

  public void unapprovePullRequest() {
    LOGGER.info("removing pull request approval");
    createOrUpdateApproval(false);
  }

  private void createOrUpdateApproval(boolean isApproved) {

    String repoOwner = config.repositoryOwner();
    String repo = config.repository();

    Response response;
    if (isApproved) {
      response = bitbucketClient.postPullRequestApproval(repoOwner, repo, pullRequest.getId());
    } else {
      response = bitbucketClient.deletePullRequestApproval(repoOwner, repo, pullRequest.getId());
    }

    response.close();

    // we are allowing Conflict (409) and Not Found (404)
    // because we are not first checking if a request is already approved before sending the calls
    // if a request is approved and another approval is sent it will return 409
    // if you try to delete an approval that doesn't exist you will get a 404
    if (response.getStatus() != Response.Status.OK.getStatusCode()
      && response.getStatus() != Response.Status.NO_CONTENT.getStatusCode()
      && response.getStatus() != Response.Status.CONFLICT.getStatusCode()
      && response.getStatus() != Response.Status.NOT_FOUND.getStatusCode()) {
      throw new IllegalStateException(
        String.format("Unable to update pull request approval status. expected:%d, got:%d",
          200, response.getStatus()));
    }
  }

  @CheckForNull
  public String getWebUrl(@Nullable InputPath inputPath, @Nullable Integer issueLine) {
    if (inputPath != null) {

      String url = pullRequest.getLinks().get("self").get(0).getHref() +
        "/" +
        pullRequest.getSource().getBranch().getName() +
        "/diff/#chg-" +
        getPath(inputPath);

      return url;
    }

    return null;
  }


}
