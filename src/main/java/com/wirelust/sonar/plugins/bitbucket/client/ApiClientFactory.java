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
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.xml.bind.DatatypeConverter;

import com.wirelust.bitbucket.client.BitbucketAuthClient;
import com.wirelust.bitbucket.client.BitbucketV2Client;
import com.wirelust.sonar.plugins.bitbucket.BitBucketPluginConfiguration;
import org.jboss.resteasy.client.jaxrs.ClientHttpEngine;
import org.jboss.resteasy.client.jaxrs.ProxyBuilder;
import org.jboss.resteasy.client.jaxrs.ProxyConfig;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Date: 02-Jun-2016
 *
 * @author T. Curran
 */
public class ApiClientFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(ApiClientFactory.class);

  private final BitBucketPluginConfiguration config;
  ResteasyProviderFactory resteasyProviderFactory = ResteasyProviderFactory.getInstance();
  ProxyConfig resteasyProxyConfig = new ProxyConfig(this.getClass().getClassLoader(), null, null);

  ClientHttpEngine clientHttpEngine;

  public ApiClientFactory(BitBucketPluginConfiguration config) {
    this.config = config;

    // We need to register Resteasy providers ourselves to make sure they are bound to the correct classloader
    try {
      ResteasyRegisterBuiltin.registerDefaultProviders(resteasyProviderFactory);
    } catch (IOException e) {
      LOGGER.error("unable to register jax-rs providers", e);
    }
  }

  public BitbucketAuthClient getAuthClient() {

    ResteasyClient client = getRestEasyClient();

    client.register((ClientRequestFilter) clientRequestContext -> {
      MultivaluedMap<String, Object> headers = clientRequestContext.getHeaders();

      String basicAuthentication;
      String token = config.clientId() + ":" + config.clientSecret();
      basicAuthentication = "BASIC " + DatatypeConverter.printBase64Binary(token.getBytes("UTF-8"));

      LOGGER.debug("basic auth:{}", basicAuthentication);

      headers.add("Authorization", basicAuthentication);
    });

    ResteasyWebTarget target = client.target(config.tokenEndpoint());
    return ProxyBuilder.proxy(BitbucketAuthClient.class, target, resteasyProxyConfig);
  }


  public BitbucketV2Client getV2Client(final String authToken) {

    ResteasyClient client = getRestEasyClient();

    if (authToken != null) {
      client.register((ClientRequestFilter) requestContext
        -> requestContext.getHeaders().add("Authorization", "Bearer " + authToken));
    }

    ResteasyWebTarget target = client.target(config.endpoint());

    return ProxyBuilder.proxy(BitbucketV2Client.class, target, resteasyProxyConfig);
  }

  public void setClientHttpEngine(ClientHttpEngine clientHttpEngine) {
    this.clientHttpEngine = clientHttpEngine;
  }

  private ResteasyClient getRestEasyClient() {

    CustomResteasyClientBuilder clientBuilder = new CustomResteasyClientBuilder();
    clientBuilder.providerFactory(resteasyProviderFactory);

    if (clientHttpEngine != null) {
      clientBuilder.httpEngine(clientHttpEngine);
    }
    ResteasyClient client = clientBuilder.build();

    client.register(JacksonConfigurationProvider.class);

    return client;
  }
}
