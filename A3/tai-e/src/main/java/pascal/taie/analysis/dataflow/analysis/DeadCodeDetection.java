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

import pascal.taie.analysis.MethodAnalysis;
import pascal.taie.analysis.dataflow.analysis.constprop.CPFact;
import pascal.taie.analysis.dataflow.analysis.constprop.ConstantPropagation;
import pascal.taie.analysis.dataflow.fact.DataflowResult;
import pascal.taie.analysis.dataflow.fact.SetFact;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.analysis.graph.cfg.CFGBuilder;
import pascal.taie.analysis.graph.cfg.Edge;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.IR;
import pascal.taie.ir.exp.ArithmeticExp;
import pascal.taie.ir.exp.ArrayAccess;
import pascal.taie.ir.exp.CastExp;
import pascal.taie.ir.exp.FieldAccess;
import pascal.taie.ir.exp.NewExp;
import pascal.taie.ir.exp.RValue;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.If;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.ir.stmt.SwitchStmt;

import java.util.*;

public class DeadCodeDetection extends MethodAnalysis {

    public static final String ID = "deadcode";

    public DeadCodeDetection(AnalysisConfig config) {
        super(config);
    }

    @Override
    public Set<Stmt> analyze(IR ir) {
        // obtain CFG
        CFG<Stmt> cfg = ir.getResult(CFGBuilder.ID);
        // obtain result of constant propagation
        DataflowResult<Stmt, CPFact> constants =
                ir.getResult(ConstantPropagation.ID);
        // obtain result of live variable analysis
        DataflowResult<Stmt, SetFact<Var>> liveVars =
                ir.getResult(LiveVariableAnalysis.ID);
        // keep statements (dead code) sorted in the resulting set
        Set<Stmt> deadCode = new TreeSet<>(Comparator.comparing(Stmt::getIndex));
        // TODO - finish me
        // Your task is to recognize dead code in ir and add it to deadCode
        var unreachedStmt = new HashSet<Stmt>(cfg.getNodes());
        var workList = new LinkedList<Stmt>();
        // 将CFG的入口节点加入工作列表
        workList.add(cfg.getEntry());
        // 从未访问到的语句集合中移除CFG的出口节点
        unreachedStmt.remove(cfg.getExit());
        // 遍历CFG
        while (!workList.isEmpty()){
            // 从工作列表中取出一个语句
            var stmt = workList.pop();
            // 从未访问到的语句集合中移除这个语句
            unreachedStmt.remove(stmt);
            // 如果这个语句定义的变量是不活跃的，那么这个语句就是死代码
            if (isUnusedVar(stmt,liveVars)){
                deadCode.add(stmt);
            }
            // 如果这个语句是if语句
            if (stmt instanceof If ifStmt){
                // 获取if语句的条件
                var condition = ifStmt.getCondition();
                // 获取这个语句的常量传播结果
                var cpFact = constants.getResult(stmt);
                // 计算条件的值
                var value = ConstantPropagation.evaluate(condition,cpFact);

                for (Edge<Stmt> stmtEdge : cfg.getOutEdgesOf(stmt)) {
                    // 如果条件的值是不确定的，或者条件的值是真并且边的类型是IF_TRUE
                    if (value.isNAC() || (stmtEdge.getKind() == Edge.Kind.IF_TRUE && value.getConstant() == 1)){
                        // 获取边的目标节点
                        var target = stmtEdge.getTarget();
                        // 如果目标节点还未访问，那么将目标节点加入工作列表
                        if (unreachedStmt.contains(target)){
                            workList.add(target);
                        }
                    }

                    // 如果条件的值是不确定的，或者条件的值是假并且边的类型是IF_FALSE
                    if (value.isNAC() || (stmtEdge.getKind() == Edge.Kind.IF_FALSE && value.getConstant() == 0)){
                        // 获取边的目标节点
                        var target = stmtEdge.getTarget();
                        // 如果目标节点还未访问，那么将目标节点加入工作列表
                        if (unreachedStmt.contains(target)){
                            workList.add(target);
                        }
                    }
                }
            } else if (stmt instanceof SwitchStmt switchStmt){
                // 如果这个语句是switch语句
                // 获取switch语句的变量
                var switchVar = switchStmt.getVar();
                // 获取这个变量的值
                var value = constants.getResult(stmt).get(switchVar);
                if (value.isNAC()){
                    // 如果这个变量的值是不确定的，那么将所有后继节点加入工作列表
                    for (Stmt succStmt : cfg.getSuccsOf(stmt)) {
                        if (unreachedStmt.contains(succStmt)){
                            workList.add(succStmt);
                        }
                    }
                } else {
                    // 如果这个变量的值是确定的，那么只将匹配的case或default加入工作列表
                    var consValue = value.getConstant();
                    Edge<Stmt> defaultEdge = null;
                    var isMatch = false;
                    for (Edge<Stmt> stmtEdge : cfg.getOutEdgesOf(stmt)) {
                        if(stmtEdge.getKind() == Edge.Kind.SWITCH_DEFAULT){
                            defaultEdge = stmtEdge;
                            continue;
                        }

                        if (stmtEdge.getKind() == Edge.Kind.SWITCH_CASE){
                            if (consValue == stmtEdge.getCaseValue()){
                                isMatch = true;
                                var target = stmtEdge.getTarget();
                                if (unreachedStmt.contains(target)){
                                    workList.add(target);
                                }
                            }
                        }
                    }

                    if (!isMatch && defaultEdge != null){
                        var target = defaultEdge.getTarget();
                        if (unreachedStmt.contains(target)){
                            workList.add(target);
                        }
                    }
                }
            } else {
                // 如果这个语句是其他类型的语句，那么将所有后继节点加入工作列表
                for (Stmt succStmt : cfg.getSuccsOf(stmt)) {
                    if (unreachedStmt.contains(succStmt)){
                        workList.add(succStmt);
                    }
                }
            }
        }
        // 将所有未访问到的语句加入死代码集合
        deadCode.addAll(unreachedStmt);
        // 返回死代码集合
        return deadCode;
    }

    private boolean isUnusedVar(Stmt node,DataflowResult<Stmt,SetFact<Var>> liveVars){
        // 获取这个语句定义的变量
        var defVar = node.getDef();
        // 如果这个语句没有定义变量，那么它不是死代码
        if (defVar.isEmpty()){
            return false;
        }

        // 遍历这个语句使用的所有值
        for (RValue use : node.getUses()) {
            // 如果这个值有副作用，那么这个语句不是死代码
            if (!hasNoSideEffect(use)){
                return false;
            }
        }

        // 如果这个语句定义的是一个变量
        if (defVar.get() instanceof Var v){
            // 如果这个变量在这个语句之后是活跃的，那么这个语句不是死代码
            return !liveVars.getResult(node).contains(v);
        }
        // 如果这个语句定义的不是一个变量，那么它不是死代码
        return false;
    }


    /**
     * @return true if given RValue has no side effect, otherwise false.
     */
    private static boolean hasNoSideEffect(RValue rvalue) {
        // new expression modifies the heap
        if (rvalue instanceof NewExp ||
                // cast may trigger ClassCastException
                rvalue instanceof CastExp ||
                // static field access may trigger class initialization
                // instance field access may trigger NPE
                rvalue instanceof FieldAccess ||
                // array access may trigger NPE
                rvalue instanceof ArrayAccess) {
            return false;
        }
        if (rvalue instanceof ArithmeticExp) {
            ArithmeticExp.Op op = ((ArithmeticExp) rvalue).getOperator();
            // may trigger DivideByZeroException
            return op != ArithmeticExp.Op.DIV && op != ArithmeticExp.Op.REM;
        }
        return true;
    }
}
