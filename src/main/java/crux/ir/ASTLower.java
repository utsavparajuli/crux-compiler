package crux.ir;

import crux.ast.SymbolTable.Symbol;
import crux.ast.*;
import crux.ast.OpExpr.Operation;
import crux.ast.traversal.NodeVisitor;
import crux.ast.types.*;
import crux.ir.insts.*;

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


  public void addEdge(Instruction inst) {
    this.end.setNext(0, inst);
  }

  public void addEdge(InstPair instPair) {
    this.end.setNext(0, instPair.start);
  }



}


/**
 * Convert AST to IR and build the CFG
 */
public final class ASTLower implements NodeVisitor<InstPair> {
  private Program mCurrentProgram = null;
  private Function mCurrentFunction = null;

  private Map<Symbol, LocalVar> mCurrentLocalVarMap = null;

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

    NopInst startInst = new NopInst();
    NopInst endInst = new NopInst();

    InstPair func_cfg = new InstPair(startInst, endInst);




    var stateIP = new ArrayList<InstPair>();
    int counter = 0;
    Instruction temp = null;
    //Init main_inst_pair
    for(var statement : statements) {
      //main_inst_pair.addEdge( what ever the accept returrns )

      var instP = statement.accept(this);

      if(counter == 0) {
        startInst.setNext(0, instP.start);
//          for (int i = 0; i < instP.start.numNext(); i++) {
//
//          }
        temp = startInst.getNext(0);
        temp.setNext(0, instP.end);
      }
      else {
        temp = temp.getNext(0);
        temp.setNext(0, instP.start);
        temp = temp.getNext(0);
        temp.setNext(0, instP.end);
      }
      counter++;



//      func_cfg.addEdge(instP);
//
//
//      func_cfg = instP;
    }


    return new InstPair(func_cfg.start, new NopInst());
    //return the main_inst_pair
//    return func_cfg;

    //return null;

  }

  /**
   * Declarations, could be either local or Global
   */
  @Override
  public InstPair visit(VariableDeclaration variableDeclaration) {
    return null;
  }

  /**
   * Create a declaration for array and connected it to the CFG
   */
  @Override
  public InstPair visit(ArrayDeclaration arrayDeclaration) {
    return null;
  }

  /**
   * LookUp the name in the map(s). For globals, we should do a load to get the value to load into a
   * LocalVar.
   */
  @Override
  public InstPair visit(VarAccess name) {
    return null;
  }

  /**
   * If the location is a VarAccess to a LocalVar, copy the value to it. If the location is a
   * VarAccess to a global, store the value. If the location is ArrayAccess, store the value.
   */
  @Override
  public InstPair visit(Assignment assignment) {
    return null;
  }

  /**
   * Lower a Call.
   */
  @Override
  public InstPair visit(Call call) {

    List<LocalVar> callArgs = new ArrayList<>();
    //var v = (FuncType) call.getCallee().getType();

    ArrayList<InstPair> genTemps = new ArrayList<>();

    for(var arg: call.getArguments()) {
      //Instruction or InstPair
      var ret = arg.accept(this);


//      callArgs.add(mCurrentFunction.getTempVar(ret.getVal().getType())); //this is correct use the caller
      callArgs.add(((CopyInst) ret.start).getDstVar());
      genTemps.add(ret);

      //add the arg to  args but  as a LocalVar.
    }
      //  public CallInst(LocalVar destVar, Symbol callee, List<LocalVar> params) {
    CallInst callInst = new CallInst(call.getCallee(), callArgs);




    InstPair retVal;
    Instruction inst;
    if(!genTemps.isEmpty()) {
      inst = genTemps.get(0).start;
    }
    else {
      return new InstPair(callInst, new NopInst());
    }








//
//      InstPair tail = retVal;
//
//      for (InstPair temp : genTemps) {
//        tail = tail.addEdge(temp);
//      }
//
//      tail.addEdge(callInst);

    return new InstPair(inst, callInst);
  }

  /**
   * Handle operations like arithmetics and comparisons. Also handle logical operations (and,
   * or, not).
   */
  @Override
  public InstPair visit(OpExpr operation) {
    return null;
  }

  private InstPair visit(Expression expression) {
    return null;
  }

  /**
   * It should compute the address into the array, do the load, and return the value in a LocalVar.
   */
  @Override
  public InstPair visit(ArrayAccess access) {
    return null;
  }

  /**
   * Copy the literal into a tempVar
   */
  @Override
  public InstPair visit(LiteralBool literalBool) {

    var v = mCurrentFunction.getTempVar(new BoolType());

    var constRet = BooleanConstant.get(mCurrentProgram, literalBool.getValue());

    return new InstPair(new CopyInst(v,constRet), new CopyInst(v,constRet), constRet);
  }

  /**
   * Copy the literal into a tempVar
   */
  @Override
  public InstPair visit(LiteralInt literalInt) {
    var v = mCurrentFunction.getTempVar(new IntType());

    var constRet = IntegerConstant.get(mCurrentProgram, literalInt.getValue());

    return new InstPair(new CopyInst(v,constRet), new CopyInst(v,constRet), constRet);
  }

  /**
   * Lower a Return.
   */
  @Override
  public InstPair visit(Return ret) {
    return null;
  }

  /**
   * Break Node
   */
  @Override
  public InstPair visit(Break brk) {
    return null;
  }

  /**
   * Implement If Then Else statements.
   */
  @Override
  public InstPair visit(IfElseBranch ifElseBranch) {
    return null;
  }

  /**
   * Implement for loops.
   */
  @Override
  public InstPair visit(For loop) {
    return null;
  }
}
