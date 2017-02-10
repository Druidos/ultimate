/*
 * Copyright (C) 2017 Christian Schilling (schillic@informatik.uni-freiburg.de)
 * Copyright (C) 2017 University of Freiburg
 * 
 * This file is part of the ULTIMATE Automaton Delta Debugger.
 * 
 * The ULTIMATE Automaton Delta Debugger is free software: you can redistribute
 * it and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * The ULTIMATE Automaton Delta Debugger is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE Automaton Delta Debugger. If not, see
 * <http://www.gnu.org/licenses/>.
 * 
 * Additional permission under GNU GPL version 3 section 7: If you modify the
 * ULTIMATE Automaton Delta Debugger, or any covered work, by linking or
 * combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE Automaton Delta Debugger grant you additional
 * permission to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.plugins.analysis.automatondeltadebugger;

import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.IOperation;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.IDoubleDeckerAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.INestedWordAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.RemoveUnreachable;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.simulation.direct.MinimizeDfaSimulation;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.simulation.util.nwa.graph.summarycomputationgraph.ReduceNwaDirectSimulationB;
import de.uni_freiburg.informatik.ultimate.automata.statefactory.IMergeStateFactory;
import de.uni_freiburg.informatik.ultimate.automata.statefactory.IStateFactory;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.automatondeltadebugger.AutomatonDebuggerExamples.EOperationType;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.automatondeltadebugger.core.AbstractTester;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.automatondeltadebugger.core.DebuggerException;

/**
 * Testers used by the automaton delta debugger.
 * <p>
 * NOTE: Users may insert their sample code as a new method and leave it here.
 * 
 * @author Christian Schilling (schillic@informatik.uni-freiburg.de)
 * @param <LETTER>
 *            letter type
 * @param <STATE>
 *            state type
 */
public class AutomatonDebuggerTesters<LETTER, STATE> {
	private final IUltimateServiceProvider mServices;
	
	/**
	 * Constructor.
	 * 
	 * @param services
	 *            Ultimate services
	 */
	public AutomatonDebuggerTesters(final IUltimateServiceProvider services) {
		mServices = services;
	}
	
	/**
	 * Possible test mode of the debugger.
	 */
	public enum EAutomatonDeltaDebuggerTestMode {
		/**
		 * The debugger watches for any kind of error. The result might be a different error from the one originally
		 * seen, but usually "any error is interesting".
		 */
		GENERAL,
		/**
		 * The debugger watches only for errors in the {@link IOperation#checkResult(IStateFactory)} method. All other
		 * errors are not considered important.
		 */
		CHECK_RESULT,
		/**
		 * The debugger watches for size changes in two minimization operations.
		 */
		MINIMIZATION
	}
	
	/**
	 * Getter for an {@link IOperation}.
	 * 
	 * @param testMode
	 *            test mode
	 * @param operationType
	 *            operation to use
	 * @return operation corresponding to type
	 */
	public AbstractTester<LETTER, STATE> getTester(final EAutomatonDeltaDebuggerTestMode testMode,
			final EOperationType operationType) {
		final AbstractTester<LETTER, STATE> tester;
		switch (testMode) {
			case GENERAL:
				tester = getGeneralTester(operationType);
				break;
			
			case CHECK_RESULT:
				tester = getCheckResultTester(operationType);
				break;
			
			case MINIMIZATION:
				tester = getMinimizationResultTester();
				break;
			
			default:
				throw new IllegalArgumentException("Unknown tester: " + testMode);
		}
		return tester;
	}
	
	/**
	 * Constructs an {@link IOperation} object from the setting.
	 * 
	 * @param operationType
	 *            operation to use
	 * @param automaton
	 *            automaton
	 * @param factory
	 *            state factory
	 * @return IOperation instance
	 * @throws Throwable
	 *             when error occurs
	 */
	public IOperation<LETTER, STATE> getIOperation(final EOperationType operationType,
			final INestedWordAutomaton<LETTER, STATE> automaton,
			final IMergeStateFactory<STATE> factory) throws Throwable {
		final AutomatonDebuggerExamples<LETTER, STATE> examples = new AutomatonDebuggerExamples<>(mServices);
		
		return examples.getOperation(operationType, automaton, factory);
	}
	
	/**
	 * Example tester for debugging general problems.
	 * 
	 * @param operationType
	 *            operation to use
	 * @return tester which listens for any throwable
	 */
	public AbstractTester<LETTER, STATE> getGeneralTester(final EOperationType operationType) {
		// 'null' stands for any exception
		final Throwable throwable = null;
		
		return new AbstractTester<LETTER, STATE>(throwable) {
			@Override
			public void execute(final INestedWordAutomaton<LETTER, STATE> automaton) throws Throwable {
				final IMergeStateFactory<STATE> factory = (IMergeStateFactory<STATE>) automaton.getStateFactory();
				
				getIOperation(operationType, automaton, factory);
			}
		};
	}
	
	/**
	 * Example tester for debugging problems with the {@code checkResult()} method of {@code IOperation}.
	 * 
	 * @param operationType
	 *            operation to use
	 * @return tester which debugs the checkResult method
	 */
	private AbstractTester<LETTER, STATE> getCheckResultTester(final EOperationType operationType) {
		final String message = "'checkResult' failed";
		final Throwable throwable = new DebuggerException(message);
		
		return new AbstractTester<LETTER, STATE>(throwable) {
			@Override
			public void execute(final INestedWordAutomaton<LETTER, STATE> automaton) throws Throwable {
				final IMergeStateFactory<STATE> factory = (IMergeStateFactory<STATE>) automaton.getStateFactory();
				
				final IOperation<LETTER, STATE> op = getIOperation(operationType, automaton, factory);
				
				// throws a fresh exception iff checkResult() fails
				if (!op.checkResult(factory)) {
					throw throwable;
				}
			}
		};
	}
	
	/**
	 * Example tester for debugging problems with the {@code checkResult()} method of {@code IOperation}.
	 * 
	 * @return tester which debugs the checkResult method
	 */
	private AbstractTester<LETTER, STATE> getMinimizationResultTester() {
		final String message = "result size differs";
		final Throwable throwable = new DebuggerException(message);
		
		return new AbstractTester<LETTER, STATE>(throwable) {
			@Override
			public void execute(final INestedWordAutomaton<LETTER, STATE> automaton) throws Throwable {
				final AutomataLibraryServices services = new AutomataLibraryServices(mServices);
				final IMergeStateFactory<STATE> factory = (IMergeStateFactory<STATE>) automaton.getStateFactory();
				
				final IDoubleDeckerAutomaton<LETTER, STATE> doubleDecker =
						(automaton instanceof IDoubleDeckerAutomaton<?, ?>)
								? (IDoubleDeckerAutomaton<LETTER, STATE>) automaton
								: new RemoveUnreachable<>(services, automaton).getResult();
				
				final INestedWordAutomaton<LETTER, STATE> result1 =
						new MinimizeDfaSimulation<>(services, factory, doubleDecker).getResult();
				final INestedWordAutomaton<LETTER, STATE> result2 =
						new ReduceNwaDirectSimulationB<>(services, factory, doubleDecker).getResult();
				
				// throws a fresh exception iff checkResult() fails
				if (result1.size() != result2.size()) {
					throw throwable;
				}
			}
		};
	}
}