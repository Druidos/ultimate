/*
 * Copyright (C) 2016 Yu-Wen Chen 
 * Copyright (C) 2016 Alexander Nutz (nutz@informatik.uni-freiburg.de)
 * Copyright (C) 2016 University of Freiburg
 *
 * This file is part of the ULTIMATE AbstractInterpretationV2 plug-in.
 *
 * The ULTIMATE AbstractInterpretationV2 plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE AbstractInterpretationV2 plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE AbstractInterpretationV2 plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE AbstractInterpretationV2 plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE AbstractInterpretationV2 plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.ConstantTerm;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.Boogie2SMT;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.BoogieConst;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgEdge;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.TransFormula;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.UnmodifiableTransFormula;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVarOrConst;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.ApplicationTermFinder;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.Substitution;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.arrays.ArrayIndex;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.arrays.MultiDimensionalSelect;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.arrays.MultiDimensionalStore;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.BoogieIcfgContainer;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.CodeBlock;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.util.RCFGEdgeVisitor;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.HashRelation;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.NestedMap2;

/**
 * Create and collects @EqNode from ApplicationTerm (store and select)
 * 
 * @author Yu-Wen Chen (yuwenchen1105@gmail.com)
 */
public class RCFGArrayIndexCollector extends RCFGEdgeVisitor {

	private final HashRelation<IProgramVarOrConst, EqFunctionNode> mArrayIdToFnNodes = new HashRelation<>();
	
	private final Map<Term, EqNode> mTermToEqNode = new HashMap<>();

	Map<Term, IProgramVarOrConst> mTermToProgramVarOrConst = new HashMap<>();

	private final Script mScript;

	private final Boogie2SMT mBoogie2SMT;

	private final NestedMap2<IProgramVarOrConst, List<EqNode>, EqFunctionNode> mEqFunctionNodeStore = new NestedMap2<>();
	private final Map<IProgramVarOrConst, EqBaseNode> mEqBaseNodeStore = new HashMap<>();
	
	private final Set<ApplicationTerm> mEquations = new HashSet<>();
	private final Set<ApplicationTerm> mSelectTerms = new HashSet<>();
	
	/**
	 * Stores for each array, which Terms(EqNodes) are used to access it.
	 */
	HashRelation<IProgramVarOrConst, EqNode> mArrayToAccessingEqNodes = new HashRelation<>();

	public RCFGArrayIndexCollector(final BoogieIcfgContainer root) {
		mScript = root.getCfgSmtToolkit().getManagedScript().getScript();
		mBoogie2SMT = root.getBoogie2SMT();
		process(BoogieIcfgContainer.extractStartEdges(root));
	}

	private <T extends IcfgEdge> void process(final Collection<T> edges) {
		final Deque<IcfgEdge> worklist = new ArrayDeque<IcfgEdge>();
		final Set<IcfgEdge> finished = new HashSet<IcfgEdge>();
		
		worklist.addAll(edges);
		while (!worklist.isEmpty()) {
			final IcfgEdge current = worklist.removeFirst();
			if (!finished.add(current)) {
				continue;
			}
			visit(current);
			worklist.addAll(current.getTarget().getOutgoingEdges());
		}
	}

	@Override
	protected void visit(final CodeBlock c) {
		c.getPrettyPrintedStatements();
		
//		final Map<Term, Term> substitionMap = new HashMap<Term, Term>();
//		for (final Entry<IProgramVar, TermVariable> entry : c.getTransitionFormula().getInVars().entrySet()) {
//			substitionMap.put(entry.getValue(), entry.getKey().getTermVariable());
//		}
//		for (final Entry<IProgramVar, TermVariable> entry : c.getTransitionFormula().getOutVars().entrySet()) {
//			substitionMap.put(entry.getValue(), entry.getKey().getTermVariable());
//		}
		TransFormula tf = c.getTransitionFormula();
		final Map<Term, Term> substitionMap = 
				computeNormalizingSubstitution(VPDomainHelpers.computeProgramVarMappingFromTransFormula(tf));
		final Term formulaWithNormalizedVariables = new Substitution(mScript, substitionMap).transform(tf.getFormula());
		
		/*
		 * handle selects in the formula
		 */
		List<MultiDimensionalSelect> mdSelects = MultiDimensionalSelect.extractSelectShallow(formulaWithNormalizedVariables, false);
		for (MultiDimensionalSelect mds : mdSelects) {
			constructEqNode(mds);
		}
		
		/*
		 * handle stores in the formula
		 */
		List<MultiDimensionalStore> mdStores = MultiDimensionalStore.extractArrayStoresShallow(formulaWithNormalizedVariables);
		for (MultiDimensionalStore mds : mdStores) {
			constructEqNode(mds);
		}

		/*
		 * Store all select-terms and equations for postprocessing
		 *  
		 */
		Set<String> equationFunctionNames = new HashSet<String>(2);
		equationFunctionNames.add("=");
		equationFunctionNames.add("distinct");
		Set<ApplicationTerm> equations = 
				new ApplicationTermFinder(equationFunctionNames, false)
					.findMatchingSubterms(formulaWithNormalizedVariables);
		mEquations.addAll(equations);
		Set<ApplicationTerm> selectTerms = mdSelects.stream().map(mds -> mds.getSelectTerm()).collect(Collectors.toSet());
		mSelectTerms.addAll(selectTerms);

	}

	/**
	 * compute a substitution mapping that translates the TermVariables in a Term
	 * to the corresponding default TermVariable of a IProgramVar.
	 * (TODO: not so happy with everything that is connected to this..)
	 * @param c
	 * @return
	 */
	private Map<Term, Term> computeNormalizingSubstitution(final Map<TermVariable, IProgramVar> tvToPvMap) {
		final Map<Term, Term> substitionMap = new HashMap<Term, Term>();
		for (Entry<TermVariable, IProgramVar> en : tvToPvMap.entrySet()) {
			substitionMap.put(en.getKey(), en.getValue().getTerm());
		}
		return substitionMap;
	}
	
	private EqNode constructEqNode(MultiDimensionalStore mds) {
		EqNode result = mTermToEqNode.get(mds.getStoreTerm());
		if (result != null) {
			return result;
		}
		
		IProgramVarOrConst arrayId;
		if (!isArrayOrConst(mds.getArray())) {
			// if mds.getArray ist not a constant or variable, it must be a select, right?
			MultiDimensionalSelect innerSelect = new MultiDimensionalSelect(mds.getArray());
			EqFunctionNode innerSelectNode = (EqFunctionNode) constructEqNode(innerSelect);
			arrayId = innerSelectNode.getFunction();
		} else {
			arrayId = getOrConstructBoogieVarOrConst(mds.getArray());
		}
		
		List<EqNode> arguments = new ArrayList<>();
		for (Term arrayIndex : mds.getIndex()) {
			EqNode argumentEqNode = getOrConstructEqNode(arrayIndex);
			arguments.add(argumentEqNode);
			mArrayToAccessingEqNodes.addPair(arrayId, argumentEqNode);
		}
		getOrConstructEqNode(mds.getValue());

		result = getOrConstructEqFnNode(arrayId, arguments);
		mTermToEqNode.put(mds.getStoreTerm(), result);
		return result;
	}

	private EqNode constructEqNode(MultiDimensionalSelect mds) {
		EqNode result = mTermToEqNode.get(mds.getSelectTerm());
		if (result != null) {
			return result;
		}

		IProgramVarOrConst arrayId;
		if (!isArrayOrConst(mds.getArray())) {
			// if mds.getArray ist not a constant or variable, it must be a store, right?
			MultiDimensionalStore innerStore = new MultiDimensionalStore(mds.getArray());
			EqFunctionNode innerStoreNode = (EqFunctionNode) constructEqNode(innerStore);
			arrayId = innerStoreNode.getFunction();
		} else {
			arrayId = getOrConstructBoogieVarOrConst(mds.getArray());
		}


		List<EqNode> arguments = new ArrayList<>();
		for (Term ai : mds.getIndex()) {
			EqNode argumentEqNode = getOrConstructEqNode(ai);
			arguments.add(argumentEqNode);
			mArrayToAccessingEqNodes.addPair(arrayId, argumentEqNode);
		}

		result = getOrConstructEqFnNode(arrayId, arguments);
		mTermToEqNode.put(mds.getSelectTerm(), result);
		return result;
	}

	private boolean isArrayOrConst(Term t) {
		return t instanceof TermVariable 
				|| t instanceof ConstantTerm
				|| (t instanceof ApplicationTerm && ((ApplicationTerm) t).getParameters().length == 0);
	}

	private EqNode getOrConstructEqNode(Term t) {
		EqNode result = mTermToEqNode.get(t);
		if (result != null) {
			return result;
		}
		// we need to construct a fresh EqNode
		if (isArrayOrConst(t)) {
			result = getOrConstructEqBaseNode(getOrConstructBoogieVarOrConst(t));
			mTermToEqNode.put(t, result);
			return result;
		} 
		// we need to construct an EqFunctionNode	
		assert t instanceof ApplicationTerm;
		ApplicationTerm at = (ApplicationTerm) t;
		if (at.getFunction().getName() == "select") {
			MultiDimensionalSelect mds = new MultiDimensionalSelect(at);
			return constructEqNode(mds);
		} else if (at.getFunction().getName() == "store") {
			MultiDimensionalStore mds = new MultiDimensionalStore(at);
			return constructEqNode(mds);
		} else {
			assert false : "should not happen";
			return null;
		}
	}
	
	private IProgramVarOrConst getOrConstructBoogieVarOrConst(final Term t) {
		IProgramVarOrConst result = mTermToProgramVarOrConst.get(t);
		if (result != null) {
			return result;
		}
		
		if (t instanceof ApplicationTerm ) {
			assert ((ApplicationTerm) t).getParameters().length == 0 : "not a constant";
			BoogieConst bc = mBoogie2SMT.getBoogie2SmtSymbolTable().getProgramConst((ApplicationTerm) t);
			result = new ConstOrLiteral(bc);
		} else if (t instanceof ConstantTerm) {
			result = new ConstOrLiteral((ConstantTerm) t);
		} else if (t instanceof TermVariable) {
			IProgramVar pv = mBoogie2SMT.getBoogie2SmtSymbolTable().getProgramVar((TermVariable) t);
			assert pv != null : "?";
			result = pv;
		} else {
			assert false;
			return null;
		}
		mTermToProgramVarOrConst.put(t, result);
		return result;
	}
	
	/**
	 * 
	 * @param tv
	 * @return
	 */
	private EqBaseNode getOrConstructEqBaseNode(final IProgramVarOrConst bv) {
		
		EqBaseNode result = mEqBaseNodeStore.get(bv);
		
		if (result == null) {
			result = new EqBaseNode(bv);
			mEqBaseNodeStore.put(bv, result);	
		}
		return result;
	}
	
//	private EqFunctionNode getOrConstructEqFnNode(final EqNode eqNode, final List<EqNode> indices) {
//
//	}

	private EqFunctionNode getOrConstructEqFnNode(final IProgramVarOrConst function, final List<EqNode> indices) {
			
		EqFunctionNode result = mEqFunctionNodeStore.get(function, indices);
		if (result == null) {
			result = new EqFunctionNode(function, indices);

			mArrayIdToFnNodes.addPair(function, result);
			
			mEqFunctionNodeStore.put(function, indices, result);
		}
		
		for (EqNode child : indices) {
			child.addParent(result);
		}
		return result;
	}


	public  HashRelation<IProgramVarOrConst, EqFunctionNode> getArrayIdToFnNodeMap() {
		assert mArrayIdToFnNodes != null;
		return  mArrayIdToFnNodes;
	}
	
	@Override
	public String toString() {
		return "-RCFGArrayIndexCollector-";
	}

	public Map<Term, EqNode> getTermToEqNodeMap() {
		return mTermToEqNode;
	}

	public IProgramVarOrConst getIProgramVarOrConstOrLiteral(Term term, Map<TermVariable, IProgramVar> tvToPvMap) {
		if (term instanceof TermVariable) {
			IProgramVar pv = tvToPvMap.get(term);
			assert mTermToProgramVarOrConst.get(pv.getTerm()) == pv : "right?...";
			return pv;
		} else {
			return  mTermToProgramVarOrConst.get(term);
		}
	}

	/**
	 * @param array
	 * @param index
	 * @return true iff the given array is ever accessed using the given index in the program.
	 */
	public boolean isArrayAccessedAt(IProgramVarOrConst array, EqNode index) {
		return mArrayToAccessingEqNodes.containsPair(array, index);
	}
	
	public Set<EqNode> getAccessingIndicesForArray(IProgramVarOrConst array) {
		return Collections.unmodifiableSet(mArrayToAccessingEqNodes.getImage(array));
	}

	/**
	 * Called after the main run (which is initiated by the constructor)
	 * 
	 * We have collected all (multi-dimensional) select-terms in the program and all equations.
	 * Plan:
	 *  construct EqNodes for everything that is equated to a select-term, and then build the
	 *  transitive closure.
	 */
	public void postProcess() {
		/*
		 * Add to the initial set all terms that are
		 * equated to a select-term 
		 */
		final Set<Term> termsEquatedWithASelectTerm = new HashSet<>();
		for (ApplicationTerm eq : mEquations) {
			if (mSelectTerms.contains(eq.getParameters()[0])
					&& !mSelectTerms.contains(eq.getParameters()[1])) {
				termsEquatedWithASelectTerm.add(eq.getParameters()[1]);
			} else if (mSelectTerms.contains(eq.getParameters()[1])
					&& !mSelectTerms.contains(eq.getParameters()[0])) {
				termsEquatedWithASelectTerm.add(eq.getParameters()[0]);
			}
		}
		
		/*
		 * Add to the initial set all terms that are equated to an array index.
		 */
		Set<Term> arrayIndices = new HashSet<>();
		for (IProgramVarOrConst array : mArrayToAccessingEqNodes.getDomain()) {
			for (EqNode eqNode : mArrayToAccessingEqNodes.getImage(array)) {
				arrayIndices.add(eqNode.getTerm(mScript));
			}
		}
		
		
		/*
		 * compute the closure over the "being-equated-in-the-program" relation
		 */
		final Set<Term> closure = new HashSet<>(termsEquatedWithASelectTerm);
		closure.addAll(arrayIndices);

		boolean madeProgress = true;
		while (madeProgress) {
			madeProgress = false;
			
			for (ApplicationTerm eq : mEquations) {
				if (closure.contains(eq.getParameters()[0])
						&& !closure.contains(eq.getParameters()[1])) {
					closure.add(eq.getParameters()[1]);
					madeProgress = true;
				} else if (closure.contains(eq.getParameters()[1])
						&& !closure.contains(eq.getParameters()[0])) {
					closure.add(eq.getParameters()[0]);
					madeProgress = true;
				}
			}
		}
		
		/*
		 * Construct EqNodes for all the additionally discovered Terms
		 */
		for (Term t : closure) {
			getOrConstructEqNode(t);
		}
			
			
//		// find the "other sides" of an equation were one side is a select term
//		Set<Term> selectTerms = mdSelects.stream().map(mds -> mds.getSelectTerm()).collect(Collectors.toSet());
//		Set<Term> termsEquatedWithASelectTerm = new HashSet<>();
//		for (ApplicationTerm eq : equations) {
//			if (selectTerms.contains(eq.getParameters()[0])
//					&& !selectTerms.contains(eq.getParameters()[1])) {
//				termsEquatedWithASelectTerm.add(eq.getParameters()[1]);
//			} else if (selectTerms.contains(eq.getParameters()[1])
//					&& !selectTerms.contains(eq.getParameters()[0])) {
//				termsEquatedWithASelectTerm.add(eq.getParameters()[0]);
//			}
//		}
		// construct nodes for the "other sides"
//		for (Term t : termsEquatedWithASelectTerm) {
//			getOrConstructEqNode(t);
//		}
	}


	/**
	 * 
	 * @param term
	 * @param tvToPvMap
	 * @return the EqNode for term (normalized by tvToPvMap) if we track term, null otherwise
	 */
	public EqNode getEqNode(final Term term, final Map<TermVariable, IProgramVar> tvToPvMap) {
		// our mapping is in terms of normalized terms, so we need to make a substitution before we can look it up
		final Map<Term, Term> substitionMap = computeNormalizingSubstitution(tvToPvMap);
		final Term termWithNormalizedVariables = new Substitution(mScript, substitionMap).transform(term);
		EqNode result = mTermToEqNode.get(termWithNormalizedVariables);
		return result;
	}
}