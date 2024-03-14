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

package pascal.taie.analysis.dataflow.analysis;

import pascal.taie.analysis.dataflow.fact.SetFact;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.exp.LValue;
import pascal.taie.ir.exp.RValue;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Stmt;

import javax.swing.text.html.Option;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of classic live variable analysis.
 */
public class LiveVariableAnalysis extends
        AbstractDataflowAnalysis<Stmt, SetFact<Var>> {

    public static final String ID = "livevar";

    public LiveVariableAnalysis(AnalysisConfig config) {
        super(config);
    }

    @Override
    public boolean isForward() {
        return false;
    }

    @Override
    public SetFact<Var> newBoundaryFact(CFG<Stmt> cfg) {
        // TODO - finish me
        // IN[exit] = Ø，初始化为空，返回空的SetFact。
        return new SetFact<>();
    }

    @Override
    public SetFact<Var> newInitialFact() {
        // TODO - finish me
        // IN[B] = Ø，初始化为空，返回空的SetFact。
        return new SetFact<>();
    }

    @Override
    public void meetInto(SetFact<Var> fact, SetFact<Var> target) {
        // TODO - finish me
        // 该函数无返回值，所以不能使用unionWith，因为该函数不会对原target作出修改。因此，使用union将fact并入target。
        target.union(fact);
    }

    @Override
    public boolean transferNode(Stmt stmt, SetFact<Var> in, SetFact<Var> out) {
        // TODO - finish me
        // (OUT[B] - def_B)
        // 拷贝一个out的副本out_B，因为不能修改out。
        SetFact<Var> out_B = out.copy();
        // 获取stmt的重定义，如果存在且为Var的实例，从out_B中移出。
        Optional<LValue> def_B = stmt.getDef();
        if (def_B.isPresent()) {
            if (def_B.get() instanceof Var) {
                out_B.remove((Var) def_B.get());
            }
        }
        // use_B
        SetFact<Var> use_B = new SetFact<>();
        // 获取stmt的使用变量，如果为Var的实例，加入use_B中。
        for (RValue use : stmt.getUses()) {
            if (use instanceof Var) {
                use_B.add((Var) use);
            }
        }
        // use_B ∪ (OUT[B] - def_B)
        out_B.union(use_B);
        // 如果out_B与in相同，说明IN未发生改变，返回false以终止算法的while循环。
        if (out_B.equals(in)) {
            return false;
        }
        // IN发生改变，将最终结果赋值给in，返回true，算法继续。
        in.set(out_B);
        return true;
    }
}
