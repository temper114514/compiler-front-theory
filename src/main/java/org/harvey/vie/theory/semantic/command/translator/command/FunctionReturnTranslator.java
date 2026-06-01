package org.harvey.vie.theory.semantic.command.translator.command;

import org.harvey.vie.theory.demo.program.ProgramSemanticTag;
import org.harvey.vie.theory.semantic.command.command.factory.DefaultCommandFactory;
import org.harvey.vie.theory.semantic.command.node.CommandNodeBuilder;
import org.harvey.vie.theory.semantic.command.node.CommandNodeListBuilder;
import org.harvey.vie.theory.semantic.command.node.TerminalNode;
import org.harvey.vie.theory.semantic.command.register.CommandNodeRegister;
import org.harvey.vie.theory.semantic.command.register.NormalCommandNodeRegister;
import org.harvey.vie.theory.semantic.context.ShiftReduceSemanticContext;
import org.harvey.vie.theory.semantic.value.ConstantAttributes;
import org.harvey.vie.theory.semantic.value.ConstantValue;
import org.harvey.vie.theory.syntax.grammar.produce.SimpleGrammarProduction;

/**
 * 翻译 return 语句。
 * <p>
 * 输入：
 * - 当前归约出的 return 语法节点；
 * - 如果带返回值，则 children[1] 对应返回表达式的命令节点。
 * <p>
 * 输出：
 * - 一个新的 {@link NormalCommandNodeRegister}；
 * - 其中包含“返回值求值命令（可选） + return 命令”。
 * <p>
 * 功能：
 * - 对 `return;` 直接生成 return 命令；
 * - 对 `return expr;` 先生成返回值求值命令，再生成 return；
 * - 如果返回值已在语义阶段折叠成常量，则直接装载常量，减少多余命令。
 *
 * @author Temper
 */
public class FunctionReturnTranslator implements CommandTranslator {
    /**
     * 把 return 语句翻译成命令流。
     *
     * @param context 当前语义上下文
     * @param production 当前归约产生式，预期对应 return_stmt
     * @param children 子节点翻译结果，可能是 2 个或 3 个
     * @return 包含返回值求值命令和 return 命令的注册器
     */
    @Override
    public CommandNodeRegister translate(
            ShiftReduceSemanticContext context,
            SimpleGrammarProduction production,
            CommandNodeRegister[] children) {
        if (children.length != 2 && children.length != 3) {
            throw new org.harvey.vie.theory.exception.CompilerException(
                    "illegal statement on return statement production: " + production + ", children=" + children.length
            );
        }
        boolean hasValue = production.containsTag(ProgramSemanticTag.VALUE);
        CommandNodeBuilder builder = new CommandNodeListBuilder();
        if (hasValue && ConstantAttributes.childIsConstant(context, 1)) {
            // 常量返回值可以直接装载，避免额外求值代码。
            ConstantValue value = ConstantAttributes.child(context, 1);
            if (value != null) {
                builder.add(new TerminalNode(context.getCommandFactory().loadConstant(value)));
            }
        } else if (hasValue) {
            children[1].register(builder);
        }
        builder.add(new TerminalNode(context.getCommandFactory().returnCommand()));
        return new NormalCommandNodeRegister(builder.build(), production, children);
    }
}

