package org.harvey.vie.theory.semantic.command.translator.command;

import org.harvey.vie.theory.semantic.array.ArrayCreationDimensions;
import org.harvey.vie.theory.semantic.command.command.factory.CommandDataType;
import org.harvey.vie.theory.semantic.command.node.CommandNodeBuilder;
import org.harvey.vie.theory.semantic.command.node.CommandNodeListBuilder;
import org.harvey.vie.theory.semantic.command.node.TerminalNode;
import org.harvey.vie.theory.semantic.command.register.CommandNodeRegister;
import org.harvey.vie.theory.semantic.command.register.NormalCommandNodeRegister;
import org.harvey.vie.theory.semantic.context.ShiftReduceSemanticContext;
import org.harvey.vie.theory.semantic.error.SemanticDiagnostics;
import org.harvey.vie.theory.semantic.tree.node.HeadNode;
import org.harvey.vie.theory.semantic.type.SemanticType;
import org.harvey.vie.theory.semantic.type.TypeAttributes;
import org.harvey.vie.theory.syntax.grammar.produce.SimpleGrammarProduction;

/**
 * 翻译数组创建表达式。
 * <p>
 * 输入：
 * - 当前归约出的 new_array_expr，例如 {@code new Student[2]} 或 {@code new Student[1][2]}；
 * - children[2] 对应各个显式维度长度表达式的命令。
 * <p>
 * 输出：
 * - 一个新的 {@link NormalCommandNodeRegister}；
 * - 其中包含“显式维度求值命令 + new_array 命令”。
 * <p>
 * 功能：
 * - 检查数组元素类型是否合法；
 * - 汇总数组总维度和显式维度数量；
 * - 先求值每个显式维度长度，再发出数组分配命令。
 */
public class NewArrayTranslator implements CommandTranslator {
    /**
     * 把数组创建表达式翻译成数组分配命令流。
     *
     * @param context 当前语义上下文
     * @param production 当前归约产生式，预期对应 new_array_expr
     * @param children 子节点翻译结果，children[2] 对应维度表达式
     * @return 包含维度求值命令和 new_array 命令的注册器
     */
    @Override
    public CommandNodeRegister translate(
            ShiftReduceSemanticContext context,
            SimpleGrammarProduction production,
            CommandNodeRegister[] children) {
        SemanticType type = TypeAttributes.childType(context, 1);
        SemanticDiagnostics.requireNotVoid(
                context,
                type,
                TypeAttributes.childAnchor(context, 1),
                "void cannot be used as array element type."
        );
        HeadNode head = context.getTreeContext().peek().toHead();
        ArrayCreationDimensions.Summary summary = ArrayCreationDimensions.summarizeAndValidate(context, head.get(2));
        CommandNodeBuilder builder = new CommandNodeListBuilder();
        // 先求出每个显式维度的长度表达式，再生成数组分配指令。
        children[2].register(builder);
        builder.add(new TerminalNode(context.getCommandFactory().newArray(
                CommandDataType.forStorage(type),
                summary.getTotalDimensions(),
                summary.getSpecifiedDimensions()
        )));
        return new NormalCommandNodeRegister(builder.build(), production, children);
    }
}
