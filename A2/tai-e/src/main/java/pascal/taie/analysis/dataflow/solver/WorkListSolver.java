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

package pascal.taie.analysis.dataflow.solver;

import pascal.taie.analysis.dataflow.analysis.DataflowAnalysis;
import pascal.taie.analysis.dataflow.analysis.constprop.CPFact;
import pascal.taie.analysis.dataflow.analysis.constprop.ConstantPropagation;
import pascal.taie.analysis.dataflow.analysis.constprop.Value;
import pascal.taie.analysis.dataflow.fact.DataflowResult;
import pascal.taie.analysis.graph.cfg.CFG;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

class WorkListSolver<Node, Fact> extends Solver<Node, Fact> {

    WorkListSolver(DataflowAnalysis<Node, Fact> analysis) {
        super(analysis);
    }

    @Override
    protected void doSolveForward(CFG<Node> cfg, DataflowResult<Node, Fact> result) {
        // TODO - finish me
        // 初始化一个工作列表，包含控制流图（CFG）的所有节点。
        Set<Node> worklist = new HashSet<>() {{ addAll(cfg.getNodes()); }};
        // 当工作列表不为空时，进行迭代。
        while (!worklist.isEmpty()) {
            // 对于工作列表中的每一个节点，执行以下操作：
            (new HashSet<Node>(worklist)).forEach(node -> {
                // 从工作列表中移除当前节点。
                worklist.remove(node);
                // 创建一个新的初始fact。
                Fact in = this.analysis.newInitialFact();
                // 如果当前节点是CFG的入口节点，将所有可以保存整数的参数的fact更新为NAC。
                if (node == cfg.getEntry()) {
                    cfg.getIR().getParams().stream()
                            .filter(ConstantPropagation::canHoldInt)
                            .forEach(param -> ((CPFact) in).update(param, Value.getNAC()));
                }
                // 对于当前节点的所有前驱节点，将它们的出口fact与当前节点的入口fact进行meet操作。
                cfg.getPredsOf(node).forEach(pred -> this.analysis.meetInto(result.getOutFact(pred), in));
                // 将当前节点的入口fact设置为计算得到的fact。
                result.setInFact(node, in);
                // 对当前节点进行转移函数操作，如果结果发生变化，将当前节点的所有后继节点加入工作列表。
                if (this.analysis.transferNode(node, in, result.getOutFact(node))) {
                    worklist.addAll(cfg.getSuccsOf(node));
                }
            });
        }
    }

    @Override
    protected void doSolveBackward(CFG<Node> cfg, DataflowResult<Node, Fact> result) {
        throw new UnsupportedOperationException();
    }
}
