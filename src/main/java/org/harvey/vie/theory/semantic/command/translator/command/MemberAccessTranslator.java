package org.harvey.vie.theory.semantic.command.translator.command;

import org.harvey.vie.theory.semantic.command.command.factory.CommandDataType;
import org.harvey.vie.theory.semantic.command.node.CommandNodeBuilder;
import org.harvey.vie.theory.semantic.command.node.CommandNodeListBuilder;
import org.harvey.vie.theory.semantic.command.node.TerminalNode;
import org.harvey.vie.theory.semantic.command.register.CommandNodeRegister;
import org.harvey.vie.theory.semantic.command.register.NormalCommandNodeRegister;
import org.harvey.vie.theory.semantic.context.ShiftReduceSemanticContext;
import org.harvey.vie.theory.semantic.error.SemanticDiagnostics;
import org.harvey.vie.theory.semantic.structure.StructField;
import org.harvey.vie.theory.semantic.structure.StructRecord;
import org.harvey.vie.theory.semantic.type.SemanticType;
import org.harvey.vie.theory.semantic.type.TypeAttributes;
import org.harvey.vie.theory.syntax.grammar.produce.SimpleGrammarProduction;

/**
 * 翻译结构体成员访问表达式。
 * <p>
 * 输入：
 * - 当前归约出的成员访问语法节点，例如 {@code obj.field}；
 * - children[0] 对应左侧对象表达式的命令。
 * <p>
 * 输出：
 * - 一个新的 {@link NormalCommandNodeRegister}；
 * - 其中包含“对象求值命令 + 字段偏移访问命令”。
 * <p>
 * 功能：
 * - 检查左操作数是否为已声明的结构体类型；
 * - 在结构体表中查找字段；
 * - 把字段访问翻译成“对象引用 + 固定字段偏移”的定位命令。
 */
public class MemberAccessTranslator implements CommandTranslator {
    /**
     * 把结构体成员访问语法节点翻译成偏移访问命令流。
     *
     * @param context 当前语义上下文
     * @param production 当前归约产生式，预期对应 loc ::= loc . id
     * @param children 子节点翻译结果
     * @return 包含成员访问命令的注册器
     */
    @Override
    public CommandNodeRegister translate(
            ShiftReduceSemanticContext context,
            SimpleGrammarProduction production,
            CommandNodeRegister[] children) {
        CommandNodeBuilder builder = new CommandNodeListBuilder();
        children[0].register(builder);
        SemanticType baseType = TypeAttributes.childType(context, 0);
        if (baseType == null) {
            SemanticDiagnostics.reject(
                    context,
                    TypeAttributes.childAnchor(context, 0),
                    "member access requires a typed left operand."
            );
        }
        StructRecord struct = context.getStruct(baseType);
        if (struct == null) {
            SemanticDiagnostics.reject(
                    context,
                    TypeAttributes.childAnchor(context, 0),
                    "member access requires a declared struct operand."
            );
        }
        StructField field = struct.field(TypeAttributes.childAnchor(context, 2));
        // 字段访问本质上就是“基址 + 固定偏移”的引用偏移。
        builder.add(new TerminalNode(context.getCommandFactory().biasFromStTopToRef(
                CommandDataType.forStorage(field.getType()),
                field.getOffset()
        )));
        return new NormalCommandNodeRegister(builder.build(), production, children);
    }
}
