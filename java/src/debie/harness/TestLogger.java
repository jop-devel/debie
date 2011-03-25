package debie.harness;

public class TestLogger {
	private final static String SEP1 = 
		"=============================================================================";
	private final static String SEP2 = 
		"------------------------------------------------------------------------------";
	
	void startTest(String name) {
		System.out.println();
		System.out.print("CASE: ");
		System.out.print(name);
		System.out.println(":");
		System.out.println();
	}

	public void endTestSuite(String name, TestSuite testSuite) {
		System.out.print(name);
		System.out.print(" Test Result: ");
		System.out.print(testSuite.getCheckErrors());
		System.out.print(" / ");
		System.out.print(testSuite.getChecks());
		System.out.println(" errors/total");
		System.out.println(SEP1);
	}

	void failedCheck(String msg) {
		System.out.print("failed check: ");
		System.out.println(msg);		
	}

}
