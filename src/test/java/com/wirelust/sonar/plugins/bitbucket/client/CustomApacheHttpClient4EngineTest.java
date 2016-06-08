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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.MediaType;

import com.wirelust.bitbucket.client.representations.auth.OauthAccessToken;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;
import org.jboss.resteasy.client.jaxrs.internal.ClientRequestHeaders;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.util.DelegatingOutputStream;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Date: 07-Jun-2016
 *
 * @author T. Curran
 */
public class CustomApacheHttpClient4EngineTest {

  CloseableHttpClient httpClient;
  CustomApacheHttpClient4Engine engine;
  CustomResteasyClientBuilder clientBuilder;
  ResteasyClient client;
  ClientConfiguration clientConfiguration;
  ClientRequestHeaders headers;
  ClientInvocation request;
  URI uri;


  @Before
  public void init() throws Exception {
    httpClient = HttpClientBuilder.create().build();

    engine = new CustomApacheHttpClient4Engine(httpClient);

    clientBuilder = new CustomResteasyClientBuilder();
    client = clientBuilder.build();
    client.register(JacksonConfigurationProvider.class);

    ResteasyProviderFactory resteasyProviderFactory = new ResteasyProviderFactory();
    clientConfiguration = new ClientConfiguration(resteasyProviderFactory);
    headers = new ClientRequestHeaders(clientConfiguration);

    uri = new URI("http://127.0.0.1");

    //request = new ClientInvocation(client, uri, headers, clientConfiguration);
    request = mock(ClientInvocation.class);
    when(request.getHeaders()).thenReturn(headers);
    when(request.getClientConfiguration()).thenReturn(clientConfiguration);
  }

  @Test
  public void shouldBeAbleToLoadHttpMethod() throws Exception {

    headers.header("header1", "value1");

    HttpRequestBase httpMethod = new HttpGet(uri);
    engine.loadHttpMethod(request, httpMethod);

    Header[] headers = httpMethod.getAllHeaders();

    assertEquals(1, headers.length);
    assertEquals("header1", headers[0].getName());
    assertEquals("value1", headers[0].getValue());
  }

  @Test
  public void shouldNotBeAbleToLoadHttpMethodForGet() throws Exception {

    OauthAccessToken accessToken = new OauthAccessToken();

    when(request.getEntity()).thenReturn(accessToken);

    HttpRequestBase httpMethod = new HttpGet(uri);

    try {
      engine.loadHttpMethod(request, httpMethod);

      Assert.fail();
    } catch (ProcessingException e) {
      assertTrue(e.getMessage().contains("RESTEASY004565"));
    }
  }


  @Test
  public void shouldBeAbleToLoadHttpMethodForPost() throws Exception {

    headers.setMediaType(MediaType.TEXT_PLAIN_TYPE);

    when(request.getDelegatingOutputStream()).thenReturn(new DelegatingOutputStream());

    InputStream requestBody = getClass().getClassLoader().getResourceAsStream("mocks/pull_request_1.json");
    ByteArrayOutputStream requestBodyOut = new ByteArrayOutputStream();
    int bytes = IOUtils.copy(requestBody, requestBodyOut);

    when(request.getEntityStream()).thenReturn(requestBodyOut);

    OauthAccessToken accessToken = new OauthAccessToken();
    when(request.getEntity()).thenReturn(accessToken);

    HttpPost post = new HttpPost(uri);

    engine.loadHttpMethod(request, post);

    verify(request).writeRequestBody(requestBodyOut);

  }

}
