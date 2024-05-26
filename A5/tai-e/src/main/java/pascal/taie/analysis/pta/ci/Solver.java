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

package pascal.taie.analysis.pta.ci;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.World;
import pascal.taie.analysis.graph.callgraph.CallGraphs;
import pascal.taie.analysis.graph.callgraph.CallKind;
import pascal.taie.analysis.graph.callgraph.DefaultCallGraph;
import pascal.taie.analysis.graph.callgraph.Edge;
import pascal.taie.analysis.pta.core.heap.HeapModel;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.ir.exp.InvokeExp;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.proginfo.MethodRef;
import pascal.taie.ir.stmt.*;
import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.JField;
import pascal.taie.language.classes.JMethod;
import pascal.taie.util.AnalysisException;
import pascal.taie.language.type.Type;

import java.util.List;

class Solver {

    private static final Logger logger = LogManager.getLogger(Solver.class);

    private final HeapModel heapModel;

    private DefaultCallGraph callGraph;

    private PointerFlowGraph pointerFlowGraph;

    private WorkList workList;

    private StmtProcessor stmtProcessor;

    private ClassHierarchy hierarchy;

    Solver(HeapModel heapModel) {
        this.heapModel = heapModel;
    }

    /**
     * Runs pointer analysis algorithm.
     */
    void solve() {
        initialize();
        analyze();
    }

    /**
     * Initializes pointer analysis.
     */
    private void initialize() {
        workList = new WorkList();
        pointerFlowGraph = new PointerFlowGraph();
        callGraph = new DefaultCallGraph();
        stmtProcessor = new StmtProcessor();
        hierarchy = World.get().getClassHierarchy();
        // initialize main method
        JMethod main = World.get().getMainMethod();
        callGraph.addEntryMethod(main);
        addReachable(main);
    }

    /**
     * Processes new reachable method.
     */
    private void addReachable(JMethod method) {
        // TODO - finish me
        // 在这个方法中，我们向可达方法的调用图中添加一个方法。
        // 如果调用图中不包含该方法，则将其添加到调用图中。
        // 然后，对方法的每个语句进行处理。

        if (!callGraph.contains(method)) {
            // 如果调用图中不包含该方法
            callGraph.addReachableMethod(method);
            // 将该方法添加到可达方法的调用图中

            for (Stmt stmt : method.getIR().getStmts()) {
                // 对于方法中的每个语句
                stmt.accept(stmtProcessor);
                // 调用语句处理器处理该语句
            }
        }
    }

    /**
     * Processes statements in new reachable methods.
     */
    private class StmtProcessor implements StmtVisitor<Void> {
        // TODO - if you choose to implement addReachable()
        //  via visitor pattern, then finish me
        @Override
        public Void visit(New stmt) {
            // 处理New语句
            // 获取左值的变量指针
            VarPtr p = pointerFlowGraph.getVarPtr(stmt.getLValue());
            // 获取对象的指针集合
            PointsToSet pointsToSet = (PointsToSet) heapModel.getObj(stmt);
            // 将左值的变量指针和对象的指针集合添加到工作列表中
            workList.addEntry(p, pointsToSet);
            return StmtVisitor.super.visit(stmt);
        }

        @Override
        public Void visit(Copy stmt) {
            // 处理Copy语句
            // 获取左值的变量指针
            VarPtr p = pointerFlowGraph.getVarPtr(stmt.getLValue());
            // 获取右值的变量指针
            VarPtr q = pointerFlowGraph.getVarPtr(stmt.getRValue());
            // 将右值的变量指针和左值的变量指针之间添加指针流图边
            addPFGEdge(q, p);
            return StmtVisitor.super.visit(stmt);
        }

        @Override
        public Void visit(LoadField stmt) {
            // 处理LoadField语句
            if (stmt.isStatic()) {
                // 如果是静态字段的加载
                // 解析字段引用并获取字段
                JField field = stmt.getFieldRef().resolve();
                // 获取静态字段的指针
                StaticField f = pointerFlowGraph.getStaticField(field);
                // 获取左值的变量指针
                VarPtr y = pointerFlowGraph.getVarPtr(stmt.getLValue());
                // 将静态字段的指针和左值的变量指针之间添加指针流图边
                addPFGEdge(f, y);
            }
            return StmtVisitor.super.visit(stmt);
        }

        @Override
        public Void visit(StoreField stmt) {
            // 处理StoreField语句
            if (stmt.isStatic()) {
                // 如果是静态字段的存储
                // 解析字段引用并获取字段
                StaticField f = pointerFlowGraph.getStaticField(stmt.getFieldRef().resolve());
                // 获取右值的变量指针
                VarPtr y = pointerFlowGraph.getVarPtr(stmt.getRValue());
                // 将右值的变量指针和静态字段的指针之间添加指针流图边
                addPFGEdge(y, f);
            }
            return StmtVisitor.super.visit(stmt);
        }

        @Override
        public Void visit(Invoke stmt) {
            // 处理Invoke语句
            if (stmt.isStatic()) {
                // 如果是静态调用
                // 解析目标方法
                JMethod method = resolveCallee(null, stmt);
                // 创建Invoke边，并将其添加到调用图中
                Edge<Invoke, JMethod> edge = new Edge<>(CallKind.STATIC, stmt, method);
                if (callGraph.addEdge(edge)) {
                    // 如果成功添加边，则执行以下操作
                    // 添加目标方法到可达方法的调用图中
                    addReachable(method);
                    // 处理参数之间的指针流图边
                    for (int i = 0; i < method.getParamCount(); i++) {
                        addPFGEdge(pointerFlowGraph.getVarPtr(stmt.getRValue().getArg(i)), pointerFlowGraph.getVarPtr(method.getIR().getParam(i)));
                    }
                    // 处理返回值之间的指针流图边
                    for (Var returnVar : method.getIR().getReturnVars()) {
                        addPFGEdge(pointerFlowGraph.getVarPtr(returnVar), pointerFlowGraph.getVarPtr(stmt.getLValue()));
                    }
                }
            }
            return StmtVisitor.super.visit(stmt);
        }
    }

    /**
     * Adds an edge "source -> target" to the PFG.
     */
    private void addPFGEdge(Pointer source, Pointer target) {
        // TODO - finish me
        if (!pointerFlowGraph.addEdge(source, target)) {
            // 如果指针流图中已经存在该边，则不执行任何操作，直接返回。
            return;
        }

        if (!source.getPointsToSet().isEmpty()) {
            // 如果源指针的指向集合不为空
            // 将目标指针和源指针的指向集合添加到工作列表中
            workList.addEntry(target, source.getPointsToSet());
        }
    }

    /**
     * Processes work-list entries until the work-list is empty.
     */
    private void analyze() {
        // TODO - finish me
        while (!workList.isEmpty()) {
            // 只要工作列表不为空，就进行循环处理

            WorkList.Entry entry = workList.pollEntry();
            // 从工作列表中获取一个条目

            PointsToSet delta = propagate(entry.pointer(), entry.pointsToSet());
            // 根据当前条目的指针和指向集合进行传播操作，并获取传播结果（delta）

            if (entry.pointer() instanceof VarPtr) {
                Var var = ((VarPtr) entry.pointer()).getVar();
                // 获取变量指针对应的变量对象

                for (Obj obj : delta) {
                    // 遍历传播结果中的每个对象

                    List<LoadArray> loadarrays = var.getLoadArrays();
                    List<StoreArray> storearrays = var.getStoreArrays();
                    List<LoadField> loadfields = var.getLoadFields();
                    List<StoreField> storefileds = var.getStoreFields();

                    loadarrays.forEach(stmt -> addPFGEdge(pointerFlowGraph.getArrayIndex(obj), pointerFlowGraph.getVarPtr(stmt.getLValue())));
                    // 对于变量的每个LoadArray语句，将数组索引指针和左值变量指针之间添加指针流图边

                    storearrays.forEach(stmt -> addPFGEdge(pointerFlowGraph.getVarPtr(stmt.getRValue()), pointerFlowGraph.getArrayIndex(obj)));
                    // 对于变量的每个StoreArray语句，将右值变量指针和数组索引指针之间添加指针流图边

                    loadfields.forEach(stmt -> addPFGEdge(pointerFlowGraph.getInstanceField(obj, stmt.getFieldAccess().getFieldRef().resolve()), pointerFlowGraph.getVarPtr(stmt.getLValue())));
                    // 对于变量的每个LoadField语句，将实例字段指针和左值变量指针之间添加指针流图边

                    storefileds.forEach(stmt -> addPFGEdge(pointerFlowGraph.getVarPtr(stmt.getRValue()), pointerFlowGraph.getInstanceField(obj, stmt.getFieldAccess().getFieldRef().resolve())));
                    // 对于变量的每个StoreField语句，将右值变量指针和实例字段指针之间添加指针流图边

                    processCall(var, obj);
                    // 处理变量和对象之间的调用关系
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
        PointsToSet delta = new PointsToSet();
        // 创建一个空的传播结果（delta）

        pointsToSet.forEach(p -> {
            // 遍历指向集合中的每个对象指针

            if (pointer.getPointsToSet().addObject(p)) {
                // 如果将对象指针添加到当前指针的指向集合中成功
                // 将对象指针添加到传播结果（delta）中
                delta.addObject(p);
            }
        });

        if (!delta.isEmpty()) {
            // 如果传播结果（delta）不为空
            // 遍历当前指针的后继节点
            pointerFlowGraph.getSuccsOf(pointer).forEach(succ -> {
                // 将后继节点和传播结果（delta）添加到工作列表中
                workList.addEntry(succ, delta);
            });
        }

        return delta;
        // 返回传播的结果（delta）
    }

    /**
     * Processes instance calls when points-to set of the receiver variable changes.
     *
     * @param var the variable that holds receiver objects
     * @param recv a new discovered object pointed by the variable.
     */
    private void processCall(Var var, Obj recv) {
        // TODO - finish me
        var.getInvokes().forEach(invoke -> {
            // 遍历变量的每个调用语句

            JMethod method = resolveCallee(recv, invoke);
            // 解析调用目标方法

            workList.addEntry(pointerFlowGraph.getVarPtr(method.getIR().getThis()), new PointsToSet(recv));
            // 将目标方法的this指针和对象的指向集合添加到工作列表中

            CallKind callkind = null;
            if (invoke.isStatic()) callkind = CallKind.STATIC;
            if (invoke.isInterface()) callkind = CallKind.INTERFACE;
            if (invoke.isSpecial()) callkind = CallKind.SPECIAL;
            if (invoke.isVirtual()) callkind = CallKind.VIRTUAL;

            Edge<Invoke, JMethod> edge = new Edge<>(callkind, invoke, method);
            if (callGraph.addEdge(edge)) {
                // 如果成功添加调用图的边，则执行以下操作

                addReachable(method);
                // 将目标方法添加到可达方法的调用图中

                for (int i = 0; i < method.getParamCount(); i++) {
                    addPFGEdge(pointerFlowGraph.getVarPtr(invoke.getRValue().getArg(i)), pointerFlowGraph.getVarPtr(method.getIR().getParam(i)));
                    // 处理参数之间的指针流图边
                }

                if (invoke.getLValue() != null) {
                    for (Var returnVar : method.getIR().getReturnVars()) {
                        addPFGEdge(pointerFlowGraph.getVarPtr(returnVar), pointerFlowGraph.getVarPtr(invoke.getLValue()));
                        // 处理返回值之间的指针流图边
                    }
                }
            }
        });
    }

    /**
     * Resolves the callee of a call site with the receiver object.
     *
     * @param recv     the receiver object of the method call. If the callSite
     *                 is static, this parameter is ignored (i.e., can be null).
     * @param callSite the call site to be resolved.
     * @return the resolved callee.
     */
    private JMethod resolveCallee(Obj recv, Invoke callSite) {
        Type type = recv != null ? recv.getType() : null;
        return CallGraphs.resolveCallee(type, callSite);
    }

    CIPTAResult getResult() {
        return new CIPTAResult(pointerFlowGraph, callGraph);
    }
}
