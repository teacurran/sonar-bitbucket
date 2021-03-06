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
package com.wirelust.sonar.plugins.bitbucket.client.dao;

import javax.ws.rs.core.Response;

import com.wirelust.bitbucket.client.BitbucketV2Client;
import com.wirelust.bitbucket.client.representations.PullRequest;
import com.wirelust.bitbucket.client.representations.v1.V1Comment;
import com.wirelust.sonar.plugins.bitbucket.BitBucketPluginConfiguration;

/**
 * Date: 11-Jun-2016
 *
 * @author T. Curran
 */
public class V2DAO {

  BitbucketV2Client client;
  BitBucketPluginConfiguration config;

  public V2DAO(BitbucketV2Client client, BitBucketPluginConfiguration config) {
    this.client = client;
    this.config = config;
  }

  public V1Comment createOrUpdatePullRequestComment(final PullRequest pullRequest,
                                                  final Long id,
                                                  final String body,
                                                  final String fileName,
                                                  final Integer line) {

      V1Comment comment = new V1Comment();
      if (id != null) {
        comment.setCommentId(id);
      }
      comment.setContent(body);
      comment.setFilename(fileName);
      comment.setLineTo(line);
      Response response;
      if (id == null) {
        response = client.postPullRequestComment(
          config.repositoryOwner(), config.repository(), pullRequest.getId(), comment);
      } else {
        response = client.putPullRequestComment(
          config.repositoryOwner(), config.repository(), pullRequest.getId(), id, comment);
      }

      if (response == null || response.getStatus() != Response.Status.OK.getStatusCode()) {
        String responseBody = null;
        int responseStatus = 0;
        if (response != null) {
          responseStatus = response.getStatus();
          responseBody = response.readEntity(String.class);

          response.close();
        }
        throw new IllegalStateException(
          String.format("Unable to update review comment file:%s, expected:%d, got:%d, body:%s",
            fileName, 200, responseStatus, responseBody));
      }

      response.close();
      return comment;
  }

  public void deletePullRequestComment(final PullRequest pullRequest, final Long commentId) {
      Response response = client.deletePullRequestComment(
        config.repositoryOwner(), config.repository(), pullRequest.getId(), commentId);
      response.close();

      if (response.getStatus() != Response.Status.OK.getStatusCode()) {
        throw new IllegalStateException(
          String.format("Unable to delete review comment id:%d, expected:%d, got:%d",
            commentId, 200, response.getStatus()));
      }
  }
}
