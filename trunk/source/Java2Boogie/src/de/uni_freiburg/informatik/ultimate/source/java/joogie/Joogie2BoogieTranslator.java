package de.uni_freiburg.informatik.ultimate.source.java.joogie;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.joogie.boogie.BasicBlock;
import org.joogie.boogie.BoogieAxiom;
import org.joogie.boogie.BoogieProcedure;
import org.joogie.boogie.BoogieProgram;
import org.joogie.boogie.LocationTag;
import org.joogie.boogie.expressions.Variable;
import org.joogie.boogie.statements.ExpressionStatement;
import org.joogie.boogie.types.BoogieBaseTypes;
import org.joogie.boogie.types.BoogieType;

import de.uni_freiburg.informatik.ultimate.core.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.ASTType;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.Attribute;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.Axiom;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.Body;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.ConstDeclaration;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.Declaration;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.EnsuresSpecification;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.Expression;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.FunctionDeclaration;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.GotoStatement;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.Label;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.ModifiesSpecification;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.Procedure;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.RequiresSpecification;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.ReturnStatement;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.Specification;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.Statement;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.TypeDeclaration;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.Unit;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.VarList;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.VariableDeclaration;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.VariableLHS;
import de.uni_freiburg.informatik.ultimate.model.location.BoogieLocation;
import de.uni_freiburg.informatik.ultimate.model.location.ILocation;
import de.uni_freiburg.informatik.ultimate.source.java.Activator;

//TODO: Pull line numbers from soot statements to the top; extend location tag for this 

/**
 * 
 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 *
 */
public class Joogie2BoogieTranslator {

	private final IUltimateServiceProvider mServices;
	private final Logger mLogger;
	private final Unit mOutput;
	private final BoogieLocation mLoc;
	private final BoogieLocation mLoopLoc;
	private final String mFilename;

	public Joogie2BoogieTranslator(final BoogieProgram program, final IUltimateServiceProvider services,
			final String filename) {
		if (program == null) {
			throw new IllegalArgumentException("program");
		}

		mServices = services;
		mLogger = services.getLoggingService().getLogger(Activator.PLUGIN_ID);
		mFilename = filename;
		mLoc = new BoogieLocation(filename, -1, -1, -1, -1, false);
		mLoopLoc = new BoogieLocation(filename, -1, -1, -1, -1, true);

		mOutput = translate(program);
	}

	public Unit getUnit() {
		return mOutput;
	}

	private Unit translate(final BoogieProgram program) {
		mLogger.info("Translating " + mFilename + " to Ultimate's data structures");
		final List<Declaration> decls = new ArrayList<>();

		decls.addAll(declarePrelude(program));
		decls.addAll(declareConstants(program));
		decls.addAll(declareVariables(program));
		decls.addAll(declareProcedures(program));

		return new Unit(getLocation(), decls.toArray(new Declaration[decls.size()]));
	}

	/**
	 * Note that we generate the Joogie legacy prelude here and that we only
	 * support the Default heap mode
	 */
	private Collection<? extends Declaration> declarePrelude(final BoogieProgram program) {
		final List<Declaration> decls = new ArrayList<>();

		decls.add(new TypeDeclaration(getLocation(), new Attribute[0], false, BoogieBaseTypes.getRefType().getName(),
				new String[0]));
		decls.add(new TypeDeclaration(getLocation(), new Attribute[0], false, BoogieBaseTypes.getRealType().getName(),
				new String[0]));
		decls.add(new TypeDeclaration(getLocation(), new Attribute[0], false,
				BoogieBaseTypes.getClassConstType().getName(), new String[0]));
		decls.add(new TypeDeclaration(getLocation(), new Attribute[0], false, "Field", new String[] { "x" }));
		decls.add(declareVariable(program.getHeapVariable()));

		decls.add(declareConstant(program.getNullReference()));

		decls.add(declareConstant(program.getNullIntArray()));
		decls.add(declareConstant(program.getNullRealArray()));
		decls.add(declareConstant(program.getNullRefArray()));

		decls.add(declareConstant(program.getSizeIndexArray()));

		decls.add(declareVariable(program.getSizeArrayInt()));
		decls.add(declareVariable(program.getSizeArrayReal()));
		decls.add(declareVariable(program.getSizeArrayRef()));

		decls.add(declareVariable(program.getSizeString()));

		for (final BoogieAxiom axiom : program.getAxioms()) {
			decls.add(declareAxiom(axiom));
		}

		return decls;
	}

	private Collection<? extends Declaration> declareProcedures(final BoogieProgram program) {
		final List<Declaration> decls = new ArrayList<>();
		program.getProcedures().stream().filter(p -> !isFunction(p)).forEach(p -> decls.add(declareProcedure(p)));
		program.getProcedures().stream().filter(p -> isFunction(p)).forEach(p -> decls.add(declareFunction(p)));
		return decls;
	}

	private Collection<? extends Declaration> declareVariables(final BoogieProgram program) {
		final List<Declaration> decls = new ArrayList<>();
		program.getGlobalVariables().stream().filter(g -> !g.isConstUnique())
				.forEach(g -> decls.add(declareVariable(g)));
		return decls;
	}

	private Collection<? extends Declaration> declareConstants(final BoogieProgram program) {
		final List<Declaration> decls = new ArrayList<>();

		for (final Variable typeVar : program.getTypeVariables()) {
			decls.add(declareConstant(typeVar));
		}

		program.getGlobalVariables().stream().filter(g -> g.isConstUnique())
				.forEach(g -> decls.add(declareConstant(g)));

		return decls;
	}

	private ILocation getLoopLocation() {
		return mLoopLoc;
	}

	private ILocation getLocation() {
		return mLoc;
	}

	private ILocation getLocation(LocationTag locationTag, boolean isLoopEntry) {
		return new BoogieLocation(mLoc.getFileName(), locationTag.getLineNumber(), -1, -1, -1, isLoopEntry);
	}

	private Declaration declareProcedure(final BoogieProcedure proc) {
		// Note that in Joogie, procedures can be functions as well as
		// procedures
		assert!isFunction(proc);

		final Collection<VarList> inParams = createProcedureInParams(proc);
		final Collection<VarList> outParams = createpProcedureOutParams(proc);
		final Collection<Specification> spec = createProcedureSpecification(proc);
		final Body body = createProcedureBody(proc);

		return new Procedure(getLocation(proc.getLocationTag(), false), new Attribute[0], proc.getName(), new String[0],
				inParams.toArray(new VarList[inParams.size()]), outParams.toArray(new VarList[outParams.size()]),
				spec.toArray(new Specification[spec.size()]), body);
	}

	private Collection<VarList> createpProcedureOutParams(final BoogieProcedure proc) {
		final Collection<VarList> outParams = new ArrayList<>();

		if (proc.getReturnVariable() != null) {
			outParams.add(makeVarList(proc.getReturnVariable()));
		}

		for (final Entry<BoogieType, org.joogie.boogie.expressions.Expression> entry : proc
				.getExceptionalReturnVariables().entrySet()) {
			final Variable exceptionalReturnVariable = (Variable) entry.getValue();
			assert exceptionalReturnVariable.getType().toBoogie().equals(entry.getKey().toBoogie());
			outParams.add(makeVarList(exceptionalReturnVariable));
		}

		return outParams;
	}

	private Body createProcedureBody(final BoogieProcedure proc) {
		final Collection<VariableDeclaration> localVars = createProcedureLocalVars(proc);
		final Collection<Statement> statements = createProcedureStatements(proc);
		return new Body(getLocation(), localVars.toArray(new VariableDeclaration[localVars.size()]),
				statements.toArray(new Statement[statements.size()]));
	}

	private List<Statement> createProcedureStatements(final BoogieProcedure proc) {
		final List<Statement> rtr = new ArrayList<Statement>();
		if (proc == null || proc.getRootBlock() == null) {
			return rtr;
		}
		final ArrayDeque<BasicBlock> worklist = new ArrayDeque<>();
		final Set<BasicBlock> closed = new HashSet<BasicBlock>();

		worklist.add(proc.getRootBlock());

		while (!worklist.isEmpty()) {
			final BasicBlock current = worklist.removeFirst();
			closed.add(current);
			final ILocation loc = current.isLoopHead() ? getLoopLocation() : getLocation();
			rtr.add(new Label(loc, current.getName()));
			current.getStatements().forEach(stmt -> rtr.add(StatementTranslator.translate(mLogger, loc, stmt)));
			final Collection<BasicBlock> succs = current.getSuccessors();
			if (succs.isEmpty()) {
				rtr.add(new ReturnStatement(loc));
				continue;
			}
			final String[] successorLabels = succs.stream().map(succ -> succ.getName()).collect(Collectors.toList())
					.toArray(new String[0]);
			rtr.add(new GotoStatement(loc, successorLabels));
			succs.stream().filter(succ -> !closed.contains(succ)).forEach(succ -> worklist.addFirst(succ));
		}

		return rtr;
	}

	private Collection<VariableDeclaration> createProcedureLocalVars(final BoogieProcedure proc) {
		final Collection<VariableDeclaration> localVars = new ArrayList<VariableDeclaration>();
		getProcLocals(proc).forEach(a -> localVars.add(declareVariable(a)));
		return localVars;
	}

	private Collection<Variable> getProcLocals(BoogieProcedure proc) {
		// this is so weird; but we have to declare all the local variables and

		final Set<Variable> procLocals = new HashSet<Variable>();
		procLocals.addAll(proc.getLocalVars());
		final Set<org.joogie.boogie.expressions.Expression> candidates = proc.getExceptionalReturnVariables().entrySet()
				.stream().map(a -> a.getValue()).filter(a -> a instanceof Variable).collect(Collectors.toSet());

		for (final org.joogie.boogie.expressions.Expression candidate : candidates) {
			final String boogieRep = candidate.toBoogie();
			if (procLocals.stream().anyMatch(a -> a.toBoogie().equals(boogieRep))) {
				continue;
			}
			procLocals.add((Variable) candidate);
		}
		return procLocals;
	}

	private Collection<Specification> createProcedureSpecification(final BoogieProcedure proc) {
		final Collection<Specification> specs = new ArrayList<>();

		if (proc.getEnsures() != null) {
			for (final org.joogie.boogie.expressions.Expression ensure : proc.getEnsures()) {
				specs.add(new EnsuresSpecification(getLocation(), false,
						ExpressionTranslator.translate(mLogger, getLocation(), ensure)));
			}
		}
		if (proc.getRequires() != null) {
			for (final org.joogie.boogie.expressions.Expression requires : proc.getRequires()) {
				specs.add(new RequiresSpecification(getLocation(), false,
						ExpressionTranslator.translate(mLogger, getLocation(), requires)));
			}
		}
		if (proc.getModifiesGlobals() != null) {
			final Collection<VariableLHS> modifiedVars = new ArrayList<>();
			for (final Variable modified : proc.getModifiesGlobals()) {
				modifiedVars.add(new VariableLHS(getLocation(), modified.getName()));
			}
			if (!modifiedVars.isEmpty()) {
				specs.add(new ModifiesSpecification(getLocation(), false,
						modifiedVars.toArray(new VariableLHS[modifiedVars.size()])));
			}
		}

		return specs;
	}

	private Declaration declareFunction(final BoogieProcedure proc) {
		assert isFunction(proc);
		assert proc.getEnsures().isEmpty();
		assert proc.getRequires().isEmpty();
		assert proc.getModifiesGlobals().isEmpty();

		final Collection<VarList> inParams = createProcedureInParams(proc);

		final VarList outParam;
		if (proc.getReturnVariable() == null && proc.getExceptionalReturnVariables().size() == 0) {
			outParam = null;
		} else {
			final Collection<String> identifiers = new ArrayList<String>();
			ASTType type = null;
			if (proc.getReturnVariable() != null) {
				identifiers.add(proc.getReturnVariable().getName());
				type = TypeTranslator.translate(proc.getReturnVariable(), getLocation());
			}

			for (final Entry<BoogieType, org.joogie.boogie.expressions.Expression> entry : proc
					.getExceptionalReturnVariables().entrySet()) {
				identifiers.add(((Variable) entry.getValue()).getName());
				type = TypeTranslator.translate(entry.getKey(), getLocation());
			}
			assert type != null;
			outParam = new VarList(getLocation(), identifiers.toArray(new String[identifiers.size()]), type);
		}
		final Expression body = getFunctionBody(proc);
		return new FunctionDeclaration(getLocation(), new Attribute[0], proc.getName(), new String[0],
				inParams.toArray(new VarList[inParams.size()]), outParam, body);
	}

	private Expression getFunctionBody(BoogieProcedure proc) {
		if (proc.getRootBlock() == null) {
			return null;
		}
		final List<org.joogie.boogie.statements.Statement> statements = proc.getRootBlock().getStatements();
		if (statements == null) {
			return null;
		}
		if (statements.isEmpty()) {
			return null;
		}
		assert statements.size() == 1 : "Functions should have only one ExpressionStatement as body";
		org.joogie.boogie.statements.Statement body = statements.get(0);
		assert body instanceof ExpressionStatement;
		return ExpressionTranslator.translate(mLogger, getLocation(), ((ExpressionStatement) body).getExpression());
	}

	private Collection<VarList> createProcedureInParams(final BoogieProcedure proc) {
		final Collection<VarList> inParams = new ArrayList<>();
		if (!proc.isStatic()) {
			inParams.add(makeVarList(proc.getThisVariable()));
		}
		proc.getParameterList().stream().map(p -> makeVarList(p)).forEach(inParams::add);
		return inParams;
	}

	private boolean isFunction(final BoogieProcedure proc) {
		return proc.isPure() && returnsOnlyOneType(proc);
	}

	private boolean returnsOnlyOneType(final BoogieProcedure proc) {
		final Set<BoogieType> set = new HashSet<BoogieType>();
		if (proc.getReturnVariable() != null) {
			set.add(proc.getReturnVariable().getType());
		}
		if (proc.getExceptionalReturnVariables() != null) {
			proc.getExceptionalReturnVariables().entrySet().stream().forEach(a -> set.add(a.getKey()));
		}
		return set.size() < 2;
	}

	private Declaration declareAxiom(final BoogieAxiom axiom) {
		return new Axiom(getLocation(), new Attribute[0], makeExpression(axiom.getExpression()));
	}

	private Expression makeExpression(final org.joogie.boogie.expressions.Expression expression) {
		return ExpressionTranslator.translate(mLogger, getLocation(), expression);
	}

	private VariableDeclaration declareVariable(final Variable var) {
		return new VariableDeclaration(getLocation(), new Attribute[0], new VarList[] { makeVarList(var) });
	}

	private ConstDeclaration declareConstant(final Variable var) {
		return new ConstDeclaration(getLocation(), new Attribute[0], var.isConstUnique(), makeVarList(var), null, true);
	}

	private VarList makeVarList(final Variable var) {
		return new VarList(getLocation(), new String[] { var.getName() }, TypeTranslator.translate(var, getLocation()));
	}

}
