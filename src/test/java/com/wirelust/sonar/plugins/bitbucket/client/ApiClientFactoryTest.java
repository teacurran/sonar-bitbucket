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
    settings.setProperty(BitBucketPlugin.BITBUCKET_TOKEN_ENDPOINT, "http://127.0.0.1");
    settings.setProperty(BitBucketPlugin.BITBUCKET_CLIENT_ID, "client_id");
    settings.setProperty(BitBucketPlugin.BITBUCKET_CLIENT_SECRET, "client_secret");

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
                                             Assert.assertEquals("BASIC Y2xpZW50X2lkOmNsaWVudF9zZWNyZXQ=", authHeader);

                                             return mockClientResponse;
                                           }

                                           @Override
                                           public void close() {

                                           }
                                         });


    BitbucketAuthClient authClient = apiClientFactory.getAuthClient();

    Response response = authClient.getTokenByUsernamePassword("password", "user", "password");
    Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
  }

  @Test
  public void shouldBeAbleToGetV2Client() throws Exception {
    settings.setProperty(BitBucketPlugin.BITBUCKET_TOKEN_ENDPOINT, "http://127.0.0.1");
    settings.setProperty(BitBucketPlugin.BITBUCKET_CLIENT_ID, "client_id");
    settings.setProperty(BitBucketPlugin.BITBUCKET_CLIENT_SECRET, "client_secret");

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
                                             Assert.assertEquals("Bearer token-xxx123", authHeader);
                                             return mockClientResponse;
                                           }

                                           @Override
                                           public void close() {

                                           }
                                         });


    BitbucketV2Client v2Client = apiClientFactory.getV2Client("token-xxx123");

    Response response = v2Client.getAllRepositories();
    Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
  }
}
