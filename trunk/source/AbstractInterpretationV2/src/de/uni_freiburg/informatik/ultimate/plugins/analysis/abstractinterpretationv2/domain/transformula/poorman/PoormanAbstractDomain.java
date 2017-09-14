/*
 * Copyright (C) 2017 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2017 Marius Greitschus (greitsch@informatik.uni-freiburg.de)
 * Copyright (C) 2017 University of Freiburg
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

package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.poorman;

import de.uni_freiburg.informatik.ultimate.core.model.preferences.IPreferenceProvider;
import de.uni_freiburg.informatik.ultimate.core.model.services.IProgressAwareTimer;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.IAbstractDomain;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.IAbstractPostOperator;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.IAbstractState;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.IAbstractStateBinaryOperator;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.IBoogieVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgEdge;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVarOrConst;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.Activator;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.FixpointEngineParameters;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.ILoopDetector;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.ITransitionProvider;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.rcfg.IcfgTransitionProvider;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.rcfg.RCFGLiteralCollector;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.rcfg.RcfgLoopDetector;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.preferences.AbsIntPrefInitializer;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.tool.initializer.FixpointEngineParameterFactory;

public class PoormanAbstractDomain<BACKING extends IAbstractState<BACKING, IBoogieVar>>
		implements IAbstractDomain<PoormanAbstractState<BACKING>, IcfgEdge, IProgramVarOrConst> {

	private final IUltimateServiceProvider mServices;
	private final IAbstractDomain<BACKING, IcfgEdge, IBoogieVar> mBackingDomain;

	private final IAbstractStateBinaryOperator<PoormanAbstractState<BACKING>> mWideningOperator = null;
	private IAbstractPostOperator<PoormanAbstractState<BACKING>, IcfgEdge, IProgramVarOrConst> mPostOperator = null;
	private final IIcfg<?> mRoot;

	public PoormanAbstractDomain(final IUltimateServiceProvider services, final IIcfg<?> root) {
		mServices = services;
		mRoot = root;

		final IPreferenceProvider ups = mServices.getPreferenceProvider(Activator.PLUGIN_ID);
		final IProgressAwareTimer timer;
		if (ups.getBoolean(AbsIntPrefInitializer.LABEL_RUN_AS_PRE_ANALYSIS)) {
			timer = mServices.getProgressMonitorService().getChildTimer(0.2);
		} else {
			timer = mServices.getProgressMonitorService();
		}

		final ITransitionProvider<IcfgEdge, IcfgLocation> transProvider = new IcfgTransitionProvider(root);
		final ILoopDetector<IcfgEdge> loopDetector = new RcfgLoopDetector<>();
		final FixpointEngineParameterFactory fpepf =
				new FixpointEngineParameterFactory(root, () -> new RCFGLiteralCollector(root), services);
		final FixpointEngineParameters<BACKING, IcfgEdge, IBoogieVar, IcfgLocation> params =
				fpepf.createParams(timer, transProvider, loopDetector);

		mBackingDomain = params.getAbstractDomain();
	}

	@Override
	public PoormanAbstractState<BACKING> createTopState() {
		return new PoormanAbstractState<>(mServices, mBackingDomain, false);
	}

	@Override
	public PoormanAbstractState<BACKING> createBottomState() {
		return new PoormanAbstractState<>(mServices, mBackingDomain, true);
	}

	@Override
	public IAbstractStateBinaryOperator<PoormanAbstractState<BACKING>> getWideningOperator() {
		if (mWideningOperator == null) {
			// TODO Fill with widening operator
			// mWideningOperator = ...
		}
		return mWideningOperator;
	}

	@Override
	public IAbstractPostOperator<PoormanAbstractState<BACKING>, IcfgEdge, IProgramVarOrConst> getPostOperator() {
		if (mPostOperator == null) {
			// TODO Fill with sense
			mPostOperator = new PoormansAbstractPostOperator<>(mServices, mRoot, mBackingDomain);
		}
		return mPostOperator;
	}

}
