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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.ws.rs.core.Response;

import com.google.common.annotations.VisibleForTesting;
import com.wirelust.bitbucket.client.BitbucketAuthClient;
import com.wirelust.bitbucket.client.BitbucketV2Client;
import com.wirelust.bitbucket.client.representations.BuildStatus;
import com.wirelust.bitbucket.client.representations.Comment;
import com.wirelust.bitbucket.client.representations.CommentList;
import com.wirelust.bitbucket.client.representations.Commit;
import com.wirelust.bitbucket.client.representations.PullRequest;
import com.wirelust.bitbucket.client.representations.User;
import com.wirelust.bitbucket.client.representations.auth.OauthAccessToken;
import com.wirelust.bitbucket.client.representations.v1.V1Comment;
import com.wirelust.sonar.plugins.bitbucket.client.ApiClientFactory;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.patch.Patch;
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

  private final BitBucketPluginConfiguration config;
  private Map<String, List<Integer>> modifiedLinesByFile;
  private Map<String, Map<Integer, Comment>> existingReviewCommentsByLocationByFile = new HashMap<>();

  private PullRequest pullRequest;
  private Commit commit;

  private List<Long> commentsToBeDeleted = new ArrayList<>();
  private File gitBaseDir;
  private String authenticatedUser;
  private BitbucketV2Client bitbucketClient;
  private ApiClientFactory apiClientFactory;

  public PullRequestFacade(BitBucketPluginConfiguration config, ApiClientFactory apiClientFactory) {
    this.config = config;
    this.apiClientFactory = apiClientFactory;
  }

  public void init(int pullRequestNumber, File projectBaseDir) {
    if (findGitBaseDir(projectBaseDir) == null) {
      throw new IllegalStateException("Unable to find Git root directory. Is " + projectBaseDir + " part of a Git repository?");
    }

    String login = config.login();
    if (login == null || login.isEmpty()) {
      throw new IllegalStateException(BitBucketPlugin.BITBUCKET_LOGIN + " cannot be null");
    }

    String password = config.password();
    if (password == null || password.isEmpty()) {
      throw new IllegalStateException(BitBucketPlugin.BITBUCKET_PASSWORD + " cannot be null");
    }


    if (config.repository() == null || config.repository().isEmpty()) {
      throw new IllegalStateException(BitBucketPlugin.BITBUCKET_REPO + " cannot be null");
    }

    if (pullRequestNumber == 0) {
      throw new IllegalStateException(BitBucketPlugin.BITBUCKET_PULL_REQUEST + " cannot be null");
    }

    try {

      BitbucketAuthClient authClient = apiClientFactory.getAuthClient();
      Response response = authClient.getTokenByUsernamePassword("password", config.login(), config.password());
      LOGGER.info("received bitbucket response to login:{}", response.getStatus());

      if (response.getStatus() != Response.Status.OK.getStatusCode()) {
        LOGGER.error("error logging into bitbucket, response received:{}", response.getStatus());
        return;
      }

      OauthAccessToken accessToken = response.readEntity(OauthAccessToken.class);
      LOGGER.debug("bitbucket Access token:{}", accessToken.getAccessToken());

      bitbucketClient = apiClientFactory.getV2Client(accessToken.getAccessToken());

      Response userResponse = bitbucketClient.getUser();
      User user = userResponse.readEntity(User.class);
      authenticatedUser = user.getUsername();
      LOGGER.debug("authenticated bitbucket as username:{}", authenticatedUser);

      Response pullRequestResponse = bitbucketClient.getPullRequestById(
        config.repositoryOwner(), config.repository(), (long)pullRequestNumber);
      setPullRequest(pullRequestResponse.readEntity(PullRequest.class));
      pullRequestResponse.close();

      // The pull request commit has a shortened hash which doesn't work for setting status
      // so let's get the commit from the API so we have the full hash
      Response commitResponse = bitbucketClient.getCommitByOwnerRepoRevision(
        config.repositoryOwner(), config.repository(), pullRequest.getSource().getCommit().getHash());
      setCommit(commitResponse.readEntity(Commit.class));
      commitResponse.close();

      LOGGER.info("Starting analysis of pull request: " + pullRequest.getId());

      createOrUpdateBuildStatus(BuildStatus.State.INPROGRESS);
      loadExistingReviewComments();
      loadPatch(pullRequest);

    } catch (Exception e) {
      throw new IllegalStateException("Unable to perform Bitbucket WS operation", e);
    }
  }

  @VisibleForTesting
  void setPullRequest(PullRequest pullRequest) {
    this.pullRequest = pullRequest;
  }

  void setCommit(Commit commit) { this.commit = commit; }

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

    if (commentList.getValues() == null) {
      return;
    }

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
   * @param inputFile file to check
   * @return true if the pull request contains the provided file path
   */
  @VisibleForTesting
  public boolean hasFile(InputFile inputFile) {
    return modifiedLinesByFile.containsKey(getPath(inputFile));
  }

  /**
   * Test if the P/R contains the provided line for the file path (ie this line is "visible" in diff)
   * @param inputFile file to check
   * @param line the file to check for
   * @return true if the pull request has the line of the error
   */
  @VisibleForTesting
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

  private void createOrUpdateBuildStatus(BuildStatus.State state) {
    BuildStatus buildStatus = new BuildStatus();
    buildStatus.setKey(config.ciKey());
    buildStatus.setName(config.ciName());
    buildStatus.setUrl(config.ciURL());
    buildStatus.setState(state);

    Response statusResponse = bitbucketClient.postBuildStatus(config.repositoryOwner(), config.repository(),
      commit.getHash(), buildStatus);
    statusResponse.close();

    Response.Status buildStatusStatus = Response.Status.fromStatusCode(statusResponse.getStatus());
    if (buildStatusStatus != Response.Status.OK
      && buildStatusStatus != Response.Status.NO_CONTENT
      && buildStatusStatus != Response.Status.CREATED
      && buildStatusStatus != Response.Status.ACCEPTED
    ) {
      throw new IllegalStateException(
        String.format("Unable to update pull request build status. expected:%d, got:%d",
          200, statusResponse.getStatus()));
    }
  }

  private void createOrUpdateApproval(boolean isApproved) {

    String repoOwner = config.repositoryOwner();
    String repo = config.repository();

    Response approvalResponse;
    if (isApproved) {
      createOrUpdateBuildStatus(BuildStatus.State.SUCCESSFUL);

      approvalResponse = bitbucketClient.postPullRequestApproval(repoOwner, repo, pullRequest.getId());
    } else {
      createOrUpdateBuildStatus(BuildStatus.State.FAILED);

      approvalResponse = bitbucketClient.deletePullRequestApproval(repoOwner, repo, pullRequest.getId());
    }

    approvalResponse.close();

    // we are allowing Conflict (409) and Not Found (404)
    // because we are not first checking if a request is already approved before sending the calls
    // if a request is approved and another approval is sent it will return 409
    // if you try to delete an approval that doesn't exist you will get a 404
    Response.Status approvalStatus = Response.Status.fromStatusCode(approvalResponse.getStatus());
    if (approvalStatus != Response.Status.OK
      && approvalStatus != Response.Status.NO_CONTENT
      && approvalStatus != Response.Status.CONFLICT
      && approvalStatus != Response.Status.NOT_FOUND) {
      throw new IllegalStateException(
        String.format("Unable to update pull request approval status. expected:%d, got:%d",
          200, approvalResponse.getStatus()));
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

      if (issueLine != null) {
        url = url + "T" + issueLine.toString();
      }
      return url;
    }

    return null;
  }


}
