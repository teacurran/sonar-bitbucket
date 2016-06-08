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

import java.security.SecureRandom;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import com.wirelust.sonar.plugins.bitbucket.client.exception.ClientException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.BrowserCompatHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.jboss.resteasy.client.jaxrs.ClientHttpEngine;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.engines.PassthroughTrustManager;

/**
 * Date: 07-Dec-2015
 *
 * @author T. Curran
 *
 * This class differs from CustomResteasyClientBuilder in that it uses LaxRedirectStrategy for apache HTTP Client.
 * This allows the client to follow a redirect request on a POST action. This is required for Bitbucket login to work.
 */
public class CustomResteasyClientBuilder extends ResteasyClientBuilder {

  protected ClientHttpEngine initDefaultEngine() {
    RequestConfig.Builder requestConfigBuilder = RequestConfig.custom();

    X509HostnameVerifier verifier = new BrowserCompatHostnameVerifier();;

    try {
      SSLConnectionSocketFactory sslsf = SSLConnectionSocketFactory.getSocketFactory();

      SSLContext theContext = sslContext;
      if (disableTrustManager) {
        theContext = SSLContext.getInstance("SSL");
        theContext.init(null, new TrustManager[]{new PassthroughTrustManager()}, new SecureRandom());
        verifier = new AllowAllHostnameVerifier();
        sslsf = new SSLConnectionSocketFactory(theContext, verifier);
      } else if (theContext != null) {
        sslsf = new SSLConnectionSocketFactory(theContext, verifier);
      } else if (clientKeyStore != null || truststore != null) {
        // todo: figure out if we need this
        //sslsf = new SSLConnectionSocketFactory(SSLSocketFactory.TLS, clientKeyStore, clientPrivateKeyPassword,
        // truststore, null, verifier);
      } else {
        //sslsf = new SSLSocketFactory(SSLContext.getInstance(SSLSocketFactory.TLS), verifier);
        final SSLContext tlsContext = SSLContext.getInstance(SSLSocketFactory.TLS);
        tlsContext.init(null, null, null);
        sslsf = new SSLConnectionSocketFactory(tlsContext, verifier);
      }


      if (socketTimeout > -1) {
        requestConfigBuilder.setSocketTimeout((int) socketTimeoutUnits.toMillis(socketTimeout));
      }
      if (establishConnectionTimeout > -1) {
        // TODO, check to see if this is accidentally swapped with setConnectTimeout
        requestConfigBuilder.setConnectionRequestTimeout((int) establishConnectionTimeoutUnits.toMillis
          (establishConnectionTimeout));

      }
      if (connectionCheckoutTimeoutMs > -1) {
        requestConfigBuilder.setConnectTimeout(connectionCheckoutTimeoutMs);
      }

      CloseableHttpClient httpClient = HttpClientBuilder.create()
        .setDefaultRequestConfig(requestConfigBuilder.build())
        .setSSLSocketFactory(sslsf)
        .setRedirectStrategy(new LaxRedirectStrategy())
        .build();

      CustomApacheHttpClient4Engine engine = new CustomApacheHttpClient4Engine(httpClient);
      engine.setResponseBufferSize(responseBufferSize);
      engine.setHostnameVerifier(verifier);
      // this may be null.  We can't really support this with Apache Client.
      //engine.setSslContext(theContext);
      //engine.setDefaultProxy(defaultProxy);
      return engine;
    } catch (Exception e) {
      throw new ClientException(e);
    }
  }
}
