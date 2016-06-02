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

import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.BrowserCompatHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.StrictHostnameVerifier;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.jboss.resteasy.client.jaxrs.ClientHttpEngine;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.engines.PassthroughTrustManager;
import org.jboss.resteasy.client.jaxrs.i18n.Messages;

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

    X509HostnameVerifier verifier = null;
    if (this.verifier != null) {
      verifier = new VerifierWrapper(this.verifier);
    } else {
      switch (policy) {
        case ANY:
          verifier = new AllowAllHostnameVerifier();
          break;
        case WILDCARD:
          verifier = new BrowserCompatHostnameVerifier();
          break;
        case STRICT:
          verifier = new StrictHostnameVerifier();
          break;
        default:
          break;
      }
    }
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
      throw new RuntimeException(e);
    }
  }

  static class VerifierWrapper implements X509HostnameVerifier {
    protected HostnameVerifier verifier;

    VerifierWrapper(HostnameVerifier verifier) {
      this.verifier = verifier;
    }

    @Override
    public void verify(String host, SSLSocket ssl) throws IOException {
      if (!verifier.verify(host, ssl.getSession())) {
        throw new SSLException(Messages.MESSAGES.hostnameVerificationFailure());
      }
    }

    @Override
    public void verify(String host, X509Certificate cert) throws SSLException {
      throw new SSLException(Messages.MESSAGES.verificationPathNotImplemented());
    }

    @Override
    public void verify(String host, String[] cns, String[] subjectAlts) throws SSLException {
      throw new SSLException(Messages.MESSAGES.verificationPathNotImplemented());
    }

    @Override
    public boolean verify(String s, SSLSession sslSession) {
      return verifier.verify(s, sslSession);
    }
  }

}
