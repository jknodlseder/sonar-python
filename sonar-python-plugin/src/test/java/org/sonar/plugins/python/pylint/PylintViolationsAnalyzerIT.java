/*
 * Sonar Python Plugin
 * Copyright (C) 2011 SonarSource and Waleri Enns
 * dev@sonar.codehaus.org
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
package org.sonar.plugins.python.pylint;

import org.junit.Test;

import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

public class PylintViolationsAnalyzerIT {

  @Test
  public void violationsTest() {
    String pylintrcResource = "/org/sonar/plugins/python/pylint/pylintrc_sample";
    String codeChunksResource = "/org/sonar/plugins/python/code_chunks_2.py";
    String pylintConfigPath = getClass().getResource(pylintrcResource).getPath();
    String codeChunksPathName = getClass().getResource(codeChunksResource).getPath();
    String pylintPath = null;
    
    List<Issue> issues = new PylintViolationsAnalyzer(pylintPath, pylintConfigPath).analyze(codeChunksPathName);
    assertThat(issues.size()).isNotEqualTo(0);
  }

}