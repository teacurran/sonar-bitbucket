package com.wirelust.sonar.plugins.bitbucket.client;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.Response;

import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;
import com.wirelust.bitbucket.client.BitbucketAuthClient;
import com.wirelust.sonar.plugins.bitbucket.BitBucketPlugin;
import com.wirelust.sonar.plugins.bitbucket.BitBucketPluginConfiguration;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration;
import org.jboss.resteasy.client.jaxrs.internal.ClientResponse;
import org.jboss.resteasy.client.jaxrs.internal.proxy.extractors.ClientResponseProxy;
import org.jboss.resteasy.spi.HeaderValueProcessor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;

import static org.junit.Assert.assertTrue;

/**
 * Date: 06-Jun-2016
 *
 * @author T. Curran
 */
public class ApiClientFactoryTest {

  private Settings settings;
  private BitBucketPluginConfiguration configuration;
  private ApiClientFactory apiClientFactory;

  @Before
  public void prepare() {
    settings = new Settings(new PropertyDefinitions(BitBucketPlugin.class));
    configuration = new BitBucketPluginConfiguration(settings);
    apiClientFactory = new ApiClientFactory(configuration);
  }

  @Test
  public void shouldBeAbleToGetAuthClient() throws Exception {
    settings.setProperty(BitBucketPlugin.BITBUCKET_TOKEN_ENDPOINT, "http://127.0.0.1");

    BitbucketAuthClient authClient = apiClientFactory.getAuthClient();

    Response response = authClient.getTokenByUsernamePassword("password", "user", "password");

    assertTrue(response instanceof ClientResponse);
    ClientResponse clientResponse = (ClientResponse)response;
    Method processorMethod = ClientResponse.class.getDeclaredMethod("getHeaderValueProcessor");
    processorMethod.setAccessible(true);
    HeaderValueProcessor headerValueProcessor = (HeaderValueProcessor)processorMethod.invoke(clientResponse, null);


    clientResponse.toString();
    //assertTrue(response.getClass().isAssignableFrom(ClientResponseProxy.class));

    //assertTrue(authClient.getClass().isAssignableFrom(ResteasyClient.class));

    //ClientResponse resteasyClient = (ClientResponse)authClient;
    //ClientConfiguration clientConfiguration = (ClientConfiguration)resteasyClient.getConfiguration();

    //ClientRequestFilter[] requestFilters = clientConfiguration.getRequestFilters(ApiClientFactoryTest.class, null);
  }
}
