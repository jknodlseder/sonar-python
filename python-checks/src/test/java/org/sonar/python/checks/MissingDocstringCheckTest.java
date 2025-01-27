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
package org.sonar.python.checks;

import org.junit.Test;
import org.sonar.api.internal.apachecommons.lang.StringUtils;
import org.sonar.plugins.python.api.PythonCheck;
import org.sonar.python.checks.quickfix.PythonQuickFixVerifier;
import org.sonar.python.checks.utils.PythonCheckVerifier;

public class MissingDocstringCheckTest {

  final PythonCheck check = new MissingDocstringCheck();

  @Test
  public void test() {
    PythonCheckVerifier.verify("src/test/resources/checks/missing_docstring/missingDocstring.py", check);
  }

  @Test
  public void testMissingDocStringAtModuleLevel() {
    PythonCheckVerifier.verify("src/test/resources/checks/missing_docstring/missingDocstringAtModuleLevel.py", check);
  }

  @Test
  public void testEmptyModule() {
    PythonCheckVerifier.verify("src/test/resources/checks/missing_docstring/emptyModule.py", check);
  }

  @Test
  public void __init__without_docstring() {
    PythonCheckVerifier.verifyNoIssue("src/test/resources/checks/missing_docstring/empty_init/__init__.py", check);
    PythonCheckVerifier.verify("src/test/resources/checks/missing_docstring/nonempty_init/__init__.py", check);
  }

  @Test
  public void quick_fix_add_docstring_to_module() {
    String codeWithIssue = "";
    String fixedCode ="\"\"\" doc \"\"\"";
    PythonQuickFixVerifier.verify(check, codeWithIssue, fixedCode);
  }

  @Test
  public void quick_fix_add_docstring_for_class() {
    String codeWithIssue = module("class MyClass:",
      "  pass");
    String fixedCode = module("class MyClass:",
      "  \"\"\" doc \"\"\"",
      "  pass");
    PythonQuickFixVerifier.verify(check, codeWithIssue, fixedCode);
    PythonQuickFixVerifier.verifyQuickFixMessages(check, codeWithIssue, "Add docstring");
  }

  @Test
  public void quick_fix_add_docstring_for_class_with_new_line() {
    String codeWithIssue = module("class MyClass:",
      "",
      "  pass");
    String fixedCode = module("class MyClass:",
      "  \"\"\" doc \"\"\"",
      "",
      "  pass");
    PythonQuickFixVerifier.verify(check, codeWithIssue, fixedCode);
  }

  @Test
  public void quick_fix_add_docstring_for_class_with_method() {
    String codeWithIssue = module("class MyClass:",
      "  def my_method():",
      "    \"\"\"This is a test method\"\"\"",
      "    pass");
    String fixedCode = module("class MyClass:",
      "  \"\"\" doc \"\"\"",
      "  def my_method():",
      "    \"\"\"This is a test method\"\"\"",
      "    pass");
    PythonQuickFixVerifier.verify(check, codeWithIssue, fixedCode);
  }

  @Test
  public void quick_fix_add_docstring_for_function() {
    String codeWithIssue = module("def my_function():",
      "  pass");
    String fixedCode = module("def my_function():",
      "  \"\"\" doc \"\"\"",
      "  pass");
    PythonQuickFixVerifier.verify(check, codeWithIssue, fixedCode);
  }

  private static String module(String... lines) {
    return "\"\"\"module docstring\"\"\"\n\n" + StringUtils.join(lines, '\n');
  }
}
