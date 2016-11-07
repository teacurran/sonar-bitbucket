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
package com.wirelust.sonar.plugins.bitbucket.dao;

import javax.ws.rs.core.Response;

import com.wirelust.bitbucket.client.BitbucketV2Client;
import com.wirelust.bitbucket.client.representations.PullRequest;
import com.wirelust.bitbucket.client.representations.v1.V1Comment;
import com.wirelust.sonar.plugins.bitbucket.BitBucketPluginConfiguration;
import com.wirelust.sonar.plugins.bitbucket.client.dao.V2DAO;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sonar.api.config.Settings;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Date: 13-Jun-2016
 *
 * @author T. Curran
 */
@RunWith(MockitoJUnitRunner.class)
public class V2DAOTest {

  @Mock
  BitbucketV2Client bitbucketV2Client;

  @Mock
  Response responseSuccess;

  @Mock
  Response responseFailure;

  Settings settings;
  BitBucketPluginConfiguration configuration;
  V2DAO v2DAO;
  PullRequest pullRequest;

  @Before
  public void init() throws Exception {

    settings = new Settings();
    configuration = new BitBucketPluginConfiguration(settings);
    v2DAO = new V2DAO(bitbucketV2Client, configuration);

    pullRequest = new PullRequest();
    pullRequest.setId(123L);
  }

  @Test
  public void shouldBeAbleToPostPRCommentSuccess() {
    when(responseSuccess.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
    when(bitbucketV2Client.postPullRequestComment(
      any(String.class),
      any(String.class),
      any(Long.class),
      any(V1Comment.class))).thenReturn(responseSuccess);

    when(bitbucketV2Client.putPullRequestComment(
      any(String.class),
      any(String.class),
      any(Long.class),
      any(Long.class),
      any(V1Comment.class))).thenReturn(responseSuccess);

    v2DAO.createOrUpdatePullRequestComment(pullRequest, 100L, "body", "filename", 100);
    verify(responseSuccess).close();

  }

  @Test
  public void shouldBeAbleToPostPRCommentFailure() {
    when(responseFailure.getStatus()).thenReturn(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    when(responseFailure.readEntity(String.class)).thenReturn("failure");
    when(bitbucketV2Client.postPullRequestComment(
      any(String.class),
      any(String.class),
      any(Long.class),
      any(V1Comment.class))).thenReturn(responseFailure);

    when(bitbucketV2Client.putPullRequestComment(
      any(String.class),
      any(String.class),
      any(Long.class),
      any(Long.class),
      any(V1Comment.class))).thenReturn(responseFailure);

    try {
      v2DAO.createOrUpdatePullRequestComment(pullRequest, 100L, "body", "filename", 100);

      Assert.fail();
    } catch (IllegalStateException e) {
      assertEquals("Unable to update review comment file:filename, expected:200, got:500, body:failure",
        e.getMessage());
    }
  }

  @Test
  public void shouldBeAbleToDeletePRCommentFailure() {
    when(responseFailure.getStatus()).thenReturn(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    when(responseFailure.readEntity(String.class)).thenReturn("failure");
    when(bitbucketV2Client.deletePullRequestComment(
      any(String.class),
      any(String.class),
      any(Long.class),
      any(Long.class))).thenReturn(responseFailure);

    try {
      v2DAO.deletePullRequestComment(pullRequest, 1000L);

      Assert.fail();
    } catch (IllegalStateException e) {
      assertEquals("Unable to delete review comment id:1000, expected:200, got:500",
        e.getMessage());
    }
  }
}
