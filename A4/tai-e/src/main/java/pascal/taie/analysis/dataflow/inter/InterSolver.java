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

package pascal.taie.analysis.dataflow.inter;

import pascal.taie.analysis.dataflow.fact.DataflowResult;
import pascal.taie.analysis.graph.icfg.ICFG;
import pascal.taie.util.collection.SetQueue;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Solver for inter-procedural data-flow analysis.
 * The workload of inter-procedural analysis is heavy, thus we always
 * adopt work-list algorithm for efficiency.
 */
class InterSolver<Method, Node, Fact> {

    private final InterDataflowAnalysis<Node, Fact> analysis;

    private final ICFG<Method, Node> icfg;

    private DataflowResult<Node, Fact> result;

    private Queue<Node> workList;

    InterSolver(InterDataflowAnalysis<Node, Fact> analysis,
                ICFG<Method, Node> icfg) {
        this.analysis = analysis;
        this.icfg = icfg;
    }

    DataflowResult<Node, Fact> solve() {
        result = new DataflowResult<>();
        initialize();
        doSolve();
        return result;
    }

    private void initialize() {
        // TODO - finish me
        // 创建一个包含所有入口方法的集合
        Set<Node> entrys = new HashSet<>();
        icfg.entryMethods().toList().forEach(entry ->{
            // 将入口方法的入口节点添加到集合中
            entrys.add(icfg.getEntryOf(entry));
        });

        // 遍历 ICFG 中的所有节点
        for(Node node : icfg){
            // 如果一个节点是一个入口
            if(entrys.contains(node)){
                // 用边界事实初始化输入事实
                result.setInFact(node,analysis.newBoundaryFact(node));
                // 用边界事实初始化输出事实
                result.setOutFact(node,analysis.newBoundaryFact(node));
            }
            // 如果一个节点不是一个入口
            else{
                // 用初始事实初始化输入事实
                result.setInFact(node,analysis.newInitialFact());
                // 用初始事实初始化输出事实
                result.setOutFact(node,analysis.newInitialFact());
            }
        }
    }

    private void doSolve() {
        // TODO - finish me
        // 创建一个工作列表
        workList = new LinkedList<>();

        // 将 ICFG 中的所有节点添加到工作列表中
        for (Node node : icfg) {
            workList.add(node);
        }

        // 循环处理工作列表中的节点
        while (!workList.isEmpty()) {
            // 获取并移除工作列表的头部节点
            Node node = workList.poll();
            // 遍历所有的输入边
            icfg.getInEdgesOf(node).forEach(inedge ->{
                // 应用转移函数
                Fact t = analysis.transferEdge(inedge,result.getOutFact(inedge.getSource()));
                // 将结果与节点的输入事实进行 meet 操作
                analysis.meetInto(t,result.getInFact(node));
            });
            // 如果节点的转移函数改变了输出事实
            if(analysis.transferNode(node,result.getInFact(node),result.getOutFact(node))){
                // 遍历所有的输出边
                icfg.getOutEdgesOf(node).forEach(outedge->{
                    // 获取输出边的目标节点
                    Node outnode = outedge.getTarget();
                    // 如果目标节点还不在工作列表中，将它添加到工作列表中
                    if(!workList.contains(outnode)) workList.add(outnode);
                });
            }
        }
    }
}
