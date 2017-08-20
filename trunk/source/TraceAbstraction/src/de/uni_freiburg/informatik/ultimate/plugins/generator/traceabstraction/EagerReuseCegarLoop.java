package de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction;

import java.util.Collection;
import java.util.List;

import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryException;
import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.INwaOutgoingLetterAndTransitionProvider;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.Difference;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.PowersetDeterminizer;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.oldapi.IOpWithDelayedDeadEndRemoval;
import de.uni_freiburg.informatik.ultimate.core.model.services.IToolchainStorage;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.CfgSmtToolkit;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.interpolantautomata.transitionappender.AbstractInterpolantAutomaton;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.PredicateFactory;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TAPreferences;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer.InterpolationTechnique;

public class EagerReuseCegarLoop<LETTER extends IIcfgTransition<?>> extends BasicCegarLoop<LETTER> {

	
	private final List<AbstractInterpolantAutomaton<LETTER>> mInputFloydHoareAutomata;

	public EagerReuseCegarLoop(final String name, final IIcfg<?> rootNode, final CfgSmtToolkit csToolkit,
			final PredicateFactory predicateFactory, final TAPreferences taPrefs, final Collection<? extends IcfgLocation> errorLocs,
			final InterpolationTechnique interpolation, final boolean computeHoareAnnotation, final IUltimateServiceProvider services,
			final IToolchainStorage storage, final List<AbstractInterpolantAutomaton<LETTER>> inputFloydHoareAutomata) {
		super(name, rootNode, csToolkit, predicateFactory, taPrefs, errorLocs, interpolation, computeHoareAnnotation, services,
				storage);
		mInputFloydHoareAutomata = inputFloydHoareAutomata;
	}

	@Override
	protected void getInitialAbstraction() throws AutomataLibraryException {
		super.getInitialAbstraction();
		
		for (final AbstractInterpolantAutomaton<LETTER> ai : mInputFloydHoareAutomata) {
			ai.switchToOnDemandConstructionMode();
			final PowersetDeterminizer<LETTER, IPredicate> psd =
					new PowersetDeterminizer<>(ai, true, mPredicateFactoryInterpolantAutomata);
			IOpWithDelayedDeadEndRemoval<LETTER, IPredicate> diff;
			final boolean explointSigmaStarConcatOfIA = true;
			diff = new Difference<LETTER, IPredicate>(new AutomataLibraryServices(mServices),
					mStateFactoryForRefinement,
					(INwaOutgoingLetterAndTransitionProvider<LETTER, IPredicate>) mAbstraction, ai, psd,
					explointSigmaStarConcatOfIA);
			ai.switchToReadonlyMode();
			
			if (REMOVE_DEAD_ENDS) {
				if (mComputeHoareAnnotation) {
					final Difference<LETTER, IPredicate> difference = (Difference<LETTER, IPredicate>) diff;
					mHaf.updateOnIntersection(difference.getFst2snd2res(), difference.getResult());
				}
				diff.removeDeadEnds();
				if (mComputeHoareAnnotation) {
					mHaf.addDeadEndDoubleDeckers(diff);
				}
			}
			mAbstraction = diff.getResult();
		}
	}
	
	
	
	

}
