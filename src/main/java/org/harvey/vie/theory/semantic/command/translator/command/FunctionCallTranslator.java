package org.harvey.vie.theory.semantic.command.translator.command;

import org.harvey.vie.theory.exception.CompilerException;
import org.harvey.vie.theory.semantic.command.node.CommandNodeBuilder;
import org.harvey.vie.theory.semantic.command.node.CommandNodeListBuilder;
import org.harvey.vie.theory.semantic.command.node.TerminalNode;
import org.harvey.vie.theory.semantic.command.register.CommandNodeRegister;
import org.harvey.vie.theory.semantic.command.register.NormalCommandNodeRegister;
import org.harvey.vie.theory.semantic.context.ShiftReduceSemanticContext;
import org.harvey.vie.theory.semantic.function.FunctionRecord;
import org.harvey.vie.theory.semantic.tree.node.HeadNode;
import org.harvey.vie.theory.semantic.tree.node.ShiftReduceSyntaxTreeNode;
import org.harvey.vie.theory.syntax.grammar.produce.SimpleGrammarProduction;

/**
 * 翻译函数调用表达式。
 * <p>
 * 输入：
 * - 当前归约出的函数调用语法节点，例如 {@code f(a, b)}；
 * - children[2] 中已经准备好的实参命令节点。
 * <p>
 * 输出：
 * - 一个新的 {@link NormalCommandNodeRegister}；
 * - 其中包含“实参求值命令 + call 命令”的顺序命令流。
 * <p>
 * 功能：
 * - 恢复被调用函数的函数记录；
 * - 先按源码顺序生成实参求值命令；
 * - 再追加函数调用命令，保证参数顺序和源码一致。
 */
public class FunctionCallTranslator implements CommandTranslator {
    /**
     * 把函数调用语法节点翻译成命令流。
     *
     * @param context 当前移进-归约语义上下文，里面包含函数表、语法树上下文和命令工厂
     * @param production 当前归约产生式，预期对应 call_expr
     * @param children 子节点翻译结果，children[2] 对应 arg_list 的命令
     * @return 包含实参求值命令和 call 命令的命令节点注册器
     */
    @Override
    public CommandNodeRegister translate(
            ShiftReduceSemanticContext context,
            SimpleGrammarProduction production,
            CommandNodeRegister[] children) {
        CommandNodeBuilder builder = new CommandNodeListBuilder();
        FunctionRecord record = function(context);
        children[2].register(builder);
        builder.add(new TerminalNode(context.getCommandFactory().callFunction(record)));
        return new NormalCommandNodeRegister(builder.build(), production, children);
    }

    /**
     * 根据当前归约节点上的函数名，恢复出函数表中的函数记录。
     *
     * 输入：
     * - 当前归约好的 call_expr 头节点。
     *
     * 输出：
     * - 对应的 {@link FunctionRecord}。
     *
     * 功能：
     * - 从函数调用语法节点里提取函数名；
     * - 在函数表中找到被调函数的记录，供 call 命令生成使用。
     */
    private FunctionRecord function(ShiftReduceSemanticContext context) {
        if (context.getTreeContext().isEmpty() || !context.getTreeContext().peek().isHead()) {
            throw new CompilerException("current reduced head is absent for function call.");
        }
        HeadNode head = context.getTreeContext().peek().toHead();
        ShiftReduceSyntaxTreeNode tokenNode = head.get(0);
        if (!tokenNode.isToken()) {
            throw new CompilerException("function call name is absent.");
        }
        FunctionRecord record = context.getFunction(tokenNode.toToken().getSource());
        if (record == null) {
            throw new CompilerException("function is not declared in current visible scope.");
        }
        return record;
    }
}
