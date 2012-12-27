package ch.fhnw.cbip.compiler.generator;

import java.util.ArrayList;
import java.util.HashMap;

import ch.fhnw.cbip.compiler.error.GenerationError;
import ch.fhnw.cbip.compiler.parser.AbsTree.*;
import ch.fhnw.cbip.compiler.scanner.enums.Terminal;

/**
 * This class generates vm code from a abstract syntax tree
 * 
 * @author Jan Faessler <jan.faessler@students.fhnw.ch>
 */
public class CodeGenerator {
	
	/**
	 * The starting point from tha abstract syntax tree
	 */
	private final Program tree;
	
	/**
	 * The counter of the current line 
	 */
	private Integer lineCounter = 0;
	
	/**
	 * The code in a string
	 */
	private StringBuilder code = new StringBuilder();
	
	/**
	 * The storage for the addresses of the variables
	 */
	private HashMap<String,Integer> variables = new HashMap<String,Integer>();
	
	/**
	 * A multi dimension counter of commands
	 */
	private ArrayList<Integer> cmdCounter = new ArrayList<Integer>();
	
	/**
	 * The state of addLine if the line will added to the string or we just count the commands
	 */
	private boolean countingState = false;
	
	/**
	 * Constructor of the code generator
	 * @param Program The Abstract Tree that should be generated.
	 */
	public CodeGenerator(Program tree) {
		this.tree = tree;
	}
	
	/**
	 * This function starts the generation process
	 * @return String with the vm code
	 * @throws GenerationError
	 */
	public String generate() throws GenerationError {

		Decl declaration = tree.getDeclarations();
		Cmd commands = tree.getCommands();

		// do store declarations
		int storeCount = 0;
		Decl currentDecl = declaration;
		while (currentDecl != null) {
			if (declaration instanceof DeclStore) {
				variables.put(((DeclStore) declaration).getIdent().getName(), storeCount);
				storeCount++;
			}

			currentDecl = currentDecl.getNextDecl();
		}
		
		addLine("Alloc", storeCount);
		
		// do commands
		Cmd currentCmd = commands;
		while (currentCmd != null) {
			buildCommands(currentCmd);
			currentCmd = currentCmd.getNextCmd();
		}
		
		addLine("Stop");
		
		// do fun declarations
		currentDecl = declaration;
		while (currentDecl != null) {
			if (declaration instanceof DeclFun) buildDeclFun(declaration);

			currentDecl = currentDecl.getNextDecl();
		}
		
		// do proc declarations
		currentDecl = declaration;
		while (currentDecl != null) {
			if (declaration instanceof DeclProc) buildDeclProc(declaration);
			currentDecl = currentDecl.getNextDecl();
		}
		
		// TODO: replace routine call addresses
		
		// remove the last comma
		code.delete(code.length() - 2, code.length() - 1);
		
		// return the code in a string
		return code.toString();
	}
	
	/**
	 * This builds the code for all kind of commands
	 * @param Cmd from the Abstract Tree
	 * @throws GenerationError
	 */
	private void buildCommands(Cmd cmd) throws GenerationError {
		
		// assignment of a expression to a variable
		if (cmd instanceof CmdAssi) buildCmdAssi((CmdAssi) cmd);
		
		// code for a if/else condition
		else if (cmd instanceof CmdCond) buildCmdCond((CmdCond) cmd);
		
		// code for a user input
		else if (cmd instanceof CmdInput) buildCmdInput((CmdInput) cmd);
		
		// code for a output to the console
		else if (cmd instanceof CmdOutput) buildCmdOutput((CmdOutput) cmd);
		
		// code for a call of a procedure
		else if (cmd instanceof CmdProcCall) buildCmdProcCall((CmdProcCall) cmd);
		
		// jump to the next command.
		else if (cmd instanceof CmdSkip) buildCmdSkip((CmdSkip) cmd);
		
		// code for a while loop
		else if (cmd instanceof CmdWhile) buildCmdWhile((CmdWhile) cmd);
		
		else throw new GenerationError("unknown Command");
	}
	
	/**
	 * This build the code for a assignment of a expression to a variable
	 * @param Cmd from the Abstract Tree
	 * @throws GenerationError
	 */
	private void buildCmdAssi(CmdAssi cmd) throws GenerationError {
		if (cmd.getTargetExpr() instanceof ExprStore) {
			// resolve the source expression
			resolveExpression(cmd.getSourceExpr());
			
			// get the address for the target variable
			String variableName = ((ExprStore) cmd.getTargetExpr()).getIdent().getName();
			addLine("IntLoad", variables.get(variableName));
			
			// store the source in the target variable
			addLine("Store");
		} else throw new GenerationError("wrong target expression for Cmd Assi");
	}
	
	/**
	 * This build the code for a if/else condition
	 * @param Cmd from the Abstract Tree
	 * @throws GenerationError
	 */
	private void buildCmdCond(CmdCond cmd) throws GenerationError {
		
		// count the commands from the if part
		startCountingState();
		Cmd currentCmd = cmd.getIfCmd();
		while (currentCmd != null) {
			buildCommands(currentCmd);
			currentCmd = currentCmd.getNextCmd();
		}
		Integer cmdIfCount = stopCountingState();
		
		// count the commands from the else part
		startCountingState();
		currentCmd = cmd.getElseCmd();
		while (currentCmd != null) {
			buildCommands(currentCmd);
			currentCmd = currentCmd.getNextCmd();
		}
		Integer cmdElseCount = stopCountingState();
		
		// jump to the else part when the expression is false
		resolveExpression(cmd.getExpr());
		addLine("CondJump", lineCounter + cmdIfCount + 2);
		
		// build if commands
		buildCommands(cmd.getIfCmd());
		addLine("UncondJump", lineCounter + cmdElseCount + 1);
		
		// build else commands
		buildCommands(cmd.getElseCmd());
	}
	
	/**
	 * This builds the code for a user input
	 * @param Cmd from the Abstract Tree
	 */
	private void buildCmdInput(CmdInput cmd) {
		
		ExprStore expr = (ExprStore) cmd.getExpr();
		String variableName = expr.getIdent().getName();
		
		addLine("IntLoad", variables.get(variableName));
		addLine("IntInput", variableName);
	}
	
	/**
	 * This builds the code for a output to the console
	 * @param Cmd from the Abstract Tree
	 */
	private void buildCmdOutput(CmdOutput cmd) {
		
		ExprStore expr = (ExprStore) cmd.getExpr();
		String variableName = expr.getIdent().getName();
		
		addLine("IntLoad", variables.get(variableName));
		addLine("Deref");
		addLine("IntOutput", variableName);
	}
	
	/**
	 * This builds the code for a call of a procedure
	 * @param Cmd from the Abstract Tree
	 * @throws GenerationError
	 */
	private void buildCmdProcCall(CmdProcCall cmd) throws GenerationError {
		addLine("Alloc", 0);
		ExprList currentList = cmd.getRoutineCall().getExprList();
		while (currentList != null) {
			resolveExpression(currentList.getExpr());
			currentList = currentList.getExprList();
		}
		addLine("Call", getCallReplacement(cmd.getRoutineCall().getIdent().getName()));
	}
	
	/**
	 * This builds a jump to the next command. Used for a Condition Command if the else command isn't needed.
	 * @param Cmd from the Abstract Tree
	 */
	private void buildCmdSkip(CmdSkip cmd) {
		addLine("UncondJump", lineCounter + 1);
	}
	
	/**
	 * This builds the code for a while loop
	 * @param  Cmd from the Abstract Tree
	 * @throws GenerationError
	 */
	private void buildCmdWhile(CmdWhile cmd) throws GenerationError {
		
		// count the commands in the while loop
		startCountingState();
		Cmd currentCmd = cmd.getCmd();
		while (currentCmd != null) {
			buildCommands(currentCmd);
			currentCmd = currentCmd.getNextCmd();
		}
		Integer cmdCount = stopCountingState();
		
		// jump out of the wile when the expression is false
		resolveExpression(cmd.getExpr());
		addLine("CondJump", lineCounter + cmdCount + 1);
		
		// build the commands
		currentCmd = cmd.getCmd();
		while (currentCmd != null) {
			buildCommands(currentCmd);
			currentCmd = currentCmd.getNextCmd();
		}
	}
	
	/**
	 * This builds the code for the expressions recursively
	 * @param expr
	 * @throws GenerationError
	 */
	private void resolveExpression(Expr expr) throws GenerationError {
		if (expr instanceof ExprDyadic) {
			ExprDyadic e = (ExprDyadic) expr;
			if (e.getOperator().getTerminal() == Terminal.BOOLOPR) {
				
				// resolve expression 1
				resolveExpression(e.getExpr1());
				
				// count the commands for expression 2
				startCountingState();
				resolveExpression(e.getExpr2());
				Integer cmdCount = stopCountingState();
				
				// jump if expression 1 is false
				addLine("CondJump", lineCounter + cmdCount + 1);
				
				// resolve expression 2
				resolveExpression(e.getExpr2());
				
			} else if (e.getOperator().getTerminal() == Terminal.ADDOPR || 
					   e.getOperator().getTerminal() == Terminal.MULTOPR ||
					   e.getOperator().getTerminal() == Terminal.RELOPR) {
				
				resolveExpression(e.getExpr1());
				resolveExpression(e.getExpr2());
				
				switch (String.valueOf(e.getOperator().getAttribute())) {
					case "PLUS": addLine("IntPlus"); break;
					case "MINUS": addLine("IntMinus"); break;
					case "TIMES": addLine("IntMult"); break;
					case "DIV": addLine("IntDiv"); break;
					case "MOD": addLine("IntMod"); break;
					default: addLine("Int" + e.getOperator().getAttribute()); break;
				}
			} 
			else throw new GenerationError("unknown terminal for a Dyadic Expression");
		}
		else if (expr instanceof ExprFunCall) {
			ExprFunCall e = (ExprFunCall) expr;
			addLine("Alloc", 1);
			ExprList currentList = e.getRoutineCall().getExprList();
			while (currentList != null) {
				resolveExpression(currentList.getExpr());
				currentList = currentList.getExprList();
			}
			addLine("Call", getCallReplacement(e.getRoutineCall().getIdent().getName()));
		} 
		else if (expr instanceof ExprLiteral) {
			ExprLiteral e = (ExprLiteral) expr;
			addLine("IntLoad", e.getLiteral().getIntVal());
		} 
		else if (expr instanceof ExprMonadic) {
			// TODO: ExprMonadic
		} 
		else if (expr instanceof ExprStore) {
			ExprStore e = (ExprStore) expr;			
			addLine("IntLoad", variables.get(e.getIdent().getName()));
			addLine("Deref");
		}
		else throw new GenerationError("unknown expression");
	}

	private void buildDeclFun(Decl dcl) {
		// TODO: fun decleration
	}
	
	private void buildDeclProc(Decl dcl) {
		// TODO: proc decleration
	}

	/**
	 * This adds a line of code with a command string
	 * @param String of the command
	 */
	private void addLine(String cmd) {
		addLine(cmd, "");
	}
	
	/**
	 * This adds a line of code with a command string and a int value for a param
	 * @param String of the command
	 * @param Integer value as param
	 */
	private void addLine(String cmd, Integer param) {
		addLine(cmd, String.valueOf(param));
	}
	
	/**
	 * This adds a line of code and a string of params
	 * @param String of the command
	 * @param String of the params
	 */
	private void addLine(String cmd, String params) {
		if (countingState) cmdCounter.set(cmdCounter.size() - 1, cmdCounter.get(cmdCounter.size() - 1) + 1);
		else {
			code.append("(");
			code.append(lineCounter);
			code.append(',');
			code.append(cmd);
			if (params.length() > 0) {
				code.append(' ');
				code.append(params);
			}
			code.append("),\n");
			lineCounter++;
		}
	}
	
	/**
	 * Increase the level of command counting
	 */
	private void startCountingState() {
		cmdCounter.add(0);
		countingState = true;
	}
	
	/**
	 * Decrease the level of command counting
	 * @return Integer of the amount of the commands from the top level
	 */
	private Integer stopCountingState() {
		Integer result = cmdCounter.get(cmdCounter.size() - 1);
		cmdCounter.remove(cmdCounter.size() - 1);
		if (cmdCounter.size() > 0) cmdCounter.set(cmdCounter.size() - 1, cmdCounter.get(cmdCounter.size() - 1) + result);
		else countingState = false;
		return result;
	}
	
	/**
	 * Returna replacement string for a routine
	 * @param String of the name of the routine
	 * @return String of the replacement
	 */
	private String getCallReplacement(String name) { return ">>" + name + "<<"; }
}