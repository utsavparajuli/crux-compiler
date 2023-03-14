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
  private boolean thenBlock = false;
  private Instruction lastBlock;

  private HashMap<Instruction, String> labelMap;


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
    labelMap = f.assignLabels(count);

    String[] argReg = new String[] {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
    int argRegCount = 0;




    //printing function name
    out.printCode(".globl _" + f.getName());
    out.printLabel("_" + f.getName() + ":");

    var numVars = f.getNumTempVars() + f.getNumTempAddressVars();
    numVars = ((numVars % 2) == 0) ? numVars : numVars + 1;
    //prolog
    out.printCode("enter $(8 * " + numVars + "), $0");

    for(LocalVar lv: f.getArguments()) {
      varIndex += 1;
      varIndexMap.put(lv, varIndex);
      out.printCode("movq " + argReg[argRegCount] + ", " + -8 * varIndexMap.get(lv) + "(%rbp)");
      argRegCount++;
    }

    var startInst = f.getStart();

    instStack.push(startInst);

    ArrayList<Instruction> visited = new ArrayList<>();

    while(!instStack.isEmpty()) {
      var cur = instStack.pop();

      if(lastBlock != null) {
        if(cur.equals(lastBlock)) {
          out.printCode("leave");
          out.printCode("ret");
        }
      }
      if(!visited.contains(cur)) {
        cur.accept(this);
        visited.add(cur);
      }
      var next_false = cur.getNext(0);
      var next_true = cur.getNext(1);

      if(next_true != null)
      {
        lastBlock = next_true;
        thenBlock = true;
        instStack.push(next_true);
      }

      if(next_false != null)
      {
        instStack.push(next_false);
      }

    }

    //epilogue
    if(!thenBlock) {
      out.printCode("leave");
      out.printCode("ret");
    }
    else {
      out.printCode("jmp _L2");
    }

  }

  public void visit(AddressAt i) {
    printInstructionInfo(i);


    out.printCode("movq " + i.getBase().getName() + "@GOTPCREL(%rip), %r11");
  }

  public void visit(BinaryOperator i) {
    printInstructionInfo(i);

    LocalVar lhs = i.getLeftOperand();
    LocalVar rhs = i.getRightOperand();


    out.printCode("movq " + -8 * varIndexMap.get(lhs) + "(%rbp)" + ", %r10");

    switch (i.getOperator().toString()) {
      case "Add":
        out.printCode("addq " + -8 * varIndexMap.get(rhs) + "(%rbp)" + ", %r10");
        break;
      case "Sub":
        out.printCode("subq " + -8 * varIndexMap.get(rhs) + "(%rbp)" + ", %r10");
        break;
    }

    varIndex += 1;
    varIndexMap.put(i.getDst(), varIndex);
    out.printCode("movq %r10, " + -8 * varIndexMap.get(i.getDst()) + "(%rbp)");
  }

  public void visit(CompareInst i) {

  }

  public void visit(CopyInst i) {
    printInstructionInfo(i);
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

  public void visit(JumpInst i) {
    printInstructionInfo(i);
    out.printCode("movq " + -8 * varIndexMap.get(i.getPredicate()) + "(%rbp), " + "%r10");

    out.printCode("cmp $1, %r10");

    out.printCode("je _L1");

  }

  public void visit(LoadInst i) {
    printInstructionInfo(i);


    out.printCode("movq 0(%r11), %r10");

    varIndex += 1;
    varIndexMap.put(i.getDst(), varIndex);
    out.printCode("movq " + "%r10, " + -8 * varIndexMap.get(i.getDst()) + "(%rbp)");

  }

  public void visit(NopInst i) {
    printInstructionInfo(i);
    if(labelMap.containsKey(i)) {
      out.printLabel("_" + labelMap.get(i) + ":");
    }

  }

  public void visit(StoreInst i) {
    printInstructionInfo(i);
    out.printCode("movq " + + -8 * varIndexMap.get(i.getSrcValue()) + "(%rbp), %r10");

    var offset = 0;


    out.printCode("movq %r10, 0(%r11)");
  }

  public void visit(ReturnInst i) {
    printInstructionInfo(i);
    out.printCode("movq " + + -8 * varIndexMap.get(i.getReturnValue()) + "(%rbp), %rax");

  }

  public void visit(CallInst i) {

    printInstructionInfo(i);

//    out.printCode("movq $" + ((IntegerConstant) src).getValue() + ", %r10");
//  }
    String[] argReg = new String[] {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};

    int argRegCount = 0;
    for (LocalVar arg: i.getParams()) {
      out.printCode("movq " + -8 * varIndexMap.get(arg) + "(%rbp)" + ", " + argReg[argRegCount]);
      argRegCount++;
    }

    out.printCode("call _" + i.getCallee().getName());

    if(i.getDst() != null && !varIndexMap.containsKey(i.getDst())) {
      varIndex+= 1;
      varIndexMap.put(i.getDst(), varIndex);
      out.printCode("movq %rax, " +  + -8 * varIndexMap.get(i.getDst()) + "(%rbp)");

    }

  }

  public void visit(UnaryNotInst i) {
    printInstructionInfo(i);

  }


  private void printInstructionInfo(Instruction i) {
    var info = String.format("/* %s */", i.getClass().toString());
    out.printCode(info);
  }
}
