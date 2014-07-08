package de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstractionwithafas;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

import de.uni_freiburg.informatik.ultimate.automata.OperationCanceledException;
import de.uni_freiburg.informatik.ultimate.automata.Word;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.CodeBlock;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.ProgramPoint;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.RootNode;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.TraceAbstractionBenchmarks;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.SmtManager;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TAPreferences;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer.INTERPOLATION;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstractionconcurrent.CegarLoopConcurrentAutomata;
import de.uni_freiburg.informatik.ultimate.reachingdefinitions.plugin.ReachingDefinitions;

/*
 * plan:
 * - von CegarLoopConcurrent erben
 *  --> Produktautomat aus einem parallelen Programm wird automatisch gebaut
 * - computeInterpolantAutomaton �berschreiben
 * - "Powerset" einstellung f�r refineAbstraction w�hlen
 */
//


public class TAwAFAsCegarLoop extends CegarLoopConcurrentAutomata {

	public TAwAFAsCegarLoop(String name, RootNode rootNode,
			SmtManager smtManager,
			TraceAbstractionBenchmarks traceAbstractionBenchmarks,
			TAPreferences taPrefs, Collection<ProgramPoint> errorLocs,
			INTERPOLATION interpolation, boolean computeHoareAnnotation) {
		super(name, rootNode, smtManager, traceAbstractionBenchmarks, taPrefs,
				errorLocs);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected void constructInterpolantAutomaton()
			throws OperationCanceledException {

		
		//Daniel:
		Word<CodeBlock> trace = m_TraceChecker.getTrace(); //-> der TraceChecker hat aber auch noch ein paar andere Sachen drin..
		CodeBlock[] traceAsArray = new CodeBlock[trace.length()];
		for (int i = 0; i < trace.length(); i++) {
			traceAsArray[i] = trace.getSymbol(i);
		}
		try {
			CodeBlock[] rdAnnotatedTraceArray = ReachingDefinitions.computeRDForTrace(traceAsArray);
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		for (int i = 0; i < trace.length(); i++) {
			CodeBlock cb = trace.getSymbol(i);
			RDCodeBlockWrapper rdcbw = new RDCodeBlockWrapper(cb);
			int j = 0;
			j++;
		}
		
//		super.constructInterpolantAutomaton();
		int i = 0;
	}

	
	
}
