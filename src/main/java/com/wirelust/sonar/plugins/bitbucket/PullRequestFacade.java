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
import com.wirelust.bitbucket.client.representations.Repository;
import com.wirelust.bitbucket.client.representations.User;
import com.wirelust.bitbucket.client.representations.auth.AccessToken;
import com.wirelust.bitbucket.client.representations.v1.V1Comment;
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
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHCommitStatus;
import org.kohsuke.github.GHPullRequest;
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

  private static final Logger LOG = LoggerFactory.getLogger(PullRequestFacade.class);

  @VisibleForTesting
  static final String COMMIT_CONTEXT = "sonarqube";

  private final BitBucketPluginConfiguration config;
  private Map<String, List<Integer>> modifiedLinesByFile;
  private Map<String, Map<Integer, Comment>> existingReviewCommentsByLocationByFile = new HashMap<>();

  private Repository repository;
  private PullRequest pullRequest;
  private CommentList commentList;

  private Map<Long, Comment> reviewCommentToBeDeletedById = new HashMap<>();
  private File gitBaseDir;
  private String myself;
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
      LOG.error("unable to register jax-rs providers", e);
    }

    try {

      BitbucketAuthClient authClient = getAuthClient();
      Response response = authClient.getTokenByUsernamePassword("password", config.login(), config.password());
      LOG.info("received bitbucket response to login:{}", response.getStatus());

      if (response.getStatus() != Response.Status.OK.getStatusCode()) {
        LOG.error("error logging into bitbucket, response received:{}", response.getStatus());
        return;
      }

      AccessToken accessToken = response.readEntity(AccessToken.class);
      LOG.debug("bitbucket Access token:{}", accessToken.getAccessToken());

      bitbucketClient = getV2Client(accessToken.getAccessToken());

      Response userResponse = bitbucketClient.getUser();
      User user = userResponse.readEntity(User.class);
      myself = user.getUsername();

      Response repoResponse = bitbucketClient.getRepositoryByOwnerRepo(config.repositoryOwner(), config.repository());
      setRepository(repoResponse.readEntity(Repository.class));

      Response pullRequestResponse = bitbucketClient.getPullRequestById(
        config.repositoryOwner(), config.repository(), (long)pullRequestNumber);
      setPullRequest(pullRequestResponse.readEntity(PullRequest.class));
      pullRequestResponse.close();

      LOG.info("Starting analysis of pull request: " + pullRequest.getId());

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

        LOG.debug("basic auth:{}", basicAuthentication);

        headers.add("Authorization", basicAuthentication);
      }
    });

    ResteasyWebTarget target = client.target(config.tokenEndpoint());
    BitbucketAuthClient authClient = ProxyBuilder.proxy(BitbucketAuthClient.class, target, resteasyProxyConfig);

    return authClient;
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
    BitbucketV2Client bitbucketV2Client = ProxyBuilder.proxy(BitbucketV2Client.class, target, resteasyProxyConfig);

    return bitbucketV2Client;
  }

  @VisibleForTesting
  void setRepository(Repository repo) {
    this.repository = repo;
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
    LOG.debug("received bitbucket response getPullRequestDiff:{}", response.getStatus());

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
    Response commentResponse = bitbucketClient.getPullRequestComments(
      config.repositoryOwner(), config.repository(), pullRequest.getId());
    commentList = commentResponse.readEntity(CommentList.class);

    for (Comment comment : commentList.getValues()) {
      if (!myself.equals(comment.getUser().getUsername())) {
        // Ignore comments from other users
        continue;
      }
      String commentPath = null;
      if (comment.getInline() != null) {
        commentPath = comment.getInline().getPath();
      }
      if (commentPath != null && !existingReviewCommentsByLocationByFile.containsKey(commentPath)) {
        existingReviewCommentsByLocationByFile.put(commentPath, new HashMap<Integer, Comment>());
      }
      // By default all previous comments will be marked for deletion
      reviewCommentToBeDeletedById.put(comment.getId(), comment);
      if (commentPath != null) {
        existingReviewCommentsByLocationByFile.get(commentPath).put(comment.getInline().getTo(), comment);
      }
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

    LOG.info("createOrUpdateReviewComment:{} line:{} body:{}", fullpath, line, body);

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
        reviewCommentToBeDeletedById.remove(existingReview.getId());
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

    LOG.info("createOrUpdateReviewComment2:{} line:{} body:{}", fullpath, line, body);
  }

  public void deleteOutdatedComments() {
    for (Comment reviewToDelete : reviewCommentToBeDeletedById.values()) {
      Response response = bitbucketClient.deletePullRequestComment(
        config.repositoryOwner(), config.repository(), pullRequest.getId(), reviewToDelete.getId());

      if (response.getStatus() != Response.Status.OK.getStatusCode()) {
        throw new IllegalStateException(
          String.format("Unable to delete review comment id:%d, expected:%d, got:%d",
            reviewToDelete.getId(), 200, response.getStatus()));
      }
    }
  }

  public void addGlobalComment(String comment) {
    LOG.debug("global comment:{}", comment);

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

  public void createOrUpdateSonarQubeStatus(GHCommitState status, String statusDescription) {
        // TODO: implement
//    try {
//      // Copy previous targetUrl in case it was set by an external system (like the CI job).
//      String targetUrl = null;
//      GHCommitStatus lastStatus = getCommitStatusForContext(pullRequest, COMMIT_CONTEXT);
//      if (lastStatus != null) {
//        targetUrl = lastStatus.getTargetUrl();
//      }
//      ghRepo.createCommitStatus(pullRequest.getHead().getSha(), status, targetUrl, statusDescription, COMMIT_CONTEXT);
//    } catch (IOException e) {
//      throw new IllegalStateException("Unable to update commit status", e);
//    }
  }

  @CheckForNull
  public String getGithubUrl(@Nullable InputPath inputPath, @Nullable Integer issueLine) {
    if (inputPath != null) {
      String path = getPath(inputPath);
        // TODO: implement
//      return ghRepo.getHtmlUrl().toString() + "/blob/" + pullRequest.getHead().getSha() + "/" + path + (issueLine != null ? ("#L" + issueLine) : "");
    }
    return null;
  }

  @VisibleForTesting
  @CheckForNull
  GHCommitStatus getCommitStatusForContext(GHPullRequest pr, String context) {
    List<GHCommitStatus> statuses;
    try {
      statuses = pr.getRepository().listCommitStatuses(pr.getHead().getSha()).asList();
    } catch (IOException e) {
      throw new IllegalStateException("Unable to retrieve commit statuses.", e);
    }
    for (GHCommitStatus status : statuses) {
      if (context.equals(status.getContext())) {
        return status;
      }
    }
    return null;
  }
}
