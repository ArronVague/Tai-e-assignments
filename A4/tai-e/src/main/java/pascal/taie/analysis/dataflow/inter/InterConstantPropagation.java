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

import pascal.taie.analysis.dataflow.analysis.constprop.CPFact;
import pascal.taie.analysis.dataflow.analysis.constprop.ConstantPropagation;
import pascal.taie.analysis.dataflow.analysis.constprop.Value;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.analysis.graph.cfg.CFGBuilder;
import pascal.taie.analysis.graph.icfg.CallEdge;
import pascal.taie.analysis.graph.icfg.CallToReturnEdge;
import pascal.taie.analysis.graph.icfg.NormalEdge;
import pascal.taie.analysis.graph.icfg.ReturnEdge;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.IR;
import pascal.taie.ir.exp.InvokeExp;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.classes.JMethod;

import static pascal.taie.analysis.dataflow.analysis.constprop.ConstantPropagation.canHoldInt;

/**
 * Implementation of interprocedural constant propagation for int values.
 */
public class InterConstantPropagation extends
        AbstractInterDataflowAnalysis<JMethod, Stmt, CPFact> {

    public static final String ID = "inter-constprop";

    private final ConstantPropagation cp;

    public InterConstantPropagation(AnalysisConfig config) {
        super(config);
        cp = new ConstantPropagation(new AnalysisConfig(ConstantPropagation.ID));
    }

    @Override
    public boolean isForward() {
        return cp.isForward();
    }

    @Override
    public CPFact newBoundaryFact(Stmt boundary) {
        IR ir = icfg.getContainingMethodOf(boundary).getIR();
        return cp.newBoundaryFact(ir.getResult(CFGBuilder.ID));
    }

    @Override
    public CPFact newInitialFact() {
        return cp.newInitialFact();
    }

    @Override
    public void meetInto(CPFact fact, CPFact target) {
        cp.meetInto(fact, target);
    }

    @Override
    protected boolean transferCallNode(Stmt stmt, CPFact in, CPFact out) {
        // TODO - finish me
        // 创建输出事实的一个副本，用于后续的比较
        CPFact Copy = out.copy();
        // 遍历输入事实中的每一项
        for (Var key : in.keySet()) {
            // 用输入事实的值更新输出事实
            out.update(key, in.get(key));
        }
        // 如果输出事实在这个过程中发生了变化（即，更新后的输出事实和原始的输出事实不相等），返回 true
        // 否则，返回 false
        return !out.equals(Copy);
    }

    @Override
    protected boolean transferNonCallNode(Stmt stmt, CPFact in, CPFact out) {
        // TODO - finish me
        // 在非调用节点上应用常量传播规则，同时更新输入事实（in）和输出事实（out）
        // 如果在这个过程中输出事实发生了变化，`transferNode` 方法应该返回 true
        // 否则，返回 false
        return cp.transferNode(stmt, in, out);
    }

    @Override
    protected CPFact transferNormalEdge(NormalEdge<Stmt> edge, CPFact out) {
        // TODO - finish me
        // 创建并返回输出事实的一个副本
        // 在这个实现中，正常边不会改变事实
        return out.copy();
    }

    @Override
    protected CPFact transferCallToReturnEdge(CallToReturnEdge<Stmt> edge, CPFact out) {
        // TODO - finish me
        // 创建输出事实的一个副本
        CPFact temp = out.copy();
        // 获取边的源语句
        Stmt source = edge.getSource();
        // 检查源语句是否定义了一个变量
        source.getDef().ifPresent(var->{
            // 如果定义了一个变量，从副本中移除该变量
            // 这意味着在调用返回后，我们不再知道该变量的值
            if(var instanceof Var) temp.remove((Var) var);
        });
        // 返回更新后的事实
        return temp;
    }

    @Override
    protected CPFact transferCallEdge(CallEdge<Stmt> edge, CPFact callSiteOut) {
        // TODO - finish me
        // 创建一个新的事实
        CPFact out = new CPFact();
        // 获取调用表达式
        Invoke invoke = (Invoke) edge.getSource();
        InvokeExp invokeExp = invoke.getInvokeExp();
        // 获取被调用的方法的中间表示
        IR ir = edge.getCallee().getIR();

        // 遍历调用表达式的所有参数
        for (int i = 0; i < invokeExp.getArgCount(); i++) {
            // 获取参数
            Var param = ir.getParam(i);
            // 获取调用站点的输出事实的值
            Value value = callSiteOut.get(invokeExp.getArg(i));
            // 如果参数可以持有整数，用调用站点的输出事实的值更新新的事实
            if (canHoldInt(param)) out.update(param, value);
        }
        // 返回更新后的事实
        return out;
    }

    @Override
    protected CPFact transferReturnEdge(ReturnEdge<Stmt> edge, CPFact returnOut) {
        // TODO - finish me
        // 创建一个新的事实
        CPFact out = new CPFact();
        // 获取调用站点
        Invoke invoke = (Invoke) edge.getCallSite();
        // 获取左值
        Var var = invoke.getLValue();
        // 初始化一个值为 undef（未定义）
        final Value[] ans = {Value.getUndef()};
        // 如果没有左值，返回新的事实
        if (var == null) return out;
        // 遍历所有的返回变量
        edge.getReturnVars().forEach(rv->{
            // 获取返回的输出事实的值
            Value value = returnOut.get(rv);
            // 用返回的输出事实的值和当前值进行 meet 操作
            ans[0] = cp.meetValue(ans[0],value);
        });
        // 用结果更新新的事实
        out.update(var, ans[0]);
        // 返回更新后的事实
        return out;
    }
}
