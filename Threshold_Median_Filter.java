import ij.*;
import ij.process.*;
import ij.plugin.Duplicator;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import java.awt.*;
import java.util.*;

/** Threshold_Median_Filter - 1.1 - Talon Chandler - Takes a 2D or 3D image or
 * hyperstack and replaces pixels that have a neighborhood average less than the
 * slider threshold with the neighborhood median. The neighborhood of a pixel
 * consists of the 8 adjacent pixels in the 2D case and the 26 adjacent pixels
 * in the 3D case.
 */

public class Threshold_Median_Filter implements ExtendedPlugInFilter, DialogListener {

    private static int FLAGS =  DOES_8G | DOES_16 | DOES_32 | KEEP_PREVIEW;           

    private double threshold;      
    private ImagePlus imp;
    private ImagePlus impOriginal;
    private ImageProcessor ipPrevSlice;
    private ImageProcessor ipThisSlice;
    private ImageProcessor ipNextSlice;
    private ImageProcessor ipNew;
    private ImageStack is;
    private ImageStack is2;

    public int setup (String arg, ImagePlus imp) {
	try{impOriginal = new Duplicator().run(imp);}
	catch(Exception e){IJ.error("Please open an image."); return DONE;}
       	this.imp = imp;
        return FLAGS;
    }

    public int showDialog (ImagePlus imp, String command, PlugInFilterRunner pfr) {
        GenericDialog gd = new GenericDialog(command+"...");
        gd.addSlider("Threshold", 0.0, max(imp), 0.0);
        gd.addPreviewCheckbox(pfr);
        gd.addDialogListener(this);
        gd.showDialog();           // user input (or reading from macro) happens here
        if (gd.wasCanceled()) {    // dialog cancelled?
	    this.imp = thresholdMedian(imp, 0, false); // reset image
            return DONE;
	}
	if (gd.wasOKed()) {
	    this.imp = thresholdMedian(imp, threshold, true);
	}
        return FLAGS;              // makes the user process the slice
    }

    public boolean dialogItemChanged (GenericDialog gd, AWTEvent e) {
        threshold = gd.getNextNumber();
        return !gd.invalidNumber() && threshold>=0 && threshold <= max(imp);
    }

    public void run (ImageProcessor ip) {
	imp = thresholdMedian(imp, threshold, false);
    }

    private ImagePlus thresholdMedian (ImagePlus imp, double threshold, Boolean filterAllSlices) {
	is = impOriginal.getStack();
	is2 = imp.getStack();

	int zmin = 0;
	int nslices = 0;
	
	// Filter all slices
	if (filterAllSlices) {
	    zmin = 1;
	    nslices = is2.getSize();
	}
	// Only filter the current slice
	else { 
	    zmin = imp.getCurrentSlice();
	    nslices = zmin + 1;
	}

	for (int z=zmin; z<=nslices; z++) {
	    IJ.showProgress((double)z/nslices);
	    // Find neighborhood slices
	    ipThisSlice = is.getProcessor(z);		
	    if (nslices > 1) {
		try{ipPrevSlice = is.getProcessor(z-1);}catch(Exception e){ipPrevSlice = is.getProcessor(z+1);}	    	    
		try{ipNextSlice = is.getProcessor(z+1);}catch(Exception e){ipPrevSlice = is.getProcessor(z-1);}	    	    
	    }

	    // Find neighborhood pixels
	    ipNew = is2.getProcessor(z);
	    int xdim = ipNew.getWidth();
	    int ydim = ipNew.getHeight();
	    for (int x=0; x<xdim; x++) {
		for (int y=0; y<ydim; y++) {
 		    ArrayList<Integer> neighbors = new ArrayList<Integer>();

		    try{neighbors.add(  ipThisSlice.getPixel(x-1,y-1));}catch(Exception e){neighbors.add(  ipThisSlice.getPixel(x+1,y+1));}
		    try{neighbors.add(  ipThisSlice.getPixel(x,y-1));}catch(Exception e){neighbors.add(  ipThisSlice.getPixel(x,y+1));}
		    try{neighbors.add(  ipThisSlice.getPixel(x+1,y-1));}catch(Exception e){neighbors.add(  ipThisSlice.getPixel(x-1,y+1));}
		    try{neighbors.add(  ipThisSlice.getPixel(x-1,y));}catch(Exception e){neighbors.add(  ipThisSlice.getPixel(x+1,y));}
		    try{neighbors.add(  ipThisSlice.getPixel(x+1,y));}catch(Exception e){neighbors.add(  ipThisSlice.getPixel(x-1,y));}
		    try{neighbors.add(  ipThisSlice.getPixel(x-1,y+1));}catch(Exception e){neighbors.add(  ipThisSlice.getPixel(x+1,y-1));}
		    try{neighbors.add(  ipThisSlice.getPixel(x,y+1));}catch(Exception e){neighbors.add(  ipThisSlice.getPixel(x,y-1));}
		    try{neighbors.add(  ipThisSlice.getPixel(x+1,y+1));}catch(Exception e){neighbors.add(  ipThisSlice.getPixel(x-1,y-1));}

		    if (nslices > 1) {
			try{neighbors.add(  ipPrevSlice.getPixel(x-1,y-1));}catch(Exception e){neighbors.add(  ipPrevSlice.getPixel(x+1,y+1));}
			try{neighbors.add(  ipPrevSlice.getPixel(x,y-1));}catch(Exception e){neighbors.add(  ipPrevSlice.getPixel(x,y+1));}
			try{neighbors.add(  ipPrevSlice.getPixel(x+1,y-1));}catch(Exception e){neighbors.add(  ipPrevSlice.getPixel(x-1,y+1));}
			try{neighbors.add(  ipPrevSlice.getPixel(x-1,y));}catch(Exception e){neighbors.add(  ipPrevSlice.getPixel(x+1,y));}
			neighbors.add(  ipPrevSlice.getPixel(x,y));
			try{neighbors.add(  ipPrevSlice.getPixel(x+1,y));}catch(Exception e){neighbors.add(  ipPrevSlice.getPixel(x-1,y));}
			try{neighbors.add(  ipPrevSlice.getPixel(x-1,y+1));}catch(Exception e){neighbors.add(  ipPrevSlice.getPixel(x+1,y-1));}
			try{neighbors.add(  ipPrevSlice.getPixel(x,y+1));}catch(Exception e){neighbors.add(  ipPrevSlice.getPixel(x,y-1));}
			try{neighbors.add(  ipPrevSlice.getPixel(x+1,y+1));}catch(Exception e){neighbors.add(  ipPrevSlice.getPixel(x-1,y-1));}
			try{neighbors.add(  ipNextSlice.getPixel(x-1,y-1));}catch(Exception e){neighbors.add(  ipNextSlice.getPixel(x+1,y+1));}
			try{neighbors.add(  ipNextSlice.getPixel(x,y-1));}catch(Exception e){neighbors.add(  ipNextSlice.getPixel(x,y+1));}
			try{neighbors.add(  ipNextSlice.getPixel(x+1,y-1));}catch(Exception e){neighbors.add(  ipNextSlice.getPixel(x-1,y+1));}
			try{neighbors.add(  ipNextSlice.getPixel(x-1,y));}catch(Exception e){neighbors.add(  ipNextSlice.getPixel(x+1,y));}
			neighbors.add(  ipNextSlice.getPixel(x,y));
			try{neighbors.add(  ipNextSlice.getPixel(x+1,y));}catch(Exception e){neighbors.add(  ipNextSlice.getPixel(x-1,y));}
			try{neighbors.add(  ipNextSlice.getPixel(x-1,y+1));}catch(Exception e){neighbors.add(  ipNextSlice.getPixel(x+1,y-1));}
			try{neighbors.add(  ipNextSlice.getPixel(x,y+1));}catch(Exception e){neighbors.add(  ipNextSlice.getPixel(x,y-1));}
			try{neighbors.add(  ipNextSlice.getPixel(x+1,y+1));}catch(Exception e){neighbors.add(  ipNextSlice.getPixel(x-1,y-1));}
		    }

		    // Convert to float
		    ArrayList<Float> f_neighbors = new ArrayList<Float>();
		    for(int i = 0; i < neighbors.size(); i++) {
			int old = neighbors.get(i);
			if (ipThisSlice instanceof FloatProcessor) 
			    f_neighbors.add(Float.intBitsToFloat(old));
			else
			    f_neighbors.add((float) old);
		    }

		    // Main filter. If neighborhood mean is less than threshold,
		    // replace with the neighborhood median. Otherwise, leave 
		    // the pixel alone. 
		    if(mean(f_neighbors) < threshold) {
			int i = (int) median(f_neighbors);
			ipNew.putPixel(x,y,i);
		    }
		    else
			ipNew.putPixel(x,y,ipThisSlice.getPixel(x,y));
		}
	    }
	}
	return imp;
    }

    private float mean(ArrayList<Float> array){
	float sum = 0;
	for(int i = 0; i < array.size(); i++) {
	    sum += array.get(i);
	}
	return sum/array.size();
    }
    
    private float median(ArrayList<Float> array){
	Collections.sort(array);
	int len = array.size();
	if(len%2==0)return((array.get((len/2)-1)+array.get(len/2))/2);
	else return array.get(((len-1)/2));
    }

    // Returns the maximum pixel value in an image stack. 
    private float max(ImagePlus imp) {
	ImageStack is = imp.getStack();
	float maxPix = 0;
	float pix = 0;
	int nslices = is.getSize();
	for (int z=1; z<=is.getSize(); z++) {
	    ImageProcessor ip = is.getProcessor(z);	    
	    for (int x=0; x<ip.getWidth(); x++) {
		for (int y=0; y<ip.getHeight(); y++) {
		    if (ip instanceof FloatProcessor)
			pix = Float.intBitsToFloat(ip.getPixel(x,y));
		    else
			pix = ip.getPixel(x,y);
		    if (pix > maxPix)
			maxPix = pix;
		}
	    }
	}
	return maxPix;
    }

    public void setNPasses (int nPasses) {}
}
