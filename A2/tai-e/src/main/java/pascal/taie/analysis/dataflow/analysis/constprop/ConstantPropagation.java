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
        CPFact fact = new CPFact();
        cfg.getIR().getParams().forEach(var -> {
            if (canHoldInt(var)) {
                fact.update(var, Value.getUndef());
            }
        });
        return fact;
    }

    @Override
    public CPFact newInitialFact() {
        // TODO - finish me
        return new CPFact();
    }

    @Override
    public void meetInto(CPFact fact, CPFact target) {
        // TODO - finish me
        fact.forEach((var, value) -> {
            target.update(var, meetValue(target.get(var), value));
        });
    }

    /**
     * Meets two Values.
     */
    public Value meetValue(Value v1, Value v2) {
        // TODO - finish me
        // NAC 交 v = NAC
        if (v1.isNAC() || v2.isNAC()) {
            return Value.getNAC();
        }

        // UNDEF 交 v = v
        if (v1.isUndef() || v2.isUndef()) {
            if (v1.isConstant()) {
                return Value.makeConstant(v1.getConstant());
            }
            if (v2.isConstant()) {
                return Value.makeConstant(v2.getConstant());
            }
        }

        if (v1.isConstant() && v2.isConstant()) {
            if (v1.getConstant() == v2.getConstant()) {
                return Value.makeConstant(v1.getConstant());
            }
            return Value.getNAC();
        }
        return Value.getUndef();
    }

    @Override
    public boolean transferNode(Stmt stmt, CPFact in, CPFact out) {
        // TODO - finish me
        boolean change = false;

        for(Var key : in.keySet()){
            change |= out.update(key, in.get(key));
        }

        if(stmt instanceof DefinitionStmt<?, ?> def){
            LValue left = def.getLValue();
            if(left instanceof Var x){
                if(canHoldInt(x)){
                    Value res = evaluate(def.getRValue(), in);
                    assert res != null;
                    change |= out.update(x, res);
                }
            }
        }
        return change;
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
        if (exp instanceof Var var) {
            if(in.get(var).isConstant()){
                return Value.makeConstant(in.get(var).getConstant());
            }
            return in.get(var);
        }

        if (exp instanceof IntLiteral intLiteral) {
            return Value.makeConstant(intLiteral.getValue());
        }

        if (exp instanceof BinaryExp binaryExp) {
//            Value v1 = in.get(binaryExp.getOperand1());
//            Value v2 = in.get(binaryExp.getOperand2());
            Value v1 = evaluate(binaryExp.getOperand1(), in);
            Value v2 = evaluate(binaryExp.getOperand2(), in);
            BinaryExp.Op op = binaryExp.getOperator();

            // 被除数为0
            if (v2.isConstant() && v2.getConstant()==0) {
                if (op instanceof ArithmeticExp.Op arithmeticOp) {
                    if (arithmeticOp == ArithmeticExp.Op.DIV || arithmeticOp == ArithmeticExp.Op.REM) {
                        return Value.getUndef();
                    }
                }
            }

            if (v1.isNAC() || v2.isNAC()) {
                return Value.getNAC();
            }

            if (v1.isUndef() || v2.isUndef()) {
                return Value.getUndef();
            }

            if (v1.isConstant() && v2.isConstant()) {
                int int1 = v1.getConstant();
                int int2 = v2.getConstant();
                if (op instanceof ArithmeticExp.Op arithmeticOp) {
                    return switch (arithmeticOp) {
                        case ADD -> Value.makeConstant(int1 + int2);
                        case SUB -> Value.makeConstant(int1 - int2);
                        case MUL -> Value.makeConstant(int1 * int2);
                        case DIV -> Value.makeConstant(int1 / int2);
                        case REM -> Value.makeConstant(int1 % int2);
                    };
                }
                if (op instanceof ShiftExp.Op shiftOp) {
                    return switch (shiftOp) {
                        case SHL -> Value.makeConstant(int1 << int2);
                        case SHR -> Value.makeConstant(int1 >> int2);
                        case USHR -> Value.makeConstant(int1 >>> int2);
                    };
                }
                if (op instanceof BitwiseExp.Op bitwiseOp) {
                    return switch (bitwiseOp) {
                        case OR -> Value.makeConstant(int1 | int2);
                        case AND -> Value.makeConstant(int1 & int2);
                        case XOR -> Value.makeConstant(int1 ^ int2);
                    };
                }
                if (op instanceof ConditionExp.Op conditionOp) {
                    return switch (conditionOp) {
                        case EQ -> Value.makeConstant(int1 == int2 ? 1 : 0);
                        case NE -> Value.makeConstant(int1 != int2 ? 1 : 0);
                        case GE -> Value.makeConstant(int1 >= int2 ? 1 : 0);
                        case GT -> Value.makeConstant(int1 > int2 ? 1 : 0);
                        case LE -> Value.makeConstant(int1 <= int2 ? 1 : 0);
                        case LT -> Value.makeConstant(int1 < int2 ? 1 : 0);
                    };
                }
            }
        }

        return Value.getNAC();
    }
}
