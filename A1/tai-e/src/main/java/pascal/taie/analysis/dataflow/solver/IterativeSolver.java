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
import pascal.taie.analysis.dataflow.analysis.LiveVariableAnalysis;
import pascal.taie.analysis.dataflow.fact.DataflowResult;
import pascal.taie.analysis.dataflow.fact.SetFact;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.ir.exp.Var;

class IterativeSolver<Node, Fact> extends Solver<Node, Fact> {

    public IterativeSolver(DataflowAnalysis<Node, Fact> analysis) {
        super(analysis);
    }

    @Override
    protected void doSolveForward(CFG<Node> cfg, DataflowResult<Node, Fact> result) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doSolveBackward(CFG<Node> cfg, DataflowResult<Node, Fact> result) {
        // TODO - finish me
        // 记录IN是否发生变化。
        boolean changed = true;
        while (changed) {
            // 每次循环开始，初始化changed为false。
            changed = false;
            for (Node node : cfg) {
                // OUT[B] = union IN[B]
                // 获取节点B的IN和OUT集合。
                Fact out_B = result.getOutFact(node);
                Fact in_B = result.getInFact(node);
                // 对B的每一个后继节点，将其IN集合合并到OUT[B]中。
                for (Node succ : cfg.getSuccsOf(node)) {
                    analysis.meetInto(result.getInFact(succ), out_B);
                }
                // 如果转换函数的返回值为true，说明IN[B]发生了变化，将changed置为true。
                if (analysis.transferNode(node, in_B, out_B)) {
                    changed = true;
                }
                // 更新节点B的OUT和IN集合。
                result.setOutFact(node, out_B);
                result.setInFact(node, in_B);
            }
        }
    }
}
