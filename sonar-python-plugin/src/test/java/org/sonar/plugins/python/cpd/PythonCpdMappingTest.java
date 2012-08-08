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
package org.sonar.plugins.python.cpd;

import org.sonar.plugins.python.Python;

import org.sonar.plugins.python.cpd.PythonCpdMapping;

import org.sonar.plugins.python.cpd.PythonTokenizer;

import org.junit.Test;
import org.sonar.api.resources.ProjectFileSystem;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class PythonCpdMappingTest {

  @Test
  public void test() {
    Python language = mock(Python.class);
    ProjectFileSystem fs = mock(ProjectFileSystem.class);
    PythonCpdMapping mapping = new PythonCpdMapping(language, fs);
    assertThat(mapping.getLanguage()).isSameAs(language);
    assertThat(mapping.getTokenizer()).isInstanceOf(PythonTokenizer.class);
  }

}