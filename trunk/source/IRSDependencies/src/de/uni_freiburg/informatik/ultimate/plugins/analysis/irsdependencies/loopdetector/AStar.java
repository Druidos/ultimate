package de.uni_freiburg.informatik.ultimate.plugins.analysis.irsdependencies.loopdetector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.apache.log4j.Logger;

import de.uni_freiburg.informatik.ultimate.util.Utils;

/**
 * Executes a search on an arbitrary graph using an implementation of A*. Finds
 * a path according to a given heuristic from start to target if one exists.
 * 
 * You can specify edges that should be ignored during the search.
 * 
 * @author dietsch@informatik.uni-freiburg.de
 * 
 */
public class AStar<V, E> {

	private static final String INDENT = "   ";

	private final Logger mLogger;
	private final IHeuristic<V, E> mHeuristic;
	private final V mStart;
	private final V mTarget;
	private final IEdgeDenier<E> mEdgeDenier;
	private final IGraph<V, E> mGraph;

	public AStar(Logger logger, V start, V target, IHeuristic<V, E> heuristic, IGraph<V, E> graph) {
		this(logger, start, target, heuristic, graph, new NoEdgeDenier<E>());
	}

	public AStar(Logger logger, V start, V target, IHeuristic<V, E> heuristic, IGraph<V, E> graph,
			Collection<E> forbiddenEdges) {
		this(logger, start, target, heuristic, graph, new CollectionEdgeDenier<>(forbiddenEdges));
	}

	public AStar(Logger logger, V start, V target, IHeuristic<V, E> heuristic, IGraph<V, E> graph,
			IEdgeDenier<E> edgeDenier) {
		mLogger = logger;
		mHeuristic = heuristic;
		mStart = start;
		mTarget = target;
		mEdgeDenier = edgeDenier;
		mGraph = graph;
	}

	public List<E> findPath() {
		// create initial item
		final OpenItem<V, E> initialOpenItem = createInitialSuccessorItem(mStart);

		// check for trivial paths
		for (E edge : mGraph.getOutgoingEdges(mStart)) {
			if (mEdgeDenier.isForbidden(edge, new BackpointerIterator(initialOpenItem.getAnnotation()))) {
				if (mLogger.isDebugEnabled()) {
					mLogger.debug("Forbidden [" + edge.hashCode() + "] " + edge);
				}
				continue;
			}

			if (mGraph.getTarget(edge).equals(mTarget)) {
				if (mLogger.isDebugEnabled()) {
					mLogger.debug("Found trivial path from source " + mStart + " to target " + mTarget + ": " + edge);
				}
				return Collections.singletonList(edge);
			}
		}

		return astar(initialOpenItem);
	}

	private List<E> astar(OpenItem<V, E> initialOpenItem) {
		final FasterPriorityQueue<OpenItem<V, E>> open = new FasterPriorityQueue<OpenItem<V, E>>(
				new Comparator<OpenItem<V, E>>() {
					@Override
					public int compare(OpenItem<V, E> o1, OpenItem<V, E> o2) {
						return Integer.compare(o1.getAnnotation().getExpectedCostToTarget(), o2.getAnnotation()
								.getExpectedCostToTarget());
					}
				});

		// we want to allow that we find paths from start to target when start
		// == target
		// for this, we run the algorithm one time without the check if we
		// reached the target
		open.add(initialOpenItem);
		expandNode(open.poll(), open);

		List<E> path = null;
		while (!open.isEmpty()) {
			final OpenItem<V, E> currentItem = open.poll();

			if (currentItem.getNode().equals(mTarget)) {
				if (mLogger.isDebugEnabled()) {
					mLogger.debug("Found target");
				}
				// path found
				path = createPath(currentItem);
				if (mLogger.isDebugEnabled()) {
					mLogger.debug(String.format("Found path of length %s from source %s to target %s: %s", path.size(),
							mStart, mTarget, Utils.join(path, ", ")));
				}
				break;
			}
			expandNode(currentItem, open);
		}
		if (path == null) {
			mLogger.warn(String.format("Did not find a path from source %s to target %s!", mStart, mTarget));
		}
		return path;
	}

	private void expandNode(final OpenItem<V, E> currentItem, final FasterPriorityQueue<OpenItem<V, E>> open) {
		final V currentNode = currentItem.getNode();
		if (mLogger.isDebugEnabled()) {
			mLogger.debug("Expanding " + currentNode);
		}
		final Collection<E> outgoingEdges = mGraph.getOutgoingEdges(currentNode);
		final AstarAnnotation<E> currentAnnotation = currentItem.getAnnotation();

		for (final E nextEdge : outgoingEdges) {
			if (mEdgeDenier.isForbidden(nextEdge, new BackpointerIterator(currentAnnotation))) {
				if (mLogger.isDebugEnabled()) {
					mLogger.debug(INDENT + "Forbidden [" + nextEdge.hashCode() + "] " + nextEdge);
				}
				continue;
			}

			final V successor = mGraph.getTarget(nextEdge);
			final OpenItem<V, E> successorItem = createSuccessorItem(currentItem, nextEdge);
			final AstarAnnotation<E> successorAnnotation = successorItem.getAnnotation();

			final int costSoFar = currentAnnotation.getCostSoFar() + mHeuristic.getConcreteCost(nextEdge);
			if (open.contains(successorItem) && costSoFar >= successorAnnotation.getCostSoFar()) {
				// we already know the successor and our current way is not
				// better than the new one
				if (mLogger.isDebugEnabled()) {
					mLogger.debug(INDENT + "Not worthy [" + nextEdge.hashCode() + "][" + successorAnnotation.hashCode()
							+ "] " + nextEdge);
				}
				continue;
			}

			final int expectedCost = costSoFar + mHeuristic.getHeuristicValue(successor, nextEdge, mTarget);
			if (mLogger.isDebugEnabled()) {
				mLogger.debug(INDENT + "CostSoFar=" + costSoFar + " ExpectedCost " + expectedCost);
			}
			open.remove(successorItem);
			successorAnnotation.setExpectedCostToTarget(expectedCost);
			if (successorAnnotation.isLowest()) {
				successorAnnotation.setBackPointers(nextEdge, currentAnnotation);
				successorAnnotation.setCostSoFar(costSoFar);
				open.add(successorItem);
				if (mLogger.isDebugEnabled()) {
					mLogger.debug(INDENT + "Considering [" + nextEdge.hashCode() + "]["
							+ successorAnnotation.hashCode() + "] " + nextEdge + " --> " + successor);
				}
				continue;
			} else {
				if (mLogger.isDebugEnabled()) {
					mLogger.debug(INDENT + "Already closed  [" + nextEdge.hashCode() + "]["
							+ successorAnnotation.hashCode() + "] " + nextEdge + " --> " + successor);
				}
			}
		}
	}

	private List<E> createPath(OpenItem<V, E> currentItem) {
		assert currentItem.getNode() == mTarget;
		final AStar<V, E>.BackpointerIterator iter = new BackpointerIterator(currentItem.getAnnotation());
		final List<E> rtr = new ArrayList<E>();
		while (iter.hasNext()) {
			rtr.add(iter.next());
		}
		Collections.reverse(rtr);
		return rtr;
	}

	private OpenItem<V, E> createSuccessorItem(final OpenItem<V, E> current, final E successor) {
		final V target = mGraph.getTarget(successor);
		if (current == null) {
			final Map<V, AstarAnnotation<E>> map = new HashMap<>();
			final V source = mGraph.getSource(successor);
			map.put(source, new AstarAnnotation<E>());
			map.put(target, new AstarAnnotation<E>());
			return new OpenItem<V, E>(target, map);
		}

		final OpenItem<V, E> rtr = new OpenItem<>(target, current);
		if (mGraph.beginScope(successor)) {
			rtr.getAnnotations().beginScope();
		} else if (mGraph.endScope(successor)) {
			assert rtr.getAnnotations().getScopesCount() > 0 : "If this happens, your edge denier does not handle call/return correctly";
			rtr.getAnnotations().endScope();
		}
		AstarAnnotation<E> annot = rtr.getAnnotations().get(target);
		if (annot == null) {
			annot = new AstarAnnotation<E>();
			rtr.getAnnotations().put(target, annot);
		}
		return rtr;
	}

	private OpenItem<V, E> createInitialSuccessorItem(V initialNode) {
		final Map<V, AstarAnnotation<E>> map = new HashMap<>();
		map.put(initialNode, new AstarAnnotation<E>());
		return new OpenItem<V, E>(initialNode, map);
	}

	private static final class NoEdgeDenier<E> implements IEdgeDenier<E> {
		@Override
		public boolean isForbidden(E edge, Iterator<E> currentTrace) {
			return false;
		}
	}

	private final class BackpointerIterator implements Iterator<E> {

		private AstarAnnotation<E> mAnnotation;
		private Set<AstarAnnotation<E>> mClosed;

		private BackpointerIterator(AstarAnnotation<E> currentAnnotation) {
			mAnnotation = currentAnnotation;
			mClosed = new HashSet<AstarAnnotation<E>>();
		}

		@Override
		public boolean hasNext() {
			return mAnnotation != null && mAnnotation.getEdge() != null && !mClosed.contains(mAnnotation);
		}

		@Override
		public E next() {
			final E current = mAnnotation.getEdge();
			if (current == null) {
				throw new NoSuchElementException();
			}
			if (!mClosed.add(mAnnotation)) {
				throw new NoSuchElementException();
			}
			mAnnotation = mAnnotation.getBackpointer();
			return current;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException("remove");
		}
	}
}
