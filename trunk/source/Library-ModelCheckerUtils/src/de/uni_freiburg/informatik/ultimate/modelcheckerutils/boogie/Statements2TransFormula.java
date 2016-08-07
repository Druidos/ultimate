/*
 * Copyright (C) 2014-2015 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2014-2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2012-2015 University of Freiburg
 * 
 * This file is part of the ULTIMATE ModelCheckerUtils Library.
 * 
 * The ULTIMATE ModelCheckerUtils Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * The ULTIMATE ModelCheckerUtils Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE ModelCheckerUtils Library. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE ModelCheckerUtils Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP), 
 * containing parts covered by the terms of the Eclipse Public License, the 
 * licensors of the ULTIMATE ModelCheckerUtils Library grant you additional permission 
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.boogie.DeclarationInformation;
import de.uni_freiburg.informatik.ultimate.boogie.DeclarationInformation.StorageClass;
import de.uni_freiburg.informatik.ultimate.boogie.ast.AssertStatement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.AssignmentStatement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.AssumeStatement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.BoogieASTNode;
import de.uni_freiburg.informatik.ultimate.boogie.ast.CallStatement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.EnsuresSpecification;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Expression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.HavocStatement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.LeftHandSide;
import de.uni_freiburg.informatik.ultimate.boogie.ast.ModifiesSpecification;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Procedure;
import de.uni_freiburg.informatik.ultimate.boogie.ast.RequiresSpecification;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Specification;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Statement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.VarList;
import de.uni_freiburg.informatik.ultimate.boogie.ast.VariableLHS;
import de.uni_freiburg.informatik.ultimate.core.model.models.ILocation;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.logic.QuantifiedFormula;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Script.LBool;
import de.uni_freiburg.informatik.ultimate.logic.Sort;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.logic.Util;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.Expression2Term.IdentifierTranslator;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.Expression2Term.MultiTermResult;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.Expression2Term.SingleTermResult;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.TransFormula;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.TransFormula.Infeasibility;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.TransFormulaBuilder;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils.SimplicationTechnique;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.partialQuantifierElimination.XnfDer;
import de.uni_freiburg.informatik.ultimate.util.ToolchainCanceledException;

/**
 * Translates statements into TransFormulas. The resulting TransFormula encodes
 * the transition relation of the statements as SMT formula.
 * 
 * Idea of underlying algorithm: Starts at the end of the statement sequence
 * take current variables as outVars and then computes the inVars by traversing
 * the sequence of statements backwards and computing some kind of weakest
 * precondition.
 * 
 * @author Matthias Heizmann
 * 
 */
public class Statements2TransFormula {

	/**
	 * Compute Formulas that encode violation of one of the added assert
	 * statements. This feature was used in Evrens old CFG.
	 */
	private final static boolean s_ComputeAsserts = false;
	private final static String s_ComputeAssertsNotAvailable = "computation of asserts not available";

	private final Script mScript;
	private final ManagedScript mMgdScript;
	private final BoogieDeclarations mBoogieDeclarations;
	private final Boogie2SMT mBoogie2SMT;
	private final Boogie2SmtSymbolTable mBoogie2SmtSymbolTable;
	private final Expression2Term mExpression2Term;

	private String mCurrentProcedure;

	private TransFormulaBuilder mTransFormulaBuilder;
	private Set<TermVariable> mAuxVars;

	private Term mAssumes;
	private Term mAsserts;
	private final IUltimateServiceProvider mServices;
	private Map<String, ILocation> mOverapproximations = null;

	public Statements2TransFormula(final Boogie2SMT boogie2smt, final IUltimateServiceProvider services, final Expression2Term expression2Term) {
		super();
		mServices = services;
		mBoogie2SMT = boogie2smt;
		mScript = boogie2smt.getScript();
		mMgdScript = boogie2smt.getManagedScript();
		mExpression2Term = expression2Term;
		mBoogie2SmtSymbolTable = mBoogie2SMT.getBoogie2SmtSymbolTable();
		mBoogieDeclarations = mBoogie2SMT.getBoogieDeclarations();
	}

	/**
	 * Initialize fields to allow construction of a new TransFormula
	 * 
	 * @param procId
	 */
	private void initialize(final String procId) {
		assert mCurrentProcedure == null;
		assert mTransFormulaBuilder == null;
		assert mAuxVars == null;
		assert mAssumes == null;
		
		mOverapproximations = new HashMap<>();
		mCurrentProcedure = procId;
		mTransFormulaBuilder = new TransFormulaBuilder(null, null, true, null, false);
		mAuxVars = new HashSet<>();
		mAssumes = mScript.term("true");
		if (s_ComputeAsserts) {
			mAsserts = mScript.term("true");
		}
	}

	private TranslationResult getTransFormula(final boolean simplify, final boolean feasibilityKnown, final SimplicationTechnique simplicationTechnique) {
		TransFormula tf = null;
		try {
			tf = constructTransFormula(simplify, feasibilityKnown, simplicationTechnique);
			mCurrentProcedure = null;
			mTransFormulaBuilder = null;
			mAuxVars = null;
			mAssumes = null;
		} catch (final ToolchainCanceledException tce) {
			throw new ToolchainCanceledException(getClass(), tce.getRunningTaskInfo() + " while contructing transformula");
		}
		return new TranslationResult(tf, mOverapproximations);
	}

	private TransFormula constructTransFormula(final boolean simplify, final boolean feasibilityKnown, final SimplicationTechnique simplicationTechnique) {
		final Set<TermVariable> auxVars = mAuxVars;
		Term formula = mAssumes;
		formula = eliminateAuxVars(mAssumes, auxVars);

		Infeasibility infeasibility = null;
		if (simplify) {
			formula = SmtUtils.simplify(mMgdScript, formula, mServices, simplicationTechnique);
			if (formula == mScript.term("false")) {
				infeasibility = Infeasibility.INFEASIBLE;
			}
		}

		if (feasibilityKnown) {
			infeasibility = Infeasibility.UNPROVEABLE;
		}

		if (infeasibility == null) {
			if (simplify) {
				infeasibility = Infeasibility.UNPROVEABLE;
			} else {
				final LBool isSat = Util.checkSat(mScript, formula);
				if (isSat == LBool.UNSAT) {
					formula = mScript.term("false");
					infeasibility = Infeasibility.INFEASIBLE;
				} else {
					infeasibility = Infeasibility.UNPROVEABLE;
				}

			}
		}
		
		mTransFormulaBuilder.setFormula(formula);
		mTransFormulaBuilder.setInfeasibility(infeasibility);
		mTransFormulaBuilder.addAuxVarsButRenameToFreshCopies(auxVars, mMgdScript);
		return mTransFormulaBuilder.finishConstruction(mMgdScript);
	}

	private IProgramVar getModifiableBoogieVar(final String id, final DeclarationInformation declInfo) {
		final StorageClass storageClass = declInfo.getStorageClass();
		// assert (declInfo.getProcedure() == null ||
		// declInfo.getProcedure().equals(mCurrentProcedure));
		IProgramVar result;
		switch (storageClass) {
		case GLOBAL:
		case LOCAL:
		case IMPLEMENTATION_OUTPARAM:
		case PROC_FUNC_OUTPARAM:
			result = mBoogie2SmtSymbolTable.getBoogieVar(id, declInfo, false);
			break;
		case IMPLEMENTATION_INPARAM:
		case PROC_FUNC_INPARAM:
			throw new AssertionError("not modifiable");
		case IMPLEMENTATION:
		case PROC_FUNC:
		case QUANTIFIED:
		default:
			throw new AssertionError("no appropriate variable ");
		}
		return result;
	}

	private IdentifierTranslator[] getIdentifierTranslatorsIntraprocedural() {
		return new IdentifierTranslator[] { new LocalVarTranslatorWithInOutVarManagement(),
				new GlobalVarTranslatorWithInOutVarManagement(mCurrentProcedure, false),
				mBoogie2SMT.getConstOnlyIdentifierTranslator() };
	}

	/**
	 * Let assign be a statement of the form v_i:=expr_i Remove v_i from the
	 * inVars (if contained). If neccessary v_i is put to outVars (possibly by
	 * getSmtIdentifier).
	 */
	private void addAssignment(final AssignmentStatement assign) {
		final LeftHandSide[] lhs = assign.getLhs();
		final Expression[] rhs = assign.getRhs();
		final Map<TermVariable, Expression> addedEqualities = new HashMap<TermVariable, Expression>();
		for (int i = 0; i < lhs.length; i++) {
			/* ArrayLHS are removed by preprocessor */
			final VariableLHS vlhs = (VariableLHS) lhs[i];
			assert vlhs.getDeclarationInformation() != null : " no declaration information";
			final String name = vlhs.getIdentifier();
			final DeclarationInformation declInfo = vlhs.getDeclarationInformation();
			final IProgramVar boogieVar = getModifiableBoogieVar(name, declInfo);
			assert (boogieVar != null);
			getOrConstuctCurrentRepresentative(boogieVar);
			if (mTransFormulaBuilder.containsInVar(boogieVar)) {
				final TermVariable tv = mTransFormulaBuilder.getInVar(boogieVar);
				addedEqualities.put(tv, rhs[i]);
				removeInVar(boogieVar);
			}
		}
		final IdentifierTranslator[] its = getIdentifierTranslatorsIntraprocedural();

		for (final TermVariable tv : addedEqualities.keySet()) {

			final SingleTermResult tlres = mExpression2Term.translateToTerm(its, addedEqualities.get(tv));
			mAuxVars.addAll(tlres.getAuxiliaryVars());
			mOverapproximations.putAll(tlres.getOverappoximations()); 
			final Term rhsTerm = tlres.getTerm();
			final Term eq = mScript.term("=", tv, rhsTerm);

			mAssumes = Util.and(mScript, eq, mAssumes);
			if (s_ComputeAsserts) {
				mAsserts = Util.implies(mScript, eq, mAsserts);
			}
		}
	}

	private void addHavoc(final HavocStatement havoc) {
		for (final VariableLHS lhs : havoc.getIdentifiers()) {
			assert lhs.getDeclarationInformation() != null : " no declaration information";
			final String name = lhs.getIdentifier();
			final DeclarationInformation declInfo = lhs.getDeclarationInformation();
			final IProgramVar boogieVar = getModifiableBoogieVar(name, declInfo);
			assert (boogieVar != null);
			getOrConstuctCurrentRepresentative(boogieVar);
			if (mTransFormulaBuilder.containsInVar(boogieVar)) {
				removeInVar(boogieVar);
			}
		}
	}

	private void addAssume(final AssumeStatement assume) {
		final IdentifierTranslator[] its = getIdentifierTranslatorsIntraprocedural();

		final SingleTermResult tlres = mExpression2Term.translateToTerm(its, assume.getFormula());
		mAuxVars.addAll(tlres.getAuxiliaryVars());
		mOverapproximations.putAll(tlres.getOverappoximations()); 
		final Term f = tlres.getTerm();
		
		mAssumes = Util.and(mScript, f, mAssumes);
		if (s_ComputeAsserts) {
			mAsserts = Util.implies(mScript, f, mAsserts);
		}
	}

	private void addAssert(final AssertStatement assertstmt) {
		if (s_ComputeAsserts) {
			final IdentifierTranslator[] its = getIdentifierTranslatorsIntraprocedural();
			final SingleTermResult tlres = mExpression2Term.translateToTerm(its, assertstmt.getFormula());
			mAuxVars.addAll(tlres.getAuxiliaryVars());
			mOverapproximations.putAll(tlres.getOverappoximations()); 
			final Term f = tlres.getTerm();
			
			mAssumes = Util.and(mScript, f, mAssumes);
			mAsserts = Util.and(mScript, f, mAsserts);
			assert (mAssumes.toString() instanceof Object);
		} else {
			throw new AssertionError(s_ComputeAssertsNotAvailable);
		}
	}

	private void addSummary(final CallStatement call) {
		final Procedure procedure = mBoogieDeclarations.getProcSpecification().get(call.getMethodName());

		final HashMap<String, Term> substitution = new HashMap<String, Term>();
		final Expression[] arguments = call.getArguments();
		int offset;
		final VariableLHS[] callLhs = call.getLhs();
		offset = 0;
		final ArrayList<IProgramVar> callLhsBvs = new ArrayList<IProgramVar>();
		for (final VarList outParamVl : procedure.getOutParams()) {
			for (final String outParamId : outParamVl.getIdentifiers()) {
				final String callLhsId = callLhs[offset].getIdentifier();
				final DeclarationInformation callLhsDeclInfo = callLhs[offset].getDeclarationInformation();
				final IProgramVar callLhsBv = getModifiableBoogieVar(callLhsId, callLhsDeclInfo);
				assert (callLhsBv != null);
				final TermVariable callLhsTv = getOrConstuctCurrentRepresentative(callLhsBv);

				substitution.put(outParamId, callLhsTv);
				callLhsBvs.add(callLhsBv);
				offset++;
			}
		}

		for (final IProgramVar bv : callLhsBvs) {
			removeInVar(bv);
		}

		final Map<IProgramVar, Term> requiresSubstitution = new HashMap<IProgramVar, Term>();
		final Map<IProgramVar, Term> ensuresSubstitution = new HashMap<IProgramVar, Term>();

		for (final Specification spec : procedure.getSpecification()) {
			if (spec instanceof ModifiesSpecification) {
				for (final VariableLHS var : ((ModifiesSpecification) spec).getIdentifiers()) {
					final String id = var.getIdentifier();
					final IProgramVar boogieVar = mBoogie2SmtSymbolTable.getBoogieVar(id, var.getDeclarationInformation(),
							false);
					final IProgramVar boogieOldVar = mBoogie2SmtSymbolTable.getBoogieVar(id, var.getDeclarationInformation(),
							true);
					assert boogieVar != null;
					assert boogieOldVar != null;
					final TermVariable tvAfter = getOrConstuctCurrentRepresentative(boogieVar);
					removeInVar(boogieVar);

					final TermVariable tvBefore = mBoogie2SMT.getManagedScript().
							constructFreshTermVariable(boogieVar.getGloballyUniqueId(), boogieVar.getTermVariable().getSort());
					mTransFormulaBuilder.addInVar(boogieVar, tvBefore);
					ensuresSubstitution.put(boogieVar, tvAfter);
					ensuresSubstitution.put(boogieOldVar, tvBefore);
					requiresSubstitution.put(boogieVar, tvBefore);
					requiresSubstitution.put(boogieOldVar, tvBefore);

				}
			}
		}

		Term[] argumentTerms;
		{
			final IdentifierTranslator[] its = getIdentifierTranslatorsIntraprocedural();
			final MultiTermResult tlres = mExpression2Term.translateToTerms(its, arguments); 
			mAuxVars.addAll(tlres.getAuxiliaryVars());
			mOverapproximations.putAll(tlres.getOverappoximations()); 
			argumentTerms = tlres.getTerms();
		}

		offset = 0;
		for (final VarList vl : procedure.getInParams()) {
			for (final String id : vl.getIdentifiers()) {
				substitution.put(id, argumentTerms[offset++]);
			}
		}

		final String calledProcedure = call.getMethodName();

		final IdentifierTranslator[] ensIts = new IdentifierTranslator[] { new SubstitutionTranslatorId(substitution),
				new SubstitutionTranslatorBoogieVar(ensuresSubstitution),
				new GlobalVarTranslatorWithInOutVarManagement(calledProcedure, false),
				mBoogie2SMT.getConstOnlyIdentifierTranslator() };

		for (final Specification spec : procedure.getSpecification()) {
			if (spec instanceof EnsuresSpecification) {
				final Expression post = ((EnsuresSpecification) spec).getFormula();
				final SingleTermResult tlres = mExpression2Term.translateToTerm(ensIts, post);
				mAuxVars.addAll(tlres.getAuxiliaryVars());
				mOverapproximations.putAll(tlres.getOverappoximations()); 
				final Term f = tlres.getTerm();
				mAssumes = Util.and(mScript, f, mAssumes);
				if (s_ComputeAsserts) {
					if (spec.isFree()) {
						mAsserts = Util.implies(mScript, f, mAsserts);
					} else {
						mAsserts = Util.and(mScript, f, mAsserts);
					}
				}
			}
		}

		final IdentifierTranslator[] reqIts = new IdentifierTranslator[] { new SubstitutionTranslatorId(substitution),
				new SubstitutionTranslatorBoogieVar(requiresSubstitution),
				new GlobalVarTranslatorWithInOutVarManagement(calledProcedure, false),
				mBoogie2SMT.getConstOnlyIdentifierTranslator() };

		for (final Specification spec : procedure.getSpecification()) {
			if (spec instanceof RequiresSpecification) {
				final Expression pre = ((RequiresSpecification) spec).getFormula();
				final SingleTermResult tlres = mExpression2Term.translateToTerm(reqIts, pre);
				mAuxVars.addAll(tlres.getAuxiliaryVars());
				mOverapproximations.putAll(tlres.getOverappoximations()); 
				final Term f = tlres.getTerm();
				mAssumes = Util.and(mScript, f, mAssumes);
				if (s_ComputeAsserts) {
					if (spec.isFree()) {
						mAsserts = Util.implies(mScript, f, mAsserts);
					} else {
						mAsserts = Util.and(mScript, f, mAsserts);
					}
				}
			}
		}
	}

	/**
	 * Remove boogieVars from inVars mapping, if the inVar is not an outVar, add
	 * it to he auxilliary variables auxVar.
	 */
	private void removeInVar(final IProgramVar boogieVar) {
		final TermVariable tv = mTransFormulaBuilder.removeInVar(boogieVar);
		if (mTransFormulaBuilder.getOutVar(boogieVar) != tv) {
			mAuxVars.add(tv);
		}
	}

	/**
	 * Obtain TermVariable that represents BoogieVar bv at the current position.
	 * This is the current inVar. If this inVar does not yet exist, we create
	 * it. In this case we have to add (bv,tv) to the outVars if bv is not
	 * already an outvar.
	 */
	private TermVariable getOrConstuctCurrentRepresentative(final IProgramVar bv) {
		TermVariable tv = mTransFormulaBuilder.getInVar(bv);
		if (tv == null) {
			tv = createInVar(bv);
			if (!mTransFormulaBuilder.containsOutVar(bv)) {
				mTransFormulaBuilder.addOutVar(bv, tv);
			}
		}
		return tv;
	}

	/**
	 * Construct fresh TermVariable for BoogieVar bv and add it to inVars.
	 * Special case: If BoogieVar bv is an oldVar we do not take a fresh
	 * TermVariable but the default TermVariable for this BoogieVar.
	 */
	private TermVariable createInVar(final IProgramVar bv) {
		TermVariable tv;
		if (bv.isOldvar()) {
			tv = bv.getTermVariable();
		} else {
			tv = mBoogie2SMT.getManagedScript().constructFreshTermVariable(
					bv.getGloballyUniqueId(), bv.getTermVariable().getSort());
		}
		mTransFormulaBuilder.addInVar(bv, tv);
		return tv;
	}

	public abstract class IdentifierTranslatorWithInOutVarManagement implements IdentifierTranslator {

		@Override
		public Term getSmtIdentifier(final String id, final DeclarationInformation declInfo, final boolean isOldContext,
				final BoogieASTNode boogieASTNode) {
			final IProgramVar bv = getBoogieVar(id, declInfo, isOldContext, boogieASTNode);
			if (bv == null) {
				return null;
			} else {
				final TermVariable tv = getOrConstuctCurrentRepresentative(bv);
				return tv;
			}
		}

		abstract protected IProgramVar getBoogieVar(String id, DeclarationInformation declInfo, boolean isOldContext,
				BoogieASTNode boogieASTNode);

	}

	public class LocalVarTranslatorWithInOutVarManagement extends IdentifierTranslatorWithInOutVarManagement {

		@Override
		protected IProgramVar getBoogieVar(final String id, final DeclarationInformation declInfo, final boolean isOldContext,
				final BoogieASTNode boogieASTNode) {
			final StorageClass storageClass = declInfo.getStorageClass();
			switch (storageClass) {
			case IMPLEMENTATION_INPARAM:
			case IMPLEMENTATION_OUTPARAM:
			case PROC_FUNC_INPARAM:
			case PROC_FUNC_OUTPARAM:
			case LOCAL:
				return mBoogie2SmtSymbolTable.getBoogieVar(id, declInfo, isOldContext);
			case GLOBAL:
				return null;
			case IMPLEMENTATION:
			case PROC_FUNC:
			case QUANTIFIED:
			default:
				throw new AssertionError();
			}
		}
	}

	public class GlobalVarTranslatorWithInOutVarManagement extends IdentifierTranslatorWithInOutVarManagement {
		private final String mCurrentProcedure;
		/**
		 * Translate all variables to the non old global variable, independent
		 * of the context. This feature is not used at the moment. Maybe we can
		 * drop it.
		 */
		private final boolean mAllNonOld;
		private final Set<String> mModifiableByCurrentProcedure;

		public GlobalVarTranslatorWithInOutVarManagement(final String currentProcedure, final boolean allNonOld) {
			mCurrentProcedure = currentProcedure;
			mAllNonOld = allNonOld;
			mModifiableByCurrentProcedure = mBoogieDeclarations.getModifiedVars().get(mCurrentProcedure);

		}

		@Override
		protected IProgramVar getBoogieVar(final String id, final DeclarationInformation declInfo, final boolean isOldContext,
				final BoogieASTNode boogieASTNode) {
			final StorageClass storageClass = declInfo.getStorageClass();
			switch (storageClass) {
			case IMPLEMENTATION_INPARAM:
			case IMPLEMENTATION_OUTPARAM:
			case PROC_FUNC_INPARAM:
			case PROC_FUNC_OUTPARAM:
			case LOCAL:
				return null;
			case GLOBAL:
				IProgramVar bv;
				if (isOldContext) {
					if (mAllNonOld || !modifiableByCurrentProcedure(id)) {
						bv = mBoogie2SmtSymbolTable.getBoogieVar(id, declInfo, false);
					} else {
						bv = mBoogie2SmtSymbolTable.getBoogieVar(id, declInfo, true);
					}
				} else {
					bv = mBoogie2SmtSymbolTable.getBoogieVar(id, declInfo, false);
				}
				return bv;
			case IMPLEMENTATION:
			case PROC_FUNC:
			case QUANTIFIED:
			default:
				throw new AssertionError();
			}
		}

		private boolean modifiableByCurrentProcedure(final String id) {
			return mModifiableByCurrentProcedure.contains(id);
		}

	}

	private class SubstitutionTranslatorId implements IdentifierTranslator {
		private final Map<String, Term> mSubstitution;

		public SubstitutionTranslatorId(final Map<String, Term> substitution) {
			super();
			mSubstitution = substitution;
		}

		@Override
		public Term getSmtIdentifier(final String id, final DeclarationInformation declInfo, final boolean isOldContext,
				final BoogieASTNode boogieASTNode) {
			return mSubstitution.get(id);
		}
	}

	public class SubstitutionTranslatorBoogieVar implements IdentifierTranslator {
		private final Map<IProgramVar, Term> mSubstitution;

		public SubstitutionTranslatorBoogieVar(final Map<IProgramVar, Term> substitution) {
			super();
			mSubstitution = substitution;
		}

		@Override
		public Term getSmtIdentifier(final String id, final DeclarationInformation declInfo, final boolean isOldContext,
				final BoogieASTNode boogieASTNode) {
			final IProgramVar bv = mBoogie2SmtSymbolTable.getBoogieVar(id, declInfo, isOldContext);
			if (bv == null) {
				return null;
			} else {
				return mSubstitution.get(bv);
			}
		}
	}

	/**
	 * Eliminate auxVars from input if possible. Let {x_1,...,x_n} be a subset
	 * of auxVars. Returns a term that is equivalent to ∃x_1,...,∃x_n input and
	 * remove {x_1,...,x_n} from auxVars. The set {x_1,...,x_n} is determined by
	 * NaiveDestructiveEqualityResolution.
	 * 
	 * Returns term that is equisatisfiable to input. If a x is free variable
	 * 
	 * @param input
	 * @param auxVars
	 *            set of free variables occurring in input
	 * @return
	 */
	private Term eliminateAuxVars(final Term input, final Set<TermVariable> auxVars) {
		final XnfDer xnfDer = new XnfDer(mMgdScript, mServices);
		final Term result = Util.and(mScript, xnfDer.tryToEliminate(QuantifiedFormula.EXISTS, SmtUtils.getConjuncts(input), auxVars));
		return result;
	}

	public TranslationResult statementSequence(final boolean simplify, final SimplicationTechnique simplicationTechnique, 
			final String procId, final Statement... statements) {
		initialize(procId);
		for (int i = statements.length - 1; i >= 0; i--) {
			final Statement st = statements[i];
			if (st instanceof AssumeStatement) {
				addAssume((AssumeStatement) st);
			} else if (st instanceof AssignmentStatement) {
				addAssignment((AssignmentStatement) st);
			} else if (st instanceof HavocStatement) {
				addHavoc((HavocStatement) st);
			} else if (st instanceof CallStatement) {
				addSummary((CallStatement) st);
			} else {
				throw new IllegalArgumentException("Intenal Edge only contains"
						+ " Assume, Assignment or Havoc Statement");
			}

		}
		return getTransFormula(simplify, false, simplicationTechnique);
	}

	/**
	 * Returns a TransFormula that describes the assignment of arguments to
	 * callees (local) input parameters. The (local) input parameters of the
	 * callee are the only outVars. For each inParameter we construct a new
	 * BoogieVar which is equivalent to the BoogieVars which were constructed
	 * while processing the callee.
	 */
	public TranslationResult inParamAssignment(final CallStatement st, final SimplicationTechnique simplicationTechnique) {
		final String callee = st.getMethodName();
		initialize(callee);
		final Procedure calleeImpl = mBoogieDeclarations.getProcImplementation().get(callee);

		final IdentifierTranslator[] its = getIdentifierTranslatorsIntraprocedural();
		final MultiTermResult tlres = mExpression2Term.translateToTerms(its, st.getArguments()); 
		mAuxVars.addAll(tlres.getAuxiliaryVars());
		mOverapproximations.putAll(tlres.getOverappoximations()); 
		final Term[] argTerms = tlres.getTerms();
		
		mTransFormulaBuilder.clearOutVars();

		final DeclarationInformation declInfo = new DeclarationInformation(StorageClass.PROC_FUNC_INPARAM, callee);
		final Term[] assignments = new Term[st.getArguments().length];
		int offset = 0;
		for (final VarList varList : calleeImpl.getInParams()) {
			for (final String var : varList.getIdentifiers()) {
				final IProgramVar boogieVar = mBoogie2SMT.getBoogie2SmtSymbolTable().getBoogieVar(var, declInfo, false);
				assert boogieVar != null;
				final String suffix = "InParam";
				final TermVariable tv = constructTermVariableWithSuffix(boogieVar, suffix);
				mTransFormulaBuilder.addOutVar(boogieVar, tv);
				assignments[offset] = mScript.term("=", tv, argTerms[offset]);
				offset++;
			}
		}
		assert (st.getArguments().length == offset);
		mAssumes = Util.and(mScript, assignments);
		return getTransFormula(false, true, simplicationTechnique);
	}

	/**
	 * Returns a TransFormula that describes the assignment of (local) out
	 * parameters to variables that take the result. The variables on the left
	 * hand side of the call statement are the only outVars. For each
	 * outParameter and each left hand side of the call we construct a new
	 * BoogieVar which is equivalent to the BoogieVars of the corresponding
	 * procedures.
	 */
	public TranslationResult resultAssignment(final CallStatement st, final String caller, final SimplicationTechnique simplicationTechnique) {
		initialize(caller);
		final String callee = st.getMethodName();
		final Procedure impl = mBoogieDeclarations.getProcImplementation().get(callee);
		int offset = 0;
		final DeclarationInformation declInfo = new DeclarationInformation(StorageClass.IMPLEMENTATION_OUTPARAM, callee);
		final Term[] assignments = new Term[st.getLhs().length];
		for (final VarList ourParamVarList : impl.getOutParams()) {
			for (final String outParamId : ourParamVarList.getIdentifiers()) {
				final IProgramVar outParamBv = mBoogie2SmtSymbolTable.getBoogieVar(outParamId, declInfo, false);
				final String suffix = "OutParam";
				final TermVariable outParamTv = constructTermVariableWithSuffix(outParamBv, suffix);
				mTransFormulaBuilder.addInVar(outParamBv, outParamTv);
				final String callLhsId = st.getLhs()[offset].getIdentifier();
				final DeclarationInformation callLhsDeclInfo = st.getLhs()[offset]
						.getDeclarationInformation();
				final IProgramVar callLhsBv = mBoogie2SmtSymbolTable.getBoogieVar(callLhsId, callLhsDeclInfo, false);
				final TermVariable callLhsTv = mBoogie2SMT.getManagedScript().
						constructFreshTermVariable(callLhsBv.getGloballyUniqueId(), callLhsBv.getTermVariable().getSort());
				mTransFormulaBuilder.addOutVar(callLhsBv, callLhsTv);
				assignments[offset] = mScript.term("=", callLhsTv, outParamTv);
				offset++;
			}
		}
		assert (st.getLhs().length == offset);
		mAssumes = Util.and(mScript, assignments);
		return getTransFormula(false, true, simplicationTechnique);
	}
	
	/**
	 * Construct a TermVariable whose name is given by the BoogieVar bv and
	 * and additional suffix. This TermVariable is not unified.
	 * If you use this method make sure that you do not call it twice for the
	 * same combination of bv and suffix.
	 */
	public TermVariable constructTermVariableWithSuffix(final IProgramVar bv, final String suffix) {
		final String name = bv.getGloballyUniqueId() + SmtUtils.removeSmtQuoteCharacters(suffix);
		final Sort sort = bv.getTermVariable().getSort();
		final TermVariable result = mBoogie2SMT.getManagedScript().constructFreshTermVariable(name, sort);
		return result;
	}
	
	
	public class TranslationResult {
		private final TransFormula mTransFormula;
		private final Map<String, ILocation> mOverapproximations;
		public TranslationResult(final TransFormula transFormula,
				final Map<String, ILocation> overapproximations) {
			super();
			mTransFormula = transFormula;
			mOverapproximations = overapproximations;
		}
		public TransFormula getTransFormula() {
			return mTransFormula;
		}
		public Map<String, ILocation> getOverapproximations() {
			return mOverapproximations;
		}
		
	}

}
