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

  public Instruction getStart() {
    return this.start;
  }

  public Instruction getEnd() {
    return this.end;
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

      stateIP.add(instP);
//      if(counter == 0) {
//        startInst.setNext(0, instP.start);
////          for (int i = 0; i < instP.start.numNext(); i++) {
////
////          }
////        temp = startInst.getNext(0);
////        temp.setNext(0, instP.end);
//        temp = startInst.getNext(0);
//        temp.setNext(0, instP.end);
//
//      }
//      else {
//        temp = temp.getNext(0);
//        temp.setNext(0, instP.start);
//        temp = temp.getNext(0);
//        temp.setNext(0, instP.end);
//      }
//      counter++;

      //func_cfg.addEdge(instP);



//      func_cfg.addEdge(instP);
//
//
//      func_cfg = instP;
    }

    Instruction start = null;
    Instruction end = null;

    for (InstPair pair : stateIP) {
      if (start == null) {
        start = pair.getStart();
        end = pair.getEnd();
      } else {
        end.setNext(0, pair.getStart());
        end = pair.getEnd();
      }
    }

//start and end could be null if the list is empty;

    if(start == null) {
      start = new NopInst();
    }

    InstPair result = new InstPair (start, end);



    return result;
    //return the main_inst_pair
//    return func_cfg;

    //return null;

  }

  /**
   * Declarations, could be either local or Global
   */
  @Override
  public InstPair visit(VariableDeclaration variableDeclaration) {

    if (mCurrentFunction == null) {
      mCurrentProgram.addGlobalVar(new GlobalDecl(variableDeclaration.getSymbol(), IntegerConstant.get(mCurrentProgram, 1)));
    }
    else {
      mCurrentLocalVarMap.put(variableDeclaration.getSymbol(), mCurrentFunction.getTempVar(variableDeclaration.getType()));
    }
    return new InstPair(new NopInst());
  }

  /**
   * Create a declaration for array and connected it to the CFG
   */
  @Override
  public InstPair visit(ArrayDeclaration arrayDeclaration) {
    mCurrentProgram.addGlobalVar(new GlobalDecl(arrayDeclaration.getSymbol(),
            IntegerConstant.get(mCurrentProgram, ((ArrayType)arrayDeclaration.getSymbol().getType()).getExtent())));

    return new InstPair(new NopInst());
  }

  /**
   * LookUp the name in the map(s). For globals, we should do a load to get the value to load into a
   * LocalVar.
   */
  @Override
  public InstPair visit(VarAccess name) {
    if (mCurrentLocalVarMap.containsKey(name.getSymbol())) {
      return new InstPair(new NopInst(), new NopInst(), mCurrentLocalVarMap.get(name.getSymbol()));
    }

    var v = mCurrentFunction.getTempAddressVar(name.getType());

    var load = new LoadInst(mCurrentFunction.getTempVar(name.getType()), v);

    //var store = new StoreInst(mCurrentFunction.getTempVar(name.getType()), v);

    //mCurrentLocalVarMap.put(name.getSymbol(), load.getDst());

    return new InstPair(new AddressAt(load.getSrcAddress(), name.getSymbol()),
            new AddressAt(load.getSrcAddress(), name.getSymbol()),
            load.getSrcAddress());
    //return new InstPair(new AddressAt(v, name.getSymbol()), new AddressAt(v, name.getSymbol()), v);

  }

  /**
   * If the location is a VarAccess to a LocalVar, copy the value to it. If the location is a
   * VarAccess to a global, store the value. If the location is ArrayAccess, store the value.
   */
  @Override
  public InstPair visit(Assignment assignment) {
    var lhs = assignment.getLocation().accept(this);
    var rhs = assignment.getValue().accept(this);


    Instruction end;
    if(lhs.getVal().getClass().equals(LocalVar.class)) {
      end = new CopyInst((LocalVar) lhs.getVal(), rhs.getVal());
    }
    else {
      end = new StoreInst(((CopyInst) rhs.getStart()).getDstVar(), (AddressVar) lhs.getVal());
    }

    Instruction start = lhs.start;
    start.setNext(0, rhs.start);
    start.getNext(0).setNext(0, end);

    return new InstPair(start, end);
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
      if (ret.end.getClass().equals(BinaryOperator.class)) {
        callArgs.add(((BinaryOperator) ret.end).getDst());
      }
      else if (ret.end.getClass().equals(AddressAt.class)) {
        //callArgs.add(((AddressAt)ret.end).getOffset());
      }
      else if (ret.end.getClass().equals(CopyInst.class)){
        callArgs.add(((CopyInst) ret.start).getDstVar());
      }
      else {
        callArgs.add((LocalVar) ret.getVal());
      }

      genTemps.add(ret);

      //add the arg to  args but  as a LocalVar.
    }
      //  public CallInst(LocalVar destVar, Symbol callee, List<LocalVar> params) {
    CallInst callInst = new CallInst(mCurrentFunction.getTempVar(call.getType()), call.getCallee(), callArgs);




    InstPair retVal;
    Instruction inst;
    if(!genTemps.isEmpty()) {
      inst = genTemps.get(0).start;
    }
    else {
      return new InstPair(callInst, callInst, callInst.getDst());
    }


    //works for test 2-3
    //inst.setNext(0, callInst);

    if (genTemps.get(0).getEnd().getClass().equals(BinaryOperator.class)) {
      genTemps.get(0).getEnd().setNext(0, callInst);
    }
    else {
      inst.setNext(0, callInst);
    }
    return new InstPair(inst, callInst);
  }

  /**
   * Handle operations like arithmetics and comparisons. Also handle logical operations (and,
   * or, not).
   */
  @Override
  public InstPair visit(OpExpr operation) {
    var lhs = operation.getLeft().accept(this);
    var rhs = operation.getRight().accept(this);

    BinaryOperator.Op op;

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
      default:
        op = BinaryOperator.Op.Div;
        break;
    }
    var binOp = new BinaryOperator( op, mCurrentFunction.getTempVar(operation.getType()),
            ((CopyInst)lhs.start).getDstVar(), ((CopyInst)rhs.start).getDstVar());

    rhs.addEdge(binOp);
    lhs.addEdge(rhs.end);


    return new InstPair(lhs.end, binOp);

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
    var v = ret.getValue().accept(this);

    ReturnInst ri = new ReturnInst((LocalVar) v.val);
    v.getEnd().setNext(0, ri);
    return new InstPair(v.getStart(), ri);
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
    var cond = ifElseBranch.getCondition().accept(this);

    JumpInst j = new JumpInst(((CopyInst) cond.getStart()).getDstVar());


    cond.addEdge(j);


    var elseBlock = ifElseBranch.getElseBlock().accept(this);
    var thenBlock = ifElseBranch.getThenBlock().accept(this);

    NopInst n = new NopInst();

    if (elseBlock.getEnd() != null) {
      elseBlock.getEnd().setNext(0, n);
    }
    else {
      elseBlock.getStart().setNext(0, n);
    }

    thenBlock.getEnd().setNext(0, n);

    j.setNext(0, elseBlock.getStart());
    j.setNext(1, thenBlock.getStart());




    return new InstPair(cond.getEnd(), n);
  }

  /**
   * Implement for loops.
   */
  @Override
  public InstPair visit(For loop) {
    return null;
  }
}
