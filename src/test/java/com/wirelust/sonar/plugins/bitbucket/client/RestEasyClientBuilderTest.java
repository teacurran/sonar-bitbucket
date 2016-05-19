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
package com.wirelust.sonar.plugins.bitbucket.client;

import org.jboss.resteasy.client.jaxrs.ClientHttpEngine;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.plugins.providers.DataSourceProvider;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.Assert;
import org.junit.Test;

/**
 * Date: 18-May-2016
 *
 * @author T. Curran
 */
public class RestEasyClientBuilderTest {


  @Test
  public void shouldBeAbleToInitilizeReasteasyClientBuilder() {
    ResteasyProviderFactory resteasyProviderFactory = ResteasyProviderFactory.getInstance();

    ResteasyClient client = new ResteasyClientBuilder()
      .providerFactory(resteasyProviderFactory)
      .build();

    ClientHttpEngine httpEngine = client.httpEngine();

    Assert.assertTrue(httpEngine instanceof ApacheHttpClient4Engine);
  }

  @Test
  public void shouldBeAbleToRegisterPlugins() throws Exception {
    ResteasyProviderFactory resteasyProviderFactory = ResteasyProviderFactory.getInstance();

    ResteasyRegisterBuiltin.registerDefaultProviders(resteasyProviderFactory);

    Assert.assertTrue(resteasyProviderFactory.getProviderClasses().contains(DataSourceProvider.class));
  }

}
