/*
 * Copyright (C) 2015 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
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
package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.rcfg;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import de.uni_freiburg.informatik.ultimate.abstractinterpretation.model.IAbstractState;
import de.uni_freiburg.informatik.ultimate.abstractinterpretation.model.IAbstractStateBinaryOperator;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IAction;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgCallTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgReturnTransition;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.AbstractMultiState;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.IAbstractStateStorage;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.ITransitionProvider;
import de.uni_freiburg.informatik.ultimate.util.CoreUtil;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.Pair;

/**
 *
 * @author dietsch@informatik.uni-freiburg.de
 *
 */
public class IcfgAbstractStateStorageProvider<STATE extends IAbstractState<STATE, VARDECL>, ACTION extends IAction, LOCATION, VARDECL>
		implements IAbstractStateStorage<STATE, ACTION, VARDECL, LOCATION> {
	
	private final Map<LOCATION, AbstractMultiState<STATE, ACTION, VARDECL>> mStorage;
	private final IAbstractStateBinaryOperator<STATE> mMergeOperator;
	private final IUltimateServiceProvider mServices;
	private final Set<IcfgAbstractStateStorageProvider<STATE, ACTION, LOCATION, VARDECL>> mChildStores;
	private final ITransitionProvider<ACTION, LOCATION> mTransProvider;
	private final ACTION mScope;
	private final Set<ACTION> mScopeFixpoints;
	private final IcfgAbstractStateStorageProvider<STATE, ACTION, LOCATION, VARDECL> mParent;
	private final Set<String> mUsedSummary;
	private final Map<String, Set<ACTION>> mSummarySourcesCall;
	private final Map<String, Set<ACTION>> mSummarySourcesReturn;
	
	public IcfgAbstractStateStorageProvider(final IAbstractStateBinaryOperator<STATE> mergeOperator,
			final IUltimateServiceProvider services, final ITransitionProvider<ACTION, LOCATION> transprovider) {
		this(mergeOperator, services, transprovider, null, null, new HashSet<>(), new HashMap<>(), new HashMap<>());
	}
	
	private IcfgAbstractStateStorageProvider(final IAbstractStateBinaryOperator<STATE> mergeOperator,
			final IUltimateServiceProvider services, final ITransitionProvider<ACTION, LOCATION> transProvider,
			final ACTION scope, final IcfgAbstractStateStorageProvider<STATE, ACTION, LOCATION, VARDECL> parent,
			final Set<String> usedSummary, final Map<String, Set<ACTION>> summarySources,
			final Map<String, Set<ACTION>> summarySourcesReturn) {
		assert mergeOperator != null;
		assert services != null;
		assert transProvider != null;
		mMergeOperator = mergeOperator;
		mServices = services;
		mTransProvider = transProvider;
		mScope = scope;
		mParent = parent;
		mChildStores = new HashSet<>();
		mScopeFixpoints = new HashSet<>();
		mStorage = new HashMap<>();
		mUsedSummary = usedSummary;
		mSummarySourcesCall = summarySources;
		mSummarySourcesReturn = summarySourcesReturn;
	}
	
	@Override
	public AbstractMultiState<STATE, ACTION, VARDECL> addAbstractPostState(final ACTION transition,
			final AbstractMultiState<STATE, ACTION, VARDECL> state) {
		assert transition != null : "Cannot add state to null transition";
		assert state != null : "Cannot add null state";
		updateSummarySource(transition);
		final AbstractMultiState<STATE, ACTION, VARDECL> oldState = getPostState(transition);
		if (oldState == null) {
			replacePostState(transition, state);
			return state;
		}
		final AbstractMultiState<STATE, ACTION, VARDECL> mergedState = oldState.merge(mMergeOperator, state);
		replacePostState(transition, mergedState);
		return mergedState;
	}
	
	@Override
	public final IAbstractStateStorage<STATE, ACTION, VARDECL, LOCATION> createStorage(final ACTION scope) {
		final IcfgAbstractStateStorageProvider<STATE, ACTION, LOCATION, VARDECL> rtr =
				new IcfgAbstractStateStorageProvider<>(getMergeOperator(), getServices(), getTransitionProvider(),
						scope, this, mUsedSummary, mSummarySourcesCall, mSummarySourcesReturn);
		mChildStores.add(rtr);
		return rtr;
	}
	
	@Override
	public Map<LOCATION, Term> getLoc2Term(final ACTION initialTransition, final Script script) {
		final Map<LOCATION, StateDecorator> states = getMergedStatesOfAllChildren(initialTransition);
		return convertStates2Terms(states, script);
	}
	
	@Override
	public final Map<LOCATION, Set<AbstractMultiState<STATE, ACTION, VARDECL>>>
			getLoc2States(final ACTION initialTransition) {
		final Map<LOCATION, Set<AbstractMultiState<STATE, ACTION, VARDECL>>> states =
				getStatesOfAllChildren(initialTransition);
		return states.entrySet().stream().filter(e -> e.getValue() != null && !e.getValue().isEmpty())
				.collect(Collectors.toMap(e -> e.getKey(), v -> v.getValue()));
	}
	
	@Override
	public Map<LOCATION, STATE> getLoc2SingleStates(final ACTION initialTransition) {
		final Map<LOCATION, StateDecorator> states = getMergedStatesOfAllChildren(initialTransition);
		return states.entrySet().stream().filter(e -> e.getValue().mState != null).collect(Collectors
				.toMap(Map.Entry::getKey, decorator -> decorator.getValue().mState.getSingleState(mMergeOperator)));
	}
	
	@Override
	public Set<Term> getTerms(final ACTION initialTransition, final Script script) {
		final Set<Term> rtr = new LinkedHashSet<>();
		getLocalTerms(initialTransition, script, rtr);
		mChildStores.stream().filter(a -> a != this).forEach(a -> rtr.addAll(a.getTerms(initialTransition, script)));
		return rtr;
	}
	
	@Override
	public AbstractMultiState<STATE, ACTION, VARDECL> getAbstractPostStates(final ACTION transition) {
		assert transition != null;
		return getPostState(transition);
	}
	
	@Override
	public Set<STATE> getAbstractPostStates(final Deque<ACTION> callStack, final ACTION symbol) {
		assert symbol != null;
		assert mScope == null;
		
		// if (callStack.isEmpty()) {
		// return Optional.ofNullable(getAbstractPostStates(symbol)).map(a -> a.getStates())
		// .orElse(Collections.emptySet());
		// }
		
		final Set<Pair<Deque<ACTION>, ACTION>> summaryCallStack = getSummaryCallStack(callStack, symbol);
		return getAbstractPostStatesWithSummary(summaryCallStack);
	}
	
	private Set<STATE> getAbstractPostStatesWithSummary(final Set<Pair<Deque<ACTION>, ACTION>> summaryCallStack) {
		final Set<STATE> rtr = new HashSet<>();
		summaryCallStack.forEach(a -> rtr.addAll(getAbstractPostStatesWithSummary(a.getFirst(), a.getSecond())));
		return rtr;
	}
	
	private Set<STATE> getAbstractPostStatesWithSummary(final Deque<ACTION> callStack, final ACTION symbol) {
		final Iterator<ACTION> iterator = callStack.descendingIterator();
		List<IcfgAbstractStateStorageProvider<STATE, ACTION, LOCATION, VARDECL>> currentChilds =
				Collections.singletonList(this);
		while (iterator.hasNext()) {
			final ACTION currentScope = iterator.next();
			
			final List<IcfgAbstractStateStorageProvider<STATE, ACTION, LOCATION, VARDECL>> newChilds =
					currentChilds.stream().flatMap(a -> a.mChildStores.stream()).filter(a -> a.mScope == currentScope)
							.collect(Collectors.toList());
			currentChilds.stream().filter(a -> a.containsScopeFixpoint(currentScope)).forEach(newChilds::add);
			currentChilds = newChilds;
		}
		
		final Set<STATE> rtr = currentChilds.stream().map(a -> Optional.ofNullable(a.getAbstractPostStates(symbol))
				.map(b -> b.getStates()).orElse(Collections.emptySet())).flatMap(a -> a.stream())
				.collect(Collectors.toSet());
		return rtr;
	}
	
	private Set<Pair<Deque<ACTION>, ACTION>> getSummaryCallStack(final Deque<ACTION> callStack, final ACTION symbol) {
		final Set<ACTION> callsToReplace = callStack.stream()
				.filter(a -> mUsedSummary.contains(a.getSucceedingProcedure())).collect(Collectors.toSet());
		if (symbol instanceof IIcfgCallTransition<?> && mUsedSummary.contains(symbol.getSucceedingProcedure())) {
			callsToReplace.add(symbol);
		}
		
		final Set<ACTION> returnsToReplace = new HashSet<>();
		if (symbol instanceof IIcfgReturnTransition<?, ?> && mUsedSummary.contains(symbol.getPrecedingProcedure())) {
			returnsToReplace.addAll(getSummarySourcesReturn(symbol));
		}
		
		if (callsToReplace.isEmpty() && returnsToReplace.isEmpty()) {
			return Collections.singleton(new Pair<>(callStack, symbol));
		}
		
		final Comparator<ACTION> comparator = (o1, o2) -> Integer.compare(o1.hashCode(), o2.hashCode());
		final Pair<Map<ACTION, List<ACTION>>, Integer> callReplacementRulesPair =
				getReplacementRules(callsToReplace, comparator, this::getSummarySourcesCall);
		final Map<ACTION, List<ACTION>> callReplacementRules = callReplacementRulesPair.getFirst();
		final Map<ACTION, List<ACTION>> symbolReplacementRules;
		final int size;
		if (symbol instanceof IIcfgCallTransition<?>) {
			symbolReplacementRules = callReplacementRules;
			size = callReplacementRulesPair.getSecond();
		} else if (symbol instanceof IIcfgReturnTransition<?, ?>) {
			final Pair<Map<ACTION, List<ACTION>>, Integer> returnReplacementRulesPair =
					getReplacementRules(returnsToReplace, comparator, this::getSummarySourcesReturn);
			symbolReplacementRules = returnReplacementRulesPair.getFirst();
			size = Math.max(callReplacementRulesPair.getSecond(), returnReplacementRulesPair.getSecond());
		} else {
			symbolReplacementRules = Collections.emptyMap();
			size = callReplacementRulesPair.getSecond();
		}
		
		final Set<Pair<Deque<ACTION>, ACTION>> rtr = new HashSet<>();
		for (int i = 0; i < size; ++i) {
			final Deque<ACTION> newCallStack = new ArrayDeque<>();
			final Iterator<ACTION> iter = callStack.descendingIterator();
			while (iter.hasNext()) {
				final ACTION current = iter.next();
				newCallStack.addFirst(getMatchingSymbol(i, current, callReplacementRules.get(current)));
			}
			final ACTION newSymbol = getMatchingSymbol(i, symbol, symbolReplacementRules.get(symbol));
			rtr.add(new Pair<>(newCallStack, newSymbol));
		}
		
		return rtr;
	}
	
	private Pair<Map<ACTION, List<ACTION>>, Integer> getReplacementRules(final Set<ACTION> callsToReplace,
			final Comparator<ACTION> comparator, final Function<ACTION, Set<ACTION>> summarySourceProvider) {
		int size = 0;
		final Map<ACTION, List<ACTION>> callReplacementRules = new HashMap<>();
		for (final ACTION replace : callsToReplace) {
			final Set<ACTION> summarySources = summarySourceProvider.apply(replace);
			assert !summarySources.isEmpty() : "Should use summary, but dont know which";
			callReplacementRules.put(replace, summarySources.stream().sorted(comparator).collect(Collectors.toList()));
			final int ssize = summarySources.size();
			size = Math.max(ssize, size);
		}
		return new Pair<>(callReplacementRules, size);
	}
	
	private ACTION getMatchingSymbol(final int i, final ACTION old, final List<ACTION> replacements) {
		if (replacements == null || replacements.isEmpty()) {
			return old;
		}
		final int size = replacements.size();
		if (i < size) {
			return replacements.get(i);
		}
		return replacements.get(size - 1);
	}
	
	@Override
	public void scopeFixpointReached() {
		mParent.mScopeFixpoints.add(mScope);
	}
	
	@Override
	public void saveSummarySubstituion(final ACTION action,
			final AbstractMultiState<STATE, ACTION, VARDECL> summaryPostState, final ACTION summaryAction) {
		assert action instanceof IIcfgCallTransition<?>;
		mParent.mUsedSummary.add(action.getSucceedingProcedure());
	}
	
	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append(mScope).append(" ");
		if (mStorage == null) {
			return sb.append("NULL").toString();
		}
		if (mStorage.isEmpty()) {
			return sb.append("{}").toString();
		}
		sb.append('{');
		final Set<Entry<LOCATION, AbstractMultiState<STATE, ACTION, VARDECL>>> entries = mStorage.entrySet();
		for (final Entry<LOCATION, AbstractMultiState<STATE, ACTION, VARDECL>> entry : entries) {
			sb.append(entry.getKey().toString()).append("=[");
			final AbstractMultiState<STATE, ACTION, VARDECL> state = entry.getValue();
			if (!state.isEmpty()) {
				sb.append(state.toLogString());
			}
			sb.append("], ");
		}
		if (!entries.isEmpty()) {
			sb.delete(sb.length() - 2, sb.length());
		}
		sb.append('}');
		return sb.toString();
	}
	
	public String toTreeString() {
		return toTreeString(new StringBuilder(), "").toString();
	}
	
	public StringBuilder toTreeString(final StringBuilder sb, final String indent) {
		final String addIndent = "  ";
		sb.append(indent).append(toString()).append(CoreUtil.getPlatformLineSeparator());
		mChildStores.forEach(a -> a.toTreeString(sb, indent + addIndent));
		return sb;
	}
	
	private Set<ACTION> getSummarySourcesCall(final ACTION call) {
		assert call instanceof IIcfgCallTransition<?>;
		final String procName = call.getSucceedingProcedure();
		return getSummarySources(call, procName, mSummarySourcesCall);
	}
	
	private Set<ACTION> getSummarySourcesReturn(final ACTION ret) {
		assert ret instanceof IIcfgReturnTransition<?, ?>;
		final String procName = ret.getPrecedingProcedure();
		return getSummarySources(ret, procName, mSummarySourcesReturn);
	}
	
	private Set<ACTION> getSummarySources(final ACTION call, final String procName,
			final Map<String, Set<ACTION>> map) {
		final Set<ACTION> summarySource = map.get(procName);
		if (summarySource == null) {
			final Set<ACTION> newSummarySource = new HashSet<>();
			map.put(procName, newSummarySource);
			return newSummarySource;
		}
		return summarySource;
	}
	
	private void updateSummarySource(final ACTION symbol) {
		if (mTransProvider.isEnteringScope(symbol)) {
			getSummarySourcesCall(symbol).add(symbol);
		} else if (symbol instanceof IIcfgReturnTransition<?, ?>) {
			getSummarySourcesReturn(symbol).add(symbol);
		}
	}
	
	private AbstractMultiState<STATE, ACTION, VARDECL> getPostState(final ACTION action) {
		assert action != null;
		final LOCATION node = getTransitionProvider().getTarget(action);
		return mStorage.get(node);
	}
	
	private void replacePostState(final ACTION action, final AbstractMultiState<STATE, ACTION, VARDECL> newState) {
		assert action != null;
		final LOCATION node = getTransitionProvider().getTarget(action);
		mStorage.put(node, newState);
	}
	
	private Map<LOCATION, StateDecorator> getMergedStatesOfAllChildren(final ACTION initialTransition) {
		Map<LOCATION, StateDecorator> states = getMergedLocalStates(initialTransition);
		for (final IcfgAbstractStateStorageProvider<STATE, ACTION, LOCATION, VARDECL> child : mChildStores.stream()
				.filter(a -> a != this).collect(Collectors.toSet())) {
			states = mergeMaps(states, child.getMergedStatesOfAllChildren(initialTransition), (a, b) -> a.merge(b));
		}
		return states;
	}
	
	private Map<LOCATION, Set<AbstractMultiState<STATE, ACTION, VARDECL>>>
			getStatesOfAllChildren(final ACTION initialTransition) {
		Map<LOCATION, Set<AbstractMultiState<STATE, ACTION, VARDECL>>> states = getLocalStates(initialTransition);
		for (final IcfgAbstractStateStorageProvider<STATE, ACTION, LOCATION, VARDECL> child : mChildStores.stream()
				.filter(a -> a != this).collect(Collectors.toSet())) {
			states = mergeMaps(states, child.getStatesOfAllChildren(initialTransition));
		}
		return states;
	}
	
	private Map<LOCATION, StateDecorator> getMergedLocalStates(final ACTION initialTransition) {
		final Map<LOCATION, StateDecorator> rtr = new HashMap<>();
		final Deque<ACTION> worklist = new ArrayDeque<>();
		final Set<ACTION> closed = new HashSet<>();
		
		worklist.add(initialTransition);
		
		while (!worklist.isEmpty()) {
			final ACTION current = worklist.remove();
			// check if already processed
			if (!closed.add(current)) {
				continue;
			}
			
			final LOCATION postLoc = mTransProvider.getTarget(current);
			// add successors to worklist
			for (final ACTION outgoing : mTransProvider.getSuccessorActions(postLoc)) {
				worklist.add(outgoing);
			}
			
			final AbstractMultiState<STATE, ACTION, VARDECL> states = getPostState(current);
			
			final StateDecorator currentState;
			if (states == null || states.isEmpty()) {
				// no states for this location
				currentState = new StateDecorator();
			} else {
				currentState = new StateDecorator(states);
			}
			
			final StateDecorator alreadyKnownState = rtr.get(postLoc);
			if (alreadyKnownState != null) {
				rtr.put(postLoc, alreadyKnownState.merge(currentState));
			} else {
				rtr.put(postLoc, currentState);
			}
			
		}
		return rtr;
	}
	
	private Map<LOCATION, Set<AbstractMultiState<STATE, ACTION, VARDECL>>>
			getLocalStates(final ACTION initialTransition) {
		final Map<LOCATION, Set<AbstractMultiState<STATE, ACTION, VARDECL>>> rtr = new HashMap<>();
		final Deque<ACTION> worklist = new ArrayDeque<>();
		final Set<ACTION> closed = new HashSet<>();
		
		worklist.add(initialTransition);
		
		while (!worklist.isEmpty()) {
			final ACTION current = worklist.remove();
			
			if (!closed.add(current)) {
				continue;
			}
			
			final LOCATION postLoc = mTransProvider.getTarget(current);
			
			for (final ACTION outgoing : mTransProvider.getSuccessorActions(postLoc)) {
				worklist.add(outgoing);
			}
			
			Set<AbstractMultiState<STATE, ACTION, VARDECL>> alreadyKnownStates = rtr.get(postLoc);
			if (alreadyKnownStates == null) {
				alreadyKnownStates = new HashSet<>();
				rtr.put(postLoc, alreadyKnownStates);
			}
			
			final AbstractMultiState<STATE, ACTION, VARDECL> postState = getPostState(current);
			if (postState == null) {
				continue;
			}
			alreadyKnownStates.add(postState);
		}
		return rtr;
	}
	
	private Set<Term> getLocalTerms(final ACTION initialTransition, final Script script, final Set<Term> terms) {
		final Deque<ACTION> worklist = new ArrayDeque<>();
		final Set<ACTION> closed = new LinkedHashSet<>();
		
		worklist.add(initialTransition);
		
		final Term falseTerm = script.term("false");
		
		while (!worklist.isEmpty()) {
			final ACTION current = worklist.remove();
			// check if already processed
			if (!closed.add(current)) {
				continue;
			}
			
			final LOCATION postLoc = mTransProvider.getTarget(current);
			// add successors to worklist
			for (final ACTION outgoing : mTransProvider.getSuccessorActions(postLoc)) {
				worklist.add(outgoing);
			}
			
			final AbstractMultiState<STATE, ACTION, VARDECL> multiState = getPostState(current);
			
			if (multiState == null || multiState.isEmpty()) {
				// no states for this location
				terms.add(falseTerm);
			} else {
				terms.add(multiState.getTerm(script));
			}
		}
		return terms;
	}
	
	private static <K, V> Map<K, V> mergeMaps(final Map<K, V> a, final Map<K, V> b, final BiFunction<V, V, V> merge) {
		final Map<K, V> rtr = new HashMap<>();
		
		for (final Entry<K, V> entryA : a.entrySet()) {
			final V valueB = b.get(entryA.getKey());
			if (valueB == null) {
				rtr.put(entryA.getKey(), entryA.getValue());
			} else {
				rtr.put(entryA.getKey(), merge.apply(entryA.getValue(), valueB));
			}
		}
		
		for (final Entry<K, V> entryB : b.entrySet()) {
			final V valueA = a.get(entryB.getKey());
			if (valueA == null) {
				rtr.put(entryB.getKey(), entryB.getValue());
			} else {
				// do nothing, this was already done in first iteration
			}
		}
		return rtr;
	}
	
	private static <K, V> Map<K, Set<V>> mergeMaps(final Map<K, Set<V>> one, final Map<K, Set<V>> other) {
		return mergeMaps(one, other, (a, b) -> {
			assert a != null && b != null;
			final Set<V> newSet = new HashSet<>();
			newSet.addAll(a);
			newSet.addAll(b);
			return newSet;
		});
	}
	
	private Map<LOCATION, Term> convertStates2Terms(final Map<LOCATION, StateDecorator> states, final Script script) {
		final Map<LOCATION, Term> rtr = new HashMap<>();
		
		for (final Entry<LOCATION, StateDecorator> entry : states.entrySet()) {
			final Term term = entry.getValue().getTerm(script);
			final LOCATION loc = entry.getKey();
			rtr.put(loc, term);
		}
		
		return rtr;
	}
	
	private boolean containsScopeFixpoint(final ACTION scope) {
		return mScopeFixpoints.contains(scope);
	}
	
	protected IAbstractStateBinaryOperator<STATE> getMergeOperator() {
		return mMergeOperator;
	}
	
	protected IUltimateServiceProvider getServices() {
		return mServices;
	}
	
	protected ITransitionProvider<ACTION, LOCATION> getTransitionProvider() {
		return mTransProvider;
	}
	
	private final class StateDecorator {
		private final AbstractMultiState<STATE, ACTION, VARDECL> mState;
		
		private StateDecorator() {
			mState = null;
		}
		
		private StateDecorator(final AbstractMultiState<STATE, ACTION, VARDECL> state) {
			mState = state;
		}
		
		private Term getTerm(final Script script) {
			if (mState == null) {
				return script.term("false");
			}
			return mState.getTerm(script);
		}
		
		private StateDecorator merge(final StateDecorator other) {
			if (other == null || other.mState == null) {
				return this;
			}
			if (mState == null) {
				return other;
			}
			return new StateDecorator(mState.merge(mMergeOperator, other.mState));
		}
		
		@Override
		public String toString() {
			return mState == null ? "null" : "\"" + mState.toLogString() + "\"";
		}
	}
}