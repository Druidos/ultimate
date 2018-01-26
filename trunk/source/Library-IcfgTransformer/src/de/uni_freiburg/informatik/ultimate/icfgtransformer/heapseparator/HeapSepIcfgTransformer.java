package de.uni_freiburg.informatik.ultimate.icfgtransformer.heapseparator;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.core.model.models.ModelUtils;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.icfgtransformer.IBacktranslationTracker;
import de.uni_freiburg.informatik.ultimate.icfgtransformer.IIcfgTransformer;
import de.uni_freiburg.informatik.ultimate.icfgtransformer.ILocationFactory;
import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.Sort;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.vpdomain.HeapSepProgramConst;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgEdgeIterator;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transformations.ReplacementVarFactory;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramConst;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramNonOldVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVarOrConst;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.arrays.ArrayIndex;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.equalityanalysis.IEqualityAnalysisResultProvider;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.equalityanalysis.IEqualityProvidingIntermediateState;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.BoogieIcfgLocation;
import de.uni_freiburg.informatik.ultimate.util.datastructures.UnionFind;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.HashRelation;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.NestedMap2;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.Triple;

public class HeapSepIcfgTransformer<INLOC extends IcfgLocation, OUTLOC extends IcfgLocation>
		implements IIcfgTransformer<OUTLOC> {

	private IIcfg<OUTLOC> mResultIcfg;

	private final Preprocessing mPreprocessing = Preprocessing.FREEZE_VARIABLES;

	private final ILogger mLogger;

//	private final IEqualityAnalysisResultProvider<IcfgLocation, IIcfg<?>> mEqualityProvider;

	private final HeapSeparatorBenchmark mStatistics;

	private final ManagedScript mManagedScript;

	/**
	 * Default constructor.
	 *
	 * @param originalIcfg
	 *            an input {@link IIcfg}.
	 * @param funLocFac
	 *            A location factory.
	 * @param backtranslationTracker
	 *            A backtranslation tracker.
	 * @param outLocationClass
	 *            The class object of the type of locations of the output {@link IIcfg}.
	 * @param newIcfgIdentifier
	 *            The identifier of the new {@link IIcfg}
	 * @param validArray
	 * @param statistics
	 * @param transformer
	 *            The transformer that should be applied to each transformula of each transition of the input
	 *            {@link IIcfg} to create a new {@link IIcfg}.
	 */
	public HeapSepIcfgTransformer(final ILogger logger, final IIcfg<INLOC> originalIcfg,
			final ILocationFactory<INLOC, OUTLOC> funLocFac,
			final ReplacementVarFactory replacementVarFactory, final IBacktranslationTracker backtranslationTracker,
			final Class<OUTLOC> outLocationClass, final String newIcfgIdentifier,
			final IEqualityAnalysisResultProvider<IcfgLocation, IIcfg<?>> equalityProvider,
			final IProgramNonOldVar validArray) {
		assert logger != null;
		mStatistics = new HeapSeparatorBenchmark();
		mManagedScript = originalIcfg.getCfgSmtToolkit().getManagedScript();
		mLogger = logger;

		computeResult(originalIcfg, funLocFac, replacementVarFactory, backtranslationTracker, outLocationClass,
				newIcfgIdentifier, equalityProvider, validArray);
	}

	/**
	 * Steps in the transformation:
	 * <ul>
	 *  <li> two options for preprocessing
	 *   <ol>
	 *    <li> execute the ArrayIndexExposer: transform the input Icfg into an Icfg with additional "freeze-variables"
	 *    <li> introduce the "memloc"-array
	 *   </ol>
	 *  <li> run the equality analysis (VPDomain/map equality domain) on the preprocessed Icfg
	 *  <li> compute an array partitioning according to the analysis result
	 *  <li> transform the input Icfg into an Icfg where the arrays have been split
	 * </ul>
	 *
	 * @param originalIcfg
	 * @param funLocFac
	 * @param replacementVarFactory
	 * @param backtranslationTracker
	 * @param outLocationClass
	 * @param newIcfgIdentifier
	 * @param equalityProvider
	 * @param validArray
	 * @return
	 */
	private void computeResult(final IIcfg<INLOC> originalIcfg, final ILocationFactory<INLOC, OUTLOC> funLocFac,
			final ReplacementVarFactory replacementVarFactory, final IBacktranslationTracker backtranslationTracker,
			final Class<OUTLOC> outLocationClass, final String newIcfgIdentifier,
			final IEqualityAnalysisResultProvider<IcfgLocation, IIcfg<?>> equalityProvider,
			final IProgramNonOldVar validArray) {


		final ILocationFactory<OUTLOC, OUTLOC> outToOutLocFac =
				(ILocationFactory<OUTLOC, OUTLOC>) createIcfgLocationToIcfgLocationFactory();

		// TODO : where do we get this variable from?
//		final IProgramVar validArray = originalIcfg.getCfgSmtToolkit().getSymbolTable().getProgramVar(
//		final IProgramVar validArray = originalIcfg.getCfgSmtToolkit().getSymbolTable().getProgramVar(
//				mManagedScript.variable(validArrayName, null));

		/*
		 * 1. Execute the preprocessing
		 */
		final IIcfg<OUTLOC> preprocessedIcfg;
		final NestedMap2<EdgeInfo, Term, StoreIndexInfo> edgeToIndexToStoreIndexInfo;
		final Map<StoreIndexInfo, IProgramNonOldVar> storeIndexInfoToFreezeVar;
		if (mPreprocessing == Preprocessing.FREEZE_VARIABLES) {
			/*
			 * add the freeze var updates to each transition with an array update
			 */
			final StoreIndexFreezerIcfgTransformer<INLOC, OUTLOC> sifit =
					new StoreIndexFreezerIcfgTransformer<>(mLogger, "icfg_with_uninitialized_freeze_vars",
							outLocationClass, originalIcfg, funLocFac, backtranslationTracker);
			final IIcfg<OUTLOC> icfgWFreezeVarsUninitialized = sifit.getResult();

			storeIndexInfoToFreezeVar = sifit.getArrayAccessInfoToFreezeVar();
			edgeToIndexToStoreIndexInfo = sifit.getEdgeToIndexToStoreIndexInfo();

			/*
			 * Create a fresh literal/constant for each freeze variable that was introduced, we call them freeze
			 * literals.
			 * Announce them to the equality analysis as special literals, which are, by axiom, pairwise disjoint.
			 */
			final Map<IProgramNonOldVar, IProgramConst> freezeVarTofreezeVarLit = new HashMap<>();

			mManagedScript.lock(this);
			for (final IProgramNonOldVar freezeVar : storeIndexInfoToFreezeVar.values()) {

				final String freezeVarLitName = getFreezeVarLitName(freezeVar);
				mManagedScript.declareFun(this, freezeVarLitName, new Sort[0], freezeVar.getSort());
				final ApplicationTerm freezeVarLitTerm = (ApplicationTerm) mManagedScript.term(this, freezeVarLitName);

				freezeVarTofreezeVarLit.put(freezeVar, new HeapSepProgramConst(freezeVarLitTerm));
//						(IProgramConst) replacementVarFactory.getOrConstuctReplacementVar(null, false));
			}
			mManagedScript.unlock(this);
			equalityProvider.announceAdditionalLiterals(freezeVarTofreezeVarLit.values());

			/*
			 * Add initialization code for each of the newly introduced freeze variables.
			 * Each freeze variable is initialized to its corresponding freeze literal.
			 * Furthermore the valid-array (of the memory model) is assumed to be 1 at each freeze literal.
			 */
			final FreezeVarInitializer<OUTLOC, OUTLOC> fvi = new FreezeVarInitializer<>(mLogger,
					"icfg_with_initialized_freeze_vars", outLocationClass, icfgWFreezeVarsUninitialized, outToOutLocFac,
					backtranslationTracker, freezeVarTofreezeVarLit, validArray);
			final IIcfg<OUTLOC> icfgWFreezeVarsInitialized = fvi.getResult();

			preprocessedIcfg = icfgWFreezeVarsInitialized;
		} else {
			assert mPreprocessing == Preprocessing.MEMLOC_ARRAY;
			// TODO implement..
			preprocessedIcfg = null;

//			writeIndexTermToTfInfoToFreezeVar = null;
			storeIndexInfoToFreezeVar = null;
			edgeToIndexToStoreIndexInfo = null;
		}

		/*
		 * 2. run the equality analysis
		 */
		equalityProvider.preprocess(preprocessedIcfg);

		/*
		 * 3a. look up all locations where
		 *  <li> an array cell is accessed
		 *  <li> two arrays are related
		 */
		final HeapSepPreAnalysis heapSepPreanalysis = new HeapSepPreAnalysis(mLogger, mManagedScript);
		new IcfgEdgeIterator(originalIcfg).forEachRemaining(edge -> heapSepPreanalysis.processEdge(edge));
		heapSepPreanalysis.finish();

		final Map<IProgramVarOrConst, ArrayGroup> arrayToArrayGroup = heapSepPreanalysis.getArrayToArrayGroup();

		final PartitionManager partitionManager = new PartitionManager(arrayToArrayGroup, storeIndexInfoToFreezeVar);

		/*
		 * 3b. compute an array partitioning
		 */
		if (mPreprocessing == Preprocessing.FREEZE_VARIABLES) {
			for (final SelectInfo si : heapSepPreanalysis.getSelectInfos()) {
				partitionManager.processSelect(si, getEqualityProvidingIntermediateState(si.getEdgeInfo(),
						equalityProvider));
			}
			partitionManager.finish();
		} else {
			// TODO
			assert false;
		}

		/*
		 * 4. Execute the transformer that splits up the arrays according to the result from the equality analysis.
		 *  Note that this transformation is done on the original input Icfg, not on the output of the
		 *  ArrayIndexExposer, which we ran the equality analysis on.
		 */
		final PartitionProjectionTransitionTransformer<INLOC, OUTLOC> heapSeparatingTransformer =
				new PartitionProjectionTransitionTransformer<>(mLogger, "HeapSeparatedIcfg", outLocationClass,
						originalIcfg, funLocFac, backtranslationTracker,
						partitionManager.getSelectInfoToDimensionToLocationBlock(),
						edgeToIndexToStoreIndexInfo,
						arrayToArrayGroup);
		mResultIcfg = heapSeparatingTransformer.getResult();
	}

	private String getFreezeVarLitName(final IProgramNonOldVar freezeVar) {
		// TODO make _really_ sure that the new id is unique
		return freezeVar.getGloballyUniqueId() + "_lit";
	}

	/**
	 * For the moment this will return the EqState of the source location of edgeInfo, but in order to be able to
	 *  deal with select indices that are aux vars, we need to have something different here
	 * @param edgeInfo
	 * @param equalityProvider
	 * @return
	 */
	private IEqualityProvidingIntermediateState getEqualityProvidingIntermediateState(final EdgeInfo edgeInfo,
			final IEqualityAnalysisResultProvider<IcfgLocation, IIcfg<?>> equalityProvider) {
		return equalityProvider.getEqualityProvidingIntermediateState(edgeInfo.getEdge());
	}

	@Override
	public IIcfg<OUTLOC> getResult() {
		return mResultIcfg;
	}


	enum Preprocessing {
		FREEZE_VARIABLES, MEMLOC_ARRAY;
	}


//	public String getHeapSeparationSummary() {
//		return null;
//	}

	public HeapSeparatorBenchmark getStatistics() {
		return mStatistics;
	}

	/**
	 * (almost) a copy from IcfgTranformationObserver
	 *  --> should probably replace this with a less ad-hoc solution some time
	 *
	 * @return
	 */
//	private static ILocationFactory<IcfgLocation, IcfgLocation> createIcfgLocationToIcfgLocationFactory() {
	private static ILocationFactory<BoogieIcfgLocation, BoogieIcfgLocation> createIcfgLocationToIcfgLocationFactory() {
		return (oldLocation, debugIdentifier, procedure) -> {
				final BoogieIcfgLocation rtr = new BoogieIcfgLocation(debugIdentifier, procedure,
					oldLocation.isErrorLocation(), oldLocation.getBoogieASTNode());
			ModelUtils.copyAnnotations(oldLocation, rtr);
			return rtr;
		};

//		return (oldLocation, debugIdentifier, procedure) -> {
//			final IcfgLocation rtr = new IcfgLocation(debugIdentifier, procedure);
//			ModelUtils.copyAnnotations(oldLocation, rtr);
//			return rtr;
//		};
	}


//		private static ILocationFactory<BoogieIcfgLocation, BoogieIcfgLocation> createBoogieLocationFactory() {
//		return (oldLocation, debugIdentifier, procedure) -> {
//			final BoogieIcfgLocation rtr = new BoogieIcfgLocation(debugIdentifier, procedure,
//					oldLocation.isErrorLocation(), oldLocation.getBoogieASTNode());
//			ModelUtils.copyAnnotations(oldLocation, rtr);
//			return rtr;
//		};
//	}
//
//	private static <INLOC extends IcfgLocation> ILocationFactory<INLOC, IcfgLocation> createIcfgLocationFactory() {
//		return (oldLocation, debugIdentifier, procedure) -> {
//			final IcfgLocation rtr = new IcfgLocation(debugIdentifier, procedure);
//			ModelUtils.copyAnnotations(oldLocation, rtr);
//			return rtr;
//		};
//	}
}

class PartitionManager {

	// input
	private final Map<IProgramVarOrConst, ArrayGroup> mArrayToArrayGroup;

	// input
	private final Map<IProgramVar, StoreIndexInfo> mFreezeVarToStoreIndexInfo;

	// output
//	private final Map<SelectInfo, LocationBlock> mSelectInfoToLocationBlock;
	private final NestedMap2<SelectInfo, Integer, LocationBlock> mSelectInfoToDimensionToLocationBlock;

//	private final Map<ArrayGroup, UnionFind<StoreIndexInfo>> mArrayGroupToStoreIndexInfoPartition;
	private final NestedMap2<ArrayGroup, Integer, UnionFind<StoreIndexInfo>>
		mArrayGroupToDimensionToStoreIndexInfoPartition;

	/**
	 * maps a selectInfo to any one of the StoreIndexInfos that may be equal to the selectInfo
	 */
//	Map<SelectInfo, StoreIndexInfo> mSelectInfoToToSampleStoreIndexInfo;
	NestedMap2<SelectInfo, Integer, StoreIndexInfo> mSelectInfoToDimensionToToSampleStoreIndexInfo;

	private boolean mIsFinished = false;

	public PartitionManager(final Map<IProgramVarOrConst, ArrayGroup> arrayToArrayGroup,
			final Map<StoreIndexInfo, IProgramNonOldVar> arrayAccessInfoToFreezeVar) {

//		mArrayToArrayGroup = new HashMap<>();
//		for (final ArrayGroup ag : arrayGroups) {
//			for (final IProgramVarOrConst a : ag.getArrays()) {
//				mArrayToArrayGroup.put(a, ag);
//			}
//		}
		mArrayToArrayGroup = arrayToArrayGroup;

		mFreezeVarToStoreIndexInfo = new HashMap<>();
		for (final Entry<StoreIndexInfo, IProgramNonOldVar> en : arrayAccessInfoToFreezeVar.entrySet()) {
			mFreezeVarToStoreIndexInfo.put(en.getValue(), en.getKey());
		}

		mArrayGroupToDimensionToStoreIndexInfoPartition = new NestedMap2<>();
		mSelectInfoToDimensionToLocationBlock = new NestedMap2<>();

		mSelectInfoToDimensionToToSampleStoreIndexInfo = new NestedMap2<>();
	}

	/**
	 * Merge all LocationBlocks (sets of StoreIndexInfos) that
	 *  <li> may write to the same array as the given SelectInfo reads from
	 *  <li> whose freezeVar may equal the SelectInfo's index at the SelectInfo's location (according to our equality
	 * 		 analysis)
	 *
	 * @param selectInfo
	 * @param eps
	 */
	void processSelect(final SelectInfo selectInfo, final IEqualityProvidingIntermediateState eps) {
		final HashRelation<Integer, StoreIndexInfo> dimensionToMayEqualStoreIndexInfos = new HashRelation<>();

//		final Term selectIndex = selectInfo.getArrayCellAccess().getIndex();
		final ArrayIndex selectIndex = selectInfo.getIndex();

		for (final Entry<IProgramVar, StoreIndexInfo> en : mFreezeVarToStoreIndexInfo.entrySet()) {
			final IProgramVar freezeVar = en.getKey();
			final StoreIndexInfo sii = en.getValue();

			if (!sii.getArrays().contains(selectInfo.getArrayPvoc())) {
				// arrays don't match (coarse check failed..)
				continue;
			}
//			// finer grained check: do the access dimensions match -- EDIT: may be nonsense..
//			if (!storeAndSelectMayInteract(sii, selectInfo)) {
//
//			}

			for (int dim = 0; dim < selectIndex.size(); dim++) {

				if (!sii.getArrayToAccessDimensions().containsPair(selectInfo.getArrayPvoc(), dim)) {
					/*
					 * not the right base-array/dimension combination --> continue
					 *  (more detailed: the array write(s) that the storeIndexInfo represents are not to the array that
					 *   the SelectInfo reads or not to the current dimension dim that the current element of the
					 *   SelectInfo's index tuple refers to)
					 */
					continue;
				}

				if (eps.areUnequal(selectIndex.get(dim), freezeVar.getTerm())) {
					// nothing to do
				} else {
					// select index and freezeVar may be equal at this location

//					mayEqualStoreIndexInfos.add(sii);
					dimensionToMayEqualStoreIndexInfos.addPair(dim, sii);
				}
			}
		}


//		if (mayEqualStoreIndexInfos.size() <= 1) {
		for (int i = 0; i < selectIndex.size(); i++) {
			final Set<StoreIndexInfo> mayEqualStoreIndexInfos = dimensionToMayEqualStoreIndexInfos.getImage(i);

			if (mayEqualStoreIndexInfos.size() <= 1) {
				// nothing to do
			} else {
				final StoreIndexInfo sample = mayEqualStoreIndexInfos.iterator().next();

				mSelectInfoToDimensionToToSampleStoreIndexInfo.put(selectInfo, i, sample);

				for (final StoreIndexInfo sii : mayEqualStoreIndexInfos) {
					mergeBlocks(selectInfo, i, sii, sample);
				}

			}
		}
	}

//	/**
//	 * Check for two cases where an StoreIndexInfo does definitively not interact with an array read (SelectInfo).
//	 *  <li> the StoreIndexInfo is about a write to a different array than the read
//	 *  <li> the StoreIndexInfo is about a write to a different dimension than the array read
//	 *
//	 * @param sii
//	 * @param selectInfo
//	 * @return
//	 */
//	private static boolean storeAndSelectMayInteract(final StoreIndexInfo sii, final SelectInfo selectInfo) {
//		for (final Entry<IProgramVarOrConst, Integer> en : sii.getArrayToAccessDimensions()) {
//			if ()
//
//		}
//		return false;
//	}

	public void finish() {
		/*
		 * rewrite the collected information into our output format
		 */
//		for (final Entry<SelectInfo, StoreIndexInfo> en : mSelectInfoToToSampleStoreIndexInfo.entrySet()) {
		for (final Triple<SelectInfo, Integer, StoreIndexInfo> en :
				mSelectInfoToDimensionToToSampleStoreIndexInfo.entrySet()) {

//			final StoreIndexInfo sampleSii = en.getValue();
			final StoreIndexInfo sampleSii = en.getThird();

//			final SelectInfo selectInfo = en.getKey();
			final SelectInfo selectInfo = en.getFirst();
			final Integer dim = en.getSecond();

			final ArrayGroup arrayGroup = mArrayToArrayGroup.get(selectInfo.getArrayPvoc());

//			final UnionFind<StoreIndexInfo> partition = mArrayGroupToStoreIndexInfoPartition.get(arrayGroup);
			final UnionFind<StoreIndexInfo> partition =
					mArrayGroupToDimensionToStoreIndexInfoPartition.get(arrayGroup, dim);
			final Set<StoreIndexInfo> eqc = partition.getEquivalenceClassMembers(sampleSii);

//			assert assertWritesAreToReadArray(eqc, selectInfo);

			mSelectInfoToDimensionToLocationBlock.put(selectInfo, dim, new LocationBlock(eqc, arrayGroup, dim));
		}
		mIsFinished = true;

		assert sanityCheck();
	}

	private boolean assertWritesAreToReadArray(final Set<StoreIndexInfo> eqc, final SelectInfo selectInfo) {
		for (final StoreIndexInfo sii : eqc) {
			if (!sii.getArrays().contains(selectInfo.getArrayPvoc())) {
				assert false;
				return false;
			}
		}
		return true;
	}

	private boolean sanityCheck() {
		/*
		 * each location block in the image of a selectInfo must only contain StoreIndexInfos that refer to the same
		 *  array
		 * (a write cannot influence a read if it is to a different array)
		 */
//		for (final Entry<SelectInfo, LocationBlock> en : mSelectInfoToDimensionToLocationBlock.entrySet()) {
		for (final Triple<SelectInfo, Integer, LocationBlock> en : mSelectInfoToDimensionToLocationBlock.entrySet()) {
//			assert assertWritesAreToReadArray(en.getValue().getLocations(), en.getKey());
			assert assertWritesAreToReadArray(en.getThird().getLocations(), en.getFirst());

			if (!en.getSecond().equals(en.getThird().getDimension())) {
				assert false;
				return false;
			}
//			for (final StoreIndexInfo sii : en.getValue().getLocations()) {
//				if (!sii.getArrays().contains(en.getKey().getArrayPvoc())) {
//					assert false;
//					return false;
//				}
//			}
		}
		return true;
	}

	private void mergeBlocks(final SelectInfo selectInfo, final int dim, final StoreIndexInfo sii1,
			final StoreIndexInfo sii2) {
//	private void mergeBlocks(final SelectInfo selectInfo, final StoreIndexInfo sii1, final StoreIndexInfo sii2) {
		final IProgramVarOrConst array = selectInfo.getArrayPvoc();
		final ArrayGroup arrayGroup = mArrayToArrayGroup.get(array);

//		UnionFind<StoreIndexInfo> partition = mArrayGroupToStoreIndexInfoPartition.get(arrayGroup);
		UnionFind<StoreIndexInfo> partition = mArrayGroupToDimensionToStoreIndexInfoPartition.get(arrayGroup, dim);
		if (partition == null) {
			partition = new UnionFind<>();
			mArrayGroupToDimensionToStoreIndexInfoPartition.put(arrayGroup, dim, partition);
		}

		partition.findAndConstructEquivalenceClassIfNeeded(sii1);
		partition.findAndConstructEquivalenceClassIfNeeded(sii2);
		partition.union(sii1, sii2);
	}

//	public Map<SelectInfo, LocationBlock> getSelectInfoToLocationBlock() {
	public NestedMap2<SelectInfo, Integer, LocationBlock> getSelectInfoToDimensionToLocationBlock() {
		if (!mIsFinished) {
			throw new AssertionError();
		}
		return mSelectInfoToDimensionToLocationBlock;
//		return Collections.unmodifiableMap(mSelectInfoToDimensionToLocationBlock);
	}

//	public LocationBlock getLocationBlock(final SelectInfo si) {
	public LocationBlock getLocationBlock(final SelectInfo si, final Integer dim) {
		if (!mIsFinished) {
			throw new AssertionError();
		}
//		return mSelectInfoToDimensionToLocationBlock.get(si);
		return mSelectInfoToDimensionToLocationBlock.get(si, dim);
	}
}
