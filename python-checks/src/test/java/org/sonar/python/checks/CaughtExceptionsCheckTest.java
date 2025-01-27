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
import org.sonar.python.checks.quickfix.PythonQuickFixVerifier;
import org.sonar.python.checks.utils.PythonCheckVerifier;

public class CaughtExceptionsCheckTest {

  @Test
  public void test() {
    PythonCheckVerifier.verify("src/test/resources/checks/caughtExceptions.py", new CaughtExceptionsCheck());
  }

  @Test
  public void quickFixTest() {
    var check = new CaughtExceptionsCheck();
    var before = "class CustomException:\n" +
      "  ...\n" +
      "\n" +
      "def foo():\n" +
      "  try:\n" +
      "    a = bar()\n" +
      "  except CustomException:\n" +
      "    print(\"Exception\")";

    var after = "class CustomException(Exception):\n" +
      "  ...\n" +
      "\n" +
      "def foo():\n" +
      "  try:\n" +
      "    a = bar()\n" +
      "  except CustomException:\n" +
      "    print(\"Exception\")";

    var expectedMessage = String.format(CaughtExceptionsCheck.QUICK_FIX_MESSAGE_FORMAT, "CustomException");

    PythonQuickFixVerifier.verify(check, before, after);
    PythonQuickFixVerifier.verifyQuickFixMessages(check, before, expectedMessage);
  }

  @Test
  public void exceptionWithEmptyParenthesisQuickFixTest() {
    var check = new CaughtExceptionsCheck();
    var before = "class CustomException():\n" +
      "  ...\n" +
      "\n" +
      "def foo():\n" +
      "  try:\n" +
      "    a = bar()\n" +
      "  except CustomException:\n" +
      "    print(\"Exception\")";

    var after = "class CustomException(Exception):\n" +
      "  ...\n" +
      "\n" +
      "def foo():\n" +
      "  try:\n" +
      "    a = bar()\n" +
      "  except CustomException:\n" +
      "    print(\"Exception\")";

    var expectedMessage = String.format(CaughtExceptionsCheck.QUICK_FIX_MESSAGE_FORMAT, "CustomException");

    PythonQuickFixVerifier.verify(check, before, after);
    PythonQuickFixVerifier.verifyQuickFixMessages(check, before, expectedMessage);
  }

  @Test
  public void exceptionWithNotEmptyParenthesisQuickFixTest() {
    var check = new CaughtExceptionsCheck();
    var before = "class AbcEx:\n" +
      "    ...\n" +
      "\n" +
      "class Ex(AbcEx):\n" +
      "    ...\n" +
      "\n" +
      "def someLogic():\n" +
      "    try:\n" +
      "        a = foo()\n" +
      "    except Ex:\n" +
      "        ...";

    var after = "class AbcEx:\n" +
      "    ...\n" +
      "\n" +
      "class Ex(AbcEx, Exception):\n" +
      "    ...\n" +
      "\n" +
      "def someLogic():\n" +
      "    try:\n" +
      "        a = foo()\n" +
      "    except Ex:\n" +
      "        ...";

    var expectedMessage = String.format(CaughtExceptionsCheck.QUICK_FIX_MESSAGE_FORMAT, "Ex");

    PythonQuickFixVerifier.verify(check, before, after);
    PythonQuickFixVerifier.verifyQuickFixMessages(check, before, expectedMessage);
  }
}
