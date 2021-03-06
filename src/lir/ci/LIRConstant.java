package lir.ci;

/**
 * Represents a constant (boxed) value, such as an integer, floating point number,
 * or object reference, within the compiler and across the compiler/runtime interface.
 * Exports a set of {@code LIRConstant}instances that represent frequently used
 * constant VALUES, such as ZERO.
 */
public final class LIRConstant extends LIRValue
{
	/**
	 *
	 */
	private static final long serialVersionUID = 1897221263708494020L;
	private static final LIRConstant[] INT_CONSTANT_CACHE = new LIRConstant[100];

	static
	{
		for (int i = 0; i < INT_CONSTANT_CACHE.length; ++i)
		{
			INT_CONSTANT_CACHE[i] = new LIRConstant(LIRKind.Int, i);
		}
	}

	public static final LIRConstant NULL_OBJECT = new LIRConstant(LIRKind.Object,
			null);
	public static final LIRConstant INT_MINUS_1 = new LIRConstant(LIRKind.Int, -1);
	public static final LIRConstant INT_0 = forInt(0);
	public static final LIRConstant INT_1 = forInt(1);
	public static final LIRConstant INT_2 = forInt(2);
	public static final LIRConstant INT_3 = forInt(3);
	public static final LIRConstant INT_4 = forInt(4);
	public static final LIRConstant INT_5 = forInt(5);
	public static final LIRConstant LONG_0 = new LIRConstant(LIRKind.Long, 0L);
	public static final LIRConstant LONG_1 = new LIRConstant(LIRKind.Long, 1L);
	public static final LIRConstant FLOAT_0 = new LIRConstant(LIRKind.Float,
			Float.floatToRawIntBits(0.0F));
	public static final LIRConstant FLOAT_1 = new LIRConstant(LIRKind.Float,
			Float.floatToRawIntBits(1.0F));
	public static final LIRConstant FLOAT_2 = new LIRConstant(LIRKind.Float,
			Float.floatToRawIntBits(2.0F));
	public static final LIRConstant DOUBLE_0 = new LIRConstant(LIRKind.Double,
			Double.doubleToRawLongBits(0.0D));
	public static final LIRConstant DOUBLE_1 = new LIRConstant(LIRKind.Double,
			Double.doubleToRawLongBits(1.0D));
	public static final LIRConstant TRUE = new LIRConstant(LIRKind.Boolean, 1L);
	public static final LIRConstant FALSE = new LIRConstant(LIRKind.Boolean, 0L);

	static
	{
		assert NULL_OBJECT.isDefaultValue();
		assert INT_0.isDefaultValue();
		assert FLOAT_0.isDefaultValue();
		assert DOUBLE_0.isDefaultValue();
		assert FALSE.isDefaultValue();

		// Ensure difference between 0.0f and -0.0f is preserved
		assert FLOAT_0 != forFloat(-0.0F);
		assert !forFloat(-0.0F).isDefaultValue();

		// Ensure difference between 0.0d and -0.0d is preserved
		assert DOUBLE_0 != forDouble(-0.0d);
		assert !forDouble(-0.0D).isDefaultValue();

		assert NULL_OBJECT.isNull();
	}

	/**
	 * The boxed object value. This is ignored iff {@code !kind.isObject()}.
	 */
	private final Object object;

	/**
	 * The boxed primitive value as a {@code long}. This is ignored iff {@code kind.isObject()}.
	 * For {@code float} and {@code double} VALUES, this value is the result of
	 * {@link Float#floatToRawIntBits(float)} and {@link Double#doubleToRawLongBits(double)} respectively.
	 */
	private final long primitive;

	/**
	 * Create a new constant represented by the specified object reference.
	 *
	 * @param kind   the type of this constant
	 * @param object the value of this constant
	 */
	private LIRConstant(LIRKind kind, Object object)
	{
		super(kind);
		this.object = object;
		this.primitive = 0L;
	}

	/**
	 * Create a new constant represented by the specified primitive.
	 *
	 * @param kind      the type of this constant
	 * @param primitive the value of this constant
	 */
	public LIRConstant(LIRKind kind, long primitive)
	{
		super(kind);
		this.object = null;
		this.primitive = primitive;
	}

	/**
	 * Checks whether this constant is non-null.
	 *
	 * @return {@code true} if this constant is a primitive, or an object constant that is not null
	 */
	public boolean isNonNull()
	{
		return !kind.isObject() || object != null;
	}

	/**
	 * Checks whether this constant is null.
	 *
	 * @return {@code true} if this constant is the null constant
	 */
	public boolean isNull()
	{
		return kind.isObject() && object == null;
	}

	@Override public String name()
	{
		return "const[" + kind.format(boxedValue()) + (kind != LIRKind.Object ?
				"|0x" + Long.toHexString(primitive) :
				"") + "]";
	}

	/**
	 * Gets this constant's value as a string.
	 *
	 * @return this constant's value as a string
	 */
	public String valueString()
	{
		if (kind.isPrimitive())
		{
			return boxedValue().toString();
		}
		else if (kind.isObject())
		{
			if (object == null)
			{
				return "null";
			}
			else if (object instanceof String)
			{
				return "\"" + object + "\"";
			}
			else
			{
				return "<object: " + kind.format(object) + ">";
			}
		}
		else
		{
			return "???";
		}
	}

	/**
	 * Returns the value of this constant as a boxed Java value.
	 *
	 * @return the value of this constant
	 */
	public Object boxedValue()
	{
		return boxedValue(kind);
	}

	/**
	 * Returns the value of this constant as a boxed Java value.
	 *
	 * @param kind the kind of the boxed value to be returned
	 * @return the value of this constant
	 */
	public Object boxedValue(LIRKind kind)
	{
		// Checkstyle: stop
		switch (kind)
		{
			case Byte:
				return (byte) asInt();
			case Boolean:
				return asInt() == 0 ? Boolean.FALSE : Boolean.TRUE;
			case Short:
				return (short) asInt();
			case Char:
				return (char) asInt();
			case Int:
				return asInt();
			case Long:
				return asLong();
			case Float:
				return asFloat();
			case Double:
				return asDouble();
			case Object:
				return object;
		}
		// Checkstyle: resume
		throw new IllegalArgumentException();
	}

	private boolean valueEqual(LIRConstant other, boolean ignoreKind)
	{
		// must have equivalent kinds to be equal
		if (!ignoreKind && kind != other.kind)
		{
			return false;
		}
		if (kind.isObject())
		{
			return object == other.object;
		}
		return primitive == other.primitive;
	}

	/**
	 * Converts this constant to a primitive int.
	 *
	 * @return the int value of this constant
	 */
	public int asInt()
	{
		if (kind.isInt())
		{
			return (int) primitive;
		}
		throw new Error("Constant is not int: " + this);
	}

	/**
	 * Converts this constant to a primitive boolean.
	 *
	 * @return the boolean value of this constant
	 */
	public boolean asBoolean()
	{
		if (kind == LIRKind.Boolean)
		{
			return primitive != 0L;
		}
		throw new Error("Constant is not boolean: " + this);
	}

	/**
	 * Converts this constant to a primitive long.
	 *
	 * @return the long value of this constant
	 */
	public long asLong()
	{
		// Checkstyle: stop
		switch (kind)
		{
			case Int:
			case Long:
				return primitive;
			case Float:
				return (long) asFloat();
			case Double:
				return (long) asDouble();
			default:
				throw new Error("Constant is not long: " + this);
		}
		// Checkstyle: resume
	}

	/**
	 * Converts this constant to a primitive float.
	 *
	 * @return the float value of this constant
	 */
	public float asFloat()
	{
		if (kind.isFloat())
		{
			return Float.intBitsToFloat((int) primitive);
		}
		throw new Error("Constant is not float: " + this);
	}

	/**
	 * Converts this constant to a primitive double.
	 *
	 * @return the double value of this constant
	 */
	public double asDouble()
	{
		if (kind.isFloat())
		{
			return Float.intBitsToFloat((int) primitive);
		}
		if (kind.isDouble())
		{
			return Double.longBitsToDouble(primitive);
		}
		throw new Error("Constant is not double: " + this);
	}

	/**
	 * Converts this constant to the object reference it represents.
	 *
	 * @return the object which this constant represents
	 */
	public Object asObject()
	{
		if (kind.isObject())
		{
			return object;
		}
		throw new Error("Constant is not object: " + this);
	}

	/**
	 * Unchecked access to a primitive value.
	 */
	public long asPrimitive()
	{
		if (kind.isObject())
		{
			throw new Error("Constant is not primitive: " + this);
		}
		return primitive;
	}

	/**
	 * Computes the hashcode of this constant.
	 *
	 * @return a suitable hashcode for this constant
	 */
	@Override public int hashCode()
	{
		if (kind.isObject())
		{
			return System.identityHashCode(object);
		}
		return (int) primitive;
	}

	/**
	 * Checks whether this constant equals another object. This is only
	 * true if the other object is a constant and has the same value.
	 *
	 * @param o the object to compare equality
	 * @return {@code true} if this constant is equivalent to the specified object
	 */
	@Override public boolean equals(Object o)
	{
		return o == this || o instanceof LIRConstant && valueEqual(
				(LIRConstant) o, false);
	}

	@Override public boolean equalsIgnoringKind(LIRValue o)
	{
		return o == this || o instanceof LIRConstant && valueEqual(
				(LIRConstant) o, true);
	}

	/**
	 * Checks whether this constant is identical to another constant or has the same value as it.
	 *
	 * @param other the constant to compare for equality against this constant
	 * @return {@code true} if this constant is equivalent to {@code other}
	 */
	public boolean equivalent(LIRConstant other)
	{
		return other == this || valueEqual(other, false);
	}

	/**
	 * Checks whether this constant is the default value for its type.
	 *
	 * @return {@code true} if the value is the default value for its type; {@code false} otherwise
	 */
	public boolean isDefaultValue()
	{
		// Checkstyle: stop
		switch (kind)
		{
			case Int:
				return asInt() == 0;
			case Long:
				return asLong() == 0;
			case Float:
				return this == FLOAT_0;
			case Double:
				return this == DOUBLE_0;
			case Object:
				return object == null;
		}
		// Checkstyle: resume
		throw new IllegalArgumentException(
				"Cannot det default LIRConstant for kind " + kind);
	}

	/**
	 * Gets the one with different data type.
	 *
	 * @param kind The kind of data type.
	 * @return The one for different data type.
	 */
	public static LIRConstant getOne(LIRKind kind)
	{
		// Checkstyle: stop
		switch (kind)
		{
			case Int:
				return INT_1;
			case Long:
				return LONG_1;
			case Float:
				return FLOAT_1;
			case Double:
				return DOUBLE_1;
		}
		// Checkstyle: resume
		throw new IllegalArgumentException(
				"Cannot get default LIRConstant for kind " + kind);
	}

	/**
	 * Gets the default value for a given kind.
	 *
	 * @return the default value for {@code kind}.
	 */
	public static LIRConstant defaultValue(LIRKind kind)
	{
		// Checkstyle: stop
		switch (kind)
		{
			case Int:
				return INT_0;
			case Long:
				return LONG_0;
			case Float:
				return FLOAT_0;
			case Double:
				return DOUBLE_0;
			case Object:
				return NULL_OBJECT;
		}
		// Checkstyle: resume
		throw new IllegalArgumentException(
				"Cannot get default LIRConstant for kind " + kind);
	}

	/**
	 * Creates a boxed double constant.
	 *
	 * @param d the double value to box
	 * @return a boxed copy of {@code value}
	 */
	public static LIRConstant forDouble(double d)
	{
		if (Double.compare(0.0D, d) == 0)
		{
			return DOUBLE_0;
		}
		if (Double.compare(d, 1.0D) == 0)
		{
			return DOUBLE_1;
		}
		return new LIRConstant(LIRKind.Double, Double.doubleToRawLongBits(d));
	}

	/**
	 * Creates a boxed float constant.
	 *
	 * @param f the float value to box
	 * @return a boxed copy of {@code value}
	 */
	public static LIRConstant forFloat(float f)
	{
		if (Float.compare(f, 0.0F) == 0)
		{
			return FLOAT_0;
		}
		if (Float.compare(f, 1.0F) == 0)
		{
			return FLOAT_1;
		}
		if (Float.compare(f, 2.0F) == 0)
		{
			return FLOAT_2;
		}
		return new LIRConstant(LIRKind.Float, Float.floatToRawIntBits(f));
	}

	/**
	 * Creates a boxed long constant.
	 *
	 * @param i the long value to box
	 * @return a boxed copy of {@code value}
	 */
	public static LIRConstant forLong(long i)
	{
		return i == 0 ?
				LONG_0 :
				i == 1 ? LONG_1 : new LIRConstant(LIRKind.Long, i);
	}

	/**
	 * Creates a boxed integer constant.
	 *
	 * @param i the integer value to box
	 * @return a boxed copy of {@code value}
	 */
	public static LIRConstant forInt(int i)
	{
		if (i == -1)
		{
			return INT_MINUS_1;
		}
		if (i >= 0 && i < INT_CONSTANT_CACHE.length)
		{
			return INT_CONSTANT_CACHE[i];
		}
		return new LIRConstant(LIRKind.Int, i);
	}

	/**
	 * Creates a boxed byte constant.
	 *
	 * @param i the byte value to box
	 * @return a boxed copy of {@code value}
	 */
	public static LIRConstant forByte(byte i)
	{
		return new LIRConstant(LIRKind.Byte, i);
	}

	/**
	 * Creates a boxed boolean constant.
	 *
	 * @param i the boolean value to box
	 * @return a boxed copy of {@code value}
	 */
	public static LIRConstant forBoolean(boolean i)
	{
		return i ? TRUE : FALSE;
	}

	/**
	 * Creates a boxed char constant.
	 *
	 * @param i the char value to box
	 * @return a boxed copy of {@code value}
	 */
	public static LIRConstant forChar(char i)
	{
		return new LIRConstant(LIRKind.Char, i);
	}

	/**
	 * Creates a boxed short constant.
	 *
	 * @param i the short value to box
	 * @return a boxed copy of {@code value}
	 */
	public static LIRConstant forShort(short i)
	{
		return new LIRConstant(LIRKind.Short, i);
	}

	/**
	 * Creates a boxed object constant.
	 *
	 * @param o the object value to box
	 * @return a boxed copy of {@code value}
	 */
	public static LIRConstant forObject(Object o)
	{
		if (o == null)
		{
			return NULL_OBJECT;
		}
		return new LIRConstant(LIRKind.Object, o);
	}

	/**
	 * Creates a boxed constant for the given kind from an Object.
	 * The object needs to be of the Java boxed type corresponding to the kind.
	 *
	 * @param kind  the kind of the constant to create
	 * @param value the Java boxed value: a Byte instance for LIRKind Byte, etc.
	 * @return the boxed copy of {@code value}
	 */
	public static LIRConstant forBoxed(LIRKind kind, Object value)
	{
		switch (kind)
		{
			case Byte:
				return forByte((Byte) value);
			case Char:
				return forChar((Character) value);
			case Short:
				return forShort((Short) value);
			case Int:
				return forInt((Integer) value);
			case Long:
				return forLong((Long) value);
			case Float:
				return forFloat((Float) value);
			case Double:
				return forDouble((Double) value);
			case Object:
				return forObject(value);
			default:
				throw new RuntimeException(
						"cannot create LIRConstant for boxed " + kind
								+ " value");
		}
	}
}
