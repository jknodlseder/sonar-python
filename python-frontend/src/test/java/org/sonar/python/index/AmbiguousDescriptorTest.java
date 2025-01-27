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
package org.sonar.python.index;

import java.util.Collections;
import java.util.Set;
import org.junit.Test;
import org.sonar.plugins.python.api.symbols.AmbiguousSymbol;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.python.semantic.SymbolImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sonar.python.PythonTestUtils.lastSymbolFromDef;
import static org.sonar.python.index.ClassDescriptorTest.lastClassDescriptor;
import static org.sonar.python.index.DescriptorToProtobufTestUtils.assertDescriptorToProtobuf;
import static org.sonar.python.index.DescriptorsToProtobuf.fromProtobuf;
import static org.sonar.python.index.DescriptorUtils.descriptor;
import static org.sonar.python.index.DescriptorsToProtobuf.toProtobufModuleDescriptor;
import static org.sonar.python.index.FunctionDescriptorTest.lastFunctionDescriptor;

public class AmbiguousDescriptorTest {

  @Test
  public void test_basic_ambiguous_descriptor() {
    AmbiguousDescriptor ambiguousDescriptor = lastAmbiguousDescriptor(
      "class A: ...",
      "class A: ...");
    assertThat(ambiguousDescriptor.alternatives()).extracting(Descriptor::name).containsExactly("A", "A");
    assertThat(ambiguousDescriptor.alternatives()).extracting(Descriptor::fullyQualifiedName).containsExactly("package.mod.A", "package.mod.A");
    assertDescriptorToProtobuf(ambiguousDescriptor);
  }

  @Test
  public void test_ambiguous_descriptor_different_kinds() {
    AmbiguousDescriptor ambiguousDescriptor = lastAmbiguousDescriptor(
      "class A: ...",
      "A: int = 42",
      "def A(): ...");
    assertThat(ambiguousDescriptor.alternatives()).extracting(Descriptor::name).containsExactly("A", "A", "A");
    assertThat(ambiguousDescriptor.alternatives()).extracting(Descriptor::fullyQualifiedName).containsExactly("package.mod.A", "package.mod.A", "package.mod.A");
    assertDescriptorToProtobuf(ambiguousDescriptor);
  }

  @Test
  public void test_flattened_ambiguous_descriptor() {
    AmbiguousDescriptor firstAmbiguousSymbol = lastAmbiguousDescriptor(
      "class A: ...",
      "class A: ...");
    ClassDescriptor classDescriptor = lastClassDescriptor("class A: ...");
    AmbiguousDescriptor ambiguousDescriptor = AmbiguousDescriptor.create(firstAmbiguousSymbol, classDescriptor);
    assertThat(ambiguousDescriptor.alternatives()).extracting(Descriptor::name).containsExactly("A", "A", "A");
    assertThat(ambiguousDescriptor.alternatives()).extracting(Descriptor::fullyQualifiedName).containsExactly("package.mod.A", "package.mod.A", "package.mod.A");
    assertDescriptorToProtobuf(ambiguousDescriptor);
  }

  @Test
  public void test_single_descriptor_illegal_argument() {
    FunctionDescriptor functionDescriptor = lastFunctionDescriptor("def func(): ...");
    assertThatThrownBy(() -> AmbiguousDescriptor.create(functionDescriptor)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void test_nested_ambiguous_descriptors_illegal_argument() {
    AmbiguousDescriptor ambiguousDescriptor = new AmbiguousDescriptor("foo", "foo", Collections.emptySet());
      Set<Descriptor> descriptors = Set.of(ambiguousDescriptor);
    assertThatThrownBy(() -> new AmbiguousDescriptor("foo", "foo", descriptors)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void test_different_names_illegal_argument() {
    FunctionDescriptor functionDescriptorA = lastFunctionDescriptor("def a(): ...");
    FunctionDescriptor functionDescriptorB = lastFunctionDescriptor("def b(): ...");
    assertThatThrownBy(() -> AmbiguousDescriptor.create(functionDescriptorA, functionDescriptorB)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void ambiguous_descriptor_creation_different_name_same_fqn() {
    SymbolImpl foo = new SymbolImpl("foo", "mod.bar");
    SymbolImpl bar = new SymbolImpl("bar", "mod.bar");
    Descriptor fooDesc = descriptor(foo);
    Descriptor barDesc = descriptor(bar);
    assertThatThrownBy(() -> AmbiguousDescriptor.create(fooDesc, barDesc)).isInstanceOf(IllegalArgumentException.class);
  }

  private AmbiguousDescriptor lastAmbiguousDescriptor(String... code) {
    Symbol ambiguousSymbol = lastSymbolFromDef(code);
    if (!(ambiguousSymbol instanceof AmbiguousSymbol)) {
      throw new AssertionError("Symbol is not ambiguous.");
    }
    AmbiguousDescriptor ambiguousDescriptor = (AmbiguousDescriptor) descriptor(ambiguousSymbol);
    assertThat(ambiguousDescriptor.name()).isEqualTo(ambiguousSymbol.name());
    assertThat(ambiguousDescriptor.fullyQualifiedName()).isEqualTo(ambiguousSymbol.fullyQualifiedName());
    return ambiguousDescriptor;
  }
}
