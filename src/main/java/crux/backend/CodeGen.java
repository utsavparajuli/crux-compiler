package crux.backend;

import crux.ast.FunctionDefinition;
import crux.ast.SymbolTable.Symbol;
import crux.ir.*;
import crux.ir.insts.*;
import crux.printing.IRValueFormatter;

import java.util.*;

/**
 * Convert the CFG into Assembly Instructions
 */
public final class CodeGen extends InstVisitor {
  private final Program p;
  private final CodePrinter out;
  private Stack<Instruction> instStack = new Stack<>();

  private HashMap<Variable, Integer> varIndexMap = new HashMap<>();
  private int varIndex = 0;


  public CodeGen(Program p) {
    this.p = p;
    // Do not change the file name that is outputted or it will
    // break the grader!

    out = new CodePrinter("a.s");
  }

  /**
   * It should allocate space for globals call genCode for each Function
   */
  public void genCode() {
    //TODO

    for(Iterator<GlobalDecl> glob_it = p.getGlobals(); glob_it.hasNext();) {
      GlobalDecl g = glob_it.next();
      out.printCode(".comm " + g.getSymbol().getName() + ", " + g.getNumElement().getValue() + ", 8");

    }

    int[] count = new int[1];

    for(Iterator<Function> func_it = p.getFunctions(); func_it.hasNext();) {
      Function f = func_it.next();
      genCode(f, count);

    }

    out.close();
  }

  private void genCode(Function f, int[] count) {
    f.assignLabels(count);

//    HashMap<Integer, String> argReg = new HashMap<>();

    //printInt(1,2)


    //printing function name
    out.printCode(".globl _" + f.getName());
    out.printLabel("_" + f.getName() + ":");

    var numVars = f.getNumTempVars() + f.getNumTempAddressVars();
    numVars = ((numVars % 2) == 0) ? numVars : numVars + 1;
    //prolog
    out.printCode("enter $(8 * " + numVars + "), $0");

    int argNum = 0;

//    for(LocalVar lv : f.getArguments()) {
//
//    }

    var startInst = f.getStart();

    instStack.push(startInst);

    ArrayList<Instruction> visited = new ArrayList<>();

    while(!instStack.isEmpty()) {
      var cur = instStack.pop();

      if(!visited.contains(cur)) {
        cur.accept(this);
        visited.add(cur);
      }
      var next = cur.getNext(0);

      if(next != null) {
        instStack.push(next);
      }

    }


    //epilogue
    out.printCode("leave");
    out.printCode("ret");
  }

  public void visit(AddressAt i) {

  }

  public void visit(BinaryOperator i) {
    LocalVar lhs = i.getLeftOperand();
    LocalVar rhs = i.getRightOperand();


    out.printCode("movq " + -8 * varIndexMap.get(lhs) + "(%rbp)" + ", %r10");
    out.printCode("addq " + -8 * varIndexMap.get(rhs) + "(%rbp)" + ", %r10");
    varIndex += 1;
    varIndexMap.put(i.getDst(), varIndex);
    out.printCode("movq %r10, " + -8 * varIndexMap.get(i.getDst()) + "(%rbp)");
  }

  public void visit(CompareInst i) {}

  public void visit(CopyInst i) {
//    var v = i.
    varIndex += 1;
    varIndexMap.put(i.getDstVar(), varIndex);

    var src = i.getSrcValue();
    if(src.getClass().equals(IntegerConstant.class)) {
      out.printCode("movq $" + ((Long) ((IntegerConstant) src).getValue()).intValue() + ", %r10");
    }
    else if(src.getClass().equals(BooleanConstant.class)) {
      var boolInt = ((BooleanConstant) src).getValue() ? 1 : 0;
      out.printCode("movq $" + boolInt + ", %r10");
    }

    out.printCode("movq " + "%r10, " + -8 * varIndexMap.get(i.getDstVar()) + "(%rbp)");
  }

  public void visit(JumpInst i) {}

  public void visit(LoadInst i) {}

  public void visit(NopInst i) {
  }

  public void visit(StoreInst i) {}

  public void visit(ReturnInst i) {}

  public void visit(CallInst i) {

//    out.printCode("movq $" + ((IntegerConstant) src).getValue() + ", %r10");
//  }
    String[] argReg = new String[] {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};

    int argRegCount = 0;
    for (LocalVar arg: i.getParams()) {
      out.printCode("movq " + -8 * varIndexMap.get(arg) + "(%rbp)" + ", " + argReg[argRegCount]);
      argRegCount++;
    }

    out.printCode("call _" + i.getCallee().getName());

  }

  public void visit(UnaryNotInst i) {}
}
