package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.util;

import java.util.Set;

import de.uni_freiburg.informatik.ultimate.boogie.symboltable.BoogieSymbolTable;
import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.model.IType;
import de.uni_freiburg.informatik.ultimate.model.boogie.BoogieVar;
import de.uni_freiburg.informatik.ultimate.model.boogie.DeclarationInformation;
import de.uni_freiburg.informatik.ultimate.model.boogie.IBoogieVar;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.IdentifierExpression;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.VariableDeclaration;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.BoogieConst;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.BinaryExpression.Operator;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.ConstDeclaration;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.Declaration;

/**
 * Utility functions for objects from the Boogie abstract syntax tree (AST).
 * 
 * @author schaetzc@informatik.uni-freiburg.de
 */
public class BoogieUtil {

	public static IBoogieVar createTemporaryIBoogieVar(String identifier, IType type) {
		return new IBoogieVar() {
			@Override
			public String getIdentifier() {
				return identifier;
			}

			@Override
			public IType getIType() {
				return type;
			}

			@Override
			public ApplicationTerm getDefaultConstant() {
				throw new UnsupportedOperationException("Temporary IBoogieVars dont have default constants.");
			}
		};
	}

	/**
     * Determines if a {@link IdentifierExpression} references a variable or constant.
     * {@link IdentifierExpression} can also reference functions or procedures.
     * In that case, this method will return {@code false}.
     * 
     * @param ie {@link IdentifierExpression}
     * @return expression references a variable or constant
     */
	public static boolean isVariable(IdentifierExpression ie) {
		DeclarationInformation di = ie.getDeclarationInformation();
		switch (di.getStorageClass()) {
		case PROC_FUNC:
		case IMPLEMENTATION:
			return false;
		case GLOBAL:
		case IMPLEMENTATION_INPARAM:
		case IMPLEMENTATION_OUTPARAM:
		case LOCAL:
		case QUANTIFIED:
		case PROC_FUNC_INPARAM:
		case PROC_FUNC_OUTPARAM:
			return true;
		default:
			throw new IllegalArgumentException("Unknown storage class: " + di.getStorageClass());
		}
	}
	
	public static boolean isGlobal(IBoogieVar ibv) {
		if (ibv instanceof BoogieVar) {
			return ((BoogieVar) ibv).isGlobal();
		} else if (ibv instanceof BoogieConst) {
			return true;
		} else {
			throw new AssertionError("Unknown IBoogieVar type: " + ibv.getClass().getName());
		}
	}

	public static Operator negateRelOp(Operator relOp) {
		switch (relOp) {
		case COMPEQ:
			return Operator.COMPNEQ;
		case COMPNEQ:
			return Operator.COMPEQ;
		case COMPLEQ:
			return Operator.COMPGT;
		case COMPGT:
			return Operator.COMPLEQ;
		case COMPLT:
			return Operator.COMPGEQ;
		case COMPGEQ:
			return Operator.COMPLT;
		default:
			throw new IllegalArgumentException("Not a negatable relational operator: " + relOp);
		}
	}

}
