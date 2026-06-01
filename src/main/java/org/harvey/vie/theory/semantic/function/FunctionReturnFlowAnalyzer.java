package org.harvey.vie.theory.semantic.function;

import org.harvey.vie.theory.demo.program.ProgramSemanticTag;
import org.harvey.vie.theory.semantic.context.ShiftReduceSemanticContext;
import org.harvey.vie.theory.semantic.tag.ProductionTagStrategy;
import org.harvey.vie.theory.semantic.tree.node.HeadNode;
import org.harvey.vie.theory.semantic.tree.node.ShiftReduceSyntaxTreeNode;
import org.harvey.vie.theory.semantic.value.ConstantValue;

/**
 * 判断一棵语法子树是否能在所有可达路径上保证执行到 return。
 * <p>
 * 这个分析器不直接写死具体产生式，而是根据产生式上的语义标签
 * 选择对应的判断规则，便于后续扩展文法时继续复用。
 */
public final class FunctionReturnFlowAnalyzer {
    private final ProductionTagStrategy<ReturnRule> rules;

    public FunctionReturnFlowAnalyzer() {
        ReturnRule never = (context, head) -> false;
        ReturnRule returnRule = (context, head) -> true;
        ReturnRule block = this::blockGuaranteesReturn;
        ReturnRule blockItemsSequence = this::blockItemsSequenceGuaranteesReturn;
        ReturnRule forward = this::forwardGuaranteesReturn;
        ReturnRule matchIf = this::matchedIfGuaranteesReturn;
        rules = new ProductionTagStrategy<>(never)
                .when(block, ProgramSemanticTag.BLOCK, ProgramSemanticTag.COMMAND)
                .when(never, ProgramSemanticTag.BLOCK, ProgramSemanticTag.LIST, ProgramSemanticTag.EMPTY)
                .when(
                        blockItemsSequence,
                        ProgramSemanticTag.BLOCK,
                        ProgramSemanticTag.LIST,
                        ProgramSemanticTag.SEQUENCE
                )
                .when(forward, ProgramSemanticTag.FORWARD)
                .when(returnRule, ProgramSemanticTag.RETURN)
                .when(matchIf, ProgramSemanticTag.CONDITIONAL, ProgramSemanticTag.ELSE_BRANCH);
    }

    @FunctionalInterface
    private interface ReturnRule {
        boolean test(ShiftReduceSemanticContext context, HeadNode head);
    }

    /**
     * 从当前节点开始，判断该子树是否保证返回。
     */
    public boolean guaranteesReturn(ShiftReduceSemanticContext context, ShiftReduceSyntaxTreeNode node) {
        if (node == null || !node.isHead()) {
            return false;
        }
        HeadNode head = node.toHead();
        return rules.resolve(head.getProduction()).test(context, head);
    }

    /**
     * 代码块是否保证返回，取决于它内部语句序列是否保证返回。
     */
    public boolean blockGuaranteesReturn(ShiftReduceSemanticContext context, HeadNode head) {
        return guaranteesReturn(context, head.get(1));
    }

    /**
     * 顺序语句里，只要前半段已经保证返回，后半段就不可达；
     * 否则继续检查后续语句是否补上了返回路径。
     */
    public boolean blockItemsSequenceGuaranteesReturn(
            ShiftReduceSemanticContext context, HeadNode head) {
        return guaranteesReturn(context, head.get(0)) || guaranteesReturn(context, head.get(1));
    }

    /**
     * 对纯转发节点，沿着子节点继续寻找是否存在“保证返回”的结构。
     */
    public boolean forwardGuaranteesReturn(ShiftReduceSemanticContext context, HeadNode head) {
        for (ShiftReduceSyntaxTreeNode child : head) {
            if (guaranteesReturn(context, child)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 对带 else 的条件分支做返回流分析。
     * 如果条件已经被常量折叠为 true / false，就只检查可达分支；
     * 否则要求 then 和 else 两边都保证返回。
     */
    public boolean matchedIfGuaranteesReturn(ShiftReduceSemanticContext context, HeadNode head) {
        Boolean condition = constantBoolean(context, head.get(2));
        if (Boolean.TRUE.equals(condition)) {
            return guaranteesReturn(context, head.get(4));
        }
        if (Boolean.FALSE.equals(condition)) {
            return guaranteesReturn(context, head.get(6));
        }
        return guaranteesReturn(context, head.get(4)) && guaranteesReturn(context, head.get(6));
    }

    /**
     * 读取条件表达式的布尔常量值。
     * 如果当前阶段无法判断它是不是编译期常量，就返回 null。
     */
    public Boolean constantBoolean(ShiftReduceSemanticContext context, ShiftReduceSyntaxTreeNode node) {
        ConstantValue value = context.getConstantValue(node);
        if (value == null || !value.getType().isBooleanScalar()) {
            return null;
        }
        return value.bool();
    }
}
