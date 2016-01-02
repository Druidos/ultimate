/*
 * Copyright (C) 2016 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2016 University of Freiburg
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

import java.util.Collection;

import org.apache.log4j.Logger;

import de.uni_freiburg.informatik.ultimate.automata.LibraryIdentifiers;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.INestedWordAutomatonSimple;
import de.uni_freiburg.informatik.ultimate.core.services.model.IUltimateServiceProvider;

/**
 * Builder used by buchiComplementFKV to obtain TightLevelRankingStateGenerators.
 * @author Matthias Heizmann
 *
 * @param <LETTER>
 * @param <STATE>
 */
public abstract class LevelRankingGenerator<LETTER, STATE, CONSTRAINT extends LevelRankingConstraint<LETTER, STATE>> {

	protected final IUltimateServiceProvider m_Services;
	protected final Logger m_Logger;
	protected final INestedWordAutomatonSimple<LETTER, STATE> m_Operand;
	protected final int m_UserDefinedMaxRank;

	public LevelRankingGenerator(
			IUltimateServiceProvider services,
			INestedWordAutomatonSimple<LETTER, STATE> operand,
			int userDefinedMaxRank) {
		super();
		m_Services = services;
		m_Logger = m_Services.getLoggingService().getLogger(LibraryIdentifiers.s_LibraryID);
		m_Operand = operand;
		m_UserDefinedMaxRank = userDefinedMaxRank;
	}
	
	
	public abstract Collection<LevelRankingState<LETTER, STATE>> generateLevelRankings(CONSTRAINT constraint, boolean predecessorIsSubsetComponent);
}