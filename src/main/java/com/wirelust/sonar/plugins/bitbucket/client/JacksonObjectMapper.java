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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;

/**
 * Date: 07-Oct-2015
 *
 * @author T. Curran
 */
public class JacksonObjectMapper extends ObjectMapper {

  public static JacksonObjectMapper get() {

    JacksonObjectMapper mapper = new JacksonObjectMapper();

    mapper.configure(DeserializationFeature.UNWRAP_ROOT_VALUE, false);

    // Bitbucket uses underscores in their json names, our value objects use camel case
    mapper.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);

    // Bitbucket will sometimes return an array and sometimes return a single object for most lists
    mapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

    // In the unit tests this is set to true because we want it to fail there
    // but in production we don't want it to fail so easily.
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // When sending JSON to bitbucket we want to leave out null properties.
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

    return mapper;
  }
}
