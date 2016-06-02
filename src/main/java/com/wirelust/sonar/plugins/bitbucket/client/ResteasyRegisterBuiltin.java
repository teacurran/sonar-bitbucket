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

import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.ext.Providers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class ResteasyRegisterBuiltin {

  private static final Logger LOGGER = LoggerFactory.getLogger(ResteasyRegisterBuiltin.class);

  private ResteasyRegisterBuiltin() {
    // class should not be instantiated
  }

  public static void registerDefaultProviders(ResteasyProviderFactory factory) throws IOException {
    Enumeration<URL> resources = ResteasyRegisterBuiltin.class.getClassLoader().getResources("META-INF/services/" +
      Providers.class.getName());

    List<String> classes = new ArrayList<>();
    while (resources.hasMoreElements()) {
      URL url = resources.nextElement();

      try (InputStream is = url.openStream()) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));

        for (String line; (line = reader.readLine()) != null; ) {
          String trimmedLine = line.trim();
          if (trimmedLine.length() > 0) {
            classes.add(trimmedLine);
          }
        }
      }
    }

    for (String thisClass : classes) {
      try {
        LOGGER.info("provider:{}", thisClass);
        Class clazz = ResteasyRegisterBuiltin.class.getClassLoader().loadClass(thisClass);
        factory.registerProvider(clazz, true);
      } catch (NoClassDefFoundError e) {
        LOGGER.error("class not found:{}", thisClass, e);
      } catch (ClassNotFoundException e) {
        LOGGER.error("class not found 2:{}", thisClass, e);
      }
    }
  }

}
