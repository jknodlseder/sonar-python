/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2020 SonarSource SA
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
package org.sonar.python.checks;

import java.util.Arrays;
import org.junit.Test;
import org.sonar.python.checks.utils.PythonCheckVerifier;

public class UndefinedNameAllPropertyCheckTest {

  @Test
  public void test() {
    PythonCheckVerifier.verify(Arrays.asList("src/test/resources/checks/undefinedNameAllProperty/undefinedNameAllProperty.py",
      "src/test/resources/checks/undefinedNameAllProperty/imported_with_wildcard.py"), new UndefinedNameAllPropertyCheck());
  }

  @Test
  public void test_unresolved_wildcard_import() {
    PythonCheckVerifier.verifyNoIssue("src/test/resources/checks/undefinedNameAllProperty/with_unresolved_wildcard_import.py", new UndefinedNameAllPropertyCheck());
  }

  @Test
  public void test_imports_manipulated_all() {
    PythonCheckVerifier.verifyNoIssue(Arrays.asList("src/test/resources/checks/undefinedNameAllProperty/importing_manipulated_all.py",
     "src/test/resources/checks/undefinedNameAllProperty/manipulates_globals.py"), new UndefinedNameAllPropertyCheck());
  }

  @Test
  public void test_init_exports_module() {
    PythonCheckVerifier.verify("src/test/resources/checks/undefinedNameAllProperty/__init__.py", new UndefinedNameAllPropertyCheck());
  }

  @Test
  public void test_uses_getattr() {
    PythonCheckVerifier.verifyNoIssue("src/test/resources/checks/undefinedNameAllProperty/defines_getattr.py", new UndefinedNameAllPropertyCheck());
  }

  @Test
  public void test_manipulates_globals() {
    PythonCheckVerifier.verifyNoIssue("src/test/resources/checks/undefinedNameAllProperty/manipulates_globals.py", new UndefinedNameAllPropertyCheck());
  }

  @Test
  public void test_manipulates_sys_modules() {
    PythonCheckVerifier.verifyNoIssue("src/test/resources/checks/undefinedNameAllProperty/manipulates_sys_modules.py", new UndefinedNameAllPropertyCheck());
  }

}