/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2023 SonarSource SA
 * mailto:info AT sonarsource DOT com
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
package org.sonar.plugins.python.indexer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonar.plugins.python.api.caching.PythonReadCache;
import org.sonar.plugins.python.api.caching.PythonWriteCache;
import org.sonar.plugins.python.caching.TestReadCache;
import org.sonar.plugins.python.caching.TestWriteCache;
import org.sonar.python.caching.CacheContextImpl;
import org.sonar.python.caching.PythonReadCacheImpl;
import org.sonar.python.caching.PythonWriteCacheImpl;
import org.sonar.python.index.VariableDescriptor;
import org.sonar.python.types.TypeShed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.sonar.plugins.python.TestUtils.createInputFile;
import static org.sonar.plugins.python.caching.Caching.CACHE_VERSION_KEY;
import static org.sonar.plugins.python.caching.Caching.PROJECT_FILES_KEY;
import static org.sonar.plugins.python.caching.Caching.TYPESHED_MODULES_KEY;
import static org.sonar.plugins.python.caching.Caching.fileContentHashCacheKey;
import static org.sonar.plugins.python.caching.Caching.importsMapCacheKey;
import static org.sonar.plugins.python.caching.Caching.projectSymbolTableCacheKey;
import static org.sonar.plugins.python.indexer.FileHashingUtils.inputFileContentHash;
import static org.sonar.python.index.DescriptorsToProtobuf.toProtobufModuleDescriptor;

public class SonarQubePythonIndexerTest {

  private final File baseDir = new File("src/test/resources/org/sonar/plugins/python/indexer").getAbsoluteFile();
  private SensorContextTester context;

  @org.junit.Rule
  public LogTester logTester = new LogTester();

  private InputFile file1;
  private InputFile file2;
  private SonarQubePythonIndexer pythonIndexer;
  private TestReadCache readCache;
  private TestWriteCache writeCache;
  private CacheContextImpl cacheContext;
  private String cacheVersion;

  @Before
  public void init() throws IOException {
    TypeShed.resetBuiltinSymbols();
    context = SensorContextTester.create(baseDir);
    Path workDir = Files.createTempDirectory("workDir");
    context.fileSystem().setWorkDir(workDir);
    context.settings().setProperty("sonar.python.skipUnchanged", true);

    writeCache = new TestWriteCache();
    readCache = new TestReadCache();
    writeCache.bind(readCache);
    cacheVersion = "unknownPluginVersion";
    readCache.put(CACHE_VERSION_KEY, cacheVersion.getBytes(StandardCharsets.UTF_8));
    PythonWriteCache pythonWriteCache = new PythonWriteCacheImpl(writeCache);
    PythonReadCache pythonReadCache = new PythonReadCacheImpl(readCache);
    cacheContext = new CacheContextImpl(true, pythonWriteCache, pythonReadCache);
  }

  @Test
  public void test_single_file_modified() throws IOException, NoSuchAlgorithmException {
    file1 = createInputFile(baseDir, "main.py", InputFile.Status.CHANGED, InputFile.Type.MAIN);
    file2 = createInputFile(baseDir, "mod.py", InputFile.Status.SAME, InputFile.Type.MAIN);

    List<InputFile> inputFiles = new ArrayList<>(Arrays.asList(file1, file2));

    byte[] serializedSymbolTable = toProtobufModuleDescriptor(Set.of(new VariableDescriptor("x", "main.x", null))).toByteArray();
    byte[] outdatedEntry = toProtobufModuleDescriptor(Set.of(new VariableDescriptor("outdated", "mod.outdated", null))).toByteArray();
    readCache.put(importsMapCacheKey("moduleKey:main.py"), importsAsByteArray(List.of("mod")));
    readCache.put(importsMapCacheKey("moduleKey:mod.py"), String.join(";", Collections.emptyList()).getBytes(StandardCharsets.UTF_8));
    readCache.put(projectSymbolTableCacheKey("moduleKey:main.py"), serializedSymbolTable);
    readCache.put(projectSymbolTableCacheKey("moduleKey:mod.py"), outdatedEntry);
    readCache.put(fileContentHashCacheKey("moduleKey:main.py"), inputFileContentHash(file1));
    readCache.put(fileContentHashCacheKey("moduleKey:mod.py"), inputFileContentHash(file2));
    pythonIndexer = new SonarQubePythonIndexer(inputFiles, cacheContext, context);
    pythonIndexer.buildOnce(context);

    assertThat(pythonIndexer.canBePartiallyScannedWithoutParsing(file1)).isFalse();
    assertThat(pythonIndexer.canBePartiallyScannedWithoutParsing(file2)).isTrue();
    assertThat(logTester.logs(LoggerLevel.INFO))
      .contains("Using cached data to retrieve global symbols.")
      .contains("Cached information of global symbols will be used for 1 out of 2 main files. Global symbols will be recomputed for the remaining files.")
      .contains("Fully optimized analysis can be performed for 1 out of 2 files.")
      .contains("1/1 source file has been analyzed");
    assertThat(logTester.logs(LoggerLevel.WARN)).contains("Implementation version of the Python plugin not found. Cached data may not be invalidated properly, " +
      "which may lead to inaccurate analysis results.");
    assertThat(logTester.logs(LoggerLevel.DEBUG)).contains("Cache version still up to date: \"unknownPluginVersion\".");
  }

  @Test
  public void test_modified_dependency() throws IOException, NoSuchAlgorithmException {
    file1 = createInputFile(baseDir, "main.py", InputFile.Status.SAME, InputFile.Type.MAIN);
    file2 = createInputFile(baseDir, "mod.py", InputFile.Status.CHANGED, InputFile.Type.MAIN);

    List<InputFile> inputFiles = new ArrayList<>(Arrays.asList(file1, file2));

    byte[] serializedSymbolTable = toProtobufModuleDescriptor(Set.of(new VariableDescriptor("x", "main.x", null))).toByteArray();
    byte[] outdatedEntry = toProtobufModuleDescriptor(Set.of(new VariableDescriptor("outdated", "mod.outdated", null))).toByteArray();
    readCache.put(importsMapCacheKey("moduleKey:main.py"), importsAsByteArray(List.of("unknown", "mod", "other")));
    readCache.put(importsMapCacheKey("moduleKey:mod.py"), importsAsByteArray(Collections.emptyList()));
    readCache.put(projectSymbolTableCacheKey("moduleKey:main.py") , serializedSymbolTable);
    readCache.put(projectSymbolTableCacheKey("moduleKey:mod.py"), outdatedEntry);
    readCache.put(fileContentHashCacheKey("moduleKey:main.py"), inputFileContentHash(file1));
    readCache.put(fileContentHashCacheKey("moduleKey:mod.py"), inputFileContentHash(file2));
    pythonIndexer = new SonarQubePythonIndexer(inputFiles, cacheContext, context);
    pythonIndexer.buildOnce(context);

    assertThat(pythonIndexer.canBePartiallyScannedWithoutParsing(file1)).isTrue();
    assertThat(pythonIndexer.canBeFullyScannedWithoutParsing(file1)).isFalse();
    assertThat(pythonIndexer.canBePartiallyScannedWithoutParsing(file2)).isFalse();
    assertThat(logTester.logs(LoggerLevel.INFO))
      .contains("Cached information of global symbols will be used for 1 out of 2 main files. Global symbols will be recomputed for the remaining files.")
      .contains("Fully optimized analysis can be performed for 0 out of 2 files.")
      .contains("Partially optimized analysis can be performed for 1 out of 2 files.")
      .contains("1/1 source file has been analyzed");
  }

  @Test
  public void test_deleted_dependency() throws IOException, NoSuchAlgorithmException {
    file1 = createInputFile(baseDir, "main.py", InputFile.Status.SAME, InputFile.Type.MAIN);

    List<InputFile> inputFiles = new ArrayList<>(List.of(file1));

    byte[] serializedSymbolTable = toProtobufModuleDescriptor(Set.of(new VariableDescriptor("x", "main.x", null))).toByteArray();
    byte[] outdatedEntry = toProtobufModuleDescriptor(Set.of(new VariableDescriptor("outdated", "mod.outdated", null))).toByteArray();
    readCache.put(importsMapCacheKey("moduleKey:main.py"), importsAsByteArray(List.of("unknown", "mod", "other")));
    readCache.put(importsMapCacheKey("moduleKey:mod.py"), importsAsByteArray(Collections.emptyList()));
    readCache.put(PROJECT_FILES_KEY, importsAsByteArray(List.of("main", "mod")));
    readCache.put(projectSymbolTableCacheKey("moduleKey:main.py"), serializedSymbolTable);
    readCache.put(projectSymbolTableCacheKey("moduleKey:mod.py"), outdatedEntry);
    readCache.put(fileContentHashCacheKey("moduleKey:main.py"), inputFileContentHash(file1));
    pythonIndexer = new SonarQubePythonIndexer(inputFiles, cacheContext, context);
    pythonIndexer.buildOnce(context);

    assertThat(pythonIndexer.canBePartiallyScannedWithoutParsing(file1)).isTrue();
    assertThat(pythonIndexer.canBeFullyScannedWithoutParsing(file1)).isFalse();
    assertThat(logTester.logs(LoggerLevel.INFO))
      .contains("Cached information of global symbols will be used for 1 out of 1 main files. Global symbols will be recomputed for the remaining files.")
      .contains("Fully optimized analysis can be performed for 0 out of 1 files.")
      .contains("Partially optimized analysis can be performed for 1 out of 1 files.");

    byte[] bytes = writeCache.getData().get(PROJECT_FILES_KEY);
    HashSet<String> retrievedFileList = new HashSet<>(Arrays.asList(new String(bytes, StandardCharsets.UTF_8).split(";")));
    assertThat(retrievedFileList).containsExactlyInAnyOrder("main");
  }

  @Test
  public void test_deleted_unrelated_file() throws IOException, NoSuchAlgorithmException {
    file1 = createInputFile(baseDir, "mod.py", InputFile.Status.SAME, InputFile.Type.MAIN);

    List<InputFile> inputFiles = new ArrayList<>(List.of(file1));

    byte[] serializedSymbolTable = toProtobufModuleDescriptor(Set.of(new VariableDescriptor("x", "main.x", null))).toByteArray();
    byte[] outdatedEntry = toProtobufModuleDescriptor(Set.of(new VariableDescriptor("outdated", "mod.outdated", null))).toByteArray();
    readCache.put(importsMapCacheKey("moduleKey:main.py"), importsAsByteArray(List.of("unknown", "mod", "other")));
    readCache.put(importsMapCacheKey("moduleKey:mod.py"), importsAsByteArray(Collections.emptyList()));
    readCache.put(PROJECT_FILES_KEY, importsAsByteArray(List.of("main", "mod")));
    readCache.put(projectSymbolTableCacheKey("moduleKey:main.py"), serializedSymbolTable);
    readCache.put(projectSymbolTableCacheKey("moduleKey:mod.py"), outdatedEntry);
    readCache.put(fileContentHashCacheKey("moduleKey:mod.py"), inputFileContentHash(file1));
    pythonIndexer = new SonarQubePythonIndexer(inputFiles, cacheContext, context);
    pythonIndexer.buildOnce(context);

    assertThat(pythonIndexer.canBePartiallyScannedWithoutParsing(file1)).isTrue();
    assertThat(logTester.logs(LoggerLevel.INFO))
      .contains("Cached information of global symbols will be used for 1 out of 1 main files. Global symbols will be recomputed for the remaining files.")
      .contains("Fully optimized analysis can be performed for 1 out of 1 files.");

    byte[] bytes = writeCache.getData().get(PROJECT_FILES_KEY);
    HashSet<String> retrievedFileList = new HashSet<>(Arrays.asList(new String(bytes, StandardCharsets.UTF_8).split(";")));
    assertThat(retrievedFileList).containsExactlyInAnyOrder("mod");
  }

  @Test
  public void test_no_file_modified_missing_entry() throws IOException, NoSuchAlgorithmException {
    file1 = createInputFile(baseDir, "main.py", InputFile.Status.SAME, InputFile.Type.MAIN);
    file2 = createInputFile(baseDir, "mod.py", InputFile.Status.SAME, InputFile.Type.MAIN);

    List<InputFile> inputFiles = new ArrayList<>(Arrays.asList(file1, file2));

    pythonIndexer = new SonarQubePythonIndexer(inputFiles, cacheContext, context);
    readCache.put(fileContentHashCacheKey("moduleKey:main.py"), inputFileContentHash(file1));
    readCache.put(fileContentHashCacheKey("moduleKey:mod.py"), inputFileContentHash(file2));
    pythonIndexer.buildOnce(context);

    assertThat(pythonIndexer.canBePartiallyScannedWithoutParsing(file1)).isFalse();
    assertThat(pythonIndexer.canBePartiallyScannedWithoutParsing(file2)).isFalse();
    assertThat(logTester.logs(LoggerLevel.INFO))
      .contains("Cached information of global symbols will be used for 0 out of 2 main files. Global symbols will be recomputed for the remaining files.")
      .contains("Fully optimized analysis can be performed for 0 out of 2 files.")
      .contains("2/2 source files have been analyzed");
  }

  @Test
  public void test_no_file_modified_missing_imports() throws IOException, NoSuchAlgorithmException {
    file1 = createInputFile(baseDir, "main.py", InputFile.Status.SAME, InputFile.Type.MAIN);
    file2 = createInputFile(baseDir, "mod.py", InputFile.Status.SAME, InputFile.Type.MAIN);

    List<InputFile> inputFiles = new ArrayList<>(Arrays.asList(file1, file2));

    byte[] serializedSymbolTable = toProtobufModuleDescriptor(Set.of(new VariableDescriptor("x", "main.x", null))).toByteArray();
    byte[] outdatedEntry = toProtobufModuleDescriptor(Set.of(new VariableDescriptor("outdated", "mod.outdated", null))).toByteArray();
    readCache.put(projectSymbolTableCacheKey("moduleKey:main.py"), serializedSymbolTable);
    readCache.put(projectSymbolTableCacheKey("moduleKey:mod.py"), outdatedEntry);
    readCache.put(fileContentHashCacheKey("moduleKey:main.py"), inputFileContentHash(file1));
    readCache.put(fileContentHashCacheKey("moduleKey:mod.py"), inputFileContentHash(file2));

    pythonIndexer = new SonarQubePythonIndexer(inputFiles, cacheContext, context);
    pythonIndexer.buildOnce(context);

    assertThat(pythonIndexer.canBePartiallyScannedWithoutParsing(file1)).isFalse();
    assertThat(pythonIndexer.canBePartiallyScannedWithoutParsing(file2)).isFalse();
    assertThat(logTester.logs(LoggerLevel.INFO))
      .contains("Cached information of global symbols will be used for 0 out of 2 main files. Global symbols will be recomputed for the remaining files.")
      .contains("Fully optimized analysis can be performed for 0 out of 2 files.")
      .contains("2/2 source files have been analyzed");
  }

  @Test
  public void test_no_file_modified_missing_descriptors() throws IOException, NoSuchAlgorithmException {
    file1 = createInputFile(baseDir, "main.py", InputFile.Status.SAME, InputFile.Type.MAIN);
    file2 = createInputFile(baseDir, "mod.py", InputFile.Status.SAME, InputFile.Type.MAIN);

    List<InputFile> inputFiles = new ArrayList<>(Arrays.asList(file1, file2));

    readCache.put(importsMapCacheKey("moduleKey:main.py"), importsAsByteArray(List.of("mod")));
    readCache.put(importsMapCacheKey("moduleKey:mod.py"), importsAsByteArray(Collections.emptyList()));
    readCache.put(fileContentHashCacheKey("moduleKey:main.py"), inputFileContentHash(file1));
    readCache.put(fileContentHashCacheKey("moduleKey:mod.py"), inputFileContentHash(file2));

    pythonIndexer = new SonarQubePythonIndexer(inputFiles, cacheContext, context);
    pythonIndexer.buildOnce(context);

    assertThat(pythonIndexer.canBePartiallyScannedWithoutParsing(file1)).isFalse();
    assertThat(pythonIndexer.canBePartiallyScannedWithoutParsing(file2)).isFalse();
    assertThat(logTester.logs(LoggerLevel.INFO))
      .contains("Cached information of global symbols will be used for 0 out of 2 main files. Global symbols will be recomputed for the remaining files.")
      .contains("Fully optimized analysis can be performed for 0 out of 2 files.")
      .contains("2/2 source files have been analyzed");
  }

  @Test
  public void test_no_file_modified_invalid_cache_version() throws IOException, NoSuchAlgorithmException {
    file1 = createInputFile(baseDir, "main.py", InputFile.Status.SAME, InputFile.Type.MAIN);
    file2 = createInputFile(baseDir, "mod.py", InputFile.Status.SAME, InputFile.Type.MAIN);

    List<InputFile> inputFiles = new ArrayList<>(Arrays.asList(file1, file2));

    pythonIndexer = new SonarQubePythonIndexer(inputFiles, cacheContext, context);
    readCache.put(CACHE_VERSION_KEY, "outdatedVersion".getBytes(StandardCharsets.UTF_8));

    byte[] serializedSymbolTable = toProtobufModuleDescriptor(Set.of(new VariableDescriptor("x", "main.x", null))).toByteArray();
    byte[] outdatedEntry = toProtobufModuleDescriptor(Set.of(new VariableDescriptor("outdated", "mod.outdated", null))).toByteArray();
    readCache.put(importsMapCacheKey("moduleKey:main.py"), importsAsByteArray(List.of("mod")));
    readCache.put(importsMapCacheKey("moduleKey:mod.py"), String.join(";", Collections.emptyList()).getBytes(StandardCharsets.UTF_8));
    readCache.put(projectSymbolTableCacheKey("moduleKey:main.py"), serializedSymbolTable);
    readCache.put(projectSymbolTableCacheKey("moduleKey:mod.py"), outdatedEntry);
    readCache.put(fileContentHashCacheKey("moduleKey:main.py"), inputFileContentHash(file1));
    readCache.put(fileContentHashCacheKey("moduleKey:mod.py"), inputFileContentHash(file2));

    pythonIndexer.buildOnce(context);

    assertThat(pythonIndexer.canBePartiallyScannedWithoutParsing(file1)).isFalse();
    assertThat(pythonIndexer.canBePartiallyScannedWithoutParsing(file2)).isFalse();
    assertThat(logTester.logs(LoggerLevel.INFO))
      .contains("The cache version has changed since the previous analysis, cached data will not be used during this analysis. " +
        "Retrieved: \"outdatedVersion\". Current version: \"unknownPluginVersion\".")
      .contains("2/2 source files have been analyzed");
  }


  @Test
  public void test_no_file_modified_invalid_cache_version_due_to_changed_python_version() throws IOException, NoSuchAlgorithmException {
    file1 = createInputFile(baseDir, "main.py", InputFile.Status.SAME, InputFile.Type.MAIN);
    file2 = createInputFile(baseDir, "mod.py", InputFile.Status.SAME, InputFile.Type.MAIN);

    List<InputFile> inputFiles = new ArrayList<>(Arrays.asList(file1, file2));

    context.settings().setProperty("sonar.python.version", "3.11");
    pythonIndexer = new SonarQubePythonIndexer(inputFiles, cacheContext, context);

    byte[] serializedSymbolTable = toProtobufModuleDescriptor(Set.of(new VariableDescriptor("x", "main.x", null))).toByteArray();
    byte[] outdatedEntry = toProtobufModuleDescriptor(Set.of(new VariableDescriptor("outdated", "mod.outdated", null))).toByteArray();
    readCache.put(importsMapCacheKey("moduleKey:main.py"), importsAsByteArray(List.of("mod")));
    readCache.put(importsMapCacheKey("moduleKey:mod.py"), String.join(";", Collections.emptyList()).getBytes(StandardCharsets.UTF_8));
    readCache.put(projectSymbolTableCacheKey("moduleKey:main.py"), serializedSymbolTable);
    readCache.put(projectSymbolTableCacheKey("moduleKey:mod.py"), outdatedEntry);
    readCache.put(fileContentHashCacheKey("moduleKey:main.py"), inputFileContentHash(file1));
    readCache.put(fileContentHashCacheKey("moduleKey:mod.py"), inputFileContentHash(file2));

    pythonIndexer.buildOnce(context);

    assertThat(pythonIndexer.canBePartiallyScannedWithoutParsing(file1)).isFalse();
    assertThat(pythonIndexer.canBePartiallyScannedWithoutParsing(file2)).isFalse();
    assertThat(logTester.logs(LoggerLevel.INFO))
      .contains("The cache version has changed since the previous analysis, cached data will not be used during this analysis. " +
        "Retrieved: \"unknownPluginVersion\". Current version: \"unknownPluginVersion;3.11\".")
      .contains("2/2 source files have been analyzed");
  }

  @Test
  public void test_test_files_not_using_cache() throws IOException, NoSuchAlgorithmException {
    file1 = createInputFile(baseDir, "main.py", InputFile.Status.SAME, InputFile.Type.TEST);
    file2 = createInputFile(baseDir, "mod.py", InputFile.Status.CHANGED, InputFile.Type.TEST);
    readCache.put(fileContentHashCacheKey("moduleKey:main.py"), inputFileContentHash(file1));

    List<InputFile> inputFiles = new ArrayList<>(Arrays.asList(file1, file2));

    pythonIndexer = new SonarQubePythonIndexer(inputFiles, cacheContext, context);
    pythonIndexer.buildOnce(context);

    assertThat(pythonIndexer.canBePartiallyScannedWithoutParsing(file1)).isTrue();
    assertThat(pythonIndexer.canBePartiallyScannedWithoutParsing(file2)).isFalse();
    assertThat(logTester.logs(LoggerLevel.INFO))
      .contains("Fully optimized analysis can be performed for 1 out of 2 files.")
      .contains("Partially optimized analysis can be performed for 1 out of 2 files.");
  }

  @Test
  public void test_pr_analysis_disabled() {
    file1 = createInputFile(baseDir, "main.py", InputFile.Status.CHANGED, InputFile.Type.MAIN);
    file2 = createInputFile(baseDir, "mod.py", InputFile.Status.SAME, InputFile.Type.MAIN);

    List<InputFile> inputFiles = new ArrayList<>(Arrays.asList(file1, file2));

    context.settings().setProperty("sonar.python.skipUnchanged", false);
    pythonIndexer = new SonarQubePythonIndexer(inputFiles, cacheContext, context);
    pythonIndexer.buildOnce(context);

    assertThat(pythonIndexer.canBePartiallyScannedWithoutParsing(file1)).isFalse();
    assertThat(pythonIndexer.canBePartiallyScannedWithoutParsing(file2)).isFalse();
    assertThat(logTester.logs(LoggerLevel.INFO)).doesNotContain("Using cached data to retrieve global symbols.");
  }

  @Test
  public void test_pr_analysis_enabled() {
    file1 = createInputFile(baseDir, "main.py", InputFile.Status.CHANGED, InputFile.Type.MAIN);
    file2 = createInputFile(baseDir, "mod.py", InputFile.Status.SAME, InputFile.Type.MAIN);

    List<InputFile> inputFiles = new ArrayList<>(Arrays.asList(file1, file2));

    SensorContext mockContext = spy(context);
    when(mockContext.canSkipUnchangedFiles()).thenReturn(true);
    context.settings().setProperty("sonar.python.skipUnchanged", false);
    pythonIndexer = new SonarQubePythonIndexer(inputFiles, cacheContext, context);
    pythonIndexer.buildOnce(mockContext);

    assertThat(pythonIndexer.canBePartiallyScannedWithoutParsing(file1)).isFalse();
    assertThat(pythonIndexer.canBePartiallyScannedWithoutParsing(file2)).isFalse();
    assertThat(logTester.logs(LoggerLevel.INFO)).contains("Using cached data to retrieve global symbols.");
  }

  @Test
  public void test_disabled_cache() {
    file1 = createInputFile(baseDir, "main.py", InputFile.Status.CHANGED, InputFile.Type.MAIN);
    file2 = createInputFile(baseDir, "mod.py", InputFile.Status.SAME, InputFile.Type.MAIN);

    List<InputFile> inputFiles = new ArrayList<>(Arrays.asList(file1, file2));

    cacheContext = new CacheContextImpl(false, new PythonWriteCacheImpl(new TestWriteCache()), new PythonReadCacheImpl(new TestReadCache()));
    pythonIndexer = new SonarQubePythonIndexer(inputFiles, cacheContext, context);
    pythonIndexer.buildOnce(context);

    assertThat(pythonIndexer.canBePartiallyScannedWithoutParsing(file1)).isFalse();
    assertThat(pythonIndexer.canBePartiallyScannedWithoutParsing(file2)).isFalse();
    assertThat(logTester.logs(LoggerLevel.INFO)).doesNotContain("Using cached data to retrieve global symbols.");
  }

  @Test
  public void test_typeshed_modules_cached() {
    file1 = createInputFile(baseDir, "uses_typeshed.py", InputFile.Status.CHANGED, InputFile.Type.MAIN);

    List<InputFile> inputFiles = new ArrayList<>(List.of(file1));

    pythonIndexer = new SonarQubePythonIndexer(inputFiles, cacheContext, context);
    pythonIndexer.buildOnce(context);

    assertThat(pythonIndexer.canBePartiallyScannedWithoutParsing(file1)).isFalse();

    byte[] bytes = writeCache.getData().get(TYPESHED_MODULES_KEY);
    Set<String> resolvedTypeshedModules = new HashSet<>(Arrays.asList(new String(bytes, StandardCharsets.UTF_8).split(";")));
    assertThat(resolvedTypeshedModules).containsExactlyInAnyOrder("math");
  }

  @Test
  public void test_typeshed_modules_not_cached_if_empty() {
    file1 = createInputFile(baseDir, "main.py", InputFile.Status.CHANGED, InputFile.Type.MAIN);

    List<InputFile> inputFiles = new ArrayList<>(List.of(file1));

    pythonIndexer = new SonarQubePythonIndexer(inputFiles, cacheContext, context);
    pythonIndexer.buildOnce(context);

    assertThat(pythonIndexer.canBePartiallyScannedWithoutParsing(file1)).isFalse();
    assertThat(writeCache.getData()).doesNotContainKey(TYPESHED_MODULES_KEY);
  }

  @Test
  public void test_regular_scan_when_scan_without_parsing_fails() {
    List<InputFile> files = List.of(createInputFile(baseDir, "main.py", InputFile.Status.SAME, InputFile.Type.MAIN));
    PythonIndexer.GlobalSymbolsScanner globalSymbolsScanner = spy(
      new SonarQubePythonIndexer(files, cacheContext, context).new GlobalSymbolsScanner(context)
    );
    when(globalSymbolsScanner.canBeScannedWithoutParsing(any())).thenReturn(true);
    globalSymbolsScanner.execute(files, context);

    assertThat(logTester.logs(LoggerLevel.INFO)).contains("1/1 source file has been analyzed");
  }

  @Test
  public void test_no_data_in_cache_for_parse_error() {
    file1 = createInputFile(baseDir, "parse_error.py", InputFile.Status.ADDED, InputFile.Type.MAIN);

    List<InputFile> inputFiles = new ArrayList<>(List.of(file1));

    pythonIndexer = new SonarQubePythonIndexer(inputFiles, cacheContext, context);
    pythonIndexer.buildOnce(context);
    assertThat(writeCache.getData().containsKey(projectSymbolTableCacheKey("moduleKey:parse_error.py"))).isFalse();
  }

  @Test
  public void test_file_content_hash_changed() throws IOException, NoSuchAlgorithmException {
    file1 = createInputFile(baseDir, "mod.py", InputFile.Status.SAME, InputFile.Type.MAIN);
    file2 = createInputFile(baseDir, "main.py", InputFile.Status.SAME, InputFile.Type.MAIN);

    List<InputFile> inputFiles = new ArrayList<>(List.of(file1));

    byte[] outdatedEntry = toProtobufModuleDescriptor(Set.of(new VariableDescriptor("outdated", "mod.outdated", null))).toByteArray();
    readCache.put(importsMapCacheKey("moduleKey:mod.py"), String.join(";", Collections.emptyList()).getBytes(StandardCharsets.UTF_8));
    readCache.put(projectSymbolTableCacheKey("moduleKey:mod.py"), outdatedEntry);
    readCache.put(fileContentHashCacheKey("moduleKey:mod.py"), inputFileContentHash(file2));
    pythonIndexer = new SonarQubePythonIndexer(inputFiles, cacheContext, context);
    pythonIndexer.buildOnce(context);

    assertThat(pythonIndexer.canBePartiallyScannedWithoutParsing(file1)).isFalse();
  }

  @Test
  public void hash_exception_when_trying_to_save_hash() {
    file1 = createInputFile(baseDir, "main.py", InputFile.Status.CHANGED, InputFile.Type.MAIN);

    List<InputFile> inputFiles = new ArrayList<>(List.of(file1));

    context.settings().setProperty("sonar.python.skipUnchanged", false);
    pythonIndexer = new SonarQubePythonIndexer(inputFiles, cacheContext, context);

    try (MockedStatic<FileHashingUtils> FileHashingUtilsStaticMock = Mockito.mockStatic(FileHashingUtils.class)) {
      FileHashingUtilsStaticMock.when(() -> FileHashingUtils.inputFileContentHash(any())).thenThrow(new IOException("BOOM!"));
      pythonIndexer.buildOnce(context);
      assertThat(logTester.logs(LoggerLevel.DEBUG)).contains("Failed to compute content hash for file moduleKey:main.py");
    }
  }

  @Test
  public void hash_exception_when_trying_to_compare_hash() throws IOException, NoSuchAlgorithmException {
    file1 = createInputFile(baseDir, "mod.py", InputFile.Status.SAME, InputFile.Type.MAIN);

    List<InputFile> inputFiles = new ArrayList<>(List.of(file1));
    byte[] outdatedEntry = toProtobufModuleDescriptor(Set.of(new VariableDescriptor("outdated", "mod.outdated", null))).toByteArray();
    readCache.put(importsMapCacheKey("moduleKey:mod.py"), String.join(";", Collections.emptyList()).getBytes(StandardCharsets.UTF_8));
    readCache.put(projectSymbolTableCacheKey("moduleKey:mod.py"), outdatedEntry);
    readCache.put(fileContentHashCacheKey("moduleKey:mod.py"), inputFileContentHash(file1));

    pythonIndexer = new SonarQubePythonIndexer(inputFiles, cacheContext, context);

    try (MockedStatic<FileHashingUtils> FileHashingUtilsStaticMock = Mockito.mockStatic(FileHashingUtils.class)) {
      FileHashingUtilsStaticMock.when(() -> FileHashingUtils.inputFileContentHash(any())).thenThrow(new IOException("BOOM!"));
      pythonIndexer.buildOnce(context);
      assertThat(logTester.logs(LoggerLevel.DEBUG)).contains("Failed to compute content hash for file moduleKey:mod.py");
    }
    assertThat(pythonIndexer.canBePartiallyScannedWithoutParsing(file1)).isFalse();
  }

  private byte[] importsAsByteArray(List<String> mod) {
    return String.join(";", mod).getBytes(StandardCharsets.UTF_8);
  }
}
