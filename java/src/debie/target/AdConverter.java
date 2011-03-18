package debie.target;

/** ported from harness/target_ad_conv.h */
public interface AdConverter {
	
	public static int BP_DOWN = 0xBF;
	
	void          updateADCChannelReg (/* unsigned char */ int channel);
	void          startConversion      ();
	/* unsigned char */ int endOfADC();
	/* unsigned char */ int getResult();
	void          setDACOutput         (/* unsigned char */ int level);

}
