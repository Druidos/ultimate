/*
 * Copyright (C) 2015-2016 Daniel Tischner
 * Copyright (C) 2009-2016 University of Freiburg
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
package de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.simulation.performance;

import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryException;
import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.AutomataOperationCanceledException;
import de.uni_freiburg.informatik.ultimate.automata.IOperation;
import de.uni_freiburg.informatik.ultimate.automata.LibraryIdentifiers;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.INestedWordAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.StateFactory;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.StringFactory;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.GetRandomDfa;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.GetRandomNwa;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;

/**
 * Operation that compares the different types of simulation methods for buechi
 * reduction using random automata.<br/>
 * The resulting automaton is the input automaton.
 * 
 * @author Daniel Tischner
 * 
 * @param <LETTER>
 *            Letter class of buechi automaton, not used
 * @param <STATE>
 *            State class of buechi automaton, not used
 */
public final class CompareWithRandomAutomata<LETTER, STATE> implements IOperation<LETTER, STATE> {

	/**
	 * The logger used by the Ultimate framework.
	 */
	private final ILogger mLogger;
	/**
	 * The inputed buechi automaton.
	 */
	private final INestedWordAutomaton<LETTER, STATE> mOperand;
	/**
	 * The resulting buechi automaton.
	 */
	private final INestedWordAutomaton<LETTER, STATE> mResult;
	/**
	 * Service provider of Ultimate framework.
	 */
	private final AutomataLibraryServices mServices;

	/**
	 * Compares the different types of simulation methods for buechi reduction
	 * using random automata. Resulting automaton is the input automaton.
	 * 
	 * @param services
	 *            Service provider of Ultimate framework
	 * @param stateFactory
	 *            The state factory used for creating states
	 * @param operand
	 *            A buechi automaton, it is not used by the operation
	 * @throws AutomataOperationCanceledException
	 *             If the operation was canceled, for example from the Ultimate
	 *             framework.
	 */
	public CompareWithRandomAutomata(final AutomataLibraryServices services, final StateFactory<STATE> stateFactory,
			final INestedWordAutomaton<LETTER, STATE> operand) throws AutomataOperationCanceledException {
		mServices = services;
		mLogger = mServices.getLoggingService().getLogger(LibraryIdentifiers.PLUGIN_ID);
		mOperand = operand;
		mResult = operand;
		mLogger.info(startMessage());

		// Use operation with random automata
		final StateFactory<String> snf = new StringFactory();

		final int n = 100;
		final int k = 30;
		final int f = 20;
		final int totalityInPerc = 10;
		final int logEvery = 100;
		final int amount = 1000;
		INestedWordAutomaton<String, String> buechi;

		for (int i = 1; i <= amount; i++) {
			if (i % logEvery == 0) {
				mLogger.info("Worked " + i + " automata");
			}

			final boolean useNwaInsteadDfaMethod = false;
			if (useNwaInsteadDfaMethod) {
				buechi = new GetRandomNwa(services, k, n, 0.2, 0, 0, (totalityInPerc + 0.0f) / 100).getResult();
			} else {
				buechi = new GetRandomDfa(services, n, k, f, totalityInPerc, true, false, false, false).getResult();
			}

			try {
				new CompareReduceBuchiSimulation<String, String>(services, snf, buechi);
			} catch (final AutomataOperationCanceledException e) {
				e.printStackTrace();
			}
		}

		mLogger.info(exitMessage());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uni_freiburg.informatik.ultimate.automata.IOperation#checkResult(
	 * de.uni_freiburg.informatik.ultimate.automata.nwalibrary.StateFactory)
	 */
	@Override
	public boolean checkResult(final StateFactory<STATE> stateFactory) throws AutomataLibraryException {
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.uni_freiburg.informatik.ultimate.automata.IOperation#exitMessage()
	 */
	@Override
	public String exitMessage() {
		return "Finished " + operationName() + ".";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uni_freiburg.informatik.ultimate.automata.IOperation#getResult()
	 */
	@Override
	public Object getResult() {
		return mResult;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.uni_freiburg.informatik.ultimate.automata.IOperation#operationName()
	 */
	@Override
	public String operationName() {
		return "compareWithRandomAutomata";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.uni_freiburg.informatik.ultimate.automata.IOperation#startMessage()
	 */
	@Override
	public String startMessage() {
		return "Start " + operationName() + ". Operand has " + mOperand.sizeInformation();
	}
}
