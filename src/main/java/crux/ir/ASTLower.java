package crux.ir;

import crux.ast.SymbolTable.Symbol;
import crux.ast.*;
import crux.ast.OpExpr.Operation;
import crux.ast.traversal.NodeVisitor;
import crux.ast.types.*;
import crux.ir.insts.*;
import org.antlr.v4.runtime.misc.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  private int mCurrentlyAssigning = 0;

  private ArrayList<Pair<Symbol, Integer>>  addressStored = new ArrayList<>();
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

    //declarationList.(accept)
    for (var child: children) {
      //declarationList.accept((NodeVisitor<?>) n);
      child.accept(this);
    }

//    return new InstPair(new NopInst());
    return new InstPair(new NopInst());

    //return null;
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


    //CurrentFunction.setStart(functionDefinition.getStatements());



    var v = functionDefinition.getStatements().accept(this);

    mCurrentFunction.setStart(v.start);

    mCurrentProgram.addFunction(mCurrentFunction);



    mCurrentFunction = null;
    mCurrentLocalVarMap = null;


    return v;


    //return null;
  }

  @Override
  public InstPair visit(StatementList statementList) {

    var statements = statementList.getChildren();

    InstPair retVal = new InstPair(new NopInst());

    var stateIP = new ArrayList<InstPair>();

    //Init main_inst_pair
    for(var statement : statements) {
      var instP = statement.accept(this);

      retVal.addEdge(instP);
//      stateIP.add(instP);
    }

    return retVal;


//    Instruction start = null;
//    Instruction end = null;
//
//    for (InstPair pair : stateIP) {
//      if (start == null) {
//        start = pair.getStart();
//        end = pair.getEnd();
//      } else {
//        end.setNext(0, pair.getStart());
//        end = pair.getEnd();
//      }
    //}

//start and end could be null if the list is empty;

//    if(start == null) {
//      start = new NopInst();
//    }
//
//    InstPair result = new InstPair (start, end);
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
    //mCurrentlyAssigning += 1;
    var lhs = assignment.getLocation().accept(this);
    mCurrentlyAssigning = 0;
    //global assigningCurrebntly = FALSE
    var rhs = assignment.getValue().accept(this);


    Instruction end;

    if(lhs.getVal().getClass().equals(LocalVar.class)) {
      end = new CopyInst((LocalVar) lhs.getVal(), rhs.getVal());
    }
    else {
//      if(lhs.getStart().getClass().equals(AddressAt.class)) {
//        addressStored.add(((AddressAt) lhs.getStart()).getBase());
//      }
//      else {
//        var temp = lhs.getStart().getNext(0);
//
//        addressStored.add(((AddressAt) temp).getBase());
//      }

      Symbol symbol = null;
      Integer base = null;
//      if(assignment.getLocation().getClass().equals(VarAccess.class)) {
//        symbol = ((VarAccess) assignment.getLocation()).getSymbol();
//      }
//      else {
//        symbol = ((ArrayAccess) assignment.getLocation()).getBase();
//      }
//
//      if (!addressStored.contains() && symbol != null) {
//        addressStored.add(symbol);
//      }
//
//      if !mCurrentlyAssigning:
//

//      addressStored.add(((AddressAt) lhs.getStart()).getBase());
      end = new StoreInst((LocalVar) rhs.getVal(), (AddressVar) lhs.getVal());
    }

    //i = 10;
    //Instruction start = lhs.start;

    //start.setNext(0, rhs.start);
    //start.getNext(0).setNext(0, end);
//    rhs.addEdge(end);
    lhs.addEdge(rhs);
    lhs.addEdge(end);

    return lhs;
    //return  lhs;
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

      //TODO: refactor to have value
//      if (ret.end.getClass().equals(BinaryOperator.class)) {
//        callArgs.add(((BinaryOperator) ret.end).getDst());
//      }
//      else if (ret.end.getClass().equals(LoadInst.class)) {
//        callArgs.add(((LoadInst)ret.end).getDst());
//      }
//      else if (ret.end.getClass().equals(CopyInst.class)){
//        callArgs.add(((CopyInst) ret.start).getDstVar());
//      }
      if (ret.end.getClass().equals(LoadInst.class)) {
        callArgs.add(((LoadInst)ret.end).getDst());
      }
      else {
        callArgs.add((LocalVar) ret.getVal());
      }
      genTemps.add(ret);
    }
    //  public CallInst(LocalVar destVar, Symbol callee, List<LocalVar> params) {



    CallInst callInst = new CallInst(mCurrentFunction.getTempVar(call.getType()),
            call.getCallee(), callArgs);


    InstPair retVal = new InstPair(new NopInst(), callInst.getDst());

    for(InstPair pair : genTemps) {
      retVal.addEdge(pair);
    }

    retVal.addEdge(callInst);

    return retVal;

    //Instruction inst;

//    if(!genTemps.isEmpty()) {
//      inst = genTemps.get(0).start;
//    }
//    else {
//      return new InstPair(callInst, callInst, callInst.getDst());
//    }
//
//
//    //works for test 2-3
//    //inst.setNext(0, callInst);
//
//    if (genTemps.get(0).getEnd().getClass().equals(BinaryOperator.class) ||
//            genTemps.get(0).getEnd().getClass().equals(LoadInst.class)) {
//      genTemps.get(0).getEnd().setNext(0, callInst);
//    }
//    else {
//      inst.setNext(0, callInst);
//    }
//    return new InstPair(inst, callInst);
  }

  /**
   * Handle operations like arithmetics and comparisons. Also handle logical operations (and,
   * or, not).
   */
  @Override
  public InstPair visit(OpExpr operation) {
    var lhs = operation.getLeft().accept(this);
    var rhs = operation.getRight().accept(this);

    BinaryOperator.Op op = null;
    CompareInst.Predicate pred = null;

    BinaryOperator binOp = null;
    CompareInst compareInst;

    switch (operation.getOp().toString()) {
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
      default:
        pred = CompareInst.Predicate.NE;
        break;
    }

    if(op != null) {

//      if (lhs.getStart().getClass().equals(CopyInst.class)) {
//        binOp = new BinaryOperator( op, mCurrentFunction.getTempVar(operation.getType()),
//                ((CopyInst)lhs.start).getDstVar(), ((CopyInst)rhs.start).getDstVar());
//      }
//      else {
//        binOp = new BinaryOperator( op, mCurrentFunction.getTempVar(operation.getType()),
//                ((LoadInst)lhs.end).getDst(), ((CopyInst)rhs.start).getDstVar());
//      }

      LocalVar retVar = mCurrentFunction.getTempVar(operation.getType());

      if (!lhs.end.getClass().equals(LoadInst.class)) {
        binOp = new BinaryOperator( op, retVar,
                (LocalVar) lhs.getVal(), (LocalVar) rhs.getVal());
      }
      else {
        binOp = new BinaryOperator( op, retVar,
                ((LoadInst) lhs.end).getDst(), (LocalVar) rhs.getVal());
      }




      //lhs -> rhs -> binOP
      //1 + 2 + 3
      lhs.addEdge(rhs);
      lhs.addEdge(binOp);
      lhs.setVal(retVar);

//      rhs.addEdge(binOp);
//      lhs.addEdge(rhs.end);


      return lhs;

    }
    else {
//      compareInst = new CompareInst(mCurrentFunction.getTempVar(operation.getType()), pred,
//              (LocalVar) lhs.getVal(), ((CopyInst)rhs.start).getDstVar());

      if (lhs.end.getClass().equals(LoadInst.class)) {
        compareInst = new CompareInst(mCurrentFunction.getTempVar(operation.getType()), pred,
                ((LoadInst) lhs.end).getDst(), (LocalVar) rhs.getVal());
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
    mCurrentlyAssigning += 1;
    if (mCurrentLocalVarMap.containsKey(name.getSymbol())) {
      return new InstPair(new NopInst(), mCurrentLocalVarMap.get(name.getSymbol()));
    }

    var v = mCurrentFunction.getTempAddressVar(name.getType());


//    if (addressStored.contains(name.getSymbol())) {
      if (mCurrentlyAssigning >= 2 || mCurrentlyAssigning == 0)
      {
        var load = new LoadInst(mCurrentFunction.getTempVar(name.getType()), v);

        var adAt = new AddressAt(v, name.getSymbol());

        adAt.setNext(0, load);

        return new InstPair(adAt,
                load,
                v);
    }

//      mCurrentlyAssigning -= 1;
      return new InstPair(new AddressAt(v, name.getSymbol()), v);

  }
  /**
   * It should compute the address into the array, do the load, and return the value in a LocalVar.
   */
  @Override
  public InstPair visit(ArrayAccess access) {
    mCurrentlyAssigning += 1;

    var index = access.getIndex().accept(this);


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
    if (mCurrentlyAssigning >= 2 || mCurrentlyAssigning == 0) {
      var loadInst = new LoadInst(mCurrentFunction.getTempVar(access.getType()), tempArr);
      index.addEdge(loadInst);
    }
    //else we are currentlyAssigning
    index.setVal(tempArr);
//    mCurrentlyAssigning -= 1;
    return index;

  }
//  public InstPair visit(ArrayAccess access) {
//    var index = access.getIndex().accept(this);
//    var tempArr = mCurrentFunction.getTempAddressVar(access.getType());
//    AddressAt adAt;
//    if (index.end.getClass().equals(LoadInst.class)) {
//      adAt = new AddressAt(tempArr, access.getBase(),
//              ((LoadInst) index.end).getDst());
//    }
//    else {
//      adAt = new AddressAt(tempArr, access.getBase(),
//              (LocalVar) index.getVal());
//    }
//    index.addEdge(adAt);
//    if (addressStored.contains(access.getBase())) {
//      var loadInst = new LoadInst(mCurrentFunction.getTempVar(access.getType()), tempArr);
////      adAt.setNext(0, loadInst);
//      index.addEdge(loadInst);
//      return new InstPair(index.getEnd(), loadInst, tempArr);
//    }
//    return new InstPair(adAt, tempArr);
//  }

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
//    if(cond.getStart().getClass().equals(CopyInst.class)) {
//      j = new JumpInst(((CopyInst) cond.getStart()).getDstVar());
//    }
//    else {
//      j = new JumpInst(((LoadInst) cond.getStart()).getDst());
//    }

    j = new JumpInst((LocalVar) cond.getVal());




    cond.addEdge(j);


    var elseBlock = ifElseBranch.getElseBlock().accept(this);
    var thenBlock = ifElseBranch.getThenBlock().accept(this);

    NopInst n = new NopInst();

//    if (elseBlock.getEnd() != null) {
//      elseBlock.getEnd().setNext(0, n);
//    }
//    else {
//      elseBlock.getStart().setNext(0, n);
//    }
    elseBlock.addEdge(n);

//    thenBlock.getEnd().setNext(0, n);
    thenBlock.addEdge(n);

//    j.setNext(0, elseBlock.getStart());
//    j.setNext(1, thenBlock.getStart());

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

//    if (body.end.getClass().equals(Break.class)) {
//
//    }
    body.addEdge(increment);



    body.addEdge(cond);



//    body.(exitLoop);

//    header.addEdge(body);

//    body.addEdge(new InstPair(exitLoop, header.getEnd()));

    return retVal;
  }
}
