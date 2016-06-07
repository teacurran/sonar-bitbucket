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
package com.wirelust.sonar.plugins.bitbucket;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.CheckForNull;

import org.sonar.api.batch.BatchSide;
import org.sonar.api.batch.InstantiationStrategy;
import org.sonar.api.batch.fs.InputFile;

/**
 * This is a temporary solution before being able to use new postjob API in SQ 5.2.
 */
@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
@BatchSide
public class InputFileCache {

  private final Map<String, InputFile> inputFileByKey = new HashMap<>();

  void put(String componentKey, InputFile inputFile) {
    inputFileByKey.put(componentKey, inputFile);
  }

  @CheckForNull
  public InputFile byKey(String componentKey) {
    return inputFileByKey.get(componentKey);
  }

  @Override
  public String toString() {
    return "Bitbucket Plugin InputFile Cache";
  }

}
