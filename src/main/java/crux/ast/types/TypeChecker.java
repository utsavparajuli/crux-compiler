package crux.ast.types;

import crux.ast.*;
import crux.ast.traversal.NullNodeVisitor;

import java.util.ArrayList;

/**
 * This class will associate types with the AST nodes from Stage 2
 */
public final class TypeChecker {
  private final ArrayList<String> errors = new ArrayList<>();

  public SymbolTable.Symbol currentFunctionSymbol = null;
  public Type currentFuncReturnType = null;
  public boolean lastStatementReturns;

  public ArrayList<String> getErrors() {
    return errors;
  }


  public void check(DeclarationList ast) {
    var inferenceVisitor = new TypeInferenceVisitor();
    inferenceVisitor.visit(ast);
  }

  /**
   * Helper function, should be used to add error into the errors array
   */
  private void addTypeError(Node n, String message) {
    errors.add(String.format("TypeError%s[%s]", n.getPosition(), message));
  }

  /**
   * Helper function, should be used to record Types if the Type is an ErrorType then it will call
   * addTypeError
   */
  private void setNodeType(Node n, Type ty) {
    ((BaseNode) n).setType(ty);
    if (ty.getClass() == ErrorType.class) {
      var error = (ErrorType) ty;
      addTypeError(n, error.getMessage());
    }
  }

  /**
   * Helper to retrieve Type from the map
   */
  public Type getType(Node n) {
    return ((BaseNode) n).getType();
  }


  /**
   * This calls will visit each AST node and try to resolve it's type with the help of the
   * symbolTable.
   */
  private final class TypeInferenceVisitor extends NullNodeVisitor<Void> {
    @Override
    public Void visit(VarAccess vaccess) {

      Type t = (vaccess.getSymbol().getType().equivalent(new IntType())) ?
              new IntType() : new BoolType();


      setNodeType(vaccess, t);
      return null;
    }

    @Override
    public Void visit(ArrayDeclaration arrayDeclaration) {

      if (!arrayDeclaration.getSymbol().getType().equivalent(new BoolType()) &&
              !arrayDeclaration.getSymbol().getType().equivalent(new IntType())) {
        addTypeError(arrayDeclaration, "Cannot declare array type void");
      }

      ArrayType arr = (ArrayType) arrayDeclaration.getSymbol().getType();

      setNodeType(arrayDeclaration, new ArrayType(arr.getExtent(), arr.getBase()));

      lastStatementReturns = false;
      return null;
    }

    @Override
    public Void visit(Assignment assignment) {
      var children = assignment.getChildren();

      children.get(0).accept(this);
      children.get(1).accept(this);

      var location = ((BaseNode)assignment.getLocation()).getType();

      location.assign(((BaseNode)assignment.getValue()).getType());
      //assign not working




      return null;
    }

    @Override
    public Void visit(Break brk) {
      return null;
    }

    @Override
    public Void visit(Call call) {
//      call.getCallee().getType().call(call.getCallee().)
      FuncType callee = (FuncType) call.getCallee().getType();

      TypeList tl = new TypeList();

      //if (callee.call(call.getType()))
      for(var arg: call.getArguments()) {
        arg.accept(this);
        if (arg.getClass().equals(Call.class)) {
          FuncType nestedCall = (FuncType)((Call) arg).getCallee().getType();
          tl.append(nestedCall.getRet());
        }
        else {
          tl.append(getType(arg));
        }
      }

      if (!callee.getArgs().equivalent(tl)) {
        addTypeError(call, "Types mismatch in function args");
      }
//call.getArguments()
      //callee.call(callee.getArgs());
      setNodeType(call, callee.getRet());
      return null;
    }

    @Override
    public Void visit(DeclarationList declarationList) {
      var children = declarationList.getChildren();

      //declarationList.(accept)
      for (var child: children) {
        //declarationList.accept((NodeVisitor<?>) n);
        child.accept(this);
      }
      return null;
    }

    @Override
    public Void visit(FunctionDefinition functionDefinition) {
      //setNodeType(functionDefinition, new FuncType(functionDefinition.getParameters(), functionDefinition.getType()));
      currentFunctionSymbol = functionDefinition.getSymbol();

      //var funcRet = getType(functionDefinition);
      Type functionReturnType = ((FuncType) currentFunctionSymbol.getType()).getRet();

      lastStatementReturns = !functionReturnType.equivalent(new VoidType());

      currentFuncReturnType = functionReturnType;
      if (currentFunctionSymbol.getName().equals("main")) {
        if (lastStatementReturns)
        {
          addTypeError(functionDefinition, "main must return void");
        }

        if (!functionDefinition.getParameters().isEmpty()) {
          addTypeError(functionDefinition, "main method must have no parameters");

        }
      }

      //currentFuncReturnType = currentFunctionSymbol.getType();

      var statements = functionDefinition.getStatements();

      var params = functionDefinition.getParameters();


      statements.accept(this);
//
//      visit(a);
      return null;
    }

    @Override
    public Void visit(IfElseBranch ifElseBranch) {
      ifElseBranch.getCondition().accept(this);

      ifElseBranch.getThenBlock().accept(this);

      ifElseBranch.getElseBlock().accept(this);
      return null;
    }

    @Override
    public Void visit(ArrayAccess access) {

      access.getIndex().accept(this);
      ArrayType arr = (ArrayType) access.getBase().getType();

      setNodeType(access, arr.getBase());

      //arr.index(access.getBase().getType());
      arr.index(getType(access));
      return null;
    }

    @Override
    public Void visit(LiteralBool literalBool) {
      setNodeType(literalBool, new BoolType());
      return null;
    }

    @Override
    public Void visit(LiteralInt literalInt) {
      setNodeType(literalInt, new IntType());
      return null;
    }

    @Override
    public Void visit(For forloop) {
      forloop.getCond().accept(this);
      forloop.getInit().accept(this);
      forloop.getIncrement().accept(this);
      forloop.getBody().accept(this);

      return null;
    }

    @Override
    public Void visit(OpExpr op) {
      var rhs = op.getRight();
      var lhs = op.getLeft();

      var oper = op.getOp();


      rhs.accept(this);

      lhs.accept(this);

      if(oper.equals(OpExpr.Operation.ADD) || oper.equals(OpExpr.Operation.SUB) ||
          oper.equals(OpExpr.Operation.DIV) || oper.equals(OpExpr.Operation.MULT) )
      {
        setNodeType(op, new IntType());
      }
      else if
      (oper.equals(OpExpr.Operation.GE) || oper.equals(OpExpr.Operation.LE) ||
                      oper.equals(OpExpr.Operation.GT) || oper.equals(OpExpr.Operation.LT) ||
      oper.equals(OpExpr.Operation.LOGIC_AND) || oper.equals(OpExpr.Operation.NE) ||
                      oper.equals(OpExpr.Operation.EQ) || oper.equals(OpExpr.Operation.LOGIC_OR))
      {
        setNodeType(op, new BoolType());
      }
      else {
        setNodeType(op, new BoolType());
      }
      //oper.toString()
      return null;
    }

    @Override
    public Void visit(Return ret) {

      ret.getValue().accept(this);
//      if(ret.getValue())


      return null;
    }

    @Override
    public Void visit(StatementList statementList) {
      var statements = statementList.getChildren();

      for(var statement : statements) {
        statement.accept(this);
      }
      return null;
    }

    @Override
    public Void visit(VariableDeclaration variableDeclaration) {

      if (!variableDeclaration.getSymbol().getType().equivalent(new BoolType()) &&
              !variableDeclaration.getSymbol().getType().equivalent(new IntType())) {
        addTypeError(variableDeclaration, "Cannot declare var type void");
      }

      if (variableDeclaration.getSymbol().getType().equivalent(new BoolType())) {
        setNodeType(variableDeclaration, new BoolType());
      }
      else {
        setNodeType(variableDeclaration, new IntType());
      }

      lastStatementReturns = false;

      return null;
    }
  }
}
