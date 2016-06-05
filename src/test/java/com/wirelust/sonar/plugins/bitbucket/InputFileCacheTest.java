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
