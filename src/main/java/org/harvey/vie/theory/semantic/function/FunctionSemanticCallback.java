package org.harvey.vie.theory.semantic.function;

import org.harvey.vie.theory.demo.program.ProgramSemanticTag;
import org.harvey.vie.theory.exception.CompilerException;
import org.harvey.vie.theory.lexical.analysis.token.SourceToken;
import org.harvey.vie.theory.semantic.callback.bu.ShiftReduceCallback;
import org.harvey.vie.theory.semantic.context.ShiftReduceSemanticContext;
import org.harvey.vie.theory.semantic.error.SemanticDiagnostics;
import org.harvey.vie.theory.semantic.sequence.SyntaxTreeListIterator;
import org.harvey.vie.theory.semantic.tag.ProductionTagStrategy;
import org.harvey.vie.theory.semantic.tree.node.HeadNode;
import org.harvey.vie.theory.semantic.tree.node.ShiftReduceSyntaxTreeNode;
import org.harvey.vie.theory.semantic.type.SemanticType;
import org.harvey.vie.theory.semantic.type.TypeRegister;
import org.harvey.vie.theory.syntax.grammar.produce.SimpleGrammarProduction;

import java.util.ArrayList;
import java.util.List;


/**
 * 负责函数相关的语义动作。
 * <p>
 * 输入：
 * - 语法分析阶段每一次和函数相关的归约结果；
 * - 当前语义上下文中的函数表、类型表、语法树上下文。
 * <p>
 * 输出：
 * - 对函数表的登记结果；
 * - 对非法函数调用、非法返回、参数错误等问题的诊断信息。
 * <p>
 * 功能：
 * 1. 在函数头归约时注册函数声明和参数表；
 * 2. 在 return 归约时检查返回是否合法；
 * 3. 在函数调用归约时检查被调函数和实参列表是否合法。
 */
public class FunctionSemanticCallback implements ShiftReduceCallback {
    private static final ProductionTagStrategy<ReduceAction> REDUCE_ACTIONS = new ProductionTagStrategy<>(ReduceAction.NOOP)
            .when(ReduceAction.PREPARE_FUNCTION, ProgramSemanticTag.FUNCTION, ProgramSemanticTag.HEAD)
            .when(ReduceAction.VALIDATE_RETURN, ProgramSemanticTag.RETURN)
            .when(ReduceAction.VALIDATE_CALL, ProgramSemanticTag.FUNCTION, ProgramSemanticTag.CALL);

    private final ArgumentStepper argumentStepper = new ArgumentStepper();
    private final ParameterStepper parameterStepper = new ParameterStepper();

    /**
     * 处理一次 reduce 事件。
     *
     * 输入：
     * - 当前归约产生式；
     * - 归约后已经压入 treeContext 的头节点。
     *
     * 输出：
     * - 可能触发函数注册、return 检查或函数调用检查；
     * - 然后继续执行框架默认的 reduce 上下文更新逻辑。
     */
    @Override
    public void onReduce(ShiftReduceSemanticContext context, SimpleGrammarProduction production) {
        onReduce0(context, production);
        ShiftReduceCallback.super.onReduce(context, production);
    }

    /**
     * 根据当前归约出来的产生式标签，分派不同的函数语义动作。
     * <p>
     * 输入：
     * - 当前归约产生式；
     * - 当前刚刚归约完成的头节点。
     * <p>
     * 输出：
     * - 不直接返回语义结果，而是把处理分派给 PREPARE_FUNCTION / VALIDATE_RETURN / VALIDATE_CALL。
     */
    private void onReduce0(ShiftReduceSemanticContext context, SimpleGrammarProduction production) {
        if (context.getTreeContext().isEmpty() || !context.getTreeContext().peek().isHead()) {
            return;
        }
        HeadNode head = context.getTreeContext().peek().toHead();
        REDUCE_ACTIONS.resolve(production).accept(this, context, head);
    }

    /**
     * 从函数头节点中提取函数名、返回类型、形参列表，并注册到函数表中。
     * <p>
     * 输入：
     * - 当前归约出的 function_head 节点。
     * <p>
     * 输出：
     * - 一条新的 {@link FunctionRecord} 注册到函数表中；
     * - 同时把当前函数标记为“待进入函数体”。
     */
    private void prepareFunction(ShiftReduceSemanticContext context, HeadNode head) {
        SourceToken nameToken = tokenAt(head, 1);
        if (context.existFunction(nameToken)) {
            SemanticDiagnostics.reject(context, nameToken, "duplicate function declaration is not allowed.");
        }
        TypeRegister returnTypeRegister = resolveReturnType(context, head);
        if (returnTypeRegister == null) {
            throw new CompilerException("function return type is missing.");
        }
        SemanticType returnType = returnTypeRegister.requireType("function return type is required.");
        List<FunctionParameter> parameters = collectParameters(context, head.get(3));
        FunctionRecord record = new FunctionRecord(
                context.functionTableSize(),
                new FunctionSignature(nameToken, returnType, head),
                parameters,
                head
        );
        context.registerFunction(record);
        context.markPendingFunction(record);
    }

    /**
     * 检查 return 语句是否和当前所在函数匹配：
     * - return 是否出现在函数体内部；
     * - void / non-void 的返回形式是否正确；
     * - 返回值类型是否能赋给函数返回类型。
     * <p>
     * 输入：
     * - 当前归约出的 return_stmt 节点；
     * - hasValue 表示本次 return 是否带返回值。
     * <p>
     * 输出：
     * - 不合法时向诊断系统追加错误；
     * - 合法时不生成额外对象，只完成语义检查。
     */
    private void validateReturnValue(ShiftReduceSemanticContext context, HeadNode head, boolean hasValue) {
        SourceToken returnToken = tokenAt(head, 0);
        if (!context.insideFunction()) {
            SemanticDiagnostics.reject(context, returnToken, "return is only allowed inside function body.");
        }
        SemanticType returnType = context.currentFunctionReturnType();
        if (returnType == null) {
            throw new CompilerException("current function return type is missing.");
        }
        if (!hasValue) {
            if (!returnType.isVoidScalar()) {
                SemanticDiagnostics.reject(context, returnToken, "non-void function must return a value.");
            }
            return;
        }
        if (returnType.isVoidScalar()) {
            SemanticDiagnostics.reject(context, returnToken, "void function cannot return a value.");
        }
        TypeRegister valueType = context.getType(head.get(1));
        if (valueType == null) {
            throw new CompilerException("return value type is missing.");
        }
        SemanticDiagnostics.requireAssignable(context,
                valueType.requireType("return value type is required."),
                returnType,
                returnToken,
                "return value type does not match function return type."
        );
    }

    /**
     * 检查函数调用是否合法：
     * - 被调用函数是否已经声明；
     * - 实参数量是否和形参数量一致；
     * - 每个实参类型是否能赋给对应形参类型。
     * <p>
     * 输入：
     * - 当前归约出的 call_expr 节点。
     * <p>
     * 输出：
     * - 不合法时向诊断系统追加错误；
     * - 合法时不直接返回对象，只为后续命令翻译提供已验证的调用信息。
     */
    private void validateCall(ShiftReduceSemanticContext context, HeadNode head) {
        SourceToken nameToken = tokenAt(head, 0);
        FunctionRecord record = context.getFunction(nameToken);
        if (record == null) {
            SemanticDiagnostics.reject(context, nameToken, "function must be defined before it is called.");
            return;
        }
        List<TypeRegister> args = collectArgumentTypes(context, head.get(2));
        if (args.size() != record.getParameters().size()) {
            SemanticDiagnostics.reject(context, nameToken, "function argument count does not match.");
        }
        for (int i = 0; i < args.size(); i++) {
            SemanticType sourceType = args.get(i).requireType("argument type is required.");
            SemanticType targetType = record.getParameters().get(i).getType();
            SemanticDiagnostics.requireAssignable(context,
                    sourceType,
                    targetType,
                    nameToken,
                    "function argument type does not match parameter type."
            );
        }
    }

    /**
     * 把参数列表子树整理成有序的参数表。
     * 这里会同时完成参数类型读取、void 参数禁止、重名参数检查。
     * <p>
     * 输入：
     * - 函数头中的 param_list 子树。
     * <p>
     * 输出：
     * - 一个按源码顺序排列的 {@link FunctionParameter} 列表。
     */
    private List<FunctionParameter> collectParameters(
            ShiftReduceSemanticContext context, ShiftReduceSyntaxTreeNode node) {
        List<FunctionParameter> result = new ArrayList<>();
        SyntaxTreeListIterator<HeadNode> iterator = new SyntaxTreeListIterator<>(node, parameterStepper);
        while (iterator.hasNext()) {
            HeadNode head = iterator.next();
            TypeRegister register = context.getType(head.get(0));
            if (register == null) {
                throw new CompilerException("parameter type is missing.");
            }
            SemanticType type = register.requireType("parameter type is required.");
            SourceToken nameToken = tokenAt(head, 1);
            SemanticDiagnostics.requireNotVoid(context, type, nameToken, "void cannot be used as parameter type.");
            for (FunctionParameter parameter : result) {
                if (parameter.isNamed(nameToken)) {
                    SemanticDiagnostics.reject(context, nameToken, "duplicate parameter declaration is not allowed.");
                }
            }
            result.add(new FunctionParameter(nameToken, type, head.get(0).toHead()));
        }
        return result;
    }

    /**
     * 解析函数返回类型。
     * 有些情况下类型已经被前面的类型分析注册好了，
     * 有些情况下还需要从 token 直接恢复出类型对象。
     * <p>
     * 输出：
     * - 返回类型对应的 {@link TypeRegister}；
     * - 如果当前节点还无法解析出类型，则返回 null。
     */
    private static TypeRegister resolveReturnType(ShiftReduceSemanticContext context, HeadNode head) {
        TypeRegister direct = context.getType(head.get(0));
        if (direct != null) {
            return direct;
        }
        ShiftReduceSyntaxTreeNode first = head.get(0);
        if (first.isToken()) {
            SourceToken token = first.toToken().getSource();
            SemanticType type = context.typeToken(token);
            if (type != null) {
                return TypeRegister.simple(type, token);
            }
        }
        return null;
    }

    /**
     * 取出某个子节点对应的源码 token，作为诊断报错的锚点。
     *
     * @return 指定子节点对应的源码 token
     */
    private static SourceToken tokenAt(HeadNode head, int index) {
        return ShiftReduceSyntaxTreeNode.anchor(head.get(index));
    }

    /**
     * 按源码顺序收集函数调用的实参类型。
     * <p>
     * 输入：
     * - call_expr 中的 arg_list 子树。
     * <p>
     * 输出：
     * - 一个按源码顺序排列的实参类型寄存器列表。
     */
    private List<TypeRegister> collectArgumentTypes(
            ShiftReduceSemanticContext context, ShiftReduceSyntaxTreeNode node) {
        List<TypeRegister> result = new ArrayList<>();
        SyntaxTreeListIterator<HeadNode> iterator = new SyntaxTreeListIterator<>(node, argumentStepper);
        while (iterator.hasNext()) {
            HeadNode head = iterator.next();
            TypeRegister register = context.getType(head);
            if (register != null) {
                result.add(register);
            }
        }
        return result;
    }

    private enum ReduceAction {
        NOOP {
            @Override
            void accept(FunctionSemanticCallback callback, ShiftReduceSemanticContext context, HeadNode head) {
            }
        },
        PREPARE_FUNCTION {
            @Override
            void accept(FunctionSemanticCallback callback, ShiftReduceSemanticContext context, HeadNode head) {
                callback.prepareFunction(context, head);
            }
        },
        VALIDATE_RETURN {
            @Override
            void accept(FunctionSemanticCallback callback, ShiftReduceSemanticContext context, HeadNode head) {
                callback.validateReturnValue(context, head, head.containsTag(ProgramSemanticTag.VALUE));
            }
        },
        VALIDATE_CALL {
            @Override
            void accept(FunctionSemanticCallback callback, ShiftReduceSemanticContext context, HeadNode head) {
                callback.validateCall(context, head);
            }
        };

        abstract void accept(FunctionSemanticCallback callback, ShiftReduceSemanticContext context, HeadNode head);
    }

}

