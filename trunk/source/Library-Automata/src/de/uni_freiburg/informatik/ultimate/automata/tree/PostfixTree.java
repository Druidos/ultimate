package de.uni_freiburg.informatik.ultimate.automata.tree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class PostfixTree<LETTER, STATE> {
	
	private final ArrayList<STATE> postFix;
	private final ArrayList<Integer> depths;
	
	private final ArrayList<Integer> startIdx;
	
	
	public ArrayList<STATE> getPostFix() {
		return postFix;
	}

	public ArrayList<Integer> getStartIdx() {
		return startIdx;
	}

	public PostfixTree(final TreeRun<LETTER, STATE> run) {
		postFix = new ArrayList<>();
		startIdx = new ArrayList<>();
		depths = new ArrayList<>();
		
		constructTree(run, 0);
	}
	
	private void addSymbol(final STATE st, final int depth) {
		if (depths.size() > 0 && depths.get(depths.size() - 1) < depth) {
			startIdx.add(depths.size());
		}
		postFix.add(st);
		depths.add(depth);
	}
	
	private void constructTree(final TreeRun<LETTER, STATE> run, final int depth) {
		
		constructSubtrees(run.getChildren().iterator(), depth);
		addSymbol(run.getRoot(), depth);
	}
	
	private void constructSubtrees(final Iterator<TreeRun<LETTER, STATE>> it, final int depth) {
		if (!it.hasNext()) {
			return ;
		}
		constructTree(it.next(), depth);
		constructSubtrees(it, depth + 1);
	}
	
	
	public static void main(String[] args) {
		TreeRun<Character, Integer> r3 = new TreeRun<Character, Integer>(3);
		TreeRun<Character, Integer> r5 = new TreeRun<Character, Integer>(5);
		TreeRun<Character, Integer> r7 = new TreeRun<Character, Integer>(7);
		TreeRun<Character, Integer> r8 = new TreeRun<Character, Integer>(8);
		
		ArrayList<TreeRun<Character, Integer>> rr6 = new ArrayList<>();
		rr6.add(r7); rr6.add(r8);
		TreeRun<Character, Integer> r6 = new TreeRun<Character, Integer>(6, 'a', rr6);

		ArrayList<TreeRun<Character, Integer>> rr4 = new ArrayList<>();
		rr4.add(r5);
		TreeRun<Character, Integer> r4 = new TreeRun<Character, Integer>(4, 'b', rr4);
		

		ArrayList<TreeRun<Character, Integer>> rr2 = new ArrayList<>();
		rr2.add(r3);
		TreeRun<Character, Integer> r2 = new TreeRun<Character, Integer>(2, 'c', rr2);
		
		

		ArrayList<TreeRun<Character, Integer>> rr1 = new ArrayList<>();
		rr1.add(r2); rr1.add(r4); rr1.add(r6);
		TreeRun<Character, Integer> r1 = new TreeRun<Character, Integer>(1, 'x', rr1);
		
		PostfixTree<Character, Integer> tt = new PostfixTree<>(r1);
		System.out.println(r1.toString());
		System.out.println(tt.postFix);
		System.out.println(tt.depths);
		System.out.println(tt.startIdx);
		
		HashMap<Integer, Integer> map = new HashMap<>();
		for (int i = 1; i <= 8; ++i)
			map.put(i, i * i + 10);
		

		PostfixTree<Character, Integer> tt2 = new PostfixTree<>(r1.reconstruct(map));

		System.out.println(r1.reconstruct(map).toString());
		System.out.println(tt2.postFix);
		System.out.println(tt2.depths);
		System.out.println(tt2.startIdx);
			
	}
}
