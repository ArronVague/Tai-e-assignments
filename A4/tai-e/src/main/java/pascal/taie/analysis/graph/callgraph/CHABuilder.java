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

package pascal.taie.analysis.graph.callgraph;

import pascal.taie.World;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.classes.Subsignature;

import java.util.*;

/**
 * Implementation of the CHA algorithm.
 */
class CHABuilder implements CGBuilder<Invoke, JMethod> {

    private ClassHierarchy hierarchy;

    @Override
    public CallGraph<Invoke, JMethod> build() {
        hierarchy = World.get().getClassHierarchy();
        return buildCallGraph(World.get().getMainMethod());
    }

    private CallGraph<Invoke, JMethod> buildCallGraph(JMethod entry) {
        // TODO - finish me
        DefaultCallGraph callGraph = new DefaultCallGraph();
        // 将入口方法添加到调用图中
        callGraph.addEntryMethod(entry);

        // 创建一个工作列表（WL），用于存储待处理的方法
        Deque<JMethod> WL = new ArrayDeque<>();

        // 将入口方法添加到工作列表中
        WL.push(entry);
        // 当工作列表不为空时，持续处理列表中的方法
        while(!WL.isEmpty()){
            // 从工作列表中取出（并移除）一个方法
            JMethod method = WL.pop();
            // 如果这个方法已经被处理过（即它已经在可达方法集合中），则跳过这个方法
            if(callGraph.reachableMethods.contains(method)) continue;

            // 将这个方法添加到可达方法集合中
            callGraph.addReachableMethod(method);
            // 获取这个方法中的所有调用点
            for(Invoke callsite : callGraph.getCallSitesIn(method)){
                // 对于每个调用点，解析它可能调用的所有目标方法
                Set<JMethod> T = resolve(callsite);
                // 对于每个目标方法
                for(JMethod targetmethod : T){
                    // 在调用图中添加一条从当前方法到目标方法的边
                    callGraph.addEdge(new Edge<>(CallGraphs.getCallKind(callsite), callsite, targetmethod));
                    // 将目标方法添加到工作列表中，以便后续处理
                    WL.push(targetmethod);
                }
            }
        }
        // 返回构建的调用图
        return callGraph;
    }

    /**
     * Resolves call targets (callees) of a call site via CHA.
     */
    private Set<JMethod> resolve(Invoke callSite) {
        // TODO - finish me
        // 创建一个新的集合，用于存储调用点可能调用的所有目标方法
        Set<JMethod> T = new HashSet<>();
        // 获取调用点的方法签名和声明该方法的类
        Subsignature method_signature = callSite.getMethodRef().getSubsignature();
        JClass method_class = callSite.getMethodRef().getDeclaringClass();

        // 根据调用的类型，解析可能的目标方法
        switch (CallGraphs.getCallKind(callSite)) {
            // 对于静态调用和特殊调用，只有一个可能的目标方法
            case STATIC, SPECIAL -> T.add(dispatch(method_class, method_signature));
            // 对于虚拟调用，可能的目标方法包括声明类及其所有子类中的同签名方法
            case VIRTUAL -> {
                T.add(dispatch(method_class, method_signature));
                Deque<JClass> dq = new ArrayDeque<>(hierarchy.getDirectSubclassesOf(method_class));
                while(!dq.isEmpty()){
                    JClass c = dq.poll();
                    T.add(dispatch(c,method_signature));
                    dq.addAll(hierarchy.getDirectSubclassesOf(c));
                }
            }
            // 对于接口调用，可能的目标方法包括声明接口及其所有子接口和实现类中的同签名方法
            case INTERFACE -> {
                T.add(dispatch(method_class, method_signature));
                Deque<JClass> dq = new ArrayDeque<>();
                dq.addAll(hierarchy.getDirectSubinterfacesOf(method_class));
                dq.addAll(hierarchy.getDirectImplementorsOf(method_class));

                while(!dq.isEmpty()){
                    JClass c = dq.poll();
                    T.add(dispatch(c,method_signature));
                    dq.addAll(hierarchy.getDirectSubclassesOf(c));
                    dq.addAll(hierarchy.getDirectSubinterfacesOf(c));
                    dq.addAll(hierarchy.getDirectImplementorsOf(c));
                }
            }
        }
        // 移除集合中的 null 元素（如果有的话），因为 null 不是一个有效的方法
        T.remove(null);
        // 返回可能的目标方法集合
        return T;
    }

    /**
     * Looks up the target method based on given class and method subsignature.
     *
     * @return the dispatched target method, or null if no satisfying method
     * can be found.
     */
    private JMethod dispatch(JClass jclass, Subsignature subsignature) {
        // TODO - finish me
        // 如果类是 null，返回 null，因为 null 类中没有方法
        if(jclass == null) return null;
        // 获取类中具有指定签名的方法
        JMethod method = jclass.getDeclaredMethod(subsignature);
        // 如果在类中找不到该方法，或者找到的方法是抽象的
        if(method == null || method.isAbstract()) {
            // 在类的父类中查找该方法
            return dispatch(jclass.getSuperClass(), subsignature);
        }
        // 如果在类中找到了非抽象的方法，返回该方法
        return method;
    }
}
