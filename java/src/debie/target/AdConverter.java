package debie.target;

/** ported from harness/target_ad_conv.h */
public interface AdConverter {
	
	void          updateADC_ChannelReg (/* unsigned char */ int channel);
	void          startConversion      ();
	/* unsigned char */ int End_Of_ADC();
	/* unsigned char */ int getResult();
	void          setDAC_Output         (/* unsigned char */ int level);

}
