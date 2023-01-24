package crux.ast;

import crux.ast.*;
import crux.ast.OpExpr.Operation;
import crux.pt.CruxBaseVisitor;
import crux.pt.CruxParser;
import crux.ast.types.*;
import crux.ast.SymbolTable.Symbol;
import org.antlr.v4.runtime.ParserRuleContext;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class will convert the parse tree generated by ANTLR to AST It follows the visitor pattern
 * where declarations will be by DeclarationVisitor Class Statements will be resolved by
 * StatementVisitor Class Expressions will be resolved by ExpressionVisitor Class
 */

public final class ParseTreeLower {
  private final DeclarationVisitor declarationVisitor = new DeclarationVisitor();
  private final StatementVisitor statementVisitor = new StatementVisitor();
  private final ExpressionVisitor expressionVisitor = new ExpressionVisitor();

  private final SymbolTable symTab;

  public ParseTreeLower(PrintStream err) {
    symTab = new SymbolTable(err);
  }

  private static Position makePosition(ParserRuleContext ctx) {
    var start = ctx.start;
    return new Position(start.getLine());
  }

  /**
   *
   * @return True if any errors
   */
  public boolean hasEncounteredError() {
    return symTab.hasEncounteredError();
  }


  /**
   * Lower top-level parse tree to AST
   * 
   * @return a {@link DeclarationList} object representing the top-level AST.
   */
  public DeclarationList lower(CruxParser.ProgramContext program) {
    ArrayList<Declaration> list = new ArrayList<Declaration> ();

    symTab.enter();
    for(CruxParser.DeclarationContext context: program.declarationList().declaration()) {
      Declaration node = context.accept(declarationVisitor);
      list.add(node);
    }

    symTab.exit();
    return new DeclarationList(makePosition(program), list);
  }


  /**
   * Lower statement list by lower individual statement into AST.
   * 
   * @return a {@link StatementList} AST object.
   */
  private StatementList lower(CruxParser.StatementListContext statementList) {
    ArrayList<Statement> list = new ArrayList<Statement> ();

    for(CruxParser.StatementContext context: statementList.statement()) {
      Statement node = context.accept(statementVisitor);
      list.add(node);
    }

    return new StatementList(makePosition(statementList.getParent()), list);
  }

  /**
   * Similar to {@link #lower(CruxParser.StatementListContext)}, but handles symbol table as well.
   * 
   * @return a {@link StatementList} AST object.
   */
   private StatementList lower(CruxParser.StatementBlockContext statementBlock) {
     symTab.enter();


     var stList = lower(statementBlock.statementList());

     symTab.exit();

     return stList;

   }


  /**
   * A parse tree visitor to create AST nodes derived from {@link Declaration}
   */
  private final class DeclarationVisitor extends CruxBaseVisitor<Declaration> {
    /**
     * Visit a parse tree variable declaration and create an AST {@link VariableDeclaration}
     * 
     * @return an AST {@link VariableDeclaration}
     */

     @Override
     public VariableDeclaration visitVariableDeclaration(CruxParser.VariableDeclarationContext ctx) {

       Symbol symbol = symTab.add(makePosition(ctx), ctx.Identifier().getText(), new IntType());
//       System.out.println("Here end");

       return new VariableDeclaration(makePosition(ctx), symbol);
     }


    /**
     * Visit a parse tree array declaration and creates an AST {@link ArrayDeclaration}
     * 
     * @return an AST {@link ArrayDeclaration}
     */

    /*
     * @Override
     * public Declaration visitArrayDeclaration(CruxParser.ArrayDeclarationContext ctx) { }
     */

    /**
     * Visit a parse tree function definition and create an AST {@link FunctionDefinition}
     * 
     * @return an AST {@link FunctionDefinition}
     */

    @Override
    public Declaration visitFunctionDefinition(CruxParser.FunctionDefinitionContext ctx) {
      var type = ctx.type();
      var identifier = ctx.Identifier().toString();

      Symbol symbol = symTab.add(makePosition(ctx), ctx.Identifier().getText(), null);

      ArrayList<Symbol> paramList = new ArrayList<Symbol> ();

      symTab.enter();

      for(CruxParser.ParameterContext context: ctx.parameterList().parameter()) {

        var parm = symTab.add(makePosition(ctx), context.Identifier().getText(), new IntType());
        paramList.add(parm);
      }

      var statementList = lower(ctx.statementBlock().statementList());

      symTab.exit();
      return new FunctionDefinition(makePosition(ctx), symbol, paramList, statementList);
    }

  }


  /**
   * A parse tree visitor to create AST nodes derived from {@link Statement}
   */

  private final class StatementVisitor extends CruxBaseVisitor<Statement> {
    /**
     * Visit a parse tree variable declaration and create an AST {@link VariableDeclaration}. Since
     * {@link VariableDeclaration} is both {@link Declaration} and {@link Statement}, we simply
     * delegate this to
     * {@link DeclarationVisitor#visitArrayDeclaration(CruxParser.ArrayDeclarationContext)} which we
     * implement earlier.
     * 
     * @return an AST {@link VariableDeclaration}
     */

    /*
     * @Override
     * public Statement visitVariableDeclaration(CruxParser.VariableDeclarationContext ctx) { }
     */
    
    /**
     * Visit a parse tree assignment statement and create an AST {@link Assignment}
     * 
     * @return an AST {@link Assignment}
     */

    /*
     * @Override
     * public Statement visitAssignmentStatement(CruxParser.AssignmentStatementContext ctx) { }
     */

    /**
     * Visit a parse tree assignment nosemi statement and create an AST {@link Assignment}
     * 
     * @return an AST {@link Assignment}
     */

    /*
     * @Override
     * public Statement visitAssignmentStatementNoSemi(CruxParser.AssignmentStatementNoSemiContext ctx) { }
     */

    /**
     * Visit a parse tree call statement and create an AST {@link Call}. Since {@link Call} is both
     * {@link Expression} and {@link Statement}, we simply delegate this to
     * {@link ExpressionVisitor#visitCallExpression(CruxParser.CallExpressionContext)} that we will
     * implement later.
     * 
     * @return an AST {@link Call}
     */

     @Override
     public Statement visitCallStatement(CruxParser.CallStatementContext ctx) {

       System.out.println("print call statement");

       var st = expressionVisitor.visitCallExpression(ctx.callExpression());

//       var temp = ctx.callExpression();


       System.out.println("print call 2");


       return st;
     }

    /**
     * Visit a parse tree if-else branch and create an AST {@link IfElseBranch}. The template code
     * shows partial implementations that visit the then block and else block recursively before
     * using those returned AST nodes to construct {@link IfElseBranch} object.
     * 
     * @return an AST {@link IfElseBranch}
     */

    /*
     * @Override
     * public Statement visitIfStatement(CruxParser.IfStatementContext ctx) { }
     */

    /**
     * Visit a parse tree for loop and create an AST {@link For}. You'll going to use a similar
     * techniques as {@link #visitIfStatement(CruxParser.IfStatementContext)} to decompose this
     * construction.
     * 
     * @return an AST {@link Loop}
     */

    /*
     * @Override
     * public Statement visitForStatement(CruxParser.ForStatementContext ctx) { }
     */

    /**
     * Visit a parse tree return statement and create an AST {@link Return}. Here we show a simple
     * example of how to lower a simple parse tree construction.
     * 
     * @return an AST {@link Return}
     */

    /*
     * @Override
     * public Statement visitReturnStatement(CruxParser.ReturnStatementContext ctx) { }
     */

    /**
     * Creates a Break node
     */

    /*
     * @Override
     * public Statement visitBreakStatement(CruxParser.BreakStatementContext ctx) { }
     */
  }

  private final class ExpressionVisitor extends CruxBaseVisitor<Expression> {
    /**
     * Parse Expression0 to OpExpr Node Parsing the expression should be exactly as described in the
     * grammer
     */

    //  public OpExpr(Position position, Operation op, Expression left, Expression right)
     @Override
     public Expression visitExpression0(CruxParser.Expression0Context ctx) {
//       if (ctx.op0() == null) {
//         return ctx.accept(expressionVisitor);
//       }

       ArrayList<Expression> list = new ArrayList<Expression> ();

       for(CruxParser.Expression1Context context: ctx.expression1()) {
         Expression node = context.accept(expressionVisitor);
         list.add(node);
       }

       return new OpExpr(makePosition(ctx), null, list.get(0), null);

     }

    /**
     * Parse Expression1 to OpExpr Node Parsing the expression should be exactly as described in the
     * grammer
     */

    /*
     * @Override
     * public Expression visitExpression1(CruxParser.Expression1Context ctx) { }
     */

    /**
     * Parse Expression2 to OpExpr Node Parsing the expression should be exactly as described in the
     * grammer
     */
    
    /*
     * @Override
     * public Expression visitExpression2(CruxParser.Expression2Context ctx) { }
     */

    /**
     * Parse Expression3 to OpExpr Node Parsing the expression should be exactly as described in the
     * grammer
     */

    /*
     * @Override
     * public Expression visitExpression3(CruxParser.Expression3Context ctx) { }
     */

    /**
     * Create an Call Node
     */

    @Override
    public Call visitCallExpression(CruxParser.CallExpressionContext ctx) {

      System.out.println("Print 1");

      var funcName = ctx.Identifier().getText();
      var pos = makePosition(ctx);

      var symbol = symTab.lookup(pos, funcName);

      ArrayList<Expression> list = new ArrayList<Expression> ();

      System.out.println("Print 2");


      for(CruxParser.Expression0Context context: ctx.expressionList().expression0()) {
        Expression node = context.accept(expressionVisitor);
        list.add(node);
      }

      System.out.println("Print 3");


      return new Call(pos, symbol, list);

    }


    /**
     * visitDesignator will check for a name or ArrayAccess FYI it should account for the case when
     * the designator was dereferenced
     */

    /* @Override
     * public Expression visitDesignator(CruxParser.DesignatorContext ctx) { }
     */

    /**
     * Create an Literal Node
     */

    /* @Override
     * public Expression visitLiteral(CruxParser.LiteralContext ctx) { }
     */
  }
}
