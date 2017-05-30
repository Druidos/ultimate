package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.elements;

import java.util.List;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.IAbstractPostOperator;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.CfgSmtToolkit;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramNonOldVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVarOrConst;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.PredicateTransformer;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.TransFormulaConverterCache;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.states.EqConstraintFactory;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.states.EqDisjunctiveConstraint;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.states.EqOperationProvider;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.states.EqPredicate;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.states.EqState;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.states.EqTransitionRelation;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.Call;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.Return;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.Summary;

public class EqPostOperator<ACTION extends IIcfgTransition<IcfgLocation>> implements
		IAbstractPostOperator<EqState<ACTION>, ACTION, IProgramVarOrConst> {
	
	private ManagedScript mScript;
	private EqOperationProvider<ACTION> operationProvider;
	
	private final PredicateTransformer<
			EqDisjunctiveConstraint<ACTION, EqNode, EqFunction>, 
			EqPredicate<ACTION>, 
			EqTransitionRelation<ACTION>> mPredicateTransformer;
	private final TransFormulaConverterCache<ACTION> mTransFormulaConverter;
	private CfgSmtToolkit mCfgSmtToolkit;
	private EqConstraintFactory<ACTION, EqNode, EqFunction> mEqConstraintFactory;
	private EqNodeAndFunctionFactory mEqNodeAndFunctionFactory;
	
	public EqPostOperator(EqNodeAndFunctionFactory eqNodeAndFunctionFactory, 
			EqConstraintFactory<ACTION, EqNode, EqFunction> eqConstraintFactory) {
		mEqNodeAndFunctionFactory = eqNodeAndFunctionFactory;
		mEqConstraintFactory = eqConstraintFactory;

		mPredicateTransformer = new PredicateTransformer<>(mScript, operationProvider);	
		mTransFormulaConverter = new TransFormulaConverterCache<ACTION>(mEqNodeAndFunctionFactory, 
				mEqConstraintFactory);
	}

	@Override
	public List<EqState<ACTION>> apply(EqState<ACTION> oldstate, ACTION transition) {
		
		EqTransitionRelation<ACTION> transrel = null;

		final EqDisjunctiveConstraint<ACTION, EqNode, EqFunction> postConstraint = 
				mPredicateTransformer.strongestPostcondition(
						oldstate.toEqPredicate(), transrel);
		return postConstraint.toEqStates();
	}
	@Override
	public List<EqState<ACTION>> apply(EqState<ACTION> stateBeforeLeaving, EqState<ACTION> stateAfterLeaving,
			ACTION transition) {
		if (transition instanceof Call) {
			final String calledProcedure = transition.getSucceedingProcedure();

			final EqTransitionRelation<ACTION> localVarAssignments = mTransFormulaConverter
					.getEqTransitionRelationFromTransformula(((Call) transition)
							.getLocalVarsAssignment());
			final EqTransitionRelation<ACTION> globalVarAssignments = mTransFormulaConverter
					.getEqTransitionRelationFromTransformula(
							mCfgSmtToolkit.getOldVarsAssignmentCache().getGlobalVarsAssignment(calledProcedure));
			final EqTransitionRelation<ACTION> oldVarAssignments = mTransFormulaConverter
					.getEqTransitionRelationFromTransformula(
							mCfgSmtToolkit.getOldVarsAssignmentCache().getOldVarsAssignment(calledProcedure));

			final Set<IProgramNonOldVar> modifiableGlobalsOfCalledProcedure = 
							mCfgSmtToolkit.getModifiableGlobalsTable().getModifiedBoogieVars(calledProcedure);

			final EqDisjunctiveConstraint<ACTION, EqNode, EqFunction> postConstraint = 
					mPredicateTransformer.strongestPostconditionCall(
							stateBeforeLeaving.toEqPredicate(), 
							localVarAssignments, globalVarAssignments, oldVarAssignments, 
							modifiableGlobalsOfCalledProcedure);
			return postConstraint.toEqStates();
		} else if (transition instanceof Summary) {
			return apply(stateBeforeLeaving, transition);
		} else if (transition instanceof Return) {

			/*
			 *  TODO: this is probably problematic because stateBeforeLeaving and stateAfterLeaving do not correspond
			 *   exactly to returnPred and callPred.
			 */
			EqPredicate<ACTION> returnPred = stateBeforeLeaving.toEqPredicate();
			EqPredicate<ACTION> callPred = stateAfterLeaving.toEqPredicate();

			EqTransitionRelation<ACTION> returnTF = mTransFormulaConverter
					.getEqTransitionRelationFromTransformula(((Return) transition).getAssignmentOfReturn());
			EqTransitionRelation<ACTION> callTF = mTransFormulaConverter
					.getEqTransitionRelationFromTransformula(
							((Return) transition).getLocalVarsAssignmentOfCall());
			EqTransitionRelation<ACTION> oldVarAssignments = mTransFormulaConverter
					.getEqTransitionRelationFromTransformula(
							mCfgSmtToolkit.getOldVarsAssignmentCache().getOldVarsAssignment(
									transition.getPrecedingProcedure()));
			Set<IProgramNonOldVar> modifiableGlobals = mCfgSmtToolkit.getModifiableGlobalsTable()
					.getModifiedBoogieVars(transition.getPrecedingProcedure());

			final EqDisjunctiveConstraint<ACTION, EqNode, EqFunction> postConstraint = 
					mPredicateTransformer.strongestPostconditionReturn(returnPred, 
							callPred, 
							returnTF, 
							callTF, 
							oldVarAssignments, 
							modifiableGlobals);
			
			return postConstraint.toEqStates();
		} else {
			throw new UnsupportedOperationException();
		}
	}
	
}