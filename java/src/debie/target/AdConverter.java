package debie.target;

/** ported from harness/target_ad_conv.h */
public interface AdConverter {
	
	/** Number of possible ADC channels (includes GND channels) */
	public static final int AD_CHANNELS = 0x28;
	
	public static final int BP_UP   = 0x40;
	public static final int BP_DOWN = 0xBF;
	
	int getConfirmHitResult();
	void setConfirmHitResult(int value);
	
	void clearADConverting();
	
	int getADCChannelRegister();
	void setADCChannelRegister(int channel);

	void          updateADCChannelReg (/* unsigned char */ int channel);
	void          startConversion      ();
	/* unsigned char */ int endOfADC();
	/* unsigned char */ int getResult();
	void          setDACOutput         (/* unsigned char */ int level);

}
