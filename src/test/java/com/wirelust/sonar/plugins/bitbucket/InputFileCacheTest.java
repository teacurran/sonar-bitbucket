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

import org.junit.Test;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultInputFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Date: 04-Jun-2016
 *
 * @author T. Curran
 */
public class InputFileCacheTest {

  public static final String FILE1_KEY = "file1";
  public static final String FILE2_KEY = "file2";

  @Test
  public void shouldBeAbleToStoreInputFileCache() {
    InputFileCache inputFileCache = new InputFileCache();

    InputFile inputFile1 = new DefaultInputFile("module1", "file.txt");
    inputFileCache.put(FILE1_KEY, inputFile1);

    InputFile inputFile2 = new DefaultInputFile("module2", "file2.txt");
    inputFileCache.put(FILE2_KEY, inputFile2);

    assertEquals(inputFile1, inputFileCache.byKey(FILE1_KEY));

    assertNotEquals(inputFile1, inputFileCache.byKey(FILE2_KEY));

    assertEquals("Bitbucket Plugin InputFile Cache", inputFileCache.toString());
  }

}
