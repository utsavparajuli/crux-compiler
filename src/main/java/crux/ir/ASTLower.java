package crux.ir;

import crux.ast.SymbolTable.Symbol;
import crux.ast.*;
import crux.ast.OpExpr.Operation;
import crux.ast.traversal.NodeVisitor;
import crux.ast.types.*;
import crux.ir.insts.*;
import org.antlr.v4.runtime.misc.Pair;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class InstPair {

  Instruction start;
  Instruction end;
  Value val;

  //write get and set
  public Value getVal() {
    return val;
  }

  public void setVal(Value val) {
    this.val = val;
  }

  public InstPair(Instruction start, Instruction end) {
    this.start = start;
    this.end = end;
    val = null;
  }

  public InstPair(Instruction start, Instruction end, Value val) {
    this.start = start;
    this.end = end;
    this.val = val;
  }

  public InstPair(Instruction inst) {
    this.start = inst;
    this.end = inst;
    val = null;

  }

  public InstPair(Instruction inst, Value val) {
    this.start = inst;
    this.end = inst;
    this.val = val;

  }

  public void setEnd(Instruction inst) {

    var tempEnd = this.end;
    while (this.end.getNext(0) != null) {

      tempEnd = tempEnd.getNext(0);
    }

    tempEnd.setNext(0, inst);
  }



//  public void addEdge(Instruction inst) {
//    this.end.setNext(0, inst);
//  }

  public Instruction getStart() {
    return this.start;
  }

  public Instruction getEnd() {
    return this.end;
  }


//  public void addEdge(InstPair instPair) {
//    this.end.setNext(0, instPair.start);
//  }

  public void addEdge(Instruction inst)
  {
    this.getEnd().setNext(0, inst);
    this.end = inst;
  }

  public void addEdge(InstPair instPair)
  {
    this.getEnd().setNext(0, instPair.getStart());
    this.end = instPair.getEnd();
  }


}


/**
 * Convert AST to IR and build the CFG
 */
public final class ASTLower implements NodeVisitor<InstPair> {
  private Program mCurrentProgram = null;
  private Function mCurrentFunction = null;

  private Map<Symbol, LocalVar> mCurrentLocalVarMap = null;

  private NopInst exitLoop = null;

  private boolean mCurrentlyAssigning = false;

  private boolean inDepth = false;

  private Stack<Boolean> depthBool = new Stack<>();
  /**
   * A constructor to initialize member variables
   */
  public ASTLower() {}

  public Program lower(DeclarationList ast) {
    mCurrentProgram = new Program();

    visit(ast);
    return mCurrentProgram;
  }

  @Override
  public InstPair visit(DeclarationList declarationList) {


    var children = declarationList.getChildren();

    for (var child: children) {
      child.accept(this);
    }

    return new InstPair(new NopInst());
  }

  /**
   * This visitor should create a Function instance for the functionDefinition node, add parameters
   * to the localVarMap, add the function to the program, and init the function start Instruction.
   */
  @Override
  public InstPair visit(FunctionDefinition functionDefinition) {


    mCurrentFunction = new Function(functionDefinition.getSymbol().getName(), (FuncType) functionDefinition.getSymbol().getType());
    mCurrentLocalVarMap = new HashMap<>();

    var parameters = functionDefinition.getParameters();

    ArrayList<LocalVar> listVars = new ArrayList<>();

    for(var param : parameters) {
      var localVar = mCurrentFunction.getTempVar(param.getType());

      listVars.add(localVar);

      mCurrentLocalVarMap.put(param, localVar);
    }

    mCurrentFunction.setArguments(listVars);

    var v = functionDefinition.getStatements().accept(this);

    mCurrentFunction.setStart(v.start);
    mCurrentProgram.addFunction(mCurrentFunction);

    mCurrentFunction = null;
    mCurrentLocalVarMap = null;

    return v;
  }

  @Override
  public InstPair visit(StatementList statementList) {

    var statements = statementList.getChildren();

    InstPair retVal = new InstPair(new NopInst());

    for(var statement : statements) {
      var instP = statement.accept(this);
      retVal.addEdge(instP);
    }
    return retVal;
  }

  /**
   * Declarations, could be either local or Global
   */
  @Override
  public InstPair visit(VariableDeclaration variableDeclaration) {

    if (mCurrentFunction == null) {
      mCurrentProgram.addGlobalVar(new GlobalDecl(variableDeclaration.getSymbol(),
              IntegerConstant.get(mCurrentProgram, 1)));
    }
    else {
      mCurrentLocalVarMap.put(variableDeclaration.getSymbol(),
              mCurrentFunction.getTempVar(variableDeclaration.getType()));
    }
    return new InstPair(new NopInst());
  }

  /**
   * Create a declaration for array and connected it to the CFG
   */
  @Override
  public InstPair visit(ArrayDeclaration arrayDeclaration) {
    mCurrentProgram.addGlobalVar(new GlobalDecl(arrayDeclaration.getSymbol(),
            IntegerConstant.get(mCurrentProgram,
                    ((ArrayType)arrayDeclaration.getSymbol().getType()).getExtent())));

    return new InstPair(new NopInst());
  }



  /**
   * If the location is a VarAccess to a LocalVar, copy the value to it. If the location is a
   * VarAccess to a global, store the value. If the location is ArrayAccess, store the value.
   */
  @Override
  public InstPair visit(Assignment assignment) {
    //global assigningCurrently = TRUE
    mCurrentlyAssigning = true;
    var lhs = assignment.getLocation().accept(this);
    mCurrentlyAssigning = false;
    //global assigningCurrently = FALSE
    var rhs = assignment.getValue().accept(this);

    Instruction end;

    if(lhs.getVal().getClass().equals(LocalVar.class)) {
      end = new CopyInst((LocalVar) lhs.getVal(), rhs.getVal());
    }
    else {
      if (rhs.end.getClass().equals(LoadInst.class)) {
        end = new StoreInst(((LoadInst)rhs.end).getDst(), (AddressVar) lhs.getVal());
      }
      else {
        end = new StoreInst((LocalVar) rhs.getVal(), (AddressVar) lhs.getVal());
      }
    }

    lhs.addEdge(rhs);
    lhs.addEdge(end);

    return lhs;
  }

  /**
   * Lower a Call.
   */
  @Override
  public InstPair visit(Call call) {

    List<LocalVar> callArgs = new ArrayList<>();

    ArrayList<InstPair> genTemps = new ArrayList<>();

    for(var arg: call.getArguments()) {
      //Instruction or InstPair
      var ret = arg.accept(this);

      if (ret.end.getClass().equals(LoadInst.class)) {
        callArgs.add(((LoadInst)ret.end).getDst());
      }
      else {
        callArgs.add((LocalVar) ret.getVal());
      }
      genTemps.add(ret);
    }

    CallInst callInst = new CallInst(mCurrentFunction.getTempVar(call.getType()),
            call.getCallee(), callArgs);


    InstPair retVal = new InstPair(new NopInst(), callInst.getDst());

    for(InstPair pair : genTemps) {
      retVal.addEdge(pair);
    }

    retVal.addEdge(callInst);

    return retVal;
  }

  /**
   * Handle operations like arithmetics and comparisons. Also handle logical operations (and,
   * or, not).
   */
  @Override
  public InstPair visit(OpExpr operation) {
    var lhs = operation.getLeft().accept(this);
    InstPair rhs = null;
    if(operation.getRight() != null) {
      rhs = operation.getRight().accept(this);
    }

    BinaryOperator.Op op = null;
    CompareInst.Predicate pred = null;

    BinaryOperator binOp = null;
    CompareInst compareInst;

    boolean notCase = false;
    switch (operation.getOp().toString()) {
      case "!":
        notCase = true;
      case "+":
        op = BinaryOperator.Op.Add;
        break;
      case "-":
        op = BinaryOperator.Op.Sub;
        break;
      case "*":
        op = BinaryOperator.Op.Mul;
        break;
      case "/":
        op = BinaryOperator.Op.Div;
        break;
      case ">":
        pred = CompareInst.Predicate.GT;
        break;
      case ">=":
        pred = CompareInst.Predicate.GE;
        break;
      case "<":
        pred = CompareInst.Predicate.LT;
        break;
      case "<=":
        pred = CompareInst.Predicate.LE;
        break;
      case "==":
        pred = CompareInst.Predicate.EQ;
        break;
      case "!=":
        pred = CompareInst.Predicate.NE;
        break;
      default:
        break;
    }

    if(notCase) {
      var tempUnary = mCurrentFunction.getTempVar(operation.getType());
      var unary = new UnaryNotInst(tempUnary, (LocalVar) lhs.getVal());

      lhs.addEdge(unary);

      return new InstPair(lhs.getStart(), unary, tempUnary);

    }
    else if(op != null) {
      LocalVar retVar = mCurrentFunction.getTempVar(operation.getType());

      if (!lhs.end.getClass().equals(LoadInst.class)) {
        binOp = new BinaryOperator( op, retVar,
                (LocalVar) lhs.getVal(), (LocalVar) rhs.getVal());
      }
      else {
        if (!rhs.end.getClass().equals(LoadInst.class)) {
          binOp = new BinaryOperator( op, retVar,
                  ((LoadInst) lhs.end).getDst(), (LocalVar) rhs.getVal());
        }
        else {
          binOp = new BinaryOperator( op, retVar,
                  ((LoadInst) lhs.end).getDst(), ((LoadInst) rhs.end).getDst());
        }

      }
      lhs.addEdge(rhs);
      lhs.addEdge(binOp);
      lhs.setVal(retVar);
      return lhs;

    }
    else if (pred != null){
      if (lhs.end.getClass().equals(LoadInst.class)) {
        if (!rhs.end.getClass().equals(LoadInst.class)) {
          compareInst = new CompareInst(mCurrentFunction.getTempVar(operation.getType()), pred,
                  ((LoadInst) lhs.end).getDst(), (LocalVar) rhs.getVal());
        }
        else {
          compareInst = new CompareInst(mCurrentFunction.getTempVar(operation.getType()), pred,
                  ((LoadInst) lhs.end).getDst(), ((LoadInst) rhs.end).getDst());
        }
      }
      else {
        compareInst = new CompareInst(mCurrentFunction.getTempVar(operation.getType()), pred,
                (LocalVar) lhs.getVal(), (LocalVar) rhs.getVal());
      }

      lhs.addEdge(rhs);
      lhs.addEdge(compareInst);
      lhs.setVal(compareInst.getDst());

      return lhs;
    }
    else {
      if (operation.getOp().toString().equals("||")) {

        var jump = new JumpInst((LocalVar) lhs.getVal());

        var temp = mCurrentFunction.getTempVar(rhs.getVal().getType());


        var copyInstFalse = new CopyInst(temp, rhs.getVal());
        var copyInstTrue = new CopyInst(temp, BooleanConstant.get(mCurrentProgram, true));

        var end = new NopInst();

        copyInstFalse.setNext(0, end);
        copyInstTrue.setNext(0, end);

        rhs.addEdge(copyInstFalse);


        lhs.addEdge(jump);
        jump.setNext(0, rhs.getStart());
        jump.setNext(1, copyInstTrue);

        return new InstPair(lhs.getStart(), end, temp);

      }
      else if ((operation.getOp().toString().equals("&&"))) {
        var jump = new JumpInst((LocalVar) lhs.getVal());

        var temp = mCurrentFunction.getTempVar(rhs.getVal().getType());


        var copyInstFalse = new CopyInst(temp, BooleanConstant.get(mCurrentProgram, false));
        var copyInstTrue = new CopyInst(temp, rhs.getVal());

        var end = new NopInst();

        copyInstFalse.setNext(0, end);
        copyInstTrue.setNext(0, end);

        rhs.addEdge(copyInstTrue);


        lhs.addEdge(jump);
        jump.setNext(0, copyInstFalse);
        jump.setNext(1, rhs.getStart());

        return new InstPair(lhs.getStart(), end, temp);
      }

      return  null;
    }
  }

  private InstPair visit(Expression expression) {
    return null;
  }


  /**
   * LookUp the name in the map(s). For globals, we should do a load to get the value to load into a
   * LocalVar.
   */
  @Override
  public InstPair visit(VarAccess name) {
    if (mCurrentLocalVarMap.containsKey(name.getSymbol())) {
      return new InstPair(new NopInst(), mCurrentLocalVarMap.get(name.getSymbol()));
    }

    var v = mCurrentFunction.getTempAddressVar(name.getType());


//    if (addressStored.contains(name.getSymbol())) {
      if (!mCurrentlyAssigning)
      {
        var load = new LoadInst(mCurrentFunction.getTempVar(name.getType()), v);

        var adAt = new AddressAt(v, name.getSymbol());

        adAt.setNext(0, load);

        return new InstPair(adAt,
                load,
                v);
    }

    return new InstPair(new AddressAt(v, name.getSymbol()), v);

  }
  /**
   * It should compute the address into the array, do the load, and return the value in a LocalVar.
   */
  @Override
  public InstPair visit(ArrayAccess access) {

    depthBool.push(mCurrentlyAssigning);
    mCurrentlyAssigning = false;

    var index = access.getIndex().accept(this);


    mCurrentlyAssigning = depthBool.pop();

    var tempArr = mCurrentFunction.getTempAddressVar(access.getType());

    AddressAt adAt;

    if (index.end.getClass().equals(LoadInst.class)) {
      adAt = new AddressAt(tempArr, access.getBase(),
              ((LoadInst) index.end).getDst());
    }
    else {
      adAt = new AddressAt(tempArr, access.getBase(),
              (LocalVar) index.getVal());
    }

    index.addEdge(adAt);
    // if NOT currentlyAssigning:
    if (!mCurrentlyAssigning || inDepth) {
      var loadInst = new LoadInst(mCurrentFunction.getTempVar(access.getType()), tempArr);
      index.addEdge(loadInst);
    }
    //else we are currentlyAssigning
    index.setVal(tempArr);

    return index;

  }

  /**
   * Copy the literal into a tempVar
   */
  @Override
  public InstPair visit(LiteralBool literalBool) {

    var v = mCurrentFunction.getTempVar(new BoolType());

    var constRet = BooleanConstant.get(mCurrentProgram, literalBool.getValue());

    return new InstPair(new CopyInst(v,constRet), v);
  }

  /**
   * Copy the literal into a tempVar
   */
  @Override
  public InstPair visit(LiteralInt literalInt) {
    var v = mCurrentFunction.getTempVar(new IntType());

    var constRet = IntegerConstant.get(mCurrentProgram, literalInt.getValue());

    return new InstPair(new CopyInst(v,constRet), v);
  }

  /**
   * Lower a Return.
   */
  @Override
  public InstPair visit(Return ret) {
    var v = ret.getValue().accept(this);

    ReturnInst ri = new ReturnInst((LocalVar) v.getVal());
    v.getEnd().setNext(0, ri);
    return new InstPair(v.getStart(), ri);
  }

  /**
   * Break Node
   */
  @Override
  public InstPair visit(Break brk) {

    //exitLoop = null;
    return new InstPair(new NopInst());
  }

  /**
   * Implement If Then Else statements.
   */
  @Override
  public InstPair visit(IfElseBranch ifElseBranch) {
    var cond = ifElseBranch.getCondition().accept(this);
    JumpInst j;

    j = new JumpInst((LocalVar) cond.getVal());

    cond.addEdge(j);


    var elseBlock = ifElseBranch.getElseBlock().accept(this);
    var thenBlock = ifElseBranch.getThenBlock().accept(this);

    NopInst n = new NopInst();

    elseBlock.addEdge(n);

    thenBlock.addEdge(n);


    j.setNext(0, elseBlock.getStart());
    j.setNext(1, thenBlock.getStart());




    return new InstPair(cond.getStart(), n);
  }

  /**
   * Implement for loops.
   */
  @Override
  public InstPair visit(For loop) {

    var init = loop.getInit().accept(this);
    var cond = loop.getCond().accept(this);
    var increment = loop.getIncrement().accept(this);


    InstPair retVal = new InstPair(new NopInst());
    retVal.addEdge(init);

    retVal.addEdge(cond);

    JumpInst jumpInst = new JumpInst((LocalVar) cond.getVal());

    retVal.addEdge(jumpInst);

    exitLoop = new NopInst();

    var body = loop.getBody().accept(this);


    jumpInst.setNext(0, exitLoop);
    jumpInst.setNext(1, body.getStart());

    body.addEdge(increment);
    body.addEdge(cond);

    return retVal;
  }
}
