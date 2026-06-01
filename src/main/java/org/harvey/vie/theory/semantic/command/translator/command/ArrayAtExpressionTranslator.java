package org.harvey.vie.theory.semantic.command.translator.command;

import lombok.AllArgsConstructor;
import org.harvey.vie.theory.semantic.command.command.factory.CommandDataType;
import org.harvey.vie.theory.semantic.command.node.CommandNodeBuilder;
import org.harvey.vie.theory.semantic.command.node.CommandNodeListBuilder;
import org.harvey.vie.theory.semantic.command.node.TerminalNode;
import org.harvey.vie.theory.semantic.command.register.CommandNodeRegister;
import org.harvey.vie.theory.semantic.command.register.NormalCommandNodeRegister;
import org.harvey.vie.theory.semantic.context.ShiftReduceSemanticContext;
import org.harvey.vie.theory.semantic.error.SemanticDiagnostics;
import org.harvey.vie.theory.semantic.type.SemanticType;
import org.harvey.vie.theory.semantic.type.TypeAttributes;
import org.harvey.vie.theory.syntax.grammar.produce.SimpleGrammarProduction;

/**
 * 数组下标访问翻译器。
 * <p>
 * 输入：
 * - 当前归约出的数组访问语法节点，例如 {@code arr[index]}；
 * - children[0] 对应数组对象的命令；
 * - children[2] 对应下标表达式的命令。
 * <p>
 * 输出：
 * - 一个新的 {@link NormalCommandNodeRegister}；
 * - 其中包含“数组对象求值命令 + 下标求值命令 + 数组元素偏移命令”。
 * <p>
 * 功能：
 * - 检查左操作数是否为数组；
 * - 检查下标类型是否为 `int32`；
 * - 把 `arr[index]` 翻译成数组元素的引用位置，而不是直接值。
 * 这样后续既可以读值，也可以作为左值继续写回。
 *
 * @author <a href="mailto:harvey.blocks@outlook.com">Harvey Blocks</a>
 * @version 1.0
 * @date 2026-04-20 08:29
 */
@AllArgsConstructor
public class ArrayAtExpressionTranslator implements CommandTranslator {

    /**
     * 把数组下标访问语法节点翻译成偏移访问命令流。
     *
     * @param context 当前语义上下文
     * @param production 当前归约产生式，预期对应 loc ::= loc [ bool ]
     * @param children 子节点翻译结果
     * @return 包含数组访问命令的注册器
     */
    @Override
    public CommandNodeRegister translate(
            ShiftReduceSemanticContext context, SimpleGrammarProduction production, CommandNodeRegister[] children) {
        CommandNodeBuilder thisBuilder = new CommandNodeListBuilder();

        // 先翻译数组对象和下标表达式，保证生成偏移访问命令前，两侧值已经在命令流中准备好。
        children[0].register(thisBuilder);
        children[2].register(thisBuilder);

        // 读取归约子节点上的类型属性：左侧必须是数组，下标必须是 int32。
        SemanticType baseType = TypeAttributes.childType(context, 0);
        SemanticType indexType = TypeAttributes.childType(context, 2);
        if (baseType == null) {
            SemanticDiagnostics.reject(
                    context,
                    TypeAttributes.childAnchor(context, 0),
                    "array access requires a typed left operand."
            );
        }
        if (indexType == null) {
            SemanticDiagnostics.reject(
                    context,
                    TypeAttributes.childAnchor(context, 2),
                    "array index expression requires a type."
            );
        }
        if (!baseType.isArray()) {
            SemanticDiagnostics.reject(context, TypeAttributes.childAnchor(context, 1), "subscript operator requires an array operand.");
        }
        if (!SemanticType.scalar(SemanticType.Kind.INT32).equals(indexType)) {
            SemanticDiagnostics.reject(context, TypeAttributes.childAnchor(context, 1), "array index must be int32.");
        }

        // 数组访问会降一维：int32[][] 访问一次后得到 int32[]，int32[] 访问一次后得到 int32。
        SemanticType resultType = baseType.arrayElementType();

        // 生成“根据栈顶下标从数组引用中偏移到元素引用”的命令，结果仍是引用位置，后续可读也可写。
        thisBuilder.add(new TerminalNode(context.getCommandFactory().biasFromStTopToRef(
                CommandDataType.forStorage(resultType)
        )));
        return new NormalCommandNodeRegister(thisBuilder.build(), production, children);
    }
}
