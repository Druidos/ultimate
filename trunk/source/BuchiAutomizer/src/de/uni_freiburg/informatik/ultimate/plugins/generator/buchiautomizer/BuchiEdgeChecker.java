package de.uni_freiburg.informatik.ultimate.plugins.generator.buchiautomizer;

import de.uni_freiburg.informatik.ultimate.logic.Script.LBool;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.CodeBlock;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.Return;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.EdgeChecker;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.SmtManager;

/**
 * EdgeChecker that is aware of the special Honda and RankDecrease predicates
 * used in Buchi termination analysis.
 * @author heizmann@informatik.uni-freiburg.de
 *
 */
public class BuchiEdgeChecker extends EdgeChecker {

	private final IPredicate m_HondaPredicate;
	private final IPredicate m_RankDecrease;
	public BuchiEdgeChecker(SmtManager smtManager, IPredicate hondaPredicate,
			IPredicate rankDecrease) {
		super(smtManager);
		m_HondaPredicate = hondaPredicate;
		m_RankDecrease = rankDecrease;
	}
	@Override
	public LBool postInternalImplies(IPredicate p) {
		if (p == m_HondaPredicate) {
			p = m_RankDecrease;
		}
		return super.postInternalImplies(p);
	}
	@Override
	public LBool postCallImplies(IPredicate p) {
		if (p == m_HondaPredicate) {
			p = m_RankDecrease;
		}
		return super.postCallImplies(p);
	}
	@Override
	public LBool postReturnImplies(IPredicate p) {
		if (p == m_HondaPredicate) {
			p = m_RankDecrease;
		}
		return super.postReturnImplies(p);
	}
	@Override
	public LBool sdecInternalSelfloop(IPredicate p, CodeBlock cb) {
		return null; 
	}
	@Override
	public LBool sdecCallSelfloop(IPredicate p, CodeBlock cb) {
		return null;	}
	@Override
	public LBool sdecReturnSelfloopPre(IPredicate p, Return ret) {
		return null;	}
	@Override
	public LBool sdecReturnSelfloopHier(IPredicate p, Return ret) {
		return null;
	}
	

}
