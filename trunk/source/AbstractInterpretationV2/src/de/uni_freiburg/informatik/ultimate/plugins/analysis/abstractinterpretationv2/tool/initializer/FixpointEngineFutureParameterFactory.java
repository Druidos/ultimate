package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.tool.initializer;

import java.util.Collections;
import java.util.Objects;

import de.uni_freiburg.informatik.ultimate.core.model.preferences.IPreferenceProvider;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IProgressAwareTimer;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.IAbstractDomain;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.IAbstractState;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.IVariableProvider;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgEdge;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVarOrConst;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.Activator;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.FixpointEngineParameters;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.IAbstractStateStorage;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.IDebugHelper;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.ILoopDetector;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.ITransitionProvider;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.generic.LiteralCollection;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.rcfg.FutureRcfgVariableProvider;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.rcfg.IcfgAbstractStateStorageProvider;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.rcfg.RcfgDebugHelper;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.empty.EmptyDomain;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.nonrelational.dataflow.DataflowDomain;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.nonrelational.livevariable.LiveVariableDomain;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.arraytheory.SMTTheoryDomain;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.poorman.PoormanAbstractDomain;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.VPDomain;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.VPDomainPreanalysis;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.preferences.AbsIntPrefInitializer;

public class FixpointEngineFutureParameterFactory {

	private final IIcfg<?> mRoot;
	private final IUltimateServiceProvider mServices;

	public FixpointEngineFutureParameterFactory(final IIcfg<?> root, final IUltimateServiceProvider services) {
		mRoot = root;
		mServices = services;
	}

	@SuppressWarnings("unchecked")
	public <STATE extends IAbstractState<STATE>>
			FixpointEngineParameters<STATE, IcfgEdge, IProgramVarOrConst, IcfgLocation>
			createParamsFuture(final IProgressAwareTimer timer,
					final ITransitionProvider<IcfgEdge, IcfgLocation> transitionProvider,
					final ILoopDetector<IcfgEdge> loopDetector) {
		final IAbstractDomain<STATE, IcfgEdge> domain = (IAbstractDomain<STATE, IcfgEdge>) selectDomainFutureCfg();
		final IAbstractStateStorage<STATE, IcfgEdge, IcfgLocation> storageProvider =
				new IcfgAbstractStateStorageProvider<>(mServices, transitionProvider);
		final IVariableProvider<STATE, IcfgEdge> variableProvider =
				new FutureRcfgVariableProvider<>(mRoot.getCfgSmtToolkit().getSymbolTable(), mServices);
		final IDebugHelper<STATE, IcfgEdge, IProgramVarOrConst, IcfgLocation> debugHelper =
				new RcfgDebugHelper<>(mRoot.getCfgSmtToolkit(), mServices, mRoot.getCfgSmtToolkit().getSymbolTable());

		return new FixpointEngineParameters<STATE, IcfgEdge, IProgramVarOrConst, IcfgLocation>(mServices,
				IProgramVarOrConst.class).setDomain(domain).setLoopDetector(loopDetector).setStorage(storageProvider)
						.setTransitionProvider(transitionProvider).setVariableProvider(variableProvider)
						.setDebugHelper(debugHelper).setTimer(timer);
	}

	public <STATE extends IAbstractState<STATE>>
			FixpointEngineParameters<STATE, IcfgEdge, IProgramVarOrConst, IcfgLocation>
			createParamsFuture(final IProgressAwareTimer timer,
					final ITransitionProvider<IcfgEdge, IcfgLocation> transitionProvider,
					final ILoopDetector<IcfgEdge> loopDetector, final IAbstractDomain<STATE, IcfgEdge> domain) {
		final IAbstractStateStorage<STATE, IcfgEdge, IcfgLocation> storageProvider =
				new IcfgAbstractStateStorageProvider<>(mServices, transitionProvider);
		final IVariableProvider<STATE, IcfgEdge> variableProvider =
				new FutureRcfgVariableProvider<>(mRoot.getCfgSmtToolkit().getSymbolTable(), mServices);
		final IDebugHelper<STATE, IcfgEdge, IProgramVarOrConst, IcfgLocation> debugHelper =
				new RcfgDebugHelper<>(mRoot.getCfgSmtToolkit(), mServices, mRoot.getCfgSmtToolkit().getSymbolTable());
		return new FixpointEngineParameters<STATE, IcfgEdge, IProgramVarOrConst, IcfgLocation>(mServices,
				IProgramVarOrConst.class).setDomain(domain).setLoopDetector(loopDetector).setStorage(storageProvider)
						.setTransitionProvider(transitionProvider).setVariableProvider(variableProvider)
						.setDebugHelper(debugHelper).setTimer(timer);
	}

	public <STATE extends IAbstractState<STATE>>
			FixpointEngineParameters<STATE, IcfgEdge, IProgramVarOrConst, IcfgLocation> addDefaultParamsFuture(
					final FixpointEngineParameters<STATE, IcfgEdge, IProgramVarOrConst, IcfgLocation> params,
					final ITransitionProvider<IcfgEdge, IcfgLocation> transitionProvider,
					final ILoopDetector<IcfgEdge> loopDetector) {
		final IAbstractStateStorage<STATE, IcfgEdge, IcfgLocation> storageProvider =
				new IcfgAbstractStateStorageProvider<>(mServices, transitionProvider);
		final IVariableProvider<STATE, IcfgEdge> variableProvider =
				new FutureRcfgVariableProvider<>(mRoot.getCfgSmtToolkit().getSymbolTable(), mServices);
		final IDebugHelper<STATE, IcfgEdge, IProgramVarOrConst, IcfgLocation> debugHelper =
				new RcfgDebugHelper<>(mRoot.getCfgSmtToolkit(), mServices, mRoot.getCfgSmtToolkit().getSymbolTable());
		return params.setStorage(storageProvider).setVariableProvider(variableProvider).setDebugHelper(debugHelper)
				.setTransitionProvider(transitionProvider).setLoopDetector(loopDetector);
	}

	/**
	 * Creates parameters for when the equality domain is invoked from another plugin (e.g. HeapSeparator) and should
	 * not take (all) its parameters from the settings.
	 *
	 * @param timer
	 * @param transitionProvider
	 * @param loopDetector
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public <STATE extends IAbstractState<STATE>>
			FixpointEngineParameters<STATE, IcfgEdge, IProgramVarOrConst, IcfgLocation>
			createParamsFutureEqualityDomain(final IProgressAwareTimer timer,
					final ITransitionProvider<IcfgEdge, IcfgLocation> transitionProvider,
					final ILoopDetector<IcfgEdge> loopDetector) {
		final ILogger logger = mServices.getLoggingService().getLogger(Activator.PLUGIN_ID);
		final IAbstractDomain<STATE, IcfgEdge> domain =
				(IAbstractDomain<STATE, IcfgEdge>) createEqualityDomain(logger, mRoot, mServices);
		final IAbstractStateStorage<STATE, IcfgEdge, IcfgLocation> storageProvider =
				new IcfgAbstractStateStorageProvider<>(mServices, transitionProvider);
		final IVariableProvider<STATE, IcfgEdge> variableProvider =
				new FutureRcfgVariableProvider<>(mRoot.getCfgSmtToolkit().getSymbolTable(), mServices);
		final IDebugHelper<STATE, IcfgEdge, IProgramVarOrConst, IcfgLocation> debugHelper =
				new RcfgDebugHelper<>(mRoot.getCfgSmtToolkit(), mServices, mRoot.getCfgSmtToolkit().getSymbolTable());
		return new FixpointEngineParameters<STATE, IcfgEdge, IProgramVarOrConst, IcfgLocation>(mServices,
				IProgramVarOrConst.class).setDomain(domain).setLoopDetector(loopDetector).setStorage(storageProvider)
						.setTransitionProvider(transitionProvider).setVariableProvider(variableProvider)
						.setDebugHelper(debugHelper).setTimer(timer);
	}

	public <STATE extends IAbstractState<STATE>> IAbstractDomain<STATE, IcfgEdge> selectDomainFutureCfg() {
		final IPreferenceProvider prefs = mServices.getPreferenceProvider(Activator.PLUGIN_ID);
		final String selectedDomain = prefs.getString(AbsIntPrefInitializer.LABEL_ABSTRACT_DOMAIN_FUTURE);
		final ILogger logger = mServices.getLoggingService().getLogger(Activator.PLUGIN_ID);
		return selectDomainFutureCfg(selectedDomain, logger, mRoot, mServices);
	}

	@SuppressWarnings("unchecked")
	public static <STATE extends IAbstractState<STATE>> IAbstractDomain<STATE, IcfgEdge> selectDomainFutureCfg(
			final String domainName, final ILogger logger, final IIcfg<?> root,
			final IUltimateServiceProvider services) {
		if (EmptyDomain.class.getSimpleName().equals(domainName)) {
			return (IAbstractDomain<STATE, IcfgEdge>) new EmptyDomain<IcfgEdge>();
		} else if (DataflowDomain.class.getSimpleName().equals(domainName)) {
			return (IAbstractDomain<STATE, IcfgEdge>) new DataflowDomain<IcfgEdge>(logger);
		} else if (VPDomain.class.getSimpleName().equals(domainName)) {
			return (IAbstractDomain<STATE, IcfgEdge>) createEqualityDomain(logger, root, services);
		} else if (SMTTheoryDomain.class.getSimpleName().equals(domainName)) {
			return (IAbstractDomain<STATE, IcfgEdge>) new SMTTheoryDomain(services, root.getCfgSmtToolkit());
		} else if (LiveVariableDomain.class.getSimpleName().equals(domainName)) {
			return (IAbstractDomain<STATE, IcfgEdge>) new LiveVariableDomain<IcfgEdge>(logger);
		} else if (de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.nonrelational.interval.IntervalDomain.class
				.getSimpleName().equals(domainName)) {
			return (IAbstractDomain<STATE, IcfgEdge>) new de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.nonrelational.interval.IntervalDomain(
					logger, new LiteralCollection(Collections.emptySet()), services);
		} else if (PoormanAbstractDomain.class.getSimpleName().equals(domainName)) {
			return (IAbstractDomain<STATE, IcfgEdge, IProgramVarOrConst>) new PoormanAbstractDomain(services, root);
		}
		throw new UnsupportedOperationException(getFailureString(domainName));
	}

	public <DOM extends IAbstractDomain<STATE, IcfgEdge>, STATE extends IAbstractState<STATE>>
			IAbstractDomain<STATE, IcfgEdge> selectDomainFutureCfg(final Class<DOM> domain, final ILogger logger) {
		return selectDomainFutureCfg(Objects.requireNonNull(domain).getSimpleName(), logger, mRoot, mServices);
	}

	public static VPDomain<IcfgEdge> createEqualityDomain(final ILogger logger, final IIcfg<?> root,
			final IUltimateServiceProvider services) {
		final VPDomainPreanalysis preAnalysis = new VPDomainPreanalysis(root, logger, services);
		preAnalysis.postProcess();
		// final VPTfStateBuilderPreparer tfPreparer =
		// new VPTfStateBuilderPreparer(preAnalysis, root, logger);
		return new VPDomain<>(logger, services, root.getCfgSmtToolkit(), preAnalysis);
	}

	private static String getFailureString(final String selectedDomain) {
		return "The value \"" + selectedDomain + "\" of preference \""
				+ AbsIntPrefInitializer.LABEL_ABSTRACT_DOMAIN_FUTURE + "\" was not considered before! ";
	}
}
