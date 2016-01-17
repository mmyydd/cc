package type;
import java.util.*;

import symbol.Symbol.TypeSymbol;


/**
 * 定义一个函数类型，由函数的返回值和函数的参数列表决定。
 * @author zeng
 */
public class MethodType extends Type {
    
	
	public Type returnType;
     
	public List<Type> paramTypes;


    public MethodType(Type ret, List<Type> argtypes, 
    		TypeSymbol methodClass) {
        super(METHOD, methodClass);
    	returnType = ret;
        paramTypes = argtypes;
    }
    
    public boolean equals(Object that) 
    {
        if (this == that)
            return true;
        if (!(that instanceof MethodType))
            return false;
        MethodType mthat = (MethodType) that;
        List<Type> thisargs = this.paramTypes;
        List<Type> thatargs = mthat.paramTypes;
        Iterator<Type> thisIt = thisargs.iterator();
        Iterator<Type> thatIt = thatargs.iterator();
        
        while (thisIt.hasNext() && thatIt.hasNext())
        {
        	if (! thisIt.next().equals(thatIt.next()))
        		break;
        }
        if (thisIt.hasNext() || thatIt.hasNext())
        	return false;
        
        return this.returnType.equals(mthat.returnType);
    }

    public int hashCode()
    {
    	int h = METHOD;
    	for (Type t : this.paramTypes)
    	{
    		h = (h << 5) + t.hashCode();
    	}
    	return (h << 5) + this.returnType.hashCode();
    }
    
    public boolean hasSameArgs(Type that) 
    {
        return that.tag == METHOD && isSameTypes(this.paramTypes, ((MethodType)that).paramTypes);
    }

    
        
    /**
     * Tests type equality.
     * @return true returned if target type is equivalent to self, otherwise false.
     */
    public boolean isSameType(Type other) {
        if ( other.tag != METHOD) return false;
                
        return returnType.isSameType(((MethodType)other).returnType)
            && hasSameArgs(other);
    }


    public boolean isCastableTo(Type target) {
        return target.tag == METHOD;
    }

    public Type returnType() {
        return returnType;
    }

    /**
     * Returns iterator of mandatory parameter types.
     * This method does NOT include types for varargs.
     */
    public List<Type> paramTypes() {
        return paramTypes;
    }

    public long size() {
        throw new Error("MethodType#size called");
    }

    public String toString() {
        String sep = "";
        StringBuffer buf = new StringBuffer();
        buf.append(returnType.toString());
        buf.append("(");
        for (Type t : paramTypes) {
            buf.append(sep);
            buf.append(t.toString());
            sep = ", ";
        }
        buf.append(")");
        return buf.toString();
    }
}