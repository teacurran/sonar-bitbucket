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
package com.wirelust.sonar.plugins.bitbucket.client;

import java.io.IOException;
import java.io.InputStream;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.ws.rs.core.Response;

import com.wirelust.bitbucket.client.BitbucketAuthClient;
import com.wirelust.bitbucket.client.BitbucketV2Client;
import com.wirelust.sonar.plugins.bitbucket.BitBucketPlugin;
import com.wirelust.sonar.plugins.bitbucket.BitBucketPluginConfiguration;
import org.jboss.resteasy.client.jaxrs.ClientHttpEngine;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;
import org.jboss.resteasy.client.jaxrs.internal.ClientRequestHeaders;
import org.jboss.resteasy.client.jaxrs.internal.ClientResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Date: 06-Jun-2016
 *
 * @author T. Curran
 */
public class ApiClientFactoryTest {

  private Settings settings;
  private BitBucketPluginConfiguration configuration;
  private ApiClientFactory apiClientFactory;
  ClientResponse mockClientResponse;

  @Before
  public void prepare() {
    settings = new Settings(new PropertyDefinitions(BitBucketPlugin.class));

    settings.setProperty(BitBucketPlugin.BITBUCKET_TOKEN_ENDPOINT, "http://127.0.0.1");
    settings.setProperty(BitBucketPlugin.BITBUCKET_CLIENT_ID, "client_id");
    settings.setProperty(BitBucketPlugin.BITBUCKET_CLIENT_SECRET, "client_secret");

    configuration = new BitBucketPluginConfiguration(settings);
    apiClientFactory = new ApiClientFactory(configuration);

    mockClientResponse = new ClientResponse(null) {
      @Override
      protected InputStream getInputStream() {
        return null;
      }

      @Override
      protected void setInputStream(InputStream is) {

      }

      @Override
      public void releaseConnection() throws IOException {

      }
    };
    mockClientResponse.setStatus(Response.Status.OK.getStatusCode());
  }

  @Test
  public void shouldBeAbleToGetAuthClient() throws Exception {

    apiClientFactory.setClientHttpEngine(new ClientHttpEngine() {
                                           @Override
                                           public SSLContext getSslContext() {
                                             return null;
                                           }

                                           @Override
                                           public HostnameVerifier getHostnameVerifier() {
                                             return null;
                                           }

                                           @Override
                                           public ClientResponse invoke(ClientInvocation request) {
                                             ClientRequestHeaders clientRequestHeaders = request.getHeaders();
                                             String authHeader = clientRequestHeaders.getHeader("Authorization");
                                             assertEquals("BASIC Y2xpZW50X2lkOmNsaWVudF9zZWNyZXQ=", authHeader);

                                             return mockClientResponse;
                                           }

                                           @Override
                                           public void close() {

                                           }
                                         });


    BitbucketAuthClient authClient = apiClientFactory.getAuthClient();

    Response response = authClient.getTokenByUsernamePassword("password", "user", "password");
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
  }

  @Test
  public void shouldBeAbleToGetV2Client() throws Exception {
    apiClientFactory.setClientHttpEngine(new ClientHttpEngine() {
                                           @Override
                                           public SSLContext getSslContext() {
                                             return null;
                                           }

                                           @Override
                                           public HostnameVerifier getHostnameVerifier() {
                                             return null;
                                           }

                                           @Override
                                           public ClientResponse invoke(ClientInvocation request) {
                                             ClientRequestHeaders clientRequestHeaders = request.getHeaders();
                                             String authHeader = clientRequestHeaders.getHeader("Authorization");
                                             assertEquals("Bearer token-xxx123", authHeader);
                                             return mockClientResponse;
                                           }

                                           @Override
                                           public void close() {

                                           }
                                         });


    BitbucketV2Client v2Client = apiClientFactory.getV2Client("token-xxx123");

    Response response = v2Client.getAllRepositories();
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
  }

  @Test
  public void shouldBeAbleToGetV2ClientWithoutAuthToken() throws Exception {
    apiClientFactory.setClientHttpEngine(new ClientHttpEngine() {
                                           @Override
                                           public SSLContext getSslContext() {
                                             return null;
                                           }

                                           @Override
                                           public HostnameVerifier getHostnameVerifier() {
                                             return null;
                                           }

                                           @Override
                                           public ClientResponse invoke(ClientInvocation request) {
                                             ClientRequestHeaders clientRequestHeaders = request.getHeaders();
                                             String authHeader = clientRequestHeaders.getHeader("Authorization");
                                             assertNull(authHeader);
                                             return mockClientResponse;
                                           }

                                           @Override
                                           public void close() {

                                           }
                                         });


    BitbucketV2Client v2Client = apiClientFactory.getV2Client(null);

    Response response = v2Client.getAllRepositories();
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
  }

}
