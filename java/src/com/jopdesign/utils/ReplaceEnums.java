/** Requires the following two libraries:
 *   - eclipse jdt-core
 *   - apache commons-io
 *
 *  Testing Branch/Rollback Loop:
 *   (i) git checkout -b tmp
 *   
 *   (1) try out replace enums
 *   (2) git reset --hard HEAD
 *   (3) change ReplaceEnums
 *   (4) git commit --amend ...ReplaceEnums.java
 *   (5) rinse and repeat from (1)
 */	
package com.jopdesign.utils;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.CharBuffer;
import java.util.Collection;
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

	public abstract class Pass extends DirectoryWalker<String> {
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
			setSource(cbuf);

			try {
				executePassOnFile();
			} catch (Exception e) {
				throw new IOException(e);
			}
			
			/* Rename file? Better use git for backups */

			/* Write output */
			FileWriter output = new FileWriter(inputFile);
			output.append(dest);
			output.close();
			dest = null;
		}

		public abstract void executePassOnFile() throws Exception;
	}
	
	public void runPass1(File root) throws IOException {
		new Pass(1) {
			public void executePassOnFile() throws Exception {
				executePass1();
			}
		}.runPass(root);
	}

	public void runPass2(File root) throws IOException {
		new Pass(2) {
			public void executePassOnFile() throws Exception {
				executePass2();
			}
		}.runPass(root);
	}

	private HashSet<String> enums;
	private PublicScanner scanner;
	private StringBuffer dest;
	private int offset;

	public ReplaceEnums(PublicScanner scanner) {
		this.scanner = scanner;
		this.enums = new HashSet<String>();
	}

	private void executePass1() throws InvalidInputException {

		int enumId = 0;
		
		while(! scanner.atEnd()) {
			
			if(nextToken() != TokenNameenum) continue;
			replaceToken("static class");
			/* expect identifier, recording it */
			expectNext(TokenNameIdentifier);
			enums.add(scanner.getCurrentTokenString());
			System.out.println("Found enum: "+scanner.getCurrentTokenString());
			
			expectNext(TokenNameLBRACE);
			enumId = 0;

			/* expect identifier or closing brace */ 
			int tok;
			while((tok=nextToken()) != TokenNameRBRACE) {
				/* replace 'identifier' by 'public static final int identifier' */
				expect(tok, TokenNameIdentifier);

				char[] enumIdName = scanner.getCurrentTokenSource();
				replaceToken("public static final int "+new String(enumIdName)+ " = "+enumId+";");
				enumId++;

				/* expect comma (removed) or } */
				tok = nextToken();
				if(tok == TokenNameRBRACE) break;
				expect(tok, TokenNameCOMMA);
				replaceToken("");
			}
		}
	}
	
	private void executePass2() throws InvalidInputException {

		/* XXX: Can this go wrong (horribly) ?*/
		while(! scanner.atEnd()) {
			
			if(nextToken() != TokenNameIdentifier) continue;
			
			if(enums.contains(scanner.getCurrentTokenString())) {			
				int oldStart = scanner.getCurrentTokenStartPosition();
				int oldEnd = scanner.getCurrentTokenEndPosition();
				int t2 = nextToken();
				if(t2 == TokenNameIdentifier) {
					/* MyEnum x ==> int x */
					replaceAt(oldStart,oldEnd,"int");
				} else if(t2 == TokenNameDOT) {
					/* MyEnum. [enum members] */
					expectNext(TokenNameIdentifier);
					pass2_EnumMembers(scanner.getCurrentTokenString());
				}
			}
		}
		
	}

	/* MyEnum.member */
	private void pass2_EnumMembers(String member) throws InvalidInputException {
		if(nextToken() == TokenNameDOT) {
			int memberStart = scanner.getCurrentTokenStartPosition();
			expectNext(TokenNameIdentifier);
			String fun = scanner.getCurrentTokenString();
			/* MyEnum.member.fun */
			if(fun.equals("ordinal")) {
				/* MyEnum.value.ordinal() => MyEnum.value */
				expectNext(TokenNameLPAREN);
				expectNext(TokenNameRPAREN);
				int memberEnd = scanner.getCurrentTokenEndPosition();
				replaceAt(memberStart, memberEnd, "");
			} else {
				/* Uh,uh, calling an unsupported method on an enum */
				System.err.println("Unexpected Enum operation: "+member+"."+fun);
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
