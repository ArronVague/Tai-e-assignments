/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2022 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2022 Yue Li <yueli@nju.edu.cn>
 *
 * This file is part of Tai-e.
 *
 * Tai-e is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * Tai-e is distributed in the hope that it will be useful,but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Tai-e. If not, see <https://www.gnu.org/licenses/>.
 */

package pascal.taie.analysis.pta.cs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.World;
import pascal.taie.analysis.graph.callgraph.CallGraphs;
import pascal.taie.analysis.graph.callgraph.CallKind;
import pascal.taie.analysis.graph.callgraph.Edge;
import pascal.taie.analysis.pta.PointerAnalysisResult;
import pascal.taie.analysis.pta.PointerAnalysisResultImpl;
import pascal.taie.analysis.pta.core.cs.CSCallGraph;
import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.ArrayIndex;
import pascal.taie.analysis.pta.core.cs.element.CSCallSite;
import pascal.taie.analysis.pta.core.cs.element.CSManager;
import pascal.taie.analysis.pta.core.cs.element.CSMethod;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.cs.element.InstanceField;
import pascal.taie.analysis.pta.core.cs.element.MapBasedCSManager;
import pascal.taie.analysis.pta.core.cs.element.Pointer;
import pascal.taie.analysis.pta.core.cs.element.StaticField;
import pascal.taie.analysis.pta.core.cs.selector.ContextSelector;
import pascal.taie.analysis.pta.core.heap.HeapModel;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.analysis.pta.pts.PointsToSetFactory;
import pascal.taie.config.AnalysisOptions;
import pascal.taie.ir.exp.InvokeExp;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.*;
import pascal.taie.language.classes.JField;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.Type;

class Solver {

    private static final Logger logger = LogManager.getLogger(Solver.class);

    private final AnalysisOptions options;

    private final HeapModel heapModel;

    private final ContextSelector contextSelector;

    private CSManager csManager;

    private CSCallGraph callGraph;

    private PointerFlowGraph pointerFlowGraph;

    private WorkList workList;

    private PointerAnalysisResult result;

    Solver(AnalysisOptions options, HeapModel heapModel,
           ContextSelector contextSelector) {
        this.options = options;
        this.heapModel = heapModel;
        this.contextSelector = contextSelector;
    }

    void solve() {
        initialize();
        analyze();
    }

    private void initialize() {
        csManager = new MapBasedCSManager();
        callGraph = new CSCallGraph(csManager);
        pointerFlowGraph = new PointerFlowGraph();
        workList = new WorkList();
        // process program entry, i.e., main method
        Context defContext = contextSelector.getEmptyContext();
        JMethod main = World.get().getMainMethod();
        CSMethod csMethod = csManager.getCSMethod(defContext, main);
        callGraph.addEntryMethod(csMethod);
        addReachable(csMethod);
    }

    /**
     * Processes new reachable context-sensitive method.
     */
    private void addReachable(CSMethod csMethod) {
        // TODO - finish me
        // 判断 callGraph（调用图）是否包含 csMethod（当前方法）
        if (!callGraph.contains(csMethod)) {
            // 如果 callGraph 不包含 csMethod，那么将 csMethod 添加到 callGraph
            callGraph.addReachableMethod(csMethod);
            // 遍历 csMethod 方法中的每一个语句
            for (Stmt stmt : csMethod.getMethod().getIR().getStmts()) {
                // 对每个语句执行 StmtProcessor（语句处理器），这可能会进行一些额外的处理或分析
                stmt.accept(new StmtProcessor(csMethod));
            }
        }
    }

    /**
     * Processes the statements in context-sensitive new reachable methods.
     */
    private class StmtProcessor implements StmtVisitor<Void> {

        private final CSMethod csMethod;

        private final Context context;

        private StmtProcessor(CSMethod csMethod) {
            this.csMethod = csMethod;
            this.context = csMethod.getContext();
        }

        // TODO - if you choose to implement addReachable()
        //  via visitor pattern, then finish me
        // 处理 Invoke 类型的语句
        @Override
        public Void visit(Invoke invokeStmt) {
            // 如果调用的方法不是静态的，直接返回 null
            if (!invokeStmt.isStatic()) {
                return null;
            }
            // 解析被调用的方法
            JMethod callee = resolveCallee(null, invokeStmt);
            // 获取当前调用站点的上下文敏感表示
            CSCallSite csCallSite = csManager.getCSCallSite(context, invokeStmt);
            // 选择被调用方法的上下文
            Context calleeContext = contextSelector.selectContext(csCallSite, callee);
            // 在调用图中添加边，并判断是否添加成功
            if (callGraph.addEdge(new Edge<>(CallKind.STATIC, csCallSite, csManager.getCSMethod(calleeContext, callee)))) {
                // 如果添加成功，将被调用方法添加到可达方法集合中
                addReachable(csManager.getCSMethod(calleeContext, callee));
                // 遍历调用语句的所有参数，并在指针流图中添加边
                for (int i = 0; i < invokeStmt.getInvokeExp().getArgCount(); i++) {
                    addPFGEdge(
                            csManager.getCSVar(context, invokeStmt.getInvokeExp().getArg(i)),
                            csManager.getCSVar(calleeContext, callee.getIR().getParam(i))
                    );
                }
                // 如果调用语句有左值，处理返回值在指针流图中的边
                if (invokeStmt.getLValue() != null) {
                    for (Var returnVar : callee.getIR().getReturnVars()) {
                        addPFGEdge(
                                csManager.getCSVar(calleeContext, returnVar),
                                csManager.getCSVar(context, invokeStmt.getLValue())
                        );
                    }
                }
            }
            return null;
        }

        // 处理 New 类型的语句
        @Override
        public Void visit(New newStmt) {
            // 在工作列表中添加条目，表示创建了一个新的对象
            workList.addEntry(
                    csManager.getCSVar(context, newStmt.getLValue()),
                    PointsToSetFactory.make(csManager.getCSObj(
                            contextSelector.selectHeapContext(csMethod, heapModel.getObj(newStmt)),
                            heapModel.getObj(newStmt))
                    )
            );
            return null;
        }

        // 处理 Copy 类型的语句
        @Override
        public Void visit(Copy copyStmt) {
            // 在指针流图中添加边，表示变量的赋值操作
            addPFGEdge(
                    csManager.getCSVar(context, copyStmt.getRValue()),
                    csManager.getCSVar(context, copyStmt.getLValue())
            );
            return null;
        }

        // 处理 StoreField 类型的语句
        @Override
        public Void visit(StoreField storeFieldStmt) {
            // 如果字段是静态的，那么在指针流图中添加边，表示字段的赋值操作
            if (storeFieldStmt.isStatic()) {
                addPFGEdge(
                        csManager.getCSVar(context, storeFieldStmt.getRValue()),
                        csManager.getStaticField(storeFieldStmt.getFieldRef().resolve())
                );
            }
            return null;
        }

        // 处理 LoadField 类型的语句
        @Override
        public Void visit(LoadField loadFieldStmt) {
            // 如果字段是静态的，那么在指针流图中添加边，表示从字段读取值的操作
            if (loadFieldStmt.isStatic()) {
                addPFGEdge(
                        csManager.getStaticField(loadFieldStmt.getFieldRef().resolve()),
                        csManager.getCSVar(context, loadFieldStmt.getLValue())
                );
            }
            return null;
        }
    }

    /**
     * Adds an edge "source -> target" to the PFG.
     */
    private void addPFGEdge(Pointer source, Pointer target) {
        // TODO - finish me
        // 如果指针流图中不存在从 source 指向 target 的边，那么在图中添加这条边
        if (pointerFlowGraph.addEdge(source, target)) {
            // 如果 source 的指向集合（PointsToSet）不为空
            if (!source.getPointsToSet().isEmpty()) {
                // 将 target 和 source 的指向集合添加到工作列表（WorkList）中
                workList.addEntry(target, source.getPointsToSet());
            }
        }
    }

    /**
     * Processes work-list entries until the work-list is empty.
     */
    private void analyze() {
        // TODO - finish me
        // 当工作列表不为空时，持续执行分析
        while (!workList.isEmpty()) {
            // 从工作列表中取出一个条目
            WorkList.Entry entry = workList.pollEntry();
            // 对取出的条目进行传播操作，并获取结果
            PointsToSet delta = propagate(entry.pointer(), entry.pointsToSet());
            // 如果条目的指针是一个上下文敏感变量
            if (entry.pointer() instanceof CSVar csVar) {
                // 获取变量的原始表示
                Var var = csVar.getVar();
                // 遍历 delta 中的每一个对象
                for (CSObj obj : delta) {
                    // 遍历变量的所有存储字段语句
                    for (StoreField storeField : var.getStoreFields()) {
                        // 在指针流图中添加边，表示字段的赋值操作
                        addPFGEdge(
                                csManager.getCSVar(csVar.getContext(), storeField.getRValue()),
                                csManager.getInstanceField(obj, storeField.getFieldRef().resolve())
                        );
                    }
                    // 遍历变量的所有加载字段语句
                    for (LoadField loadField : var.getLoadFields()) {
                        // 在指针流图中添加边，表示从字段读取值的操作
                        addPFGEdge(
                                csManager.getInstanceField(obj, loadField.getFieldRef().resolve()),
                                csManager.getCSVar(csVar.getContext(), loadField.getLValue())
                        );
                    }
                    // 遍历变量的所有存储数组语句
                    for (StoreArray storeArray : var.getStoreArrays()) {
                        // 在指针流图中添加边，表示数组元素的赋值操作
                        addPFGEdge(
                                csManager.getCSVar(csVar.getContext(), storeArray.getRValue()),
                                csManager.getArrayIndex(obj)
                        );
                    }
                    // 遍历变量的所有加载数组语句
                    for (LoadArray loadArray : var.getLoadArrays()) {
                        // 在指针流图中添加边，表示从数组元素读取值的操作
                        addPFGEdge(
                                csManager.getArrayIndex(obj),
                                csManager.getCSVar(csVar.getContext(), loadArray.getLValue())
                        );
                    }
                    // 处理变量的调用
                    processCall(csVar, obj);
                }
            }
        }
    }

    /**
     * Propagates pointsToSet to pt(pointer) and its PFG successors,
     * returns the difference set of pointsToSet and pt(pointer).
     */
    private PointsToSet propagate(Pointer pointer, PointsToSet pointsToSet) {
        // TODO - finish me
        // 创建一个新的 PointsToSet 对象 delta
        PointsToSet delta = PointsToSetFactory.make();
        // 遍历 pointsToSet 中的每一个对象
        for (CSObj csObj : pointsToSet.getObjects()) {
            // 如果 pointer 的 PointsToSet 不包含这个对象，那么将这个对象添加到 delta 中
            if (!pointer.getPointsToSet().contains(csObj)) {
                delta.addObject(csObj);
            }
        }
        // 如果 delta 不为空
        if (!delta.isEmpty()) {
            // 将 delta 中的每一个对象添加到 pointer 的 PointsToSet 中
            for (CSObj csObj : delta) {
                pointer.getPointsToSet().addObject(csObj);
            }
            // 遍历 pointer 在指针流图中的每一个后继
            for (Pointer succ : pointerFlowGraph.getSuccsOf(pointer)) {
                // 将后继和 delta 添加到工作列表中
                workList.addEntry(succ, delta);
            }
        }
        // 返回 delta
        return delta;
    }

    /**
     * Processes instance calls when points-to set of the receiver variable changes.
     *
     * @param recv    the receiver variable
     * @param recvObj set of new discovered objects pointed by the variable.
     */
    private void processCall(CSVar recv, CSObj recvObj) {
        // TODO - finish me
        // 遍历接收者变量的所有调用
        for (Invoke invoke : recv.getVar().getInvokes()) {
            // 如果是静态调用，跳过
            if (invoke.isStatic()) continue;
            // 解析调用的目标方法
            JMethod callee = resolveCallee(recvObj, invoke);
            // 获取上下文敏感的调用站点
            CSCallSite csCallSite = csManager.getCSCallSite(recv.getContext(), invoke);
            // 选择调用的上下文
            Context calleeContext = contextSelector.selectContext(csCallSite, recvObj, callee);
            // 将调用方法的 "this" 变量和接收者对象添加到工作列表中
            workList.addEntry(csManager.getCSVar(calleeContext, callee.getIR().getThis()), PointsToSetFactory.make(recvObj));
            // 如果在调用图中添加了新的边
            if (callGraph.addEdge(new Edge<>(CallGraphs.getCallKind(invoke), csCallSite, csManager.getCSMethod(calleeContext, callee)))) {
                // 将调用方法添加到可达方法集合中
                addReachable(csManager.getCSMethod(calleeContext, callee));
                // 遍历调用的所有参数
                for (int i = 0; i < invoke.getInvokeExp().getArgCount(); i++) {
                    // 在指针流图中添加边，表示参数的传递
                    addPFGEdge(
                            csManager.getCSVar(recv.getContext(), invoke.getInvokeExp().getArg(i)),
                            csManager.getCSVar(calleeContext, callee.getIR().getParam(i))
                    );
                }
                // 如果调用有返回值
                if (invoke.getLValue() != null) {
                    // 遍历方法的所有返回变量
                    for (Var returnVar : callee.getIR().getReturnVars()) {
                        // 在指针流图中添加边，表示返回值的传递
                        addPFGEdge(
                                csManager.getCSVar(calleeContext, returnVar),
                                csManager.getCSVar(recv.getContext(), invoke.getLValue())
                        );
                    }
                }
            }
        }
    }
    /**
     * Resolves the callee of a call site with the receiver object.
     *
     * @param recv the receiver object of the method call. If the callSite
     *             is static, this parameter is ignored (i.e., can be null).
     * @param callSite the call site to be resolved.
     * @return the resolved callee.
     */
    private JMethod resolveCallee(CSObj recv, Invoke callSite) {
        Type type = recv != null ? recv.getObject().getType() : null;
        return CallGraphs.resolveCallee(type, callSite);
    }

    PointerAnalysisResult getResult() {
        if (result == null) {
            result = new PointerAnalysisResultImpl(csManager, callGraph);
        }
        return result;
    }
}
