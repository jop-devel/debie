/** Requires the following two libraries:
 *   - eclipse jdt-core
 *   - apache commons-io
 *
 *  Testing Branch/Rollback Loop:
 *   (i) git checkout -b tmp
 *   
 *   (1) git commit [--amend] ...ReplaceEnums.java
 *   (2) git diff --stat  // paranoid mode
 *   (3) git reset --hard HEAD
 *   (4) try out replace enums
 *   (5) change ReplaceEnums
 *   (6) rinse and repeat from (1)
 */	
package com.jopdesign.utils;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.CharBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

import static org.eclipse.jdt.core.compiler.ITerminalSymbols.*;

import org.apache.commons.io.DirectoryWalker;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.core.util.PublicScanner;

/**
 * <p>Simple preprocessor script to replace enums with ints.
 * Uses Lexical Analysis only, and is thus not 100% accurate.
 * <emph>Use a DVCS such as git to make sure you can revert the
 * changes carried out by this script.</emph></p>
 * <p>Usage: java com.jopdesign.utils.ReplaceEnums root-directory<br/>
 *    with jdt-core and commons-io on the classpath</p>
 * 
 * @author benedikt.huber@gmail.com
 */
public class ReplaceEnums {

	public static/* */void main(String[] args) {
		if(args.length != 1) {
			System.err.println("Usage: java debie.ReplaceEnums <debie-root>");
			System.exit(0);
		}
		File debieRoot = new File(args[0]);
		PublicScanner scanner = new PublicScanner(false, false, false, ClassFileConstants.JDK1_6, null, null, false);
		scanner.diet = false;
		ReplaceEnums re = new ReplaceEnums(scanner);
		
		try {
			re.runPass1(debieRoot);
			re.runPass2(debieRoot);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public abstract static class Pass extends DirectoryWalker<String> {
		private int passId;
		public Pass(int id) {
			this.passId = id;
		}

		public void runPass(File root) throws IOException {
			super.walk(root, new HashSet<String>());
		}
		
		protected boolean handleDirectory(File directory, int depth, Collection<String> results) {
			// skip dirs starting with ., such as .git
			if (directory.getName().startsWith(".")) {
				return false;
			} else {
				return true;
			}
		}	
	    
		
		
		protected void handleFile(File inputFile,
				    int _depth,
					Collection<String> enums) throws IOException {
			if(! inputFile.toString().endsWith(".java")) return;
			/* Setup input */
			CharBuffer cbuf = CharBuffer.allocate((int)inputFile.length());
			FileReader fr = new FileReader(inputFile);
			fr.read(cbuf);
			cbuf.position(0);
			setInput(cbuf);

			try {
				executePassOnFile();
			} catch (Exception e) {
				throw new IOException(e);
			}
			
			/* Rename file? Better use git for backups */

			/* Write output */
			FileWriter output = new FileWriter(inputFile);
			output.append(getResult());
			output.close();
		}

		public abstract void executePassOnFile() throws Exception;
		public abstract void setInput(CharBuffer cbuf);
		public abstract CharSequence getResult();
	}
	

	private HashSet<String> enums;
	private PublicScanner scanner;
	private StringBuffer dest;
	private int offset;

	/* token buffer */
	private class TokenBuffer {
		public class Token {
			int name = -1, start = 0, end = 0;
			String str = "<invalid>";
		}
		private Token[] tokenBuffer;
		private int tokenPtr;

		public TokenBuffer(int size) {
			this.tokenBuffer = new Token[size];
			for(int i = 0; i < size; i++) tokenBuffer[i] = new Token();
			this.tokenPtr = 0;
		}
		public void add(int name) {
			Token current = new Token();
			current.name = name;
			current.start = scanner.getCurrentTokenStartPosition();
			current.end = scanner.getCurrentTokenEndPosition();
			try {
				current.str = scanner.getCurrentTokenString();
			} catch(StringIndexOutOfBoundsException _) {
				
			}
			--tokenPtr;
			if(tokenPtr < 0) tokenPtr = tokenBuffer.length - 1;
			this.tokenBuffer[tokenPtr] = current;
		}
		public Token current() { return get(0); }
		public Token last()    { return get(1); }
		public Token prev(int dist) { return get(2); }
		private Token get(int dist) {
			if(dist >= tokenBuffer.length) {
				throw new AssertionError("TokenBuffer: Bounded History, but requested of token t - "+dist);
			}
			return tokenBuffer[(tokenPtr+dist) % TOKEN_BUFFER_SIZE];
		}
	}
	
	public static final int TOKEN_BUFFER_SIZE = 16;
	private TokenBuffer tokenBuffer = new TokenBuffer(TOKEN_BUFFER_SIZE);
	private HashMap<String,String> enumIds;
	
	
	public ReplaceEnums(PublicScanner scanner) {
		this.scanner = scanner;
		this.enums = new HashSet<String>();
		this.enumIds = new HashMap<String,String>();
	}
	
	public abstract class ReplEnumsPass extends Pass {
		public ReplEnumsPass(int id) {
			super(id);
		}
		public CharSequence getResult() {
			return dest;
		}
		public void setInput(CharBuffer cbuf) {
			setSource(cbuf);
		}		
	}
	
	public void runPass1(File root) throws IOException {
		new ReplEnumsPass(1) {
			public void executePassOnFile() throws Exception {
				executePass1();
			}
		}.runPass(root);
	}

	public void runPass2(File root) throws IOException {
		new ReplEnumsPass(2) {
			public void executePassOnFile() throws Exception {
				executePass2();
			}
		}.runPass(root);
	}

	private void executePass1() throws InvalidInputException {

		int enumId = 0;
		
		while(! scanner.atEnd()) {
			nextToken();			
			removeOverrideAnnotation();			
			if(tokenBuffer.current().name != TokenNameenum) continue;
			
			/* check whether the enum was already declared static */
			if(tokenBuffer.last().name == TokenNamestatic || tokenBuffer.prev(2).name == TokenNamestatic) {
				replaceToken("class");
			} else {
				replaceToken("static class");
			}
			/* expect identifier, recording it */
			expectNext(TokenNameIdentifier);
			String enumName = tokenBuffer.current().str;
			enums.add(enumName);
			System.out.println("Found enum: "+scanner.getCurrentTokenString());
			
			expectNext(TokenNameLBRACE);
			enumId = 0;

			/* expect identifier or closing brace */ 
			int tok;
			while((tok=nextToken()) != TokenNameRBRACE) {
				/* replace 'identifier' by 'public static final int identifier' */
				expect(tok, TokenNameIdentifier);

				String enumIdName = tokenBuffer.current().str;
				addEnumId(enumName, enumIdName);
				replaceToken("public static final int " + enumIdName + " = "+enumId+";");
				enumId++;

				/* expect comma (removed) or } */
				tok = nextToken();
				if(tok == TokenNameRBRACE) break;
				expect(tok, TokenNameCOMMA);
				replaceToken("");
			}
		}
	}
	
	private void removeOverrideAnnotation() throws InvalidInputException {
		if(tokenBuffer.current().name != TokenNameAT) return;
		
		int rStart = tokenBuffer.current().start;
		
		if(nextToken() != TokenNameIdentifier) return;
		if(tokenBuffer.current().str.equals("Override")) {
			replaceAt(rStart, tokenBuffer.current().end, "");
		}
		nextToken();
	}

	private void addEnumId(String enumName, String enumIdName) {
		if(! this.enumIds.containsKey(enumName)) {
			this.enumIds.put(enumIdName, enumName);
		} else {
			throw new AssertionError("The same enum identifier is used twice in your program. This is not support yet.");
		}
	}

	private void executePass2() throws InvalidInputException {

		while(! scanner.atEnd()) {
			
			if(nextToken() != TokenNameIdentifier) continue;
			
			if(enums.contains(tokenBuffer.current().str)) {			
				int oldStart = scanner.getCurrentTokenStartPosition();
				int oldEnd = scanner.getCurrentTokenEndPosition();
				int t2 = nextToken();
				if(t2 == TokenNameIdentifier) {
					/* MyEnum x ==> int x */
					replaceAt(oldStart,oldEnd,"int");
				} else if(t2 == TokenNameDOT) {
					/* MyEnum. [enum members] */
				}
			} else if(tokenBuffer.last().name == TokenNamecase &&
					  enumIds.containsKey(tokenBuffer.current().str)) {
				/* Need to qualify the name, if we are in a switch statement */
				/* enum_x => MyEnum . enum_x */
				String enumId = tokenBuffer.current().str;
				replaceToken(enumIds.get(enumId) + "." + enumId);
			} else if(tokenBuffer.last().name == TokenNameDOT &&
					  tokenBuffer.current().str.equals("ordinal")) {
				/* heuristic: remove '.ordinal()' */
				/* MyEnum.value.ordinal() => MyEnum.value */
				int memberStart = tokenBuffer.last().start;
				expectNext(TokenNameLPAREN);
				expectNext(TokenNameRPAREN);
				replaceAt(memberStart, scanner.getCurrentTokenEndPosition(), "");				
			}
		}
		
	}

	
	private void setSource(CharBuffer source) {
		scanner.setSource(source.array());
		dest = new StringBuffer(source);
		offset = 0;
	}

	private void replaceToken(String replString) {
		replaceAt(scanner.getCurrentTokenStartPosition(), scanner.getCurrentTokenEndPosition(), replString);
	}
	private void replaceAt(int start, int end, String replString) {
		int lengthDiff = replString.length()-end+start-1;
		dest.replace(start + offset, end+offset+1, replString);
		offset += lengthDiff;
	}
	
	private int nextToken() throws InvalidInputException {
		int tok = scanner.getNextToken();
		tokenBuffer.add(tok);
		return tok;
	}
	
	private void expectNext(int expected) throws InvalidInputException {
		expect(nextToken(), expected);
	}
	
	private void expect(int tok, int expected) throws InvalidInputException {
		if(tok != expected) { 
			throw new InvalidInputException("scanner mismatch: got '"+scanner.getCurrentTokenString()+
					"' , #"+tok+" but expected #"+expected);
		}
	}
}
