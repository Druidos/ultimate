/*
 * Copyright (C) 2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2009-2015 University of Freiburg
 * 
 * This file is part of the ULTIMATE Automata Library.
 * 
 * The ULTIMATE Automata Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * The ULTIMATE Automata Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE Automata Library. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE Automata Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE Automata Library grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.automata.nestedword.buchi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.AutomataOperationCanceledException;
import de.uni_freiburg.informatik.ultimate.automata.SetOfStates;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.INwaOutgoingLetterAndTransitionProvider;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.INwaSuccessorStateProvider;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.VpAlphabet;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.OutgoingCallTransition;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.OutgoingInternalTransition;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.OutgoingReturnTransition;
import de.uni_freiburg.informatik.ultimate.automata.statefactory.IBuchiComplementNcsbStateFactory;
import de.uni_freiburg.informatik.ultimate.automata.statefactory.IStateFactory;
import de.uni_freiburg.informatik.ultimate.core.model.models.IElement;

/**
 * Buchi Complementation based on the algorithm proposed by Frantisek Blahoudek and Jan Stejcek. This complementation is
 * only sound for a special class of automata whose working title is <i>TABA</i> (termination analysis Büchi automata).
 * 
 * @author Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * @param <LETTER>
 *            letter type
 * @param <STATE>
 *            state type
 */
public final class BuchiComplementNCSBSimpleNwa<LETTER, STATE> implements INwaSuccessorStateProvider<LETTER, STATE> {

	private final AutomataLibraryServices mServices;
	
	private final SetOfStates<STATE> mSetOfStates;

	private final INwaOutgoingLetterAndTransitionProvider<LETTER, STATE> mOperand;

	private final IBuchiComplementNcsbStateFactory<STATE> mStateFactory;


	/**
	 * Constructor.
	 * 
	 * @param services
	 *            Ultimate services
	 * @param stateFactory
	 *            state factory
	 * @param operand
	 *            operand
	 * @throws AutomataOperationCanceledException
	 *             if operation was canceled
	 */
	public BuchiComplementNCSBSimpleNwa(final AutomataLibraryServices services,
			final IBuchiComplementNcsbStateFactory<STATE> stateFactory,
			final INwaOutgoingLetterAndTransitionProvider<LETTER, STATE> operand) throws AutomataOperationCanceledException {
		mServices = services;
		mOperand = operand;
		mStateFactory = stateFactory;
		mSetOfStates = new SetOfStates<>(mStateFactory.createEmptyStackState());
		constructInitialState();
	}

	private void constructInitialState() {

	}

	/**
	 * Return state of result automaton that represents detState. If no such state was constructed yet, construct it.
	 */
	private STATE getOrAdd(final boolean isInitial, final LevelRankingState<LETTER, STATE> lvlrk) {
		STATE resState = null;
		return resState;
	}

	@Override
	public Iterable<STATE> getInitialStates() {
		return mSetOfStates.getInitialStates();
	}

	@Override
	public VpAlphabet<LETTER> getVpAlphabet() {
		return mOperand.getVpAlphabet();
	}

	@Override
	public IStateFactory<STATE> getStateFactory() {
		return mStateFactory;
	}

	@Override
	public boolean isInitial(final STATE state) {
		return mSetOfStates.isInitial(state);
	}

	@Override
	public boolean isFinal(final STATE state) {
		return mSetOfStates.isAccepting(state);
	}

	@Override
	public STATE getEmptyStackState() {
		return mSetOfStates.getEmptyStackState();
	}

	@Override
	public int size() {
		return mSetOfStates.getStates().size();
	}


	@Override
	public String sizeInformation() {
		return "size Information not available";
	}

	@Override
	public Collection<STATE> internalSuccessors(final STATE state, final LETTER letter) {
		return null;
	}

	@Override
	public Collection<STATE> callSuccessors(final STATE state, final LETTER letter) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Collection<STATE> returnSuccessorsGivenHier(final STATE state, final STATE hier, final LETTER letter) {
		throw new UnsupportedOperationException();
	}

	@Override
	public IElement transformToUltimateModel(final AutomataLibraryServices services)
			throws AutomataOperationCanceledException {
		throw new UnsupportedOperationException();
	}
}