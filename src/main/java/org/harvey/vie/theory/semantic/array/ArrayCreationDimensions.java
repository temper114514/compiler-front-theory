package org.harvey.vie.theory.semantic.array;

import lombok.Getter;
import org.harvey.vie.theory.demo.program.ProgramSemanticTag;
import org.harvey.vie.theory.exception.CompilerException;
import org.harvey.vie.theory.lexical.analysis.token.SourceToken;
import org.harvey.vie.theory.semantic.context.ShiftReduceSemanticContext;
import org.harvey.vie.theory.semantic.error.SemanticDiagnostics;
import org.harvey.vie.theory.semantic.tree.node.HeadNode;
import org.harvey.vie.theory.semantic.tree.node.ShiftReduceSyntaxTreeNode;
import org.harvey.vie.theory.semantic.type.SemanticType;
import org.harvey.vie.theory.semantic.type.TypeRegister;

import java.util.ArrayDeque;

/**
 * 数组创建维度分析工具。
 * <p>
 * 这个类只负责处理 `new T[expr][ ]...` 这一段维度链：
 * - 统计总维度数；
 * - 统计显式写出长度的维度数；
 * - 检查长度表达式是否为 `int32`；
 * - 检查“有长度的维度必须出现在前面”。
 */
public final class ArrayCreationDimensions {
    private ArrayCreationDimensions() {
    }

    /**
     * 输入：数组创建维度节点。
     * 输出：只统计维度结构，不做类型校验的摘要结果。
     * 功能：用于快速判断维度数量，以及是否存在“前面省略、后面又写长度”的非法形式。
     */
    public static Summary summarize(ShiftReduceSyntaxTreeNode node) {
        Summary summary = new Summary();
        visitDimensions(node, dimension -> {
            summary.totalDimensions++;
            // 只要这个维度带有长度表达式，就记为“显式维度”。
            if (dimension.containsTag(ProgramSemanticTag.VALUE)) {
                if (summary.trailingOmittedDimensions > 0) {
                    throw new CompilerException("array creation dimensions with length must precede omitted dimensions.");
                }
                summary.specifiedDimensions++;
            } else {
                // 没写长度的维度，属于尾部省略维度。
                summary.trailingOmittedDimensions++;
            }
        });
        if (summary.specifiedDimensions <= 0) {
            throw new CompilerException("array creation requires at least one specified length.");
        }
        return summary;
    }

    /**
     * 输入：数组创建维度节点和语义上下文。
     * 输出：维度摘要，并确保所有显式长度表达式都已经完成语义检查。
     * 功能：在摘要基础上进一步检查：
     * - 显式长度必须是 int32；
     * - 省略维度只能放在末尾；
     * - 至少要有一个显式长度。
     */
    public static Summary summarizeAndValidate(ShiftReduceSemanticContext context, ShiftReduceSyntaxTreeNode node) {
        Summary summary = new Summary();
        visitDimensions(node, dimension -> {
            summary.totalDimensions++;
            if (dimension.containsTag(ProgramSemanticTag.VALUE)) {
                if (summary.trailingOmittedDimensions > 0) {
                    SourceToken token = ShiftReduceSyntaxTreeNode.anchor(dimension);
                    SemanticDiagnostics.reject(
                            context,
                            token,
                            "array creation dimensions with length must precede omitted dimensions."
                    );
                }
                // 数组长度表达式已经在语义阶段有类型结果，这里只允许 int32。
                TypeRegister register = context.getType(dimension.get(1));
                if (register == null) {
                    throw new CompilerException("array length expression type is missing.");
                }
                SemanticType type = register.requireType("array length expression type is required.");
                if (!SemanticType.scalar(SemanticType.Kind.INT32).equals(type)) {
                    SemanticDiagnostics.reject(
                            context,
                            ShiftReduceSyntaxTreeNode.anchor(dimension.get(1)),
                            "array length expression must be int32."
                    );
                }
                summary.specifiedDimensions++;
            } else {
                // 没写长度的维度，只能出现在所有显式长度之后。
                summary.trailingOmittedDimensions++;
            }
        });
        if (summary.specifiedDimensions <= 0) {
            SourceToken token = ShiftReduceSyntaxTreeNode.anchor(node);
            SemanticDiagnostics.reject(context, token, "array creation requires at least one specified length.");
        }
        return summary;
    }

    /**
     * 输入：一个数组维度节点，可能是递归链，也可能是单个维度。
     * 输出：按从左到右顺序回调每一个维度子节点。
     * 功能：把文法里的递归结构拆成顺序遍历，方便上层统一校验和统计。
     */
    private static void visitDimensions(ShiftReduceSyntaxTreeNode node, java.util.function.Consumer<HeadNode> consumer) {
        if (node == null || !node.isHead()) {
            return;
        }
        ArrayDeque<HeadNode> stack = new ArrayDeque<>();
        HeadNode cursor = node.toHead();
        while (true) {
            // LIST -> LIST ARRAY_CREATION_DIM SEQUENCE
            if (cursor.matchTags(ProgramSemanticTag.LIST, ProgramSemanticTag.ARRAY_CREATION_DIM, ProgramSemanticTag.SEQUENCE)) {
                stack.push(cursor.get(1).toHead());
                cursor = cursor.get(0).toHead();
                continue;
            }
            // LIST -> ARRAY_CREATION_DIM FORWARD
            if (cursor.matchTags(ProgramSemanticTag.LIST, ProgramSemanticTag.ARRAY_CREATION_DIM, ProgramSemanticTag.FORWARD)) {
                stack.push(cursor.get(0).toHead());
                break;
            }
            // 单个维度的情况，直接压栈。
            if (cursor.containsTag(ProgramSemanticTag.ARRAY_CREATION_DIM)) {
                stack.push(cursor);
            }
            break;
        }
        while (!stack.isEmpty()) {
            // 保持从左到右的维度顺序。
            consumer.accept(stack.pop());
        }
    }

    @Getter
    public static final class Summary {
        /** 维度总数，包括显式长度和省略长度。 */
        private int totalDimensions;
        /** 明确写出长度表达式的维度数。 */
        private int specifiedDimensions;
        /** 尾部省略的维度数，例如 `new int[1][]` 里的 `[]`。 */
        private int trailingOmittedDimensions;
    }
}
