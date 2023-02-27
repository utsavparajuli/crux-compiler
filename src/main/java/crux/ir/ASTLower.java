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
  public Instruction getStart() {
    return this.start;
  }
  public Instruction getEnd() {
    return this.end;
  }
  public Value getVal() {
    return this.val;
  }
  public void setVal(Value val) {
    this.val = val;
  }
  public InstPair(Instruction start, Instruction end) {
    this.start = start;
    //start.setNext(0,this.end);
    this.end = end;
    val = null;
  }


  public InstPair(Instruction start, Instruction end, Value val) {
    this.start = start;
    //start.setNext(0,this.end);
    this.end = end;
    this.val = val;
  }

  public InstPair(Instruction inst) {
    this.start = inst;
    this.end = inst;
    //start.setNext(0, this.end);
    val = null;
  }
    public InstPair(Instruction inst, Value val) {
        this.start = inst;
        this.end = inst;
        //start.setNext(0, this.end);
        this.val = val;
    }
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

    return new InstPair(new NopInst(), new NopInst());

    //return null;
  }

  /**
   * This visitor should create a Function instance for the functionDefinition node, add parameters
   * to the localVarMap, add the function to the program, and init the function start Instruction.
   */
  @Override
  public InstPair visit(FunctionDefinition functionDefinition) {


    mCurrentFunction = new Function(functionDefinition.getSymbol().getName(),
                            (FuncType) functionDefinition.getSymbol().getType());


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

    InstPair func_cfg = new InstPair(new NopInst());


    //Init main_inst_pair
    for(var statement : statements) {
      //main_inst_pair.addEdge( what ever the accept returrns )
      var instP = statement.accept(this);
      func_cfg.addEdge(instP);
    }
    //return the main_inst_pair
    return func_cfg;
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


      //callArgs.add(mCurrentFunction.getTempVar(ret.getVal().getType())); //this is correct use the caller
      callArgs.add(((CopyInst) ret.end).getDstVar());
      genTemps.add(ret);

      //add the arg to  args but  as a LocalVar.
    }
      //  public CallInst(LocalVar destVar, Symbol callee, List<LocalVar> params) {
    CallInst callInst = new CallInst(call.getCallee(), callArgs);

    //InstPair start = new InstPair(new NopInst());
    //InstPair justAdded = start;

    //Instruction start = null;
    //Instruction end = null;
    InstPair test = new InstPair(new NopInst());

    InstPair ip = new InstPair(new NopInst());

    for(InstPair pair : genTemps)
    {
      //if(start == null)
      //{
      //  start = pair.getStart();
      //  end = pair.getEnd();
      //}
      //else
      //{
      //  end.setNext(0, pair.getStart());
      //  end = pair.getEnd();
      //}
      //start.addEdge(temp);
      //  justAdded = temp; //need to get the just added edge
      ip.addEdge(pair);
    }
    //end.setNext(0, callInst);
    //InstPair retVal;
    //if(!genTemps.isEmpty()) {
    //  retVal = new InstPair(genTemps.get(0).start);
    //}
    //else {
    //  return new InstPair(callInst);
    //}

    //justAdded.addEdge(new InstPair(callInst, new NopInst()));


//
//      InstPair tail = retVal;
//
//      for (InstPair temp : genTemps) {
//        tail = tail.addEdge(temp);
//      }
//
//      tail.addEdge(callInst);
    ip.addEdge(callInst);
    return ip;
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
    return null;
  }

  /**
   * Copy the literal into a tempVar
   */
  @Override
  public InstPair visit(LiteralInt literalInt) {
    var v = mCurrentFunction.getTempVar(new IntType());

    var constRet = IntegerConstant.get(mCurrentProgram, literalInt.getValue());

    return new InstPair(new CopyInst(v,constRet), constRet);
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
