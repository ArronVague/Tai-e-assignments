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

package pascal.taie.analysis.dataflow.analysis.constprop;

import pascal.taie.analysis.dataflow.analysis.AbstractDataflowAnalysis;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.IR;
import pascal.taie.ir.exp.*;
import pascal.taie.ir.stmt.DefinitionStmt;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.type.PrimitiveType;
import pascal.taie.language.type.Type;
import pascal.taie.util.AnalysisException;

public class ConstantPropagation extends
        AbstractDataflowAnalysis<Stmt, CPFact> {

    public static final String ID = "constprop";

    public ConstantPropagation(AnalysisConfig config) {
        super(config);
    }

    @Override
    public boolean isForward() {
        return true;
    }

    @Override
    public CPFact newBoundaryFact(CFG<Stmt> cfg) {
        // TODO - finish me
        // 这里的CPFact对象用于表示控制流图的边界情况。
        return new CPFact();
    }

    @Override
    public CPFact newInitialFact() {
        // TODO - finish me
        // 这里的CPFact对象用于表示分析的初始状态。
        return new CPFact();
    }

    @Override
    public void meetInto(CPFact fact, CPFact target) {
        // TODO - finish me
        // 对每个条目，获取其键值对应的Value对象，然后使用meetValue方法与条目的Value对象进行meet操作。
        // meet操作的结果用于更新target中对应键的Value对象。
        fact.entries().forEach(entry -> target.update(entry.getKey(), meetValue(target.get(entry.getKey()), entry.getValue())));
    }

    /**
     * Meets two Values.
     */
    public Value meetValue(Value v1, Value v2) {
        // TODO - finish me
        if (v1.isNAC() || v2.isNAC()) {
            // 如果v1或v2是NAC，返回NAC。
            return Value.getNAC();
        }
        if (v1.isConstant() && v2.isConstant()) {
            // 如果v1和v2都是常量，且它们相等，返回v1；否则返回NAC。
            return v1.equals(v2) ? v1 : Value.getNAC();
        }
        // 如果v1是Undefined，返回v2；否则返回v1。
        return v1.isUndef() ? v2 : v1;
    }

    @Override
    public boolean transferNode(Stmt stmt, CPFact in, CPFact out) {
        // TODO - finish me
        // 首先，创建一个新的CPFact对象newOut，其内容是输入CPFact的副本。
        CPFact newOut = in.copy();

        if (stmt instanceof DefinitionStmt
                && stmt.getDef().filter(v -> v instanceof Var && canHoldInt((Var) v)).isPresent()) {
            // 如果语句是一个定义语句，且定义的变量是一个可以持有整数值的Var对象，
            // 那么就对右值进行求值，然后在newOut中更新该Var对象的值。
            newOut.update((Var) ((DefinitionStmt<?, ?>) stmt).getLValue(), evaluate(((DefinitionStmt<?, ?>) stmt).getRValue(), in));
        }
        // 检查newOut是否与out相等。
        boolean result = !newOut.equals(out);
        // 清空out，并将newOut的值复制到out。
        out.clear();
        out.copyFrom(newOut);
        // 返回一个布尔值，表示out是否被修改。
        return result;
    }

    /**
     * @return true if the given variable can hold integer value, otherwise false.
     */
    public static boolean canHoldInt(Var var) {
        Type type = var.getType();
        if (type instanceof PrimitiveType) {
            switch ((PrimitiveType) type) {
                case BYTE:
                case SHORT:
                case INT:
                case CHAR:
                case BOOLEAN:
                    return true;
            }
        }
        return false;
    }

    /**
     * Evaluates the {@link Value} of given expression.
     *
     * @param exp the expression to be evaluated
     * @param in  IN fact of the statement
     * @return the resulting {@link Value}
     */
    public static Value evaluate(Exp exp, CPFact in) {
        // TODO - finish me
        // 如果表达式是一个整数字面量，返回该整数的值作为常量Value。
        if (exp instanceof IntLiteral) {
            return Value.makeConstant(((IntLiteral) exp).getValue());
        }
        // 如果表达式是一个变量，返回在输入CPFact中该变量的Value。
        if (exp instanceof Var) {
            return in.get((Var) exp);
        }
        // 如果表达式是一个算术表达式，并且右操作数为0，以及操作符是除法或取余，返回Undefined值。
        if (exp instanceof ArithmeticExp) {
            final Value right = evaluate(((ArithmeticExp) exp).getOperand2(), in);
            if (right.isConstant() && right.getConstant() == 0
                    && (((ArithmeticExp) exp).getOperator() == ArithmeticExp.Op.DIV
                    || ((ArithmeticExp) exp).getOperator() == ArithmeticExp.Op.REM)) {
                return Value.getUndef();
            }
        }
        // 如果表达式是一个二元表达式，对其进行求值。
        if (exp instanceof BinaryExp binaryExp) {
            // 分别求值左操作数和右操作数。
            final Value left = evaluate(binaryExp.getOperand1(), in);
            final Value right = evaluate(binaryExp.getOperand2(), in);
            // 如果左操作数或右操作数是NAC，返回NAC。
            if (left.isNAC() || right.isNAC()) {
                return Value.getNAC();
            }
            // 如果左操作数或右操作数不是常量，返回Undefined。
            if (!left.isConstant() || !right.isConstant()) {
                return Value.getUndef();
            }
            // 如果二元表达式是一个算术表达式，根据操作符进行相应的计算。
            if (binaryExp instanceof ArithmeticExp arithmeticExp) {
                // 对应加、减、乘、除和取余五种操作，注意在除和取余操作中，如果右操作数为0，返回Undefined。
                return switch (arithmeticExp.getOperator()) {
                    case ADD -> Value.makeConstant(left.getConstant() + right.getConstant());
                    case SUB -> Value.makeConstant(left.getConstant() - right.getConstant());
                    case MUL -> Value.makeConstant(left.getConstant() * right.getConstant());
                    case DIV -> right.getConstant() == 0
                            ? Value.getUndef()
                            : Value.makeConstant(left.getConstant() / right.getConstant());
                    case REM -> right.getConstant() == 0
                            ? Value.getUndef()
                            : Value.makeConstant(left.getConstant() % right.getConstant());
                };
            }
            // 如果二元表达式是一个条件表达式，根据操作符进行相应的比较。
            if (binaryExp instanceof ConditionExp conditionExp) {
                // 对应等于、不等于、小于、大于、小于等于和大于等于六种操作，如果比较结果为真，返回1，否则返回0。
                return switch (conditionExp.getOperator()) {
                    case EQ -> Value.makeConstant(left.getConstant() == right.getConstant() ? 1 : 0);
                    case NE -> Value.makeConstant(left.getConstant() != right.getConstant() ? 1 : 0);
                    case LT -> Value.makeConstant(left.getConstant() < right.getConstant() ? 1 : 0);
                    case GT -> Value.makeConstant(left.getConstant() > right.getConstant() ? 1 : 0);
                    case LE -> Value.makeConstant(left.getConstant() <= right.getConstant() ? 1 : 0);
                    case GE -> Value.makeConstant(left.getConstant() >= right.getConstant() ? 1 : 0);
                };
            }
            // 如果二元表达式是一个位移表达式，根据操作符进行相应的位移操作。
            if (binaryExp instanceof ShiftExp shiftExp) {
                // 对应左移、有符号右移和无符号右移三种操作。
                return switch (shiftExp.getOperator()) {
                    case SHL -> Value.makeConstant(left.getConstant() << right.getConstant());
                    case SHR -> Value.makeConstant(left.getConstant() >> right.getConstant());
                    case USHR -> Value.makeConstant(left.getConstant() >>> right.getConstant());
                };
            }
            // 如果二元表达式是一个位运算表达式，根据操作符进行相应的位运算。
            if (binaryExp instanceof BitwiseExp bitwiseExp) {
                // 对应或、与和异或三种操作。
                return switch (bitwiseExp.getOperator()) {
                    case OR -> Value.makeConstant(left.getConstant() | right.getConstant());
                    case AND -> Value.makeConstant(left.getConstant() & right.getConstant());
                    case XOR -> Value.makeConstant(left.getConstant() ^ right.getConstant());
                };
            }
        }
        // 如果表达式不符合以上任何一种类型，返回NAC。
        return Value.getNAC();
    }
}
