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
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.jboss.resteasy.client.jaxrs.ClientHttpEngine;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.engines.PassthroughTrustManager;
import org.jboss.resteasy.plugins.providers.DataSourceProvider;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Date: 18-May-2016
 *
 * @author T. Curran
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({SSLContext.class})
public class RestEasyClientBuilderTest {

  @Mock
  SSLContext sslContext;

  @Before
  public void init() throws Exception {

    when(sslContext.getSocketFactory()).thenReturn(new MockSSLSocketFactory());

    PowerMockito.mockStatic(SSLContext.class);
    when(SSLContext.getInstance("SSL")).thenReturn(sslContext);

  }

  @Test
  public void shouldBeAbleToInitilizeReasteasyClientBuilder() throws Exception {
    ResteasyProviderFactory resteasyProviderFactory = ResteasyProviderFactory.getInstance();

    when(SSLContext.getInstance(SSLConnectionSocketFactory.TLS)).thenReturn(sslContext);

    ResteasyClient client = new CustomResteasyClientBuilder()
      .providerFactory(resteasyProviderFactory)
      .build();

    ClientHttpEngine httpEngine = client.httpEngine();

    Assert.assertTrue(httpEngine instanceof CustomApacheHttpClient4Engine);
  }

  @Test
  public void shouldBeAbleToInitilizeReasteasyClientBuilderWithoutTrustManager() throws Exception {
    ResteasyProviderFactory resteasyProviderFactory = ResteasyProviderFactory.getInstance();

    ResteasyClient client = new CustomResteasyClientBuilder()
      .providerFactory(resteasyProviderFactory)
      .disableTrustManager()
      .build();

    verify(sslContext).init(any(), any(PassthroughTrustManager[].class), any(SecureRandom.class));
  }

  @Test
  public void shouldBeAbleToInitilizeReasteasyClientBuilderWithTrustManager() throws Exception {
    ResteasyProviderFactory resteasyProviderFactory = ResteasyProviderFactory.getInstance();

    when(SSLContext.getInstance(SSLConnectionSocketFactory.TLS)).thenReturn(sslContext);

    ResteasyClient client = new CustomResteasyClientBuilder()
      .providerFactory(resteasyProviderFactory)
      .build();

    PowerMockito.verifyStatic();
  }

  @Test
  public void shouldBeAbleToRegisterPlugins() throws Exception {
    ResteasyProviderFactory resteasyProviderFactory = ResteasyProviderFactory.getInstance();

    ResteasyRegisterBuiltin.registerDefaultProviders(resteasyProviderFactory);

    Assert.assertTrue(resteasyProviderFactory.getProviderClasses().contains(DataSourceProvider.class));
  }

  /**
   * This method simply instantiates a private constructor to ensure code coverage for it so the
   * coverage reports aren't diminished
   */
  @Test
  public void testResteasyRegisterConstructorIsPrivate() throws Exception {
    Constructor<ResteasyRegisterBuiltin> constructor = ResteasyRegisterBuiltin.class.getDeclaredConstructor();
    Assert.assertTrue(Modifier.isPrivate(constructor.getModifiers()));
    constructor.setAccessible(true);
    constructor.newInstance();
  }

  private class MockSSLSocketFactory extends SSLSocketFactory {

    @Override
    public String[] getDefaultCipherSuites() {
      return new String[0];
    }

    @Override
    public String[] getSupportedCipherSuites() {
      return new String[0];
    }

    @Override
    public Socket createSocket(Socket socket, String s, int i, boolean b) throws IOException {
      return null;
    }

    @Override
    public Socket createSocket(String s, int i) throws IOException, UnknownHostException {
      return null;
    }

    @Override
    public Socket createSocket(String s, int i, InetAddress inetAddress, int i1) throws IOException, UnknownHostException {
      return null;
    }

    @Override
    public Socket createSocket(InetAddress inetAddress, int i) throws IOException {
      return null;
    }

    @Override
    public Socket createSocket(InetAddress inetAddress, int i, InetAddress inetAddress1, int i1) throws IOException {
      return null;
    }
  }
}
