import ij.*;
import ij.process.*;
import ij.plugin.Duplicator;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import java.awt.*;
import java.util.*;

/**Cross_Fader 1.0 Michael Schmid 2014-04-22
 * Takes a two-slice stack and copies part of the second slice into the current one.
 */

public class Cross_Fader implements ExtendedPlugInFilter, DialogListener {
    private static int FLAGS =      //bitwise or of the following flags:
            STACK_REQUIRED |
	    DOES_ALL |              //this plugin processes 8-bit, 16-bit, 32-bit gray & 24-bit/pxl RGB
            KEEP_PREVIEW;           //When using preview, the preview image can be kept as a result

    private double threshold;      
    private ImageProcessor otherSlice;  //Image data of the other slice

    private ImagePlus imp;
    private ImagePlus impOriginal;


    private ImageProcessor ipPrevSlice;
    private ImageProcessor ipThisSlice;
    private ImageProcessor ipNextSlice;
    private ImageProcessor ipNew;

    private ImageStack is;
    private ImageStack is2;

    private boolean atebit = false;
    private boolean include = false;
    
    public int setup (String arg, ImagePlus imp) {
	impOriginal = new Duplicator().run(imp); // deep copy of original image
       	this.imp = imp;
        return FLAGS;
    }

    public int showDialog (ImagePlus imp, String command, PlugInFilterRunner pfr) {
        if (imp.getNSlices() < 2) {
            IJ.error("stack with more than one slice required");
            return DONE;
        }
        int currentSliceN = imp.getSlice();
        GenericDialog gd = new GenericDialog(command+"...");
        gd.addSlider("Threshold", 0.0, 255.0, 0.0);
        gd.addPreviewCheckbox(pfr);
        gd.addDialogListener(this);
        gd.showDialog();           // user input (or reading from macro) happens here
        if (gd.wasCanceled()) {    // dialog cancelled?
	    this.imp = thresholdMedian(imp, 0); // restore original image
            return DONE;
	}
        return FLAGS;              // makes the user process the slice
    }

    public boolean dialogItemChanged (GenericDialog gd, AWTEvent e) {
        threshold = gd.getNextNumber();
        return !gd.invalidNumber() && threshold>=0 && threshold <=255;
    }

    public void run (ImageProcessor ip) {
        imp = thresholdMedian(imp, threshold);
    }

    private ImagePlus thresholdMedian (ImagePlus imp, double threshold) {
	is = impOriginal.getStack();
	is2 = imp.getStack();

	// Testing
	double [] array2 = {1,3,5,6};
	IJ.log("AVG: "+mean(array2));
	IJ.log("MED: "+median(array2));

	// Thresholding
	int nslices = is2.getSize();
	IJ.log("THRESHOLD:"+threshold+"\n");	    
	for (int z=1; z<=nslices; z++) {
	    
	    try{ipPrevSlice = is.getProcessor(z-1);}catch(Exception e){ipPrevSlice = is.getProcessor(z+1);}	    	    
	    ipThisSlice = is.getProcessor(z);
	    try{ipNextSlice = is.getProcessor(z+1);}catch(Exception e){ipPrevSlice = is.getProcessor(z-1);}	    	    

	    ipNew = is2.getProcessor(z);
	    int xdim = ipNew.getWidth();
	    int ydim = ipNew.getHeight();
	    for (int x=0; x<xdim; x++) {
		for (int y=0; y<ydim; y++) {
		    double neighbors[] = new double[26];		    
		    try{neighbors[0] = ipPrevSlice.getPixel(x-1,y-1);}catch(Exception e){neighbors[0]=ipPrevSlice.getPixel(x+1,y+1);}
		    try{neighbors[1] = ipPrevSlice.getPixel(x,y-1);}catch(Exception e){neighbors[0]=ipPrevSlice.getPixel(x,y+1);}
		    try{neighbors[2] = ipPrevSlice.getPixel(x+1,y-1);}catch(Exception e){neighbors[0]=ipPrevSlice.getPixel(x-1,y+1);}
		    try{neighbors[3] = ipPrevSlice.getPixel(x-1,y);}catch(Exception e){neighbors[0]=ipPrevSlice.getPixel(x+1,y);}
		    neighbors[4] = ipPrevSlice.getPixel(x,y);
		    try{neighbors[5] = ipPrevSlice.getPixel(x+1,y);}catch(Exception e){neighbors[0]=ipPrevSlice.getPixel(x-1,y);}
		    try{neighbors[6] = ipPrevSlice.getPixel(x-1,y+1);}catch(Exception e){neighbors[0]=ipPrevSlice.getPixel(x+1,y-1);}
		    try{neighbors[7] = ipPrevSlice.getPixel(x,y+1);}catch(Exception e){neighbors[0]=ipPrevSlice.getPixel(x,y-1);}
		    try{neighbors[8] = ipPrevSlice.getPixel(x+1,y+1);}catch(Exception e){neighbors[0]=ipPrevSlice.getPixel(x-1,y-1);}

		    try{neighbors[9] = ipThisSlice.getPixel(x-1,y-1);}catch(Exception e){neighbors[0]=ipThisSlice.getPixel(x+1,y+1);}
		    try{neighbors[10] = ipThisSlice.getPixel(x,y-1);}catch(Exception e){neighbors[0]=ipThisSlice.getPixel(x,y+1);}
		    try{neighbors[11] = ipThisSlice.getPixel(x+1,y-1);}catch(Exception e){neighbors[0]=ipThisSlice.getPixel(x-1,y+1);}
		    try{neighbors[12] = ipThisSlice.getPixel(x-1,y);}catch(Exception e){neighbors[0]=ipThisSlice.getPixel(x+1,y);}
		    //neighbors[13] = ipThisSlice.getPixel(x,y); 
		    try{neighbors[14] = ipThisSlice.getPixel(x+1,y);}catch(Exception e){neighbors[0]=ipThisSlice.getPixel(x-1,y);}
		    try{neighbors[15] = ipThisSlice.getPixel(x-1,y+1);}catch(Exception e){neighbors[0]=ipThisSlice.getPixel(x+1,y-1);}
		    try{neighbors[16] = ipThisSlice.getPixel(x,y+1);}catch(Exception e){neighbors[0]=ipThisSlice.getPixel(x,y-1);}
		    try{neighbors[17] = ipThisSlice.getPixel(x+1,y+1);}catch(Exception e){neighbors[0]=ipThisSlice.getPixel(x-1,y-1);}

		    try{neighbors[18] = ipNextSlice.getPixel(x-1,y-1);}catch(Exception e){neighbors[0]=ipNextSlice.getPixel(x+1,y+1);}
		    try{neighbors[19] = ipNextSlice.getPixel(x,y-1);}catch(Exception e){neighbors[0]=ipNextSlice.getPixel(x,y+1);}
		    try{neighbors[20] = ipNextSlice.getPixel(x+1,y-1);}catch(Exception e){neighbors[0]=ipNextSlice.getPixel(x-1,y+1);}
		    try{neighbors[21] = ipNextSlice.getPixel(x-1,y);}catch(Exception e){neighbors[0]=ipNextSlice.getPixel(x+1,y);}
		    neighbors[22] = ipNextSlice.getPixel(x,y);
		    try{neighbors[23] = ipNextSlice.getPixel(x+1,y);}catch(Exception e){neighbors[0]=ipNextSlice.getPixel(x-1,y);}
		    try{neighbors[24] = ipNextSlice.getPixel(x-1,y+1);}catch(Exception e){neighbors[0]=ipNextSlice.getPixel(x+1,y-1);}
		    try{neighbors[25] = ipNextSlice.getPixel(x,y+1);}catch(Exception e){neighbors[0]=ipNextSlice.getPixel(x,y-1);}
		    try{neighbors[26] = ipNextSlice.getPixel(x+1,y+1);}catch(Exception e){neighbors[0]=ipNextSlice.getPixel(x-1,y-1);}

		    if(mean(neighbors) < threshold) {
			int i = (int) median(neighbors);
			ipNew.putPixel(x,y,i);
		    }
		    else {
			ipNew.putPixel(x,y,ipThisSlice.getPixel(x,y));
		    }
		    // if(ipThisSlice.getPixel(x,y) < threshold) {
		    // 	ipNew.putPixel(x,y,0);
		    // }
		    // else {
		    // 	ipNew.putPixel(x,y,ipThisSlice.getPixel(x,y));
		    // }
		}
	    }


	}
	return imp;
    }
    
    private double mean(double array[]){
	double sum = 0;
	for(int i = 0; i < array.length; i++) {
	    sum += array[i];
	}
	return sum/array.length;
    }
    
    private double median(double array[]){
	Arrays.sort(array);
	int len = array.length;
	if(len%2==0)return((array[(len/2)-1]+array[len/2])/2);
	else return array[((len-1)/2)];
    }

    public void setNPasses (int nPasses) {}

}
