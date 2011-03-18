package debie.harness;

public class TestLogger {
	private static String SEP1 = 
		"=============================================================================";
	private static String SEP2 = 
		"------------------------------------------------------------------------------";
	
	void startTest(String name) {
		System.out.print("Running Test: ");
		System.out.println(name);
		if(Harness.TRACE) System.out.println(SEP2);
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
