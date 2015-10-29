import ij.*;
import ij.process.*;
import ij.plugin.Duplicator;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import java.awt.*;

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

    private ImageProcessor ip;
    private ImageProcessor ip2;

    private ImageStack stack;
    private ImageStack stack2;
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
	IJ.log("TESTER\n");
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

	// Thresholding
	int nslices = is2.getSize();
	IJ.log("THRESHOLD:"+threshold+"\n");	    
	for (int z=0; z<nslices; z++) {
	    ImageProcessor ip = is.getProcessor(z+1);	    
	    ImageProcessor ip2 = is2.getProcessor(z+1);
	    int xdim = ip2.getWidth();
	    int ydim = ip2.getHeight();
	    for (int x=0; x<xdim; x++) {
		for (int y=0; y<ydim; y++) {
		    if(ip.getPixel(x,y) < threshold) {
			ip2.putPixel(x,y,0);
		    }
		    else {
			ip2.putPixel(x,y,ip.getPixel(x,y));
		    }
		}
	    }


	}
	return imp;
    }

    public void setNPasses (int nPasses) {}

}
