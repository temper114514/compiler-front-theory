package org.harvey.vie.theory.semantic.structure;


import org.harvey.vie.theory.demo.program.ProgramSemanticTag;
import org.harvey.vie.theory.lexical.analysis.token.SourceToken;
import org.harvey.vie.theory.semantic.callback.bu.ShiftReduceCallback;
import org.harvey.vie.theory.semantic.context.ShiftReduceSemanticContext;
import org.harvey.vie.theory.semantic.error.SemanticDiagnostics;
import org.harvey.vie.theory.semantic.sequence.SyntaxTreeListIterator;
import org.harvey.vie.theory.semantic.tag.ProductionTagStrategy;
import org.harvey.vie.theory.semantic.tree.node.HeadNode;
import org.harvey.vie.theory.semantic.type.SemanticType;
import org.harvey.vie.theory.semantic.type.TypeRegister;
import org.harvey.vie.theory.syntax.grammar.produce.SimpleGrammarProduction;

import java.util.ArrayList;
import java.util.List;

/**
 * 负责结构体声明的注册，以及字段布局的建立。
 * <p>
 * 输入：
 * - 语法分析阶段每一次和结构体相关的归约结果；
 * - 当前语义上下文中的结构体表、类型表和语法树上下文。
 * <p>
 * 输出：
 * - 新的结构体记录注册到结构体表；
 * - 字段重名、非法字段类型等诊断信息。
 * <p>
 * 功能：
 * - 在归约出结构体声明时登记结构体类型；
 * - 展开字段列表并分配字段偏移；
 * - 让后续成员访问可以直接根据偏移生成命令。
 */
public class StructSemanticCallback implements ShiftReduceCallback {
    private static final ProductionTagStrategy<ReduceAction> REDUCE_ACTIONS = new ProductionTagStrategy<>(ReduceAction.NOOP)
            .when(ReduceAction.REGISTER_STRUCT, ProgramSemanticTag.STRUCT_DECL);

    private final StructFieldStepper fieldStepper = new StructFieldStepper();

    /**
     * 处理一次 reduce 事件。
     *
     * 输入：
     * - 当前归约产生式；
     * - 当前归约后生成的语法树头节点。
     *
     * 输出：
     * - 如果本次归约是结构体声明，则完成结构体注册；
     * - 然后继续执行框架默认的 reduce 逻辑。
     */
    @Override
    public void onReduce(ShiftReduceSemanticContext context, SimpleGrammarProduction production) {
        if (!context.getTreeContext().isEmpty() && context.getTreeContext().peek().isHead()) {
            HeadNode head = context.getTreeContext().peek().toHead();
            REDUCE_ACTIONS.resolve(production).accept(this, context, head, production);
        }
        ShiftReduceCallback.super.onReduce(context, production);
    }

    /**
     * 注册结构体类型，并检查结构体名是否重复。
     * <p>
     * 输入：
     * - 当前归约出的 struct_decl 节点。
     * <p>
     * 输出：
     * - 新的 {@link StructRecord} 注册到结构体表；
     * - 同时检查字段引用到的类型是否已声明。
     */
    private void registerStruct(ShiftReduceSemanticContext context, HeadNode head, SimpleGrammarProduction production) {
        SourceToken nameToken = head.get(1).toToken().getSource();
        if (context.existStruct(nameToken)) {
            SemanticDiagnostics.reject(context, nameToken, "duplicate struct declaration is not allowed.");
        }
        List<StructField> fields = collectFields(context, head.get(3).toHead());
        StructRecord record = new StructRecord(context.structTableSize(), nameToken, fields, head);
        context.registerStruct(record);
        for (StructField field : fields) {
            context.requireDeclaredType(field.getType(), field.getNameToken(), "struct field type is not declared.");
        }
    }

    /**
     * 把链式字段列表展开成有序字段表。
     * 这里顺便完成：
     * - 字段类型读取
     * - void 字段禁止
     * - 重名字段检查
     * - 字段偏移分配
     * <p>
     * 输入：
     * - struct_field_list 对应的语法树头节点。
     * <p>
     * 输出：
     * - 一个按源码顺序排列、并已分配 offset 的字段列表。
     */
    private List<StructField> collectFields(ShiftReduceSemanticContext context, HeadNode listHead) {
        List<StructField> fields = new ArrayList<>();
        SyntaxTreeListIterator<HeadNode> iterator = new SyntaxTreeListIterator<>(listHead, fieldStepper);
        int offset = 0;
        while (iterator.hasNext()) {
            HeadNode fieldHead = iterator.next();
            TypeRegister register = context.getType(fieldHead.get(0));
            if (register == null) {
                throw new IllegalStateException("struct field type is missing.");
            }
            SemanticType type = register.requireType("struct field type is required.");
            SourceToken fieldName = fieldHead.get(1).toToken().getSource();
            SemanticDiagnostics.requireNotVoid(context, type, fieldName, "void cannot be used as struct field type.");
            for (StructField field : fields) {
                if (field.isNamed(fieldName)) {
                    SemanticDiagnostics.reject(context, fieldName, "duplicate struct field declaration is not allowed.");
                }
            }
            // 当前实现里偏移按字段顺序递增，成员访问时直接使用这个偏移定位字段。
            fields.add(new StructField(fieldName, type, offset++));
        }
        return fields;
    }

    private enum ReduceAction {
        NOOP {
            @Override
            void accept(
                    StructSemanticCallback callback,
                    ShiftReduceSemanticContext context,
                    HeadNode head,
                    SimpleGrammarProduction production) {
            }
        },
        REGISTER_STRUCT {
            @Override
            void accept(
                    StructSemanticCallback callback,
                    ShiftReduceSemanticContext context,
                    HeadNode head,
                    SimpleGrammarProduction production) {
                callback.registerStruct(context, head, production);
            }
        };

        abstract void accept(
                StructSemanticCallback callback,
                ShiftReduceSemanticContext context,
                HeadNode head,
                SimpleGrammarProduction production);
    }
}
