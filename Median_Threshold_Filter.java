import ij.*;
import ij.process.*;
import ij.plugin.Duplicator;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import java.awt.*;
import java.util.*;

/**Median_Threshold_Filter - 1.0 - Talon Chandler 
 * Takes a 3D image and replaces pixels that have a neighborhood average less
 * than the slider threshold with the neighborhood median. The neighborhood 
 * of a pixel is a 3x3x3 cube excluding the original pixel. 
 *
 * TODO
 * - Allow 2D images
 * - Allow non 8-bit images
 */

public class Median_Threshold_Filter implements ExtendedPlugInFilter, DialogListener {
    private static int FLAGS =      //bitwise or of the following flags:
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

	// Thresholding
	int nslices = is2.getSize();
	for (int z=1; z<=nslices; z++) {
	    ipThisSlice = is.getProcessor(z);		
	    if (nslices > 1) {
		try{ipPrevSlice = is.getProcessor(z-1);}catch(Exception e){ipPrevSlice = is.getProcessor(z+1);}	    	    
		try{ipNextSlice = is.getProcessor(z+1);}catch(Exception e){ipPrevSlice = is.getProcessor(z-1);}	    	    
	    }
	    
	    ipNew = is2.getProcessor(z);
	    int xdim = ipNew.getWidth();
	    int ydim = ipNew.getHeight();
	    for (int x=0; x<xdim; x++) {
		for (int y=0; y<ydim; y++) {
 		    ArrayList<Double> neighbors = new ArrayList<Double>();

		    try{neighbors.add( (double) ipThisSlice.getPixel(x-1,y-1));}catch(Exception e){neighbors.add( (double) ipThisSlice.getPixel(x+1,y+1));}
		    try{neighbors.add( (double) ipThisSlice.getPixel(x,y-1));}catch(Exception e){neighbors.add( (double) ipThisSlice.getPixel(x,y+1));}
		    try{neighbors.add( (double) ipThisSlice.getPixel(x+1,y-1));}catch(Exception e){neighbors.add( (double) ipThisSlice.getPixel(x-1,y+1));}
		    try{neighbors.add( (double) ipThisSlice.getPixel(x-1,y));}catch(Exception e){neighbors.add( (double) ipThisSlice.getPixel(x+1,y));}
		    try{neighbors.add( (double) ipThisSlice.getPixel(x+1,y));}catch(Exception e){neighbors.add( (double) ipThisSlice.getPixel(x-1,y));}
		    try{neighbors.add( (double) ipThisSlice.getPixel(x-1,y+1));}catch(Exception e){neighbors.add( (double) ipThisSlice.getPixel(x+1,y-1));}
		    try{neighbors.add( (double) ipThisSlice.getPixel(x,y+1));}catch(Exception e){neighbors.add( (double) ipThisSlice.getPixel(x,y-1));}
		    try{neighbors.add( (double) ipThisSlice.getPixel(x+1,y+1));}catch(Exception e){neighbors.add( (double) ipThisSlice.getPixel(x-1,y-1));}

		    if (nslices > 1) {
			try{neighbors.add( (double) ipPrevSlice.getPixel(x-1,y-1));}catch(Exception e){neighbors.add( (double) ipPrevSlice.getPixel(x+1,y+1));}
			try{neighbors.add( (double) ipPrevSlice.getPixel(x,y-1));}catch(Exception e){neighbors.add( (double) ipPrevSlice.getPixel(x,y+1));}
			try{neighbors.add( (double) ipPrevSlice.getPixel(x+1,y-1));}catch(Exception e){neighbors.add( (double) ipPrevSlice.getPixel(x-1,y+1));}
			try{neighbors.add( (double) ipPrevSlice.getPixel(x-1,y));}catch(Exception e){neighbors.add( (double) ipPrevSlice.getPixel(x+1,y));}
			neighbors.add( (double) ipPrevSlice.getPixel(x,y));
			try{neighbors.add( (double) ipPrevSlice.getPixel(x+1,y));}catch(Exception e){neighbors.add( (double) ipPrevSlice.getPixel(x-1,y));}
			try{neighbors.add( (double) ipPrevSlice.getPixel(x-1,y+1));}catch(Exception e){neighbors.add( (double) ipPrevSlice.getPixel(x+1,y-1));}
			try{neighbors.add( (double) ipPrevSlice.getPixel(x,y+1));}catch(Exception e){neighbors.add( (double) ipPrevSlice.getPixel(x,y-1));}
			try{neighbors.add( (double) ipPrevSlice.getPixel(x+1,y+1));}catch(Exception e){neighbors.add( (double) ipPrevSlice.getPixel(x-1,y-1));}
			try{neighbors.add( (double) ipNextSlice.getPixel(x-1,y-1));}catch(Exception e){neighbors.add( (double) ipNextSlice.getPixel(x+1,y+1));}
			try{neighbors.add( (double) ipNextSlice.getPixel(x,y-1));}catch(Exception e){neighbors.add( (double) ipNextSlice.getPixel(x,y+1));}
			try{neighbors.add( (double) ipNextSlice.getPixel(x+1,y-1));}catch(Exception e){neighbors.add( (double) ipNextSlice.getPixel(x-1,y+1));}
			try{neighbors.add( (double) ipNextSlice.getPixel(x-1,y));}catch(Exception e){neighbors.add( (double) ipNextSlice.getPixel(x+1,y));}
			neighbors.add( (double) ipNextSlice.getPixel(x,y));
			try{neighbors.add( (double) ipNextSlice.getPixel(x+1,y));}catch(Exception e){neighbors.add( (double) ipNextSlice.getPixel(x-1,y));}
			try{neighbors.add( (double) ipNextSlice.getPixel(x-1,y+1));}catch(Exception e){neighbors.add( (double) ipNextSlice.getPixel(x+1,y-1));}
			try{neighbors.add( (double) ipNextSlice.getPixel(x,y+1));}catch(Exception e){neighbors.add( (double) ipNextSlice.getPixel(x,y-1));}
			try{neighbors.add( (double) ipNextSlice.getPixel(x+1,y+1));}catch(Exception e){neighbors.add( (double) ipNextSlice.getPixel(x-1,y-1));}
		    }
		    
		    if(mean(neighbors) < threshold) {
			int i = (int) median(neighbors);
			ipNew.putPixel(x,y,i);
		    }
		    else {
			ipNew.putPixel(x,y,ipThisSlice.getPixel(x,y));
		    }
		}
	    }
	}
	return imp;
    }
    
    private double mean(ArrayList<Double> array){
	double sum = 0;
	for(int i = 0; i < array.size(); i++) {
	    sum += array.get(i);
	}
	return sum/array.size();
    }
    
    private double median(ArrayList<Double> array){
	Collections.sort(array);
	int len = array.size();
	if(len%2==0)return((array.get((len/2)-1)+array.get(len/2))/2);
	else return array.get(((len-1)/2));
    }

    public void setNPasses (int nPasses) {}
}
