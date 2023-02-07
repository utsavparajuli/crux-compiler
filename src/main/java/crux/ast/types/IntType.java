package crux.ast.types;

/**
 * Types for Integers values. This should implement the equivalent methods along with add, sub, mul,
 * div, and compare. The method equivalent will check if the param is an instance of IntType.
 */
public final class IntType extends Type implements java.io.Serializable {
  static final long serialVersionUID = 12022L;

  @Override
  Type add(Type that) {
    if(this.equivalent(that)) {
      return new IntType();
    } else
    {
      return super.add(that);
    }
    //return new ErrorType("cannot add " + this + " with " + that);
  }

  @Override
  Type sub(Type that) {
    if(this.equivalent(that)) {
      return new IntType();
    } else
    {
      return super.sub(that);
    }
    //return new ErrorType("cannot subtract " + this + " from " + that);
  }

  @Override
  Type mul(Type that) {
    if(this.equivalent(that)) {
      return new IntType();
    } else
    {
      return super.mul(that);
    }

    //return new ErrorType("cannot multiply " + this + " with " + that);
  }

  @Override
  Type div(Type that) {
    if(this.equivalent(that)) {
      return new IntType();
    } else
    {
      return super.div(that);
    }

    //return new ErrorType("cannot divide " + this + " by " + that);
  }

  @Override
  public boolean equivalent(Type that) {
    return that.getClass() == IntType.class;
  }


  @Override
  public String toString() {
    return "int";
  }
}
