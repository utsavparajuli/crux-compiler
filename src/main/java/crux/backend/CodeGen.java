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

  private Instruction lastInst;

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
    varIndexMap = new HashMap<>();
    varIndex = 0;

    String[] argReg = new String[] {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9", "16(%rbp)", "24(%rbp)", "32(%rbp)"};
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
      if (argRegCount > 5) {
        out.printCode("movq " + argReg[argRegCount] + ", %r10");
        out.printCode("movq %r10, " + -8 * varIndexMap.get(lv) + "(%rbp)");
      }
      else
      {
        out.printCode("movq " + argReg[argRegCount] + ", " + -8 * varIndexMap.get(lv) + "(%rbp)");
      }
      argRegCount++;
    }

    var startInst = f.getStart();

    instStack.push(startInst);

    Instruction prev = null;
    ArrayList<Instruction> visited = new ArrayList<>();

    Instruction next_false = null;
    Instruction next_true = null;

    while(!instStack.isEmpty()) {
      var cur = instStack.pop();

      if(lastBlock != null) {
        if(cur.equals(lastBlock) && prev.numNext() == 0) {
          out.printCode("leave");
          out.printCode("ret");
        }
      }

      if(visited.contains(cur) && labelMap.get(cur) != null) {
        out.printCode("jmp _" + labelMap.get(cur));

        next_false = null;
      }

      if(!visited.contains(cur)) {
        cur.accept(this);
        visited.add(cur);
        next_false = cur.getNext(0);
        next_true = cur.getNext(1);
      }


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

      prev = cur;
    }

    //epilogue
    if(!thenBlock) {
      out.printCode("leave");
      out.printCode("ret");
    }
  }

  public void visit(AddressAt i) {
    printInstructionInfo(i);


    if(labelMap.containsKey(i)) {
      out.printLabel("_" + labelMap.get(i) + ":");
    }

    var offset = i.getOffset();


    out.printCode("movq " + i.getBase().getName() + "@GOTPCREL(%rip), %r11");

    if (offset != null) {
      out.printCode("movq " + -8 * varIndexMap.get(offset)+"(%rbp)" + ", %r10");
      out.printCode("imulq $8, %r10");
      out.printCode("addq %r10, %r11");
    }


  }

  public void visit(BinaryOperator i) {
    printInstructionInfo(i);

    LocalVar lhs = i.getLeftOperand();
    LocalVar rhs = i.getRightOperand();


    out.printCode("movq " + -8 * varIndexMap.get(lhs) + "(%rbp)" + ", %r10");

    if (!varIndexMap.containsKey(i.getDst())) {
      varIndex += 1;
      varIndexMap.put(i.getDst(), varIndex);
    }

    switch (i.getOperator().toString()) {
      case "Add":
        out.printCode("addq " + -8 * varIndexMap.get(rhs) + "(%rbp)" + ", %r10");
        out.printCode("movq %r10, " + -8 * varIndexMap.get(i.getDst()) + "(%rbp)");

        break;
      case "Sub":
        out.printCode("subq " + -8 * varIndexMap.get(rhs) + "(%rbp)" + ", %r10");
        out.printCode("movq %r10, " + -8 * varIndexMap.get(i.getDst()) + "(%rbp)");

        break;
      case "Div":
        out.printCode("movq " + -8 * varIndexMap.get(lhs) + "(%rbp)" + ", %rax");
        out.printCode("cqto");
        out.printCode("idivq " + -8 * varIndexMap.get(rhs) + "(%rbp)");
        out.printCode("movq %rax, " + -8 * varIndexMap.get(i.getDst()) + "(%rbp)");
        break;
      case "Mul":
        out.printCode("imul " + -8 * varIndexMap.get(rhs) + "(%rbp)" + ", %r10");
        out.printCode("movq %r10, " + -8 * varIndexMap.get(i.getDst()) + "(%rbp)");

        break;
    }


//    if (!varIndexMap.containsKey(i.getDst())) {
//      varIndex += 1;
//      varIndexMap.put(i.getDst(), varIndex);
//    }

  }

  public void visit(CompareInst i) {

    printInstructionInfo(i);
    out.printCode("movq $0, %rax");
    out.printCode("movq $1, %r10");
    out.printCode("movq " + -8 * varIndexMap.get(i.getLeftOperand()) + "(%rbp), %r11");
    out.printCode("cmp " + -8 * varIndexMap.get(i.getRightOperand()) + "(%rbp), %r11");

    switch (i.getPredicate().toString()) {
      case "GT":
        out.printCode("cmovg %r10, %rax");
        break;
      case "LT":
        out.printCode("cmovl %r10, %rax");
        break;
      case "LE":
        out.printCode("cmovle %r10, %rax");
        break;
      case "GE":
        out.printCode("cmovge %r10, %rax");
        break;
      case "NE":
        out.printCode("cmovne %r10, %rax");
        break;
      case "EQ":
        out.printCode("cmove %r10, %rax");
        break;
    }
    varIndex += 1;
    varIndexMap.put(i.getDst(), varIndex);
    out.printCode("movq %rax, " + -8 * varIndexMap.get(i.getDst()) + "(%rbp)");

  }

  public void visit(CopyInst i) {
    printInstructionInfo(i);


    var src = i.getSrcValue();
    if(src.getClass().equals(IntegerConstant.class)) {
      out.printCode("movq $" + ((Long) ((IntegerConstant) src).getValue()).intValue() + ", %r10");
    }
    else if(src.getClass().equals(BooleanConstant.class)) {
      var boolInt = ((BooleanConstant) src).getValue() ? 1 : 0;
      out.printCode("movq $" + boolInt + ", %r10");
    }
    else if(src.getClass().equals(LocalVar.class)) {
      out.printCode("movq " + -8 * varIndexMap.get(src) + "(%rbp), %r10");
    }

    if (!varIndexMap.containsKey(i.getDstVar())) {
      varIndex += 1;
      varIndexMap.put(i.getDstVar(), varIndex);
    }

    out.printCode("movq " + "%r10, " + -8 * varIndexMap.get(i.getDstVar()) + "(%rbp)");
  }

  public void visit(JumpInst i) {
    printInstructionInfo(i);
    out.printCode("movq " + -8 * varIndexMap.get(i.getPredicate()) + "(%rbp), " + "%r10");

    out.printCode("cmp $1, %r10");

    out.printCode("je _" + labelMap.get(i.getNext(1)));

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
    String[] argReg = new String[] {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9", "16(%rbp)", "24(%rbp)", "32(%rbp)"};

    int argRegCount = 0;
    for (LocalVar arg: i.getParams()) {
      if (argRegCount > 5) {
        out.printCode("movq " + -8 * varIndexMap.get(arg) + "(%rbp)" + ", %r10");
        out.printCode("movq %r10, " + argReg[argRegCount]);
      }
      else
      {
        out.printCode("movq " + -8 * varIndexMap.get(arg) + "(%rbp)" + ", " + argReg[argRegCount]);
      }

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


    varIndex+= 1;
    varIndexMap.put(i.getDst(), varIndex);

    out.printCode("movq $1, %r11");
    out.printCode("subq " + -8 * varIndexMap.get(i.getDst()) + "(%rbp), %r11");
  }


  private void printInstructionInfo(Instruction i) {
    var info = String.format("/* %s */", i.getClass().toString());
    out.printCode(info);
  }
}
