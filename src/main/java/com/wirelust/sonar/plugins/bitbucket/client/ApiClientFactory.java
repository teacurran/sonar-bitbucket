package com.wirelust.sonar.plugins.bitbucket.client;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.xml.bind.DatatypeConverter;

import com.wirelust.bitbucket.client.BitbucketAuthClient;
import com.wirelust.bitbucket.client.BitbucketV2Client;
import com.wirelust.sonar.plugins.bitbucket.BitBucketPluginConfiguration;
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
    ResteasyClient client = new CustomResteasyClientBuilder()
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

        LOGGER.debug("basic auth:{}", basicAuthentication);

        headers.add("Authorization", basicAuthentication);
      }
    });

    ResteasyWebTarget target = client.target(config.tokenEndpoint());

    return ProxyBuilder.proxy(BitbucketAuthClient.class, target, resteasyProxyConfig);
  }


  public BitbucketV2Client getV2Client(final String authToken) {

    ResteasyClient client = new CustomResteasyClientBuilder()
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

    return ProxyBuilder.proxy(BitbucketV2Client.class, target, resteasyProxyConfig);
  }

}
