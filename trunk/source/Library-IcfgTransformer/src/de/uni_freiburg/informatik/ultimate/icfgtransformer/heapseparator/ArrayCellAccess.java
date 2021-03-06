package de.uni_freiburg.informatik.ultimate.icfgtransformer.heapseparator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.arrays.ArrayIndex;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.arrays.MultiDimensionalSelect;

/**
 * Union type of ArraySelect and ArraySelectOverStore.
 * (EDIT: .. no more.. now it's a very simple wrapper for MultiDimensionalSelect..)
 *
 * @author Alexander Nutz (nutz@informatik.uni-freiburg.de)
 *
 */
public class ArrayCellAccess {

//	private final ArraySelect mArraySelect;
//	private final ArraySelectOverStore mArraySelectOverStore;

	private final MultiDimensionalSelect mMdSelect;

	/**
	 * The "base" array, i.e. if we have a store as array term of mMdSelect, then this is the array that is stored to.
	 *  (otherwise it's just that array term of mMdSelect)
	 */
	private final Term mSimpleArrayTerm;

//	public ArrayCellAccess(final ArraySelect arraySelect) {
//		mArraySelect = arraySelect;
//		mArraySelectOverStore = null;
//	}
//
//	public ArrayCellAccess(final ArraySelectOverStore arraySelectOverStore) {
//		mArraySelect = null;
//		mArraySelectOverStore = arraySelectOverStore;
//	}

	public ArrayCellAccess(final MultiDimensionalSelect mdSelect) {
		mMdSelect = mdSelect;

		Term innerArrayTerm = mdSelect.getArray();
		while (SmtUtils.isFunctionApplication(innerArrayTerm, "store")) {
			innerArrayTerm = ((ApplicationTerm) innerArrayTerm).getParameters()[0];
		}
		mSimpleArrayTerm = innerArrayTerm;

	}

	public static List<ArrayCellAccess> extractArrayCellAccesses(final Term formula) {
		final List<ArrayCellAccess> result = new ArrayList<>();

		final List<MultiDimensionalSelect> mdSelects = MultiDimensionalSelect.extractSelectShallow(formula, true);

		mdSelects.forEach(mds -> result.add(new ArrayCellAccess(mds)));

//		final Set<String> functionSymbolNames = Collections.singleton("select");
//
//		final ApplicationTermFinder atf = new ApplicationTermFinder(functionSymbolNames, false);
//		for (final ApplicationTerm subterm : atf.findMatchingSubterms(formula)) {
//			final Term firstParam = subterm.getParameters()[0];
//			if (SmtUtils.isFunctionApplication(firstParam, "store")) {
//				result.add(new ArrayCellAccess(ArraySelectOverStore.convert(subterm)));
//			} else {
//				result.add(new ArrayCellAccess(ArraySelect.convert(subterm)));
//			}
//		}

		return result;
	}

	/**
	 * get the array of the underlying mdSelect (can be a store term)
	 * @return
	 */
	public Term getArray() {
		return mMdSelect.getArray();
	}

	/**
	 * get the array variable/constant (look inside store terms)
	 * @return
	 */
	public Term getSimpleArray() {
		return mSimpleArrayTerm;
//		if (mArraySelect != null) {
//			return mArraySelect.getArray();
//		}
//		if (mArraySelectOverStore != null) {
//			return mArraySelectOverStore.getArrayStore().getArray();
//		}
//		throw new AssertionError();
	}

	public ArrayIndex getIndex() {
		return mMdSelect.getIndex();
//		if (mArraySelect != null) {
//			return mArraySelect.getIndex();
//		}
//		if (mArraySelectOverStore != null) {
//			return mArraySelectOverStore.getIndex();
//		}
//		throw new AssertionError();
	}

	public Term getTerm() {
//	public Term getTerm(final Script script) {
		return mMdSelect.getSelectTerm();
//		if (mArraySelect != null) {
//			return mArraySelect.toTerm(script);
//		}
//		if (mArraySelectOverStore != null) {
//			return mArraySelectOverStore.toTerm(script);
//		}
//		throw new AssertionError();
	}

	@Override
	public String toString() {
		return mMdSelect.toString();

//		if (mArraySelect != null) {
//			return mArraySelect.toTerm(script);
//		}
//		if (mArraySelectOverStore != null) {
//			return mArraySelectOverStore.toTerm(script);
//		}
//		throw new AssertionError();
//
//
//		return "(array " + getArray() + " at " + getIndex() + ")";
	}

	public Set<Integer> getDimensionsOfIndexTerm(final Term indexSubterm) {
		final Set<Integer> result = new HashSet<>();
		for (int dim = 0; dim < mMdSelect.getIndex().size(); dim++) {
			if (indexSubterm.equals(mMdSelect.getIndex().get(dim))) {
				result.add(dim);
			}
		}
		return result;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((mMdSelect == null) ? 0 : mMdSelect.hashCode());
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final ArrayCellAccess other = (ArrayCellAccess) obj;
		if (mMdSelect == null) {
			if (other.mMdSelect != null) {
				return false;
			}
		} else if (!mMdSelect.equals(other.mMdSelect)) {
			return false;
		}
		return true;
	}



}
