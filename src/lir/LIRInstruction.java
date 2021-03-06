package lir;

import lir.ci.*;
import lir.LIROperand.*;
import java.util.ArrayList;
import java.util.List;
import lir.ci.LIRValue.Formatter;

/**
 * <p>This file defines a base class of all LIR instruction that explicitly owns
 * several operands and result variable, which is different from Module instruction
 * which represents both operation and result of operation itself.
 * <p>It is designed to assign register to variable and lifetime computation
 * conveniently.
 *
 * @author Xlous.zeng
 */
public abstract class LIRInstruction
{
	private static final LIROperand ILLEGAL_SLOT = new LIROperand(
			LIRValue.IllegalValue);

	private static final LIRValue[] NO_OPERANDS = {};

	public static final OperandMode[] OPERAND_MODES = OperandMode.values();

	/**
	 * This enumeration is designed to computation of live interval conveniently
	 * for register allocation.
	 */
	public enum OperandMode
	{
		/**
		 * An LIROperand that is defined by a LIR instruction and it is live after
		 * of this emitted instruction.
		 */
		Output,
		/**
		 * An LIROperand that is used by a LIR instruction and is live before the
		 * code emitted. Unless such an LIROperand is also an output or temp LIROperand,
		 * it must not be modified by a LIR instruction.
		 */
		Input,

		/**
		 * An LIROperand that is both modified and used by a LIR instruction.
		 */
		Temp
	}

	/**
	 * The opcode of this instruction.
	 */
	public final LIROpcode opcode;

	/**
	 * <p>
	 * The result LIROperand for this instruction.
	 * </p>
	 * <p>
	 * If this instruction produce no value, then result is {@code LIRValue#IllegalValue}.
	 * </p>
	 */
	private final LIROperand result;

	/**
	 * The input and temporary operands of this instruction.
	 */
	protected final LIROperand[] operands;

	/**
	 * Value id for register allocation.
	 */
	public int id;

	/**
	 * Determines if all caller-saved LIRRegisters are destroyed by this instruction.
	 */
	public final boolean hasCall;

	/**
	 * The number of variable or register output operands for this instruction.
	 * These operands are at indexes {@code [0 .. allocatorOutputCount-1]} in
	 * {@link #allocatorOperands}.
	 *
	 * @see OperandMode#Output
	 */
	private byte allocatorOutputCount;

	/**
	 * The number of variable or register input operands for this instruction.
	 * These operands are at indexes {@code [allocatorOutputCount .. (allocator
	 * InputCount+allocatorOutputCount-1)]} in {@link #allocatorOperands}.
	 *
	 * @see OperandMode#Input
	 */
	private byte allocatorInputCount;

	/**
	 * The number of variable or register temp operands for this instruction.
	 * These operands are at indexes {@code [allocatorInputCount+allocatorOutputCount
	 * .. (allocatorTempCount+allocatorInputCount+allocatorOutputCount-1)]} in
	 * {@link #allocatorOperands}.
	 *
	 * @see OperandMode#Temp
	 */
	private byte allocatorTempCount;

	/**
	 * The number of variable or register input or temp operands for this instruction.
	 */
	private byte allocatorTempInputCount;

	/**
	 * The set of operands that must be known to the register allocator either to
	 * bind a register or stack slot to a {@linkplain LIRVariable variable} or to
	 * inform the allocator about operands that are already fixed to a specific
	 * register. This set excludes all constant operands as well as operands that
	 * are bound to a stack slot in the {@linkplain StackSlot#inCallerFrame()
	 * caller's frame}. This array is partitioned as follows.
	 * <pre>
	 *
	 *   <-- allocatorOutputCount --> <-- allocatorInputCount --> <-- allocatorTempCount -->
	 *  +----------------------------+---------------------------+--------------------------+
	 *  |       output operands      |       input operands      |      temp operands       |
	 *  +----------------------------+---------------------------+--------------------------+
	 *
	 * </pre>
	 */
	final List<LIRValue> allocatorOperands;

	/**
	 * Constructs a new LIR instruction that has no input or temp operands.
	 *
	 * @param opcode  the opcode of the new instruction
	 * @param result  the LIROperand that holds the operation result of this instruction.
	 *                   This will be{@link LIRValue#IllegalValue} for instructions
	 *                   that do not produce a result.
	 * @param hasCall specifies if all caller-saved LIRRegisters are destroyed by
	 *                   this instruction
	 */
	public LIRInstruction(LIROpcode opcode, LIRValue result, boolean hasCall)
	{
		this(opcode, result, hasCall, 0, 0, NO_OPERANDS);
	}

	/**
	 * Constructs a new LIR instruction. The {@code operands} array is partitioned as follows:
	 * <pre>
	 *
	 *                              <------- tempInput -------> <--------- temp --------->
	 *  +--------------------------+---------------------------+--------------------------+
	 *  |       input operands     |   input+temp operands     |      temp operands       |
	 *  +--------------------------+---------------------------+--------------------------+
	 *
	 * </pre>
	 *
	 * @param opcode    the opcode of the new instruction
	 * @param result    the LIROperand that holds the operation result of this instruction.
	 *                     This will be{@link LIRValue#IllegalValue} for instructions
	 *                     that do not produce a result.
	 * @param hasCall   specifies if all caller-saved LIRRegisters are destroyed by
	 *                     this instruction
	 * @param tempInput the number of operands that are both {@linkplain OperandMode#Input input}
	 *                     and {@link OperandMode#Temp temp} operands for this instruction
	 * @param temp      the number of operands that are {@link OperandMode#Temp temp}
	 *                     operands for this instruction
	 * @param operands  the input and temp operands for the instruction
	 */
	public LIRInstruction(LIROpcode opcode, LIRValue result, boolean hasCall,
			int tempInput, int temp, LIRValue... operands)
	{
		this.opcode = opcode;
		this.hasCall = hasCall;

		assert opcode != LIROpcode.Move || result != LIRValue.IllegalValue;
		allocatorOperands = new ArrayList<LIRValue>(operands.length + 3);
		this.result = initOutput(result);

		//C1XMetrics.LIRInstructions++;

		if (opcode == LIROpcode.Move)
		{
			//C1XMetrics.LIRMoveInstructions++;
		}
		id = -1;
		this.operands = new LIROperand[operands.length];
		initInputsAndTemps(tempInput, temp, operands);

		assert verifyOperands();
	}

	private LIROperand initOutput(LIRValue output)
	{
		assert output != null;
		if (output != LIRValue.IllegalValue)
		{
			if (output.isAddress())
			{
				return addAddress((LIRAddress) output);
			}
			if (output.isStackSlot())
			{
				return new LIROperand(output);
			}

			assert allocatorOperands.size() == allocatorOutputCount;
			allocatorOperands.add(output);
			allocatorOutputCount++;
			return new LIRVariableOperand(allocatorOperands.size() - 1);
		}
		else
		{
			return ILLEGAL_SLOT;
		}
	}

	/**
	 * Adds a {@linkplain LIRValue#isLegal() legal} value that is part of an address to
	 * the list of {@linkplain #allocatorOperands register allocator operands}. If
	 * the value is {@linkplain LIRVariable variable}, then its index into the list
	 * of register allocator operands is returned. Otherwise, {@code -1} is returned.
	 */
	private int addAddressPart(LIRValue part)
	{
		if (part.isRegister())
		{
			allocatorInputCount++;
			allocatorOperands.add(part);
			return -1;
		}
		if (part.isVariable())
		{
			allocatorInputCount++;
			allocatorOperands.add(part);
			return allocatorOperands.size() - 1;
		}
		assert part.isIllegal();
		return -1;
	}

	private LIROperand addAddress(LIRAddress LIRAddress)
	{
		assert LIRAddress.base.isVariableOrRegister();

		int base = addAddressPart(LIRAddress.base);
		int index = addAddressPart(LIRAddress.index);

		if (base != -1 || index != -1)
		{
			return new LIRAddressOperand(base, index, LIRAddress);
		}

		assert LIRAddress.base.isRegister() && (LIRAddress.index.isIllegal()
				|| LIRAddress.index.isRegister());
		return new LIROperand(LIRAddress);
	}

	private LIROperand addOperand(LIRValue operand, boolean isInput,
			boolean isTemp)
	{
		assert operand != null;
		if (operand != LIRValue.IllegalValue)
		{
			assert !(operand.isAddress());
			if (operand.isStackSlot())
			{
				// no variables to add
				return new LIROperand(operand);
			}
			else if (operand.isConstant())
			{
				// no variables to add
				return new LIROperand(operand);
			}
			else
			{
				assert allocatorOperands.size()
						== allocatorOutputCount + allocatorInputCount
						+ allocatorTempInputCount + allocatorTempCount;
				allocatorOperands.add(operand);

				if (isInput && isTemp)
				{
					allocatorTempInputCount++;
				}
				else if (isInput)
				{
					allocatorInputCount++;
				}
				else
				{
					assert isTemp;
					allocatorTempCount++;
				}

				return new LIRVariableOperand(allocatorOperands.size() - 1);
			}
		}
		else
		{
			return ILLEGAL_SLOT;
		}
	}

	/**
	 * Gets an input or temp LIROperand of this instruction.
	 *
	 * @param index the index of the LIROperand requested
	 * @return the {@code index}'th LIROperand
	 */
	public final LIRValue operand(int index)
	{
		if (index >= operands.length)
		{
			return LIRValue.IllegalValue;
		}

		return operands[index].value(this);
	}

	private void initInputsAndTemps(int tempInputCount, int tempCount,
			LIRValue[] operands)
	{

		// Addresses in instruction
		for (int i = 0; i < operands.length; i++)
		{
			LIRValue op = operands[i];
			if (op.isAddress())
			{
				this.operands[i] = addAddress((LIRAddress) op);
			}
		}

		int z = 0;
		// Input-only operands
		for (int i = 0; i < operands.length - tempInputCount - tempCount; i++)
		{
			if (this.operands[z] == null)
			{
				this.operands[z] = addOperand(operands[z], true, false);
			}
			z++;
		}

		// Operands that are both inputs and temps
		for (int i = 0; i < tempInputCount; i++)
		{
			if (this.operands[z] == null)
			{
				this.operands[z] = addOperand(operands[z], true, true);
			}
			z++;
		}

		// Temp-only operands
		for (int i = 0; i < tempCount; i++)
		{
			if (this.operands[z] == null)
			{
				this.operands[z] = addOperand(operands[z], false, true);
			}
			z++;
		}
	}

	private boolean verifyOperands()
	{
		for (LIROperand operandSlot : operands)
		{
			assert operandSlot != null;
		}

		for (LIRValue operand : this.allocatorOperands)
		{
			assert operand != null;
			assert operand.isVariableOrRegister() :
					"LIR operands can only be variables and LIRRegisters initially, not "
							+ operand.getClass().getSimpleName();
		}
		return true;
	}

	/**
	 * Gets the result LIROperand for this instruction.
	 *
	 * @return return the result LIROperand
	 */
	public final LIRValue result()
	{
		return result.value(this);
	}

	/**
	 * Gets the instruction name.
	 *
	 * @return the name of the enum constant that represents the instruction opcode, exactly as declared in the enum
	 * LIROpcode declaration.
	 */
	public String name()
	{
		return opcode.name();
	}

	/**
	 * Abstract method to be used to emit targetAbstractLayer code for this instruction.
	 *
	 * @param masm the targetAbstractLayer assembler.
	 */
	public abstract void emitCode(LIRAssembler masm);

	/**
	 * Gets the operation performed by this instruction in terms of its operands as a string.
	 */
	public String operationString(Formatter operandFmt)
	{
		StringBuilder buf = new StringBuilder();
		if (result != ILLEGAL_SLOT)
		{
			buf.append(operandFmt.format(result.value(this))).append(" = ");
		}
		if (operands.length > 1)
		{
			buf.append("(");
		}
		boolean first = true;
		for (LIROperand operandSlot : operands)
		{
			String operand = operandFmt.format(operandSlot.value(this));
			if (!operand.isEmpty())
			{
				if (!first)
				{
					buf.append(", ");
				}
				else
				{
					first = false;
				}
				buf.append(operand);
			}
		}
		if (operands.length > 1)
		{
			buf.append(")");
		}
		return buf.toString();
	}

	public boolean verify()
	{
		return true;
	}

	/**
	 * Determines if a given opcode is in a given range of valid opcodes.
	 *
	 * @param opcode the opcode to be tested.
	 * @param start  the lower bound range limit of valid opcodes
	 * @param end    the upper bound range limit of valid opcodes
	 */
	protected static boolean isInRange(LIROpcode opcode, LIROpcode start,
			LIROpcode end)
	{
		return start.ordinal() < opcode.ordinal() && opcode.ordinal() < end
				.ordinal();
	}

	public boolean hasOperands()
	{
		return hasCall ? true : allocatorOperands.size() > 0;
	}

	public final int operandCount(OperandMode mode)
	{
		if (mode == OperandMode.Output)
		{
			return allocatorOutputCount;
		}
		else if (mode == OperandMode.Input)
		{
			return allocatorInputCount + allocatorTempInputCount;
		}
		else
		{
			assert mode == OperandMode.Temp;
			return allocatorTempInputCount + allocatorTempCount;
		}
	}

	public final LIRValue operandAt(OperandMode mode, int index)
	{
		if (mode == OperandMode.Output)
		{
			assert index < allocatorOutputCount;
			return allocatorOperands.get(index);
		}
		else if (mode == OperandMode.Input)
		{
			assert index < allocatorInputCount + allocatorTempInputCount;
			return allocatorOperands.get(index + allocatorOutputCount);
		}
		else
		{
			assert mode == OperandMode.Temp;
			assert index < allocatorTempInputCount + allocatorTempCount;
			return allocatorOperands
					.get(index + allocatorOutputCount + allocatorInputCount);
		}
	}

	public final void setOperandAt(OperandMode mode, int index,
			LIRValue location)
	{
		assert index < operandCount(mode);
		assert location.kind != LIRKind.Illegal;
		assert operandAt(mode, index).isVariable();
		if (mode == OperandMode.Output)
		{
			assert index < allocatorOutputCount;
			allocatorOperands.set(index, location);
		}
		else if (mode == OperandMode.Input)
		{
			assert index < allocatorInputCount + allocatorTempInputCount;
			allocatorOperands.set(index + allocatorOutputCount, location);
		}
		else
		{
			assert mode == OperandMode.Temp;
			assert index < allocatorTempInputCount + allocatorTempCount;
			allocatorOperands
					.set(index + allocatorOutputCount + allocatorInputCount,
							location);
		}
	}

	@Override public String toString()
	{
		return toString(Formatter.DEFAULT);
	}

	public final String toStringWithIdPrefix()
	{
		if (id != -1)
		{
			return String.format("%4d %s", id, toString());
		}
		return "     " + toString();
	}

	public String toString(Formatter operandFmt)
	{
		StringBuilder buf = new StringBuilder(name()).append(' ')
				.append(operationString(operandFmt));

		return buf.toString();
	}
}
