package lir.alloc;

import lir.LIRInstruction;
import lir.LIRList;
import lir.LIROp1;
import lir.LIROpcode;
import lir.ci.LIRValue;
import utils.IntList;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Xlous.zeng
 */
public final class LIRInsertionBuffer
{
	/**
	 * the lir list where ops of this buffer should be inserted later
	 * (null when uninitialized)
	 */
	private LIRList lir;

	/** list of insertion points. index and count are stored alternately:
	 * indexAndCount[i * 2]: the index into lir list where "count" ops should be inserted
	 * indexAndCount[i * 2 + 1]: the number of ops to be inserted at index
	 */
	private final IntList indexAndCount;

	// the LIROps to be inserted
	private final List<LIRInstruction> ops;

	private void appendNew(int index, int count)
	{
		indexAndCount.add(index);
		indexAndCount.add(count);
	}

	private void setCountAt(int i, int value)
	{
		indexAndCount.set((i << 1) + 1, value);
	}

	LIRInsertionBuffer()
	{
		ops = new ArrayList<LIRInstruction>(8);
		indexAndCount = new IntList(8);
	}

	// must be called before using the insertion buffer
	void init(LIRList lir)
	{
		assert !initialized() : "already initialized";
		this.lir = lir;
		indexAndCount.clear();
		ops.clear();
	}

	boolean initialized()
	{
		return lir != null;
	}

	// called automatically when the buffer is appended to the LIRList
	public void finish()
	{
		lir = null;
	}

	// accessors
	public LIRList lirList()
	{
		return lir;
	}

	public int numberOfInsertionPoints()
	{
		return indexAndCount.size() >> 1;
	}

	public int indexAt(int i)
	{
		return indexAndCount.get(i << 1);
	}

	public int countAt(int i)
	{
		return indexAndCount.get((i << 1) + 1);
	}

	public int numberOfOps()
	{
		return ops.size();
	}

	public LIRInstruction opAt(int i)
	{
		return ops.get(i);
	}

	void move(int index, LIRValue src, LIRValue dst)
	{
		append(index, new LIROp1(LIROpcode.Move, src, dst, dst.kind));
	}

	// Implementation of LIRInsertionBuffer

	/**
	 * Appends a LIRInstruction into the ops list.
	 * @param index
	 * @param op
     */
	private void append(int index, LIRInstruction op)
	{
		assert indexAndCount.size() % 2
				== 0 : "must have a count for each index";

		int i = numberOfInsertionPoints() - 1;
		if (i < 0 || indexAt(i) < index)
		{
			appendNew(index, 1);
		}
		else
		{
			assert indexAt(i)
					== index : "can append LIROps in ascending order only";
			assert countAt(i) > 0 : "check";
			setCountAt(i, countAt(i) + 1);
		}
		ops.add(op);

		assert verify();
	}

	private boolean verify()
	{
		int sum = 0;
		int prevIdx = -1;

		for (int i = 0; i < numberOfInsertionPoints(); i++)
		{
			assert prevIdx < indexAt(i) : "index must be ordered ascending";
			sum += countAt(i);
		}
		assert sum == numberOfOps() : "wrong total sum";
		return true;
	}
}