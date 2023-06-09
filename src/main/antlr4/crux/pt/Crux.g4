grammar Crux;

program
 : declarationList EOF
 ;
literal
 : Integer
 | True
 | False
 ;

 designator
   :Identifier (OPEN_BRACKET expression0 CLOSE_BRACKET)?
   ;

type
  : Identifier
  ;
expressionList
  : (expression0 (COMMA expression0)*)?
  ;
op0
  : GREATER_EQUAL
  | LESSER_EQUAL
  | NOT_EQUAL
  | EQUAL
  | GREATER_THAN
  | LESS_THAN
  ;
op1
  : ADD
  | SUB
  | OR
  ;
op2
  : MUL
  | DIV
  | AND
  ;
expression0
  : expression1(op0 expression1)?
  ;
expression1
  : expression2
  | expression1 op1 expression2
  ;
expression2
  : expression3
  | expression2 op2 expression3
  ;

expression3
  : NOT expression3
  | OPEN_PAREN expression0 CLOSE_PAREN
  | designator
  | callExpression
  | literal
  ;
callExpression
  : Identifier OPEN_PAREN expressionList CLOSE_PAREN
  ;
declarationList
 : (declaration)*
 ;

declaration
 : variableDeclaration
 | arrayDeclaration
 | functionDefinition
 ;

variableDeclaration
 : type Identifier ';'
 ;

arrayDeclaration
  : type Identifier OPEN_BRACKET Integer CLOSE_BRACKET SemiColon
  ;
returnStatement
  : RETURN expression0 SemiColon
  ;
breakStatement
  : BREAK SemiColon
  ;
forStatement
  : FOR OPEN_PAREN assignmentStatement expression0 SemiColon assignmentStatementNoSemi CLOSE_PAREN statementBlock
  ;
ifStatement
  : IF expression0 statementBlock (ELSE statementBlock)?
  ;
assignmentStatement
  : designator ASSIGN expression0 SemiColon
  ;
assignmentStatementNoSemi
  : designator ASSIGN expression0
  ;


callStatement
  : callExpression SemiColon
  ;

statement
  : variableDeclaration
  | callStatement
  | assignmentStatement
  | ifStatement
  | forStatement
  | breakStatement
  | returnStatement
  ;
statementList
  : statement*
  ;
statementBlock
  : OPEN_BRACE statementList CLOSE_BRACE
  ;
parameter
  : type Identifier
  ;

parameterList
  : (parameter (COMMA parameter)*)?
  ;

functionDefinition
  : type Identifier OPEN_PAREN parameterList CLOSE_PAREN statementBlock
  ;

SemiColon: ';';

Integer
 : '0'
 | [1-9] [0-9]*
 ;

True: 'true';
False: 'false';
AND: '&&';
OR:	'||';
NOT:	'!';
IF:	'if';
ELSE:	'else';
FOR:	'for';
BREAK:	'break';
RETURN:	'return';
OPEN_PAREN:	'(';
CLOSE_PAREN:	')';
OPEN_BRACE:	'{';
CLOSE_BRACE:	'}';
OPEN_BRACKET:	'[';
CLOSE_BRACKET:	']';
ADD:	'+';
SUB:	'-';
MUL:	'*';
DIV:	'/';
GREATER_EQUAL:	'>=';
LESSER_EQUAL:	'<=';
NOT_EQUAL:	'!=';
EQUAL:	'==';
GREATER_THAN:	'>';
LESS_THAN:	'<';
ASSIGN:	'=';
COMMA:	',';

Identifier
 : [a-zA-Z] [a-zA-Z0-9_]*
 ;

WhiteSpaces
 : [ \t\r\n]+ -> skip
 ;

Comment
 : '//' ~[\r\n]* -> skip
 ;
