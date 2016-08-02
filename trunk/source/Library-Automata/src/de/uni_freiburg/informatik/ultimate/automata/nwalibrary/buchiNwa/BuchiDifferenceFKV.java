/*
 * Copyright (C) 2013-2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
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
package de.uni_freiburg.informatik.ultimate.automata.nwalibrary.buchiNwa;

import java.util.ArrayList;
import java.util.List;

import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryException;
import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.AutomataOperationCanceledException;
import de.uni_freiburg.informatik.ultimate.automata.IOperation;
import de.uni_freiburg.informatik.ultimate.automata.LibraryIdentifiers;
import de.uni_freiburg.informatik.ultimate.automata.ResultChecker;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.INestedWordAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.INestedWordAutomatonSimple;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.StateFactory;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.buchiNwa.MultiOptimizationLevelRankingGenerator.FkvOptimization;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.operations.IStateDeterminizer;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.operations.PowersetDeterminizer;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.reachableStatesAutomaton.NestedWordAutomatonReachableStates;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;


public class BuchiDifferenceFKV<LETTER,STATE> implements IOperation<LETTER,STATE> {

	private final AutomataLibraryServices mServices;
	private final ILogger mLogger;
	
	private final INestedWordAutomatonSimple<LETTER,STATE> mFstOperand;
	private final INestedWordAutomatonSimple<LETTER,STATE> mSndOperand;
	private final IStateDeterminizer<LETTER, STATE> mStateDeterminizer;
	private BuchiComplementFKVNwa<LETTER,STATE> mSndComplemented;
	private BuchiIntersectNwa<LETTER, STATE> mIntersect;
	private NestedWordAutomatonReachableStates<LETTER,STATE> mResult;
	private final StateFactory<STATE> mStateFactory;
	
	
	@Override
	public String operationName() {
		return "buchiDifferenceFKV";
	}
	
	
	@Override
	public String startMessage() {
		return "Start " + operationName() + ". First operand " + 
				mFstOperand.sizeInformation() + ". Second operand " + 
				mSndOperand.sizeInformation();	
	}
	
	
	@Override
	public String exitMessage() {
		return "Finished " + operationName() + ". First operand " + 
				mFstOperand.sizeInformation() + ". Second operand " + 
				mSndOperand.sizeInformation() + " Result " + 
				mResult.sizeInformation() + 
				" Complement of second has " + mSndComplemented.size() +
				" states " +
				mSndComplemented.getPowersetStates() + " powerset states" +
				mSndComplemented.getRankStates() + " rank states" +
			" the highest rank that occured is " + mSndComplemented.getHighesRank();
	}
	
	public int getHighestRank() {
		return mSndComplemented.getHighesRank();
	}
	
	
	public BuchiDifferenceFKV(final AutomataLibraryServices services,
			final StateFactory<STATE> stateFactory,
			final INestedWordAutomatonSimple<LETTER,STATE> fstOperand,
			final INestedWordAutomatonSimple<LETTER,STATE> sndOperand
			) throws AutomataLibraryException {
		mServices = services;
		mLogger = mServices.getLoggingService().getLogger(LibraryIdentifiers.PLUGIN_ID);
		mFstOperand = fstOperand;
		mSndOperand = sndOperand;
		mStateFactory = mFstOperand.getStateFactory();
		mStateDeterminizer = new PowersetDeterminizer<LETTER,STATE>(sndOperand, true, stateFactory);
		mLogger.info(startMessage());
		constructDifference(Integer.MAX_VALUE, FkvOptimization.HeiMat2);
		mLogger.info(exitMessage());
	}
	
	
	public BuchiDifferenceFKV(final AutomataLibraryServices services,
			final INestedWordAutomatonSimple<LETTER,STATE> fstOperand,
			final INestedWordAutomatonSimple<LETTER,STATE> sndOperand,
			final IStateDeterminizer<LETTER, STATE> stateDeterminizer,
			final StateFactory<STATE> sf, final String optimization, final int userDefinedMaxRank) throws AutomataLibraryException {
		mServices = services;
		mLogger = mServices.getLoggingService().getLogger(LibraryIdentifiers.PLUGIN_ID);
		mFstOperand = fstOperand;
		mSndOperand = sndOperand;
		mStateFactory = sf;
		mStateDeterminizer = stateDeterminizer;
		mLogger.info(startMessage());
		try {
			constructDifference(userDefinedMaxRank, FkvOptimization.valueOf(optimization));
		} catch (final AutomataOperationCanceledException oce) {
			throw new AutomataOperationCanceledException(getClass());
		}
		mLogger.info(exitMessage());
	}
	
	private void constructDifference(final int userDefinedMaxRank, final FkvOptimization optimization) throws AutomataLibraryException {
		mSndComplemented = new BuchiComplementFKVNwa<LETTER, STATE>(mServices, mSndOperand, mStateDeterminizer, mStateFactory, optimization, userDefinedMaxRank);
		mIntersect = new BuchiIntersectNwa<LETTER, STATE>(mFstOperand, mSndComplemented, mStateFactory);
		mResult = new NestedWordAutomatonReachableStates<LETTER, STATE>(mServices, mIntersect);
	}
	






	@Override
	public INestedWordAutomaton<LETTER, STATE> getResult()
			throws AutomataLibraryException {
		return mResult;
	}
	
	
	

	public BuchiComplementFKVNwa<LETTER, STATE> getSndComplemented() {
		return mSndComplemented;
	}


	@Override
	public boolean checkResult(final StateFactory<STATE> stateFactory)
			throws AutomataLibraryException {
		final boolean underApproximationOfComplement = false;
		boolean correct = true;
			mLogger.info("Start testing correctness of " + operationName());
			final INestedWordAutomaton<LETTER, STATE> fstOperandOldApi = ResultChecker.getNormalNwa(mServices, mFstOperand);
			final INestedWordAutomaton<LETTER, STATE> sndOperandOldApi = ResultChecker.getNormalNwa(mServices, mSndOperand);
			final List<NestedLassoWord<LETTER>> lassoWords = new ArrayList<NestedLassoWord<LETTER>>();
			final BuchiIsEmpty<LETTER, STATE> fstOperandEmptiness = new BuchiIsEmpty<LETTER, STATE>(mServices, fstOperandOldApi);
			final boolean fstOperandEmpty = fstOperandEmptiness.getResult();
			if (!fstOperandEmpty) {
				lassoWords.add(fstOperandEmptiness.getAcceptingNestedLassoRun().getNestedLassoWord());
			}
			final BuchiIsEmpty<LETTER, STATE> sndOperandEmptiness = new BuchiIsEmpty<LETTER, STATE>(mServices, fstOperandOldApi);
			final boolean sndOperandEmpty = sndOperandEmptiness.getResult();
			if (!sndOperandEmpty) {
				lassoWords.add(sndOperandEmptiness.getAcceptingNestedLassoRun().getNestedLassoWord());
			}
			final BuchiIsEmpty<LETTER, STATE> resultEmptiness = new BuchiIsEmpty<LETTER, STATE>(mServices, mResult);
			final boolean resultEmpty = resultEmptiness.getResult();
			if (!resultEmpty) {
				lassoWords.add(resultEmptiness.getAcceptingNestedLassoRun().getNestedLassoWord());
			}
			correct &= (!fstOperandEmpty || resultEmpty);
			assert correct;
			lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, mResult.size()));
			lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, fstOperandOldApi.size()));
			lassoWords.add(ResultChecker.getRandomNestedLassoWord(mResult, sndOperandOldApi.size()));
			lassoWords.addAll((new LassoExtractor<LETTER, STATE>(mServices, mFstOperand)).getResult());
			lassoWords.addAll((new LassoExtractor<LETTER, STATE>(mServices, mSndOperand)).getResult());
			lassoWords.addAll((new LassoExtractor<LETTER, STATE>(mServices, mResult)).getResult());

			for (final NestedLassoWord<LETTER> nlw : lassoWords) {
				correct &= checkAcceptance(nlw, fstOperandOldApi, sndOperandOldApi, underApproximationOfComplement);
				assert correct;
			}
			if (!correct) {
				ResultChecker.writeToFileIfPreferred(mServices, operationName() + "Failed", "", mFstOperand,mSndOperand);
			}
			mLogger.info("Finished testing correctness of " + operationName());
		return correct;
	}
	
	private boolean checkAcceptance(final NestedLassoWord<LETTER> nlw,
			final INestedWordAutomaton<LETTER, STATE> operand1, 
			final INestedWordAutomaton<LETTER, STATE> operand2,
			final boolean underApproximationOfComplement) throws AutomataLibraryException {
		boolean correct;
		final boolean op1 = (new BuchiAccepts<LETTER, STATE>(mServices, operand1, nlw)).getResult();
		final boolean op2 = (new BuchiAccepts<LETTER, STATE>(mServices, operand2, nlw)).getResult();
		final boolean res = (new BuchiAccepts<LETTER, STATE>(mServices, mResult, nlw)).getResult();
		if (res) {
			correct = op1 && !op2;
		} else {
			correct = !(!underApproximationOfComplement && op1 && !op2);
		}
		assert correct : operationName() + " wrong result!";
		return correct;
	}

}
