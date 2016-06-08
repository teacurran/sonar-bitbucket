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
import java.io.IOException;
import javax.ws.rs.ProcessingException;

import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.jboss.resteasy.client.jaxrs.engines.ApacheHttpClient4Engine;
import org.jboss.resteasy.client.jaxrs.i18n.Messages;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;

/**
 * Date: 08-Dec-2015
 *
 * @author T. Curran
 *
 * This class is overridden because the super version has setRedirecting(false) with no way to configure it.
 * Using this overrided class we can set the redirect strategy in the client without any issue.
 *
 */
public class CustomApacheHttpClient4Engine extends ApacheHttpClient4Engine {

  public CustomApacheHttpClient4Engine(HttpClient httpClient) {
    this.httpClient = httpClient;
    this.createdHttpClient = true;
  }

  @Override
  protected void loadHttpMethod(final ClientInvocation request, HttpRequestBase httpMethod) throws Exception {
    if (request.getEntity() == null) {
      // no body
      commitHeaders(request, httpMethod);
      return;
    }

    if (httpMethod instanceof HttpGet) {
      throw new ProcessingException(Messages.MESSAGES.getRequestCannotHaveBody());
    }

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    request.getDelegatingOutputStream().setDelegate(baos);
    try {
      HttpEntity entity = buildEntity(request);
      HttpPost post = (HttpPost) httpMethod;
      commitHeaders(request, httpMethod);
      post.setEntity(entity);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
