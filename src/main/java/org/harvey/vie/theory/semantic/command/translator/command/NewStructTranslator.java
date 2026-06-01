package org.harvey.vie.theory.semantic.command.translator.command;

import org.harvey.vie.theory.lexical.analysis.token.SourceToken;
import org.harvey.vie.theory.semantic.command.node.CommandNode;
import org.harvey.vie.theory.semantic.command.node.TerminalNode;
import org.harvey.vie.theory.semantic.command.register.CommandNodeRegister;
import org.harvey.vie.theory.semantic.command.register.NormalCommandNodeRegister;
import org.harvey.vie.theory.semantic.context.ShiftReduceSemanticContext;
import org.harvey.vie.theory.semantic.error.SemanticDiagnostics;
import org.harvey.vie.theory.semantic.structure.StructRecord;
import org.harvey.vie.theory.semantic.tree.node.HeadNode;
import org.harvey.vie.theory.syntax.grammar.produce.SimpleGrammarProduction;

/**
 * 翻译结构体实例化表达式。
 * <p>
 * 输入：
 * - 当前归约出的 new_struct_expr，例如 {@code new Student()}。
 * <p>
 * 输出：
 * - 一个只包含 `new_struct` 命令的命令节点注册器。
 * <p>
 * 功能：
 * - 从语法树中取出结构体类型名；
 * - 在结构体表中查找对应定义；
 * - 生成结构体实例分配命令。
 *
 * @author Temper
 */
public class NewStructTranslator implements CommandTranslator {
    /**
     * 把结构体实例化表达式翻译成对象分配命令。
     *
     * @param context 当前语义上下文
     * @param production 当前归约产生式，预期对应 new_struct_expr
     * @param children 子节点翻译结果
     * @return 只包含结构体创建命令的注册器
     */
    @Override
    public CommandNodeRegister translate(
            ShiftReduceSemanticContext context,
            SimpleGrammarProduction production,
        CommandNodeRegister[] children) {
        HeadNode head = context.getTreeContext().peek().toHead();
        SourceToken nameToken = head.get(1).toToken().getSource();
        StructRecord record = context.getStruct(nameToken);
        if (record == null) {
            SemanticDiagnostics.reject(context, nameToken, "struct type is not declared.");
        }
        // 结构体实例的内存布局完全由 StructRecord 描述。
        CommandNode node = new TerminalNode(context.getCommandFactory().newStruct(record));
        return new NormalCommandNodeRegister(new CommandNode[]{node}, production, children);
    }
}
