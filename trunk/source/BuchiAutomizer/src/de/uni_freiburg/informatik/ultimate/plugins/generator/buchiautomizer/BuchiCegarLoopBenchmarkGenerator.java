package de.uni_freiburg.informatik.ultimate.plugins.generator.buchiautomizer;

import java.util.ArrayList;
import java.util.Arrays;

import de.uni_freiburg.informatik.ultimate.plugins.generator.buchiautomizer.BuchiCegarLoopBenchmark.LassoAnalysisResults;
import de.uni_freiburg.informatik.ultimate.plugins.generator.buchiautomizer.LassoChecker.ContinueDirective;
import de.uni_freiburg.informatik.ultimate.plugins.generator.buchiautomizer.LassoChecker.SynthesisResult;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.CegarLoopBenchmarkGenerator;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.CoverageAnalysis.BackwardCoveringInformation;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.benchmark.IBenchmarkType;

public class BuchiCegarLoopBenchmarkGenerator extends CegarLoopBenchmarkGenerator {
	
	int[] m_NontrivialModuleStages = new int[5];
	LassoAnalysisResults m_LassoAnalysisResults = new LassoAnalysisResults();
	private BackwardCoveringInformation m_BciFinite = new BackwardCoveringInformation(0, 0);
	private BackwardCoveringInformation m_BciBuchi = new BackwardCoveringInformation(0, 0);
	
	@Override
	public IBenchmarkType getBenchmarkType() {
		return BuchiCegarLoopBenchmark.getInstance();
	}
	
	@Override
	public String[] getStopwatches() {
		ArrayList<String> al = new ArrayList<String>();
		al.addAll(Arrays.asList(super.getStopwatches()));
		al.add(BuchiCegarLoopBenchmark.s_NonLiveStateRemoval);
		al.add(BuchiCegarLoopBenchmark.s_BuchiClosure);
		al.add(BuchiCegarLoopBenchmark.s_NontrivialModuleStages);
		al.add(BuchiCegarLoopBenchmark.s_LassoAnalysisTime);
		return al.toArray(new String[0]);
	}

	public void announceSuccessfullRefinementStage(int stage) {
		m_NontrivialModuleStages[stage]++;
	}
	
	public void addBackwardCoveringInformationFinite(BackwardCoveringInformation bci) {
		m_BciFinite = new BackwardCoveringInformation(m_BciFinite, bci);
	}
	
	public void addBackwardCoveringInformationBuchi(BackwardCoveringInformation bci) {
		m_BciBuchi = new BackwardCoveringInformation(m_BciBuchi, bci);
	}

	@Override
	public Object getValue(String key) {
		switch (key) {
		case BuchiCegarLoopBenchmark.s_NonLiveStateRemoval:
		case BuchiCegarLoopBenchmark.s_BuchiClosure:
		case BuchiCegarLoopBenchmark.s_LassoAnalysisTime:
			try {
				return getElapsedTime(key);
			} catch (StopwatchStillRunningException e) {
				throw new AssertionError("clock still running: " + key);
			}
		case BuchiCegarLoopBenchmark.s_NontrivialModuleStages:
			return m_NontrivialModuleStages;
		case BuchiCegarLoopBenchmark.s_LassoAnalysisResults:
			return m_LassoAnalysisResults;
		case BuchiCegarLoopBenchmark.s_InterpolantCoveringCapabilityFinite:
			return m_BciFinite;
		case BuchiCegarLoopBenchmark.s_InterpolantCoveringCapabilityBuchi:
			return m_BciBuchi;
		default:
			return super.getValue(key);
		}
	}

	public void reportLassoAnalysis(LassoChecker lassoChecker) {
		ContinueDirective cd = lassoChecker.getContinueDirective();
		switch (cd) {

		case REFINE_BOTH:
			if (lassoChecker.isStemInfeasible()) {
				m_LassoAnalysisResults.increment(LassoAnalysisResults.s_StemInfeasibleLoopTerminating);
			} else {
				assert lassoChecker.isConcatInfeasible();
				assert (lassoChecker.getLoopTermination() == SynthesisResult.TERMINATING);
				m_LassoAnalysisResults.increment(LassoAnalysisResults.s_ConcatInfeasibleLoopTerminating);
			}
			break;
		case REFINE_BUCHI:
			assert !lassoChecker.isStemInfeasible();
			if (lassoChecker.getLoopTermination() == SynthesisResult.TERMINATING) {
				m_LassoAnalysisResults.increment(LassoAnalysisResults.s_StemFeasibleLoopTerminating);
			} else {
				assert lassoChecker.getLassoTermination() == SynthesisResult.TERMINATING;
				m_LassoAnalysisResults.increment(LassoAnalysisResults.s_LassoTerminating);
			}
			break;
		case REFINE_FINITE:
			if (lassoChecker.isStemInfeasible()) {
				if (lassoChecker.isLoopInfeasible()) {
					m_LassoAnalysisResults.increment(LassoAnalysisResults.s_StemInfeasibleLoopInfeasible);
				} else {
					m_LassoAnalysisResults.increment(LassoAnalysisResults.s_StemInfeasibleLoopNonterminating);
					//TODO: Loop unknown??
				}
			} else {
				if (lassoChecker.isLoopInfeasible()) {
					m_LassoAnalysisResults.increment(LassoAnalysisResults.s_StemFeasibleLoopInfeasible);
				} else {
					assert lassoChecker.isConcatInfeasible();
					m_LassoAnalysisResults.increment(LassoAnalysisResults.s_ConcatenationInfeasible);
				}
			}
			break;
		case REPORT_NONTERMINATION:
			assert !lassoChecker.isStemInfeasible();
			assert !lassoChecker.isLoopInfeasible();
			assert !lassoChecker.isConcatInfeasible();
			assert lassoChecker.getNonTerminationArgument() != null;
			assert !lassoChecker.getBinaryStatePredicateManager().providesPredicates();
			m_LassoAnalysisResults.increment(LassoAnalysisResults.s_LassoNonterminating);
			break;
		case REPORT_UNKNOWN:
			assert !lassoChecker.isStemInfeasible();
			assert !lassoChecker.isLoopInfeasible();
			assert !lassoChecker.isConcatInfeasible();
			assert lassoChecker.getNonTerminationArgument() == null;
			assert !lassoChecker.getBinaryStatePredicateManager().providesPredicates();
			m_LassoAnalysisResults.increment(LassoAnalysisResults.s_TerminationUnknown);
			break;
		default:
			throw new AssertionError("unknown case");
		}
	}
	
}
