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

import java.util.List;
import java.util.stream.Collectors;
import org.sonar.check.Rule;
import org.sonar.plugins.python.api.PythonSubscriptionCheck;
import org.sonar.plugins.python.api.SubscriptionContext;
import org.sonar.plugins.python.api.cfg.CfgBranchingBlock;
import org.sonar.plugins.python.api.cfg.ControlFlowGraph;
import org.sonar.plugins.python.api.tree.FunctionDef;
import org.sonar.plugins.python.api.tree.ReturnStatement;
import org.sonar.plugins.python.api.tree.Statement;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.plugins.python.api.tree.Tree.Kind;
import org.sonar.plugins.python.api.tree.WhileStatement;

@Rule(key = "S3801")
public class ConsistentReturnCheck extends PythonSubscriptionCheck {

  public static final String MESSAGE = "Refactor this function to use \"return\" consistently.";

  @Override
  public void initialize(Context context) {
    context.registerSyntaxNodeConsumer(Kind.FUNCDEF, ctx -> {
      FunctionDef functionDef = (FunctionDef) ctx.syntaxNode();
      ControlFlowGraph cfg = ControlFlowGraph.build(functionDef, ctx.pythonFile());
      if (cfg == null || hasExceptOrFinally(cfg)) {
        return;
      }
      List<Tree> endStatements = cfg.end().predecessors().stream()
        .map(block -> parentStatement(block.elements().get(block.elements().size() - 1)))
        .filter(s -> !s.is(Kind.RAISE_STMT, Kind.ASSERT_STMT, Kind.WITH_STMT) && !isWhileTrue(s))
        .collect(Collectors.toList());

      List<Tree> returnsWithValue = endStatements.stream()
        .filter(s -> s.is(Kind.RETURN_STMT) && hasValue((ReturnStatement) s))
        .collect(Collectors.toList());

      if (returnsWithValue.size() != endStatements.size() && !returnsWithValue.isEmpty()) {
        addIssue(ctx, functionDef, endStatements);
      }
    });
  }

  private static boolean hasExceptOrFinally(ControlFlowGraph cfg) {
    return cfg.blocks().stream().anyMatch(block ->
      block instanceof CfgBranchingBlock && ((CfgBranchingBlock) block).branchingTree().is(Kind.EXCEPT_CLAUSE, Kind.FINALLY_CLAUSE));
  }

  private static boolean isWhileTrue(Statement statement) {
    return statement.is(Kind.WHILE_STMT) && Expressions.isTruthy(((WhileStatement) statement).condition());
  }

  private static void addIssue(SubscriptionContext ctx, FunctionDef functionDef, List<Tree> endStatements) {
    PreciseIssue issue = ctx.addIssue(functionDef.name(), MESSAGE);
    for (Tree statement : endStatements) {
      if (statement.is(Kind.RETURN_STMT)) {
        ReturnStatement returnStatement = (ReturnStatement) statement;
        boolean hasValue = hasValue(returnStatement);
        issue.secondary(statement, String.format("Return %s value", hasValue ? "with" : "without"));
      } else if (statement.is(Kind.IF_STMT, Kind.FOR_STMT, Kind.WHILE_STMT)) {
        issue.secondary(statement.firstToken(), "Implicit return without value if the condition is false");
      } else if (statement.is(Kind.MATCH_STMT)) {
        issue.secondary(statement.firstToken(), "Implicit return without value when no case matches");
      } else if (statement.is(Kind.FUNCDEF, Kind.CLASSDEF)) {
        issue.secondary(statement.firstToken(), "Implicit return without value");
      } else {
        issue.secondary(statement, "Implicit return without value");
      }
    }
  }

  private static boolean hasValue(ReturnStatement returnStatement) {
    return !returnStatement.expressions().isEmpty();
  }

  private static Statement parentStatement(Tree tree) {
    while (!(tree instanceof Statement)) {
      tree = tree.parent();
    }
    return (Statement) tree;
  }
}
