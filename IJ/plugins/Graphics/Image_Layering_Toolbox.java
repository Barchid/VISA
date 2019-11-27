import javax.swing.*;
import javax.swing.event.*;
import java.awt.event.*;
import ij.*;
import ij.plugin.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.util.*;
import ij.plugin.filter.Analyzer;
import java.text.*;



/** 
 *   Image Layering Toolbox Plugin for ImageJ:
 *     This plugin creates a new image frame which
 *     maintains an image composed of other open images
 *     layered together. It also generates a control panel
 *     to provide access to layer properties. 
 * 
 *    NOTE: REQUIRES JAVA 1.4 
 *  
 *    AUTHOR
 *    @author Karl Schmidt kfschmidt@bwh.harvard.edu
 *    @version 0.9 Beta rel. 05/20/02
 *
 *    REVISIONS:  (Please note revisions to private 
 *                 classes here as well)
 * 
 *    05/22/02 - Added multicolor Contour map; needs reimplementation
 *    06/05/02 - Reimplemented Contour map, Released as 1.0
 *    06/13/02 - Fixed bug in contour mode w/ images which contain 
 *               negative values
 *
 *
 */
public class Image_Layering_Toolbox 
    implements PlugIn, Runnable
{

    private static Thread thisILTThread;
    private static ILTControlPanel mCP;
    private static ILTImageManager mIM;
    private static SortedSet mLayers;
    private static boolean mPerformUpdateAtNextPass;
    private boolean mKeepRunning;
    private static final String NO_ROI_SELECTED_MSG =
	"Please open an image and select an roi on the Layered Image Window";
    private static final String NO_IMAGE_OPEN_MSG = 
	"Please open some images before adding new layers";
    private static final String CANT_DEMOTE_MSG = 
	"Less than 2 elements in set, can't demote";
    private static final String CANT_PROMOTE_MSG = 
	"Less than 2 elements in set, can't promote";
    
    private int mLastMouseDownX;
    private int mLastMouseDownY;
    private int mOrigLayerXOffset;
    private int mOrigLayerYOffset;
    private Layer mCurCPSelectedLayer;

    public Image_Layering_Toolbox() {
	// constructor tasks: check singleton 
	//    create control panel frame & show
	//    create layer image & show in new window
	//    start control panel thread
	
	if (thisILTThread == null) {
	    thisILTThread = new Thread(this); 
	    thisILTThread.start();
	    System.out.println("New Image_Layering_Toolbox"+
			       " thread created ");
	}
    }




    /**
     *  runs when the thread is created,
     *  loops until finished
     *
     */
    public synchronized void run() {
	int q = 0;
	init();
	mKeepRunning = true;
		
	while (mKeepRunning) {
	    try {
		if (mPerformUpdateAtNextPass || (q%100) == 0) {
		    q=0;
		    updateImage();
		}
		q++;
		Thread.sleep(20);
	    } catch (Exception e) {
		System.out.print("Unhandled Exception in "+
				 "Image Layering Toolbox main thread: "+e);
		e.printStackTrace();
	    }
	}
	
	// check for image saves?
	mIM = null;
	mLayers = null;
	mCP.dispose();
	mCP = null;
	thisILTThread = null;
	System.out.println("Image Layering Toolbox main thread exited");
    }



    // ================ ILT USER ACTION METHODS ================

    public void promoteLayer() {
	promoteLayer(mCurCPSelectedLayer);
    }

    public void promoteLayer(Layer l) {
	if (mLayers.size()<2) {
	    IJ.showMessage(CANT_PROMOTE_MSG);
	    return;
	}

	Layer l1 = null;
	SortedSet tail = mLayers.tailSet(l);

	tail.remove(l);
	l1 = ((Layer) tail.first());
	mLayers.remove(l1);
	mLayers.remove(l);
	l1.incrementOrder();
	l.decrementOrder();
	mLayers.add(l1);
	mLayers.add(l);
	mPerformUpdateAtNextPass = true;
	updateControlPanel();
    }

    public void demoteLayer() {
	demoteLayer(mCurCPSelectedLayer);
    }

    public void demoteLayer(Layer l) {
	if (mLayers.size()<2) {
	    IJ.showMessage(CANT_DEMOTE_MSG);
	    return;
	}

	Layer l1 = null;
	SortedSet head = mLayers.headSet(l);

	l1 = ((Layer) head.last());
	mLayers.remove(l1);
	mLayers.remove(l);
	l1.decrementOrder();
	l.incrementOrder();
	mLayers.add(l1);
	mLayers.add(l);
	mPerformUpdateAtNextPass = true;
	updateControlPanel();
    }

    public void delLayer() {
	delLayer(mCurCPSelectedLayer);
    }

    public void delLayer(Layer l) {
	mLayers.remove(l);
	Iterator i = mLayers.iterator();
	TreeSet newset = new TreeSet();
	int p = 0;
	while (i.hasNext())
	    {
		newset.add(((Layer)i.next()).setOrder(p));
		p++;
	    }
	if (mCurCPSelectedLayer == l) mCurCPSelectedLayer = null;
	mPerformUpdateAtNextPass = true;
	updateControlPanel();
    }



    /**
     *   Utility for taking roi measurements based on layer
     *   alignmnents and an ROI drawn on the Layered Image itself
     * 
     *   Measures the roi for each layer of the
     *   layered image, as the roi appears to be located on 
     *   the layer (ie, including x-y offsets)
     *     
     *
     */
    public void calcRoi() {
	System.out.println("ILT calcRoi()");

	// create a new ImageStack w/ each of our
	// images, aligned as they are in the layer view 
	
	Iterator it = mLayers.iterator();
	Layer tmpl = null;
	int s = 0;
	int width = mIM.getIP().getWidth();
	int height = mIM.getIP().getHeight();
	String results_msg = "Results Table Entries:\n\n";

	while (it.hasNext()) {
	    tmpl = (Layer) it.next();

	    // create a new image for manipulating the results
	    ImagePlus img =  NewImage.createFloatImage(tmpl.getName()+"_calcROI",
						       width, 
						       height, 
						       1, NewImage.FILL_BLACK 
						       );


	    // set the images pixel array
	    try {
		img.getProcessor().
		    setPixels(getLayerAsFloatPixelArrayWithOffsets(tmpl, 
								   width, 
								   height));
	    } catch (Exception e) {
		System.out.println("Exception in calcRoi(): "+e);
		return;
	    }


	    // copy the roi
	    if (mIM.getIP() == null || mIM.getIP().getRoi() == null) {
		IJ.showMessage(NO_ROI_SELECTED_MSG);
		return;
	    }

	    img.setRoi(mIM.getIP().getRoi());

	    // create a new analyzer, calc, and display the results
	    Analyzer a = new Analyzer(img);
	    a.run(img.getProcessor());
	    ResultsTable rt = a.getResultsTable();
	    int counter = rt.getCounter();
	    results_msg += "   "+counter+" -- "+tmpl.getName()+"\n"; 
	    System.out.println("Measured layer "+tmpl.getName());
	    s++;
	}
	IJ.showMessage(results_msg);

    }




    /**
     *
     *
     *
     */
    public void saveLayer() 
    {
	saveLayer(mCurCPSelectedLayer);

    }



    /**
     *  saves the layer (including offsets) to a new Image
     *
     */
    public ImagePlus saveLayer(Layer l) {

      // create a new image for holding the results, 
      ImagePlus result =  
	NewImage.createFloatImage(l.getName()+"_NEW",
				  l.getImagePlus().getWidth(), 
				  l.getImagePlus().getHeight(), 
				  1, NewImage.FILL_BLACK 
				  );	

      ImageStack stack = new ImageStack(l.getImagePlus().getWidth(),
					   l.getImagePlus().getHeight());
      
      
      for (int q = 1; q <= l.getImagePlus().getStackSize(); q++) {
	
	// create a new image for holding each slice results, 
	ImagePlus tim =  
	  NewImage.createFloatImage(l.getName()+"_NEW",
				    l.getImagePlus().getWidth(), 
				    l.getImagePlus().getHeight(), 
				    1, NewImage.FILL_BLACK 
				    );	

	FloatProcessor ip = (FloatProcessor) tim.getProcessor();
	ImageProcessor orig = l.getImagePlus().getStack().getProcessor(q);

	int width = l.getImagePlus().getWidth();
	int height = l.getImagePlus().getHeight();
	
	// copy the pixels (with offset) from the orig layer
	for (int x = 0; x<width; x++) {
	    for (int y = 0; y<height; y++) {
		if ((x+l.getXOffset()) > 0 && 
		    (x+l.getXOffset()) <= width && 
		    (y+l.getYOffset()) > 0 && 
		    (y+l.getYOffset()) <= height)
		    {
			ip.putPixelValue(x+l.getXOffset(), 
					 y+l.getYOffset(), 
					 orig.getPixelValue(x, y));
		    }
	    }
	}

	// copy the threshold  and min-max levels from layer
	ip.setThreshold(l.getImagePlus().getProcessor().getMinThreshold(),
			l.getImagePlus().getProcessor().getMaxThreshold(),
			ImageProcessor.NO_LUT_UPDATE);
	if(l.getImagePlus().getProcessor().isInvertedLut()) ip.invertLut();

	ip.setMinAndMax(l.getImagePlus().getProcessor().getMin(),
			 l.getImagePlus().getProcessor().getMax());

	stack.addSlice(tim.getTitle(), tim.getProcessor());
	
      }

      // show the new window
      result.setStack(result.getTitle(), stack);
      result.show();
      return result;
    }



    /**
     *   adds a layer
     *
     */
    public void addLayer() 
    {

	// prompt the user with a list of images
        int[] wList = WindowManager.getIDList();
        if (wList==null) {
            IJ.showMessage("Layering Error", NO_IMAGE_OPEN_MSG);

            return;
        }

	String[] titles = new String[wList.length];
	for (int i=0; i<wList.length; i++) {
	    ImagePlus imp = WindowManager.getImage(wList[i]);
	    if (imp!=null)
		titles[i] = imp.getTitle();
            else 
                titles[i] = "";
        } 
	
	// create the display dialog
	GenericDialog gd = new GenericDialog("New Layer Selection");
	gd.addMessage("Choose the image to add as a new layer");
	gd.addChoice(" ", titles, titles[0]);

	// get the image 
        gd.showDialog();
        if (gd.wasCanceled()) return;	
        int i = gd.getNextChoiceIndex();
        ImagePlus s1 = WindowManager.getImage(wList[i]);

	// create a layer from this image
	Layer l = new Layer(s1);

	// can we add this layer?
	try {
	    checkNewLayer(l);
	} catch (Exception e) {
	    System.out.println("ADD LAYER EXCEPTION: ");
	}

	// Housekeeping: do we have a layers set already? if not create it
	TreeSet tmp = new TreeSet();
	Iterator it = null;
	if (mLayers == null){ mLayers = tmp;}
	else {   
	    // shuffle the order of all layers 
	    // in the set up by 1 to make
	    // room for this new layer at 0
	    it = mLayers.iterator();
	    while (it.hasNext()) {
		tmp.add(((Layer) it.next()).incrementOrder());
	    }
	    mLayers = tmp;
	}
	mLayers.add(l);
	
	// do we have an existing image manager?
	// if not, create a new one and update
	if (mIM == null) mIM = new ILTImageManager (this, l);
	mPerformUpdateAtNextPass = true;
	updateControlPanel();

    }


    // =============== ILT EVENT HANDLING ======================

    /**
     *   handles an actions and events sent from the control
     *   panel itself
     *
     */
    public void handleCPEvent(int cp_event) 
    {
	if (cp_event == ILTControlPanel.ADD_LAYER) {
	    addLayer();
	} else 	if (cp_event == ILTControlPanel.DEL_LAYER) {
	    delLayer();
	} else 	if (cp_event == ILTControlPanel.CALC_ROI) {
	    calcRoi();
	} else 	if (cp_event == ILTControlPanel.SHUT_DOWN) {
	    mKeepRunning = false;
	} else 	if (cp_event == ILTControlPanel.PROMOTE_LAYER) {
	    promoteLayer();
	} else 	if (cp_event == ILTControlPanel.DEMOTE_LAYER) {
	    demoteLayer();
	} else 	if (cp_event == ILTControlPanel.SAVE_AS) {
	    saveLayer();
	} else {
	    System.out.print ("UNHANDLED CP EVENT: "+cp_event);
	}
    }

    /**
     *   handles actions performed on layers in the control
     *   panel 
     *
     */
    public void handleCPEventOnLayer(int event, Layer l) 
    {

	if (event == LayerDisplayPanel.ALPHA_CHANGE) {
	    mPerformUpdateAtNextPass = true;
	} else 	if (event == LayerDisplayPanel.LAYER_SELECTED) {
	    System.out.println("ILT new Layer selected in CP");
	    mCurCPSelectedLayer = l;
	} else 	if (event == LayerDisplayPanel.ORDER_CHANGE) {
	    updateControlPanel();
	    mPerformUpdateAtNextPass = true;
	} else 	if (event == LayerDisplayPanel.USE_CUSTOM_COLOR_CHANGE) {
	    System.out.println("Custom color change");
	    mPerformUpdateAtNextPass = true;
	} else 	if (event == LayerDisplayPanel.COLOR_BURN_SELECTED) {
	    System.out.println("Color burn overlay selected");
	    mPerformUpdateAtNextPass = true;
	} else 	if (event == LayerDisplayPanel.CONTOUR_SELECTED) {
	    System.out.println("Contour map overlay selected");
	    mPerformUpdateAtNextPass = true;
	} else 	if (event == LayerDisplayPanel.CONTOUR_MIN_CHANGE) {
	    mPerformUpdateAtNextPass = true;
	} else 	if (event == LayerDisplayPanel.CONTOUR_MAX_CHANGE) {
	    mPerformUpdateAtNextPass = true;
	} else {
	    System.out.print ("UNHANDLED LAYER EVENT: "+event);
	}
    }


    /**
     *   Handles mouse actions on the Layer image
     *
     */
    public void handleMouseActionOnImage(int code, int x, int y) {
	if (code == ILTImageManager.MOUSE_DOWN) {
	    mLastMouseDownX = x;
	    mLastMouseDownY = y;
	    if (mCurCPSelectedLayer != null) {
		mOrigLayerXOffset = mCurCPSelectedLayer.getXOffset();
		mOrigLayerYOffset = mCurCPSelectedLayer.getYOffset();
	    }
	} else if (code == ILTImageManager.MOUSE_DRAG) {
	    if (mCurCPSelectedLayer != null && 
		mCurCPSelectedLayer.getIsTranslating()) 
		{
		    mCurCPSelectedLayer.setXOffset
			(mOrigLayerXOffset + x -mLastMouseDownX);
		    mCurCPSelectedLayer.setYOffset
			(mOrigLayerYOffset + y - mLastMouseDownY);
		    mPerformUpdateAtNextPass = true;  
		}
	} else if (code == ILTImageManager.MOUSE_UP) {
	    if (mCurCPSelectedLayer != null && 
		mCurCPSelectedLayer.getIsTranslating()) 
		{
		    mCurCPSelectedLayer.setXOffset
			(mOrigLayerXOffset + x -mLastMouseDownX);
		    mCurCPSelectedLayer.setYOffset
			(mOrigLayerYOffset + y - mLastMouseDownY);
		    mPerformUpdateAtNextPass = true;  
		    mCurCPSelectedLayer.setIsTranslating(false);
		    mCP.updateLayerControlState();
		}
	}

 
    }


    // ===================  ILT SUPPORT METHODS ==========



    /** 
     *    copies the original pixel values (cast to floats) 
     *    from the image in layer l 
     *    to the same canvas space as the layered image (including 
     *    the layers x,y offsets). Then returns this image's pixels 
     *    for composition in a stack roi calculation.
     *
     *    @param l      The layer of interest
     *    @param width  The width of the Layered Image 
     *    @param height The height of the Layered Image
     *
     */
    private float[] getLayerAsFloatPixelArrayWithOffsets(Layer l, 
							 int width, 
							 int height) 
	throws Exception 
    {

	// tmp image for painting purposes
	ImagePlus tim =  NewImage.createFloatImage("tmp_pixel_map",
						   width, 
						   height, 
						   1, NewImage.FILL_BLACK 
						   );

	FloatProcessor ti = (FloatProcessor) tim.getProcessor();

	// orig image for copying from
	if (l.getImagePlus() == null) { 
	    IJ.showMessage("Layer "+l.getName()+" is EMPTY! Did you close the image?");
	    throw new Exception("No image found for layer "+l.getName());
	}


	ImagePlus ipl = l.getImagePlus();
	ImageProcessor img = ipl.getProcessor();
	double px = 0D;

	for (int x=0; x<width;x++) {
	    for (int y=0; y<height;y++) {
		px = (double) img.getPixelValue(x, y);
		if ((x + l.getXOffset()) < width && (x + l.getXOffset()) > 0 &&
		    (y + l.getYOffset()) < height && (y + l.getYOffset()) > 0) 
		    {
			ti.putPixelValue(x + l.getXOffset(),
				    y + l.getYOffset(),
				    px);
		    }
	    }
	}
	return (float[]) ti.getPixels();
    }


    /**
     *   updates the image
     */
    private void updateImage() {
	if (mIM != null) {
	mIM.updateLayers(mLayers);
	mPerformUpdateAtNextPass = false;
	}
    }

    /**
     *   updates the control panel
     */
    private void updateControlPanel() {
	System.out.println("updateControlPanel()");
	mCP.updateLayers(mLayers);
    }


    /**
     *   runs when the plugin is selected 
     *   from the menu
     *
     */
    public void run (String argv) {
	new Image_Layering_Toolbox();
    }




    /**
     * Validate that this layer can be added, 
     *  (ie. not duplicate, etc)
     *
     */
    private void checkNewLayer(Layer l) 
    throws Exception 
    {

    }



    /**
     *  run when the plugin first starts
     *
     */
    private void init() {
	// create the CP
	System.out.println("Initializing ILT");
	mCP = new ILTControlPanel(this);
    }    

} 






/**
 *   CLASS: Control Panel class for use with Image Layering Toolbox
 *   plugin
 *
 */
class ILTControlPanel 
    extends JFrame
    implements ActionListener
{
    private Image_Layering_Toolbox mParent;
    private JPanel mLayersPanel;
    private int mWidth = 250;
    private LayerDisplayPanel mCurSelectedLayer;
    private LayerDisplayPanel mCurMouseInLayer;
    private LayerDisplayPanel mAllLayerPanels[];
    private JButton addbutton;
    private JButton delbutton;
    private JButton promotebutton;
    private JButton demotebutton;
    private JButton roibutton;
    private JButton saveasbutton;

    public static final int ADD_LAYER = 1;
    public static final int DEL_LAYER = 2;
    public static final int CALC_ROI = 3;
    public static final int SHUT_DOWN = 4;
    public static final int PROMOTE_LAYER = 5;
    public static final int DEMOTE_LAYER = 6;
    public static final int SAVE_AS = 7;
    
    public ILTControlPanel (Image_Layering_Toolbox p) {
	mParent = p;
	System.out.println("ILTControlPanel created");
	init();
    }


    // =============  PUBLIC METHODS ================

    /**
     *  requests an update of the existing 
     *  layers' control states
     */
    public void updateLayerControlState() {
	for (int q=0; q<mAllLayerPanels.length; q++) {
	    mAllLayerPanels[q].syncControls();
	}
    }


    /**
     *  Updates the control panel with a new complete
     *  layer set
     */
    public void updateLayers(SortedSet layers) {
	mAllLayerPanels = new LayerDisplayPanel[layers.size()];
	int p = 0;
	resetLayerPanel(layers.size());
	Iterator i = layers.iterator();
	while (i.hasNext()) {
	    mAllLayerPanels[p] = new LayerDisplayPanel((Layer) i.next(), this);
	    p++;
	}
	
	// for a more logical top-to-bottom layer order
	for (int x=mAllLayerPanels.length-1; x>=0; x--) {
	    mLayersPanel.add(mAllLayerPanels[x]);
	}
	show();
    }



    /**
     *  Called by LayerDisplay 
     *
     */
    public void userActionOnLayer(int actioncode, Layer l) {
	mParent.handleCPEventOnLayer(actioncode, l);
    }

    /**
     *  gets called from the LayerDisplayPanels regarding
     *  layer selection activities
     *
     */
    public void layerSelectionAction(LayerDisplayPanel lp, int code) {
	if (code == LayerDisplayPanel.LAYER_MOUSE_OVER) {
	    if (lp != mCurMouseInLayer) {
		mCurMouseInLayer = lp;
		updateLayerHighlights();
	    }
	} else if (code == LayerDisplayPanel.LAYER_SELECTED) {
	    if (lp != mCurSelectedLayer) {
		mCurMouseInLayer = lp;
		mCurSelectedLayer = lp;
		updateLayerHighlights();
		
		// notify the parent
		mParent.handleCPEventOnLayer(LayerDisplayPanel.LAYER_SELECTED,
					     mCurSelectedLayer.getLayer());
	    }
	} 
    }



    /**
     *   sets the correct border highlight on 
     *   LayerDisplayPanels based on mouse position & 
     *   user selection
     */
    private void updateLayerHighlights() {

	for (int p = 0; p<mAllLayerPanels.length;p++) {
	    if (mAllLayerPanels[p] == mCurSelectedLayer && 
		mAllLayerPanels[p] == mCurMouseInLayer) {
		mAllLayerPanels[p].setBorderToSelectedAndIn();
	    } else if (mAllLayerPanels[p] == mCurMouseInLayer) {
		mAllLayerPanels[p].setBorderToRolledOver();
	    } else if (mAllLayerPanels[p] == mCurSelectedLayer) {
		mAllLayerPanels[p].setBorderToSelected();
	    } else {
		mAllLayerPanels[p].setBorderToNormal();
	    }
	}
	
    }




    // ========= CP EVENT HANDLING ===================

    /**
     *  Called by buttons on toolbar
     *
     */
    public void actionPerformed(ActionEvent ae) {
	if (ae.getSource() == addbutton) {
	    mParent.handleCPEvent(ADD_LAYER);
	} else if (ae.getSource() == delbutton) {
	    mParent.handleCPEvent(DEL_LAYER);
	} else if (ae.getSource() == promotebutton) {
	    mParent.handleCPEvent(PROMOTE_LAYER);
	} else if (ae.getSource() == demotebutton) {
	    mParent.handleCPEvent(DEMOTE_LAYER);
	} else if (ae.getSource() == roibutton) {
	    mParent.handleCPEvent(CALC_ROI);
	} else if (ae.getSource() == saveasbutton) {
	    mParent.handleCPEvent(SAVE_AS);
	} else {
	    System.out.println("Unhandled Control Panel action: "+ae);
	}

    }



    //  ========   HELPER  ==================


    private JButton setupButton (String over_icon_gif,
				 String off_icon_gif,
				 String disabled_icon_gif,
				 String on_icon_gif,
				 int width,
				 int height,
				 String tooltip_text)
    {
	JButton b = new JButton(new ImageIcon(off_icon_gif));
	b.setRolloverEnabled(true);
	b.setToolTipText(tooltip_text);

	b.setDisabledIcon(new ImageIcon(disabled_icon_gif));
	b.setRolloverIcon(new ImageIcon(over_icon_gif));
	b.setPressedIcon(new ImageIcon(on_icon_gif));

	b.setMaximumSize(new Dimension(width, height));
	b.setMinimumSize(new Dimension(width, height));

	b.addActionListener(this);
	return b;
    }



    /**
     *  Initialize the display
     *
     */
    private void init() {

	this.setTitle("Layer Control Panel");

	// create some new buttons  for the toolbar 	
	addbutton = setupButton ("plugins/plugin_images/add_layer_over.gif",
				 "plugins/plugin_images/add_layer_off.gif",
				 "plugins/plugin_images/add_layer_off.gif",
				 "plugins/plugin_images/add_layer_over.gif",
				 20,
				 20,
				 "Add a new Layer");

	delbutton = setupButton ("plugins/plugin_images/del_layer_over.gif",
				 "plugins/plugin_images/del_layer_off.gif",
				 "plugins/plugin_images/del_layer_off.gif",
				 "plugins/plugin_images/del_layer_over.gif",
				 20,
				 20,
				 "Delete a Layer");
	
	promotebutton = setupButton ("plugins/plugin_images/promote_over.gif",
				     "plugins/plugin_images/promote_off.gif",
				     "plugins/plugin_images/promote_off.gif",
				     "plugins/plugin_images/promote_over.gif",
				     20,
				     20,
				     "<html>Promote the order of"+
				     " <br>the selected layer</html>");
	
	demotebutton = setupButton ("plugins/plugin_images/demote_over.gif",
				    "plugins/plugin_images/demote_off.gif",
				    "plugins/plugin_images/demote_off.gif",
				    "plugins/plugin_images/demote_over.gif",
				    20,
				    20,
				    "<html>Demote the order of"+
				    " <br>the selected layer</html>");
	
	
	roibutton = setupButton ("plugins/plugin_images/calc_roi_over.gif",
				 "plugins/plugin_images/calc_roi_off.gif",
				 "plugins/plugin_images/calc_roi_off.gif",
				 "plugins/plugin_images/calc_roi_over.gif",
				 20,
				 20,
				 "<html>Calculate this ROI <br> for all layers</html>");
	

	saveasbutton = setupButton ("plugins/plugin_images/saveas_over.gif",
				     "plugins/plugin_images/saveas_off.gif",
				     "plugins/plugin_images/saveas_off.gif",
				     "plugins/plugin_images/saveas_over.gif",
				     20,
				     20,
				     "<html>Save layer <br>(with offsets) <br>to new Image</html>");



	// create the toolbar
	JToolBar toolBar = new JToolBar();
	toolBar.setMargin(new Insets(1,1,1,1));
	toolBar.setPreferredSize(new Dimension(mWidth, 30));
	// toolBar.setRollover(true); // java 1.3 incompatible

	toolBar.add(addbutton);
	toolBar.add(delbutton);
	toolBar.addSeparator();
	toolBar.add(promotebutton);
	toolBar.add(demotebutton);
	toolBar.addSeparator();
	toolBar.addSeparator();
	toolBar.add(saveasbutton);
	toolBar.add(roibutton);

	// init the panel
	mLayersPanel = new JPanel();

	// add the panel and toolbar
	getContentPane().add(toolBar, BorderLayout.NORTH);
	getContentPane().add(mLayersPanel, BorderLayout.CENTER);

	setSize(200, 190);
	show();

	//Add a listener for window events
	addWindowListener(new WindowAdapter() {
		public void windowIconified(WindowEvent e) {
		   
		}
		public void windowDeiconified(WindowEvent e) {
		   
		}
		public void windowClosing(WindowEvent e) {
		    mParent.handleCPEvent(SHUT_DOWN);
		}  
	    });
	
    }

    /**
     *  resets the LayerPanel to an empty grid of layers size
     */
    private void resetLayerPanel(int size) {
	int s = 3;
	if (size > 3) s = size;

	// clear out the layers panel
	// init grid layout w/ 1 col, and size rows
	mLayersPanel.removeAll();
	GridLayout grid = new GridLayout (s,1,1,1);
	mLayersPanel.setLayout(grid);
	setSize(200, 150+s*LayerDisplayPanel.PREF_HEIGHT);
	mLayersPanel.repaint();

    }
}




/**
 *   CLASS: This class is responsible for the layer information panel
 *   displayed in the ControlPanel
 *
 */
class LayerDisplayPanel 
    extends JPanel 
    implements ChangeListener, ActionListener, MouseListener
{
    
    public static final int ALPHA_CHANGE = 1;
    public static final int ORDER_CHANGE = 3;
    public static final int TRANSLATE_ON = 5;
    public static final int TRANSLATE_OFF = 6;
    public static final int USE_CUSTOM_COLOR_CHANGE = 7;
    public static final int LAYER_SELECTED = 8;    
    public static final int LAYER_MOUSE_OVER = 9;
    public static final int COLOR_BURN_SELECTED = 10;
    public static final int CONTOUR_SELECTED = 11;
    public static final int CONTOUR_MIN_CHANGE = 12;
    public static final int CONTOUR_MAX_CHANGE = 13;

    private static final String TRANS_TT = 
	"Translate this layer with respect to other layers";
    private static final String CONTOUR_MODE_TT =
	"<html>Contour mode with Legend<br> (White is transparent)</html>";
    private static final String COLOR_BURN_MODE_TT =
	"Color burn mode, no legend";
    private static final String CUSTOM_COLORS_TT =
	"<html>Toggle the use of<br>"+
	"original image colors,<br>"+
	"or user specified</html>";
    private static final String ALPHA_TT = 
	"Change the layer Alpha";
    private static final String CONTOUR_MIN_TT = "Colorbar MIN";
    private static final String CONTOUR_MAX_TT = "Colorbar MAX";

    public static int PREF_HEIGHT = 30;
    public static int mSmallButtonHeight = 16;
    public static int mSmallButtonWidth = 16;
    
    private Layer  mLayer;
    private ILTControlPanel mParent;

    // state variables
    private int mDisplayMode; // 1 = COLOR_BURN, 2 - CONTOUR
    private boolean mIsCurrentlySelected;
    private double mMinMaxOffset;
    private double mMinMaxScaleFactor;


    // controls
    private JCheckBox translate;
    private JLabel nametag;
    private JSlider alpha;
    private JButton color1;
    private JButton color2;
    private JCheckBox useCustomColors;
    private JRadioButton cb_radio;
    private JRadioButton opaque_radio;
    private JSlider contour_min;
    private JSlider contour_max;

    public LayerDisplayPanel (Layer l, ILTControlPanel p)  
    {
	System.out.println("LayerDisplayPanel created for layer: "+
			   l.getName());
	mParent = p;
	mLayer = l;
	mIsCurrentlySelected = false;
	mDisplayMode = 0;
	initControls();
	initContourScalingFactors();
	checkLayout();
	syncControls();

    }


    // ========== PUBLIC METHODS ============

    /**
     *   Updates each control in the layerdisplaypanel 
     *   w/ the actual Layer setting, does not
     *   sets enabled/disabled based on selection
     */
    public void syncControls() {
	alpha.setValue((int) (mLayer.getAlpha()*100));
	updateContourSliderWithLayerVal(contour_min);
	updateContourSliderWithLayerVal(contour_max);
	if (mLayer.getOverlayMode() == 
	    Layer.COLOR_BURN) cb_radio.setSelected(true);
	else cb_radio.setSelected(false);
	if (mLayer.getOverlayMode() == Layer.CONTOUR) 
	    opaque_radio.setSelected(true);
	else opaque_radio.setSelected(false);
	if (mLayer.getUseCustomColors()) useCustomColors.setSelected(true);
	else useCustomColors.setSelected(false);
	if (mLayer.getIsTranslating()) translate.setSelected(true);
	else translate.setSelected(false);
	
	if (mIsCurrentlySelected) {
	    this.setBorder(BorderFactory.createLineBorder(Color.red, 2));
	    translate.setEnabled(true);
	    useCustomColors.setEnabled(true);
	    color1.setEnabled(true);
	    color2.setEnabled(true);
	    alpha.setEnabled(true);
	    cb_radio.setEnabled(true);
	    opaque_radio.setEnabled(true);
	    contour_min.setEnabled(true);
	    contour_max.setEnabled(true);
	} else {
	    this.setBorder(BorderFactory.createLineBorder(Color.gray, 1));
	    translate.setEnabled(false);
	    useCustomColors.setEnabled(false);
	    color1.setEnabled(false);
	    color2.setEnabled(false);
	    alpha.setEnabled(false);
	    cb_radio.setEnabled(false);
	    opaque_radio.setEnabled(false);
	    contour_min.setEnabled(false);
	    contour_max.setEnabled(false);
	}
	updateColorComponents();
    }

    public Layer getLayer() {
	return mLayer;
    }

    public void setBorderToSelected() {
	this.setBorder(BorderFactory.createLineBorder(Color.red, 2));
	mIsCurrentlySelected = true;
	syncControls();
    }

    public void setBorderToSelectedAndIn() {
	this.setBorder(BorderFactory.createLineBorder(Color.red, 3));
	mIsCurrentlySelected = true;
	syncControls();
    }
    
    public void setBorderToRolledOver() {
	this.setBorder(BorderFactory.createLineBorder(Color.blue, 1));
    }




    public void setBorderToNormal()
    {
	this.setBorder(BorderFactory.createLineBorder(Color.gray, 1));
	mIsCurrentlySelected = false;
	syncControls();
    }



    // ===========  LAYER DISPLAY PANEL event handling ================

    /**
     *  Called by the translate and color select buttons
     *  
     */
    public void actionPerformed(ActionEvent ae) {
	// test to see what the user has done
	if (ae.getSource() == translate) {
	    mLayer.setIsTranslating(translate.isSelected());
	} else if (ae.getSource() == color1) {
	    try {
		mLayer.setColor1(JColorChooser.showDialog(this, 
							  "Select Color 1",
							  mLayer.getColor1()));
		pushEventToCP(USE_CUSTOM_COLOR_CHANGE);
	    } catch (Exception e) {
		System.out.println("LayerDisplayPanel exception "+
				   "while choosing color");
	    }
	} else if (ae.getSource() == color2) {
	    try {
		mLayer.setColor2(JColorChooser.showDialog(this, 
							  "Select Color 2",
							  mLayer.getColor2()));
		pushEventToCP(USE_CUSTOM_COLOR_CHANGE);
	    } catch (Exception e) {
		System.out.println("LayerDisplayPanel exception "+
				   "while choosing color");
	    }
	} else if (ae.getSource() == useCustomColors) {
	    if (useCustomColors.isSelected()) {
		// enable the color pickers
		mLayer.setUseCustomColors(true);
		pushEventToCP(USE_CUSTOM_COLOR_CHANGE);
	    } else {
		// disable the color pickers
		mLayer.setUseCustomColors(false);
		pushEventToCP(USE_CUSTOM_COLOR_CHANGE);
	    }
	    updateColorComponents();
	} else if (ae.getSource() == cb_radio) {
	    if (cb_radio.isSelected()) {
		// set the color burn overlay
		mLayer.setOverlayMode(Layer.COLOR_BURN);
		pushEventToCP(COLOR_BURN_SELECTED);
		checkLayout();
	    }
	} else if (ae.getSource() == opaque_radio) {
	    if (opaque_radio.isSelected()) {
		// set the color burn overlay
		mLayer.setOverlayMode(Layer.CONTOUR);
		pushEventToCP(CONTOUR_SELECTED);
		checkLayout();
	    }
	} 
	else {
	    System.out.println("Unhandled actionEvent"+
			       " at LayerDisplayPanel:"+ae);
	}
	syncControls();
    }


    private void checkLayout() {
	int real_mode = mLayer.getOverlayMode();
	if (real_mode == Layer.CONTOUR && mDisplayMode != 1) 
	    {
		layoutContour();
	    }
	else if (real_mode == Layer.COLOR_BURN && mDisplayMode != 2) 
	    {
		layoutColorBurn();
	    }
    }

    private void initContourScalingFactors() {
	double min = mLayer.getImagePlus().getProcessor().getMin();
	double max = mLayer.getImagePlus().getProcessor().getMax();
	
	mMinMaxScaleFactor = (max - min) / (double) 100;
	mMinMaxOffset = min;

    }


    /**
     *   Handles 
     *
     */
    private void updateContourSliderWithLayerVal(JSlider js) 
    {
	if (js == contour_min) {
	    contour_min.setValue((int) 
				 ((mLayer.getContourPlotMinVal()-mMinMaxOffset)
				  /mMinMaxScaleFactor)
				 );
	}
	
	else if (js == contour_max) {
	    contour_max.setValue((int) 
				 ((mLayer.getContourPlotMaxVal()-mMinMaxOffset)
				  /mMinMaxScaleFactor)
				 );
	}
	else {
	    System.out.println("Problem: not a contour slider: "+js);
	}
    }




    private void updateLayerWithContourSliderVal(JSlider js) 
    {

	if (js == contour_min) {
	    mLayer.setContourPlotMinVal((float) (mMinMaxOffset + 
					mMinMaxScaleFactor*
					(float)contour_min.getValue()));
	}

	else if (js == contour_max) {
	    mLayer.setContourPlotMaxVal((float) (mMinMaxOffset + 
					mMinMaxScaleFactor*
					(float)contour_max.getValue()));
	}
	else {
	    System.out.println("Problem: not a contour slider: "+js);
	}
    }



    /**
     *  Called by sliders
     *
     */
    public void stateChanged(ChangeEvent e) {
	if (e.getSource() == alpha) {
	    mLayer.setAlpha
		((float) 
		 (((JSlider)e.getSource()).getValue()/100F)
		);
	    pushEventToCP(ALPHA_CHANGE);
	} else 	if (e.getSource() == contour_min) {
	    updateLayerWithContourSliderVal(contour_min);
	    pushEventToCP(CONTOUR_MIN_CHANGE);
	} else 	if (e.getSource() == contour_max) {
	    updateLayerWithContourSliderVal(contour_max);
	    pushEventToCP(CONTOUR_MAX_CHANGE);
	} else {
	    System.out.println("Unhandled stateChanged event"+
			       " at LayerDisplayPanel:"+e);
	}
	syncControls();
    }




    /**
     *  mouse event handles
     *
     *
     */
    public void mouseClicked(MouseEvent e) { 
	mParent.layerSelectionAction(this, LayerDisplayPanel.LAYER_MOUSE_OVER);
    }

    public void mouseExited(MouseEvent e) {	
    }

    public void mouseEntered(MouseEvent e) { 
	mParent.layerSelectionAction(this, LayerDisplayPanel.LAYER_MOUSE_OVER);
    }

    public void mousePressed(MouseEvent e) { 
    }

    public void mouseReleased(MouseEvent e) { 
	mParent.layerSelectionAction(this, LayerDisplayPanel.LAYER_SELECTED);
    }


    // =========== HELPER METHODS ============

    /**
     *   utility method to pass code & layer back to ILT via CP
     *
     */
    private void pushEventToCP(int code) {
	mParent.userActionOnLayer(code, mLayer);
    }


    private void updateColorComponents() {
	color1.setEnabled(mLayer.getUseCustomColors());
	color2.setEnabled(mLayer.getUseCustomColors());
	color1.setBackground(mLayer.getColor1());
	color2.setBackground(mLayer.getColor2());
    }


    private void setSmallButtonDimensions(JComponent c) {
	c.setPreferredSize(new Dimension(mSmallButtonWidth,mSmallButtonHeight));
	c.setMaximumSize(new Dimension(mSmallButtonWidth,mSmallButtonHeight));
	c.setMinimumSize(new Dimension(mSmallButtonWidth,mSmallButtonHeight));
    }

    private void layoutColorBurn() {
	mDisplayMode = 2;

	System.out.println("layoutColorBurn()");
	this.removeAll();
	this.setBorder(BorderFactory.createLineBorder(Color.gray));
	this.setBackground(Color.white);
	GridBagLayout gridbag = new GridBagLayout();
	this.setLayout(gridbag);
	GridBagConstraints c = new GridBagConstraints();
	// assemble the components

	// nametag
	c.gridx = 1; c.gridy=0;
	c.gridwidth = 4; c.gridheight = 1;
	c.insets = new Insets(5,1,5,1);
	c.anchor=GridBagConstraints.NORTHWEST;
	gridbag.setConstraints(nametag, c);
	nametag.setMaximumSize(new Dimension(100, 10));
	nametag.setMinimumSize(new Dimension(100, 10));
	this.add(nametag);
	
	// add a panel for the radio & translate buttons
	Panel p = new Panel();
	GridLayout gl = new GridLayout();
	p.setLayout(gl);
	c.weightx=1;
	c.anchor=GridBagConstraints.NORTHEAST;
	c.gridwidth=3;c.gridx=4;c.gridy=0;
	gridbag.setConstraints(p, c);
	this.add(p);

	// contour radio
	p.add(opaque_radio);

	// color_burn mode radio
	p.add(cb_radio);

	// translate
	p.add(translate);

	// useCustomColors
	c.gridx=0; c.gridy = 0;
	c.gridwidth=1; c.anchor=GridBagConstraints.NORTH;
	gridbag.setConstraints(useCustomColors, c);
	this.add(useCustomColors);

	// colors & alpha slider
	c.weightx=0.5;
	c.anchor=GridBagConstraints.EAST;
	c.gridwidth=1;c.gridx=0;c.gridy=1;
	gridbag.setConstraints(color2, c);
	this.add(color2);

	c.gridwidth=1;c.gridx=7;c.gridy=1;
	c.weightx = 0.5; c.weighty=1.0;
	c.anchor=GridBagConstraints.WEST;
	gridbag.setConstraints(color1, c);
	this.add(color1);

	c.weightx = 0.3; c.weighty=1.0;
	c.gridwidth=6;c.gridx=1;c.gridy=1;
	c.fill=GridBagConstraints.HORIZONTAL;
	c.anchor=GridBagConstraints.CENTER;
	gridbag.setConstraints(alpha, c);
	this.add(alpha);	
	this.show();
    }


    private void layoutContour() {
	mDisplayMode = 1;
	System.out.println("layoutContour()");
	this.removeAll();
	this.setBorder(BorderFactory.createLineBorder(Color.gray));
	this.setBackground(Color.white);
	GridBagLayout gridbag = new GridBagLayout();
	this.setLayout(gridbag);
	GridBagConstraints c = new GridBagConstraints();
	// assemble the components

	// empty placeholder label
	JLabel pl = new JLabel("  ");
	c.gridx = 0; c.gridy=0;
	c.gridwidth = 1; c.gridheight = 1;
	c.anchor=GridBagConstraints.NORTHWEST;
	gridbag.setConstraints(pl, c);
	this.add(pl);

	// nametag
	c.gridx = 1; c.gridy=0;
	c.gridwidth = 4; c.gridheight = 1;
	c.insets = new Insets(5,1,5,1);
	c.anchor=GridBagConstraints.NORTHWEST;
	gridbag.setConstraints(nametag, c);
	nametag.setMaximumSize(new Dimension(100, 10));
	nametag.setMinimumSize(new Dimension(100, 10));
	this.add(nametag);
	
	// add a panel for the radio & translate buttons
	Panel p2 = new Panel();
	GridLayout gl2 = new GridLayout();
	p2.setLayout(gl2);
	c.weightx=1;
	c.anchor=GridBagConstraints.NORTHEAST;
	c.gridwidth=3;c.gridx=4;c.gridy=0;
	gridbag.setConstraints(p2, c);
	this.add(p2);

	// contour radio
	p2.add(opaque_radio);

	// color_burn mode radio
	p2.add(cb_radio);

	// translate
	p2.add(translate);

	// add a panel for the min,max slider
	Panel p = new Panel();
	GridLayout gl = new GridLayout();
	p.setLayout(gl);
	c.weightx=1;
	c.fill=GridBagConstraints.HORIZONTAL;
	c.gridwidth=8;c.gridx=0;c.gridy=1;
	gridbag.setConstraints(p, c);
	this.add(p);

	// contour_min slider
	contour_min.setMinimumSize
	    (new Dimension(30,20));
	contour_min.setMaximumSize
	    (new Dimension(30,20));
	p.add(contour_min);

	// contour_max slider
	contour_max.setMinimumSize
	    (new Dimension(30,20));
	contour_max.setMaximumSize
	    (new Dimension(30,20));
	p.add(contour_max);

	this.show();
    }


    private void initControls() {
	// tranlslation check box
	Icon translate_off_icon = 
	    new ImageIcon ("plugins/plugin_images/translate_off.gif");
	Icon translate_on_icon = 
	    new ImageIcon ("plugins/plugin_images/translate_on.gif");
	Icon translate_over_icon = 
	    new ImageIcon ("plugins/plugin_images/translate_over.gif");
	Icon translate_disabled = 
	    new ImageIcon("plugins/plugin_images/translate_disabled.gif");
	translate = new JCheckBox(translate_off_icon);
	translate.setBackground(Color.white);
	translate.setToolTipText(TRANS_TT);
	translate.setRolloverEnabled(true);
	translate.setRolloverIcon(translate_over_icon);
	translate.setSelectedIcon(translate_on_icon);
	translate.setDisabledIcon(translate_disabled);
	translate.setBorderPainted(false);

	// radio components 
	opaque_radio = new JRadioButton();
	opaque_radio.setToolTipText(CONTOUR_MODE_TT);

	cb_radio = new JRadioButton();
	cb_radio.setToolTipText(COLOR_BURN_MODE_TT);
	opaque_radio.setBackground(Color.white);
	cb_radio.setBackground(Color.white);
	cb_radio.
	    setMaximumSize
	    (new Dimension(mSmallButtonWidth,mSmallButtonHeight));
	cb_radio.
	    setMinimumSize
	    (new Dimension(mSmallButtonWidth,mSmallButtonHeight));
	opaque_radio.
	    setMaximumSize
	    (new Dimension(mSmallButtonWidth,mSmallButtonHeight));
	opaque_radio.
	    setMinimumSize
	    (new Dimension(mSmallButtonWidth,mSmallButtonHeight));

	// color components
	useCustomColors = new JCheckBox();
	useCustomColors.setToolTipText(CUSTOM_COLORS_TT);
	useCustomColors.setBackground(Color.white);
	color1 = new JButton();
	color2 = new JButton();
	updateColorComponents();

	// image name label
	nametag = new JLabel(mLayer.getName());
	Font lab_font = new Font("SanSerif", Font.PLAIN, 10);
	nametag.setFont(lab_font);
	nametag.setForeground(Color.gray);

	// alpha slider
	alpha = new JSlider(1, 100);
	alpha.setToolTipText(ALPHA_TT);
	alpha.setBackground(Color.white);
	alpha.setMaximumSize(new Dimension (80, 20));
	alpha.setMinimumSize(new Dimension (80, 20));

	// contour sliders
	contour_min = new JSlider(0, 100);
	contour_min.setToolTipText(CONTOUR_MIN_TT);
	contour_min.setBackground(Color.white);
	contour_min.setMaximumSize(new Dimension (40, 20));
	contour_min.setMinimumSize(new Dimension (40, 20));

	contour_max = new JSlider(0, 100);
	contour_max.setToolTipText(CONTOUR_MAX_TT);
	contour_max.setBackground(Color.white);
	contour_max.setMaximumSize(new Dimension (40, 20));
	contour_max.setMinimumSize(new Dimension (40, 20));

	// set the dimensions on the small buttons
	setSmallButtonDimensions(translate);
	setSmallButtonDimensions(color1);
	setSmallButtonDimensions(color2);
	setSmallButtonDimensions(useCustomColors);
	
	// add listeners
	this.addMouseListener(this);
	translate.addActionListener(this);
	color1.addActionListener(this);
	color2.addActionListener(this);
	useCustomColors.addActionListener(this);
	alpha.addChangeListener(this);
	contour_max.addChangeListener(this);
	contour_min.addChangeListener(this);
	cb_radio.addActionListener(this);
	opaque_radio.addActionListener(this);

    }



}



/**
 *   Object representation of a layer
 *
 */
class Layer implements Comparable {

    ImagePlus mOrigImage;
    float mAlpha;
    float mContrast;
    Color mColor1;
    Color mColor2;
    String mId;
    int mXoffset;
    int mYoffset;
    int mOrder;
    boolean mUseCustomColors;
    boolean mIsTranslating;
    int mOverlayMode;
    float mContourPlotMinVal;
    float mContourPlotMaxVal;
    

    public static final int COLOR_BURN = 1;
    public static final int CONTOUR = 2;
    public static final int SRC_OVER = 3;
    public static final int SRC = 4;
    public static final int SRC_SPECIAL = 4;

    public Layer (ImagePlus i) {
	mAlpha = 0.8F;
	mOrigImage = i;
	mId = i.getTitle();
	mColor2 = new Color(1F,1F,1F);
	mColor1 = new Color(0F, 0F, 0F);
	mUseCustomColors = true;
	mOverlayMode = COLOR_BURN;
	mContourPlotMinVal = (float) i.getProcessor().getMin();
	mContourPlotMaxVal = (float) i.getProcessor().getMax();
	System.out.println("init mContourPlotMinVal=>"+mContourPlotMinVal);
	System.out.println("init mContourPlotMaxVal=>"+mContourPlotMaxVal);

    }

    public String getName() {return mId;}
    
    public int getOverlayMode() {return mOverlayMode;}
    public void setOverlayMode(int m) {mOverlayMode = m;}

    public boolean getIsTranslating() {return mIsTranslating;}
    public void setIsTranslating(boolean b) {mIsTranslating = b;}

    public float getContourPlotMinVal() {return mContourPlotMinVal;}
    public void  setContourPlotMinVal(float v) {
	if (v > mContourPlotMaxVal) mContourPlotMinVal = mContourPlotMaxVal;
	else mContourPlotMinVal = v;
	//System.out.println("mContourPlotMinVal ("+v+")=>"+mContourPlotMinVal);
    }

    public float getContourPlotMaxVal() {return mContourPlotMaxVal;}
    public void  setContourPlotMaxVal(float v) {
	if (v < mContourPlotMinVal) mContourPlotMaxVal = mContourPlotMinVal;
	else mContourPlotMaxVal = v;
	//System.out.println("mContourPlotMaxVal("+v+") =>"+mContourPlotMaxVal);
    }


    public int getOrder() {return mOrder;}
    public Layer setOrder(int o) {mOrder = o; return this;}

    public void setAlpha(float a) {mAlpha = a; }
    public float getAlpha() { return mAlpha;}

    public void setContrast(float c) {mContrast = c; }
    public float getContrast() { return mContrast;}

    public boolean getUseCustomColors() {return mUseCustomColors;}
    public void setUseCustomColors(boolean b) {mUseCustomColors = b;}

    public Color getColor1 () {return mColor1;}
    public Color getColor2 () {return mColor2;}
    public void setColor1(Color c) {
	if (c!=null) {mColor1 = c;}
	else mColor1 = Color.white;
    }
    public void setColor2(Color c) {
	if (c!=null) {mColor2 = c;}
	else mColor1 = Color.black;
    }

    public void setImagePlus(ImagePlus i) {mOrigImage = i;}
    public ImagePlus getImagePlus() {return mOrigImage;}

    public int getHeight() {return mOrigImage.getHeight(); }
    public int getWidth() {return mOrigImage.getWidth();}

    public int getXOffset() {return mXoffset;}
    public int getYOffset() {return mYoffset;}
    public void setXOffset(int xo) {mXoffset = xo;}
    public void setYOffset(int yo) {mYoffset = yo;}

    public Layer incrementOrder() {
	mOrder++;
	return this;
    }

    public Layer decrementOrder() {
	mOrder--;
	return this;
    }

    public int compareTo(Object o) {
	int other_order = ((Layer) o).getOrder();
	if (mOrder < other_order) return 1;
	if (mOrder > other_order) return -1;
	else return 0;
    }

}


/**
 *   Manages the display of layered images in
 *   alpha composition, renders the image to 
 *   an offscreen buffer then writes pixels
 *   to an image that ImageJ can maipulate,
 *   also brokers mouse events from the ImageJ canvas
 *
 */
class ILTImageManager implements ImageObserver, 
				 MouseListener, 
				 MouseMotionListener 
{
    private ImagePlus mImage;
    private Image_Layering_Toolbox mParent;
    private BufferedImage mBuffer;
    private Hashtable mColorMappers;
    public static final int MOUSE_DOWN = 1;
    public static final int MOUSE_DRAG = 2;
    public static final int MOUSE_UP = 3;
    



    /**
     *   Construct a new ILTImageManager with an initial layer
     *
     */
    ILTImageManager(Image_Layering_Toolbox p, Layer l) 
    {
	mColorMappers = new Hashtable();
	mParent = p;
	System.out.println("ILTImageManager constructed");
	
	initNewDisplayImage("Layered Image",
			    l.getWidth(), 
			    l.getHeight());

	mBuffer = new BufferedImage(l.getWidth(),
				    l.getHeight(),
				    BufferedImage.TYPE_INT_RGB);
    }



    //  ============= PUBLIC METHODS =========

    /**
     *   Called by ILT, updates the display 
     *   with all of the layers
     *
     */
    public void updateLayers(SortedSet l) {
	try {
	    drawAllLayersToBuffer(l);
	    displayBuffer();
	} 
	catch (java.util.ConcurrentModificationException e) {
	    System.out.println("ImageManager caught a ConcurrentModException,"+
			       "\n trying to continue");
	}
	catch (Exception e) {

	    // bit of a hack to handle exit when images are 
	    // closed arbitrarily
	    e.printStackTrace();
	    IJ.showMessage("Exception updating images: "+e+
			   "\n Image Layering Toolbox Plugin will now exit.");
	    mParent.handleCPEvent(ILTControlPanel.SHUT_DOWN);
	}
    }



    // ===============  EVENT HANDLERS  ================
    public void mouseDragged(MouseEvent e) {
	mParent.handleMouseActionOnImage(MOUSE_DRAG, e.getX(), e.getY());
    }
    public void mouseMoved(MouseEvent e) {   }
    public void mouseClicked(MouseEvent e) {  }
    public void mouseExited(MouseEvent e) {    }
    public void mouseEntered(MouseEvent e) {  }
    public void mousePressed(MouseEvent e) { 
	mParent.handleMouseActionOnImage(MOUSE_DOWN, e.getX(), e.getY());
    }
    public void mouseReleased(MouseEvent e) { 
	mParent.handleMouseActionOnImage(MOUSE_UP, e.getX(), e.getY());
    }

    public boolean imageUpdate (Image img, 
				int infoflags, 
				int x, int y, int width, int height) 
    {
	return true;
    }







    // ========= SUPPORT METHODS  ==============

    /**
     *   updates the imge by drawing 
     *   all o the layers to a pixel buffer
     *
     *
     */
    private void drawAllLayersToBuffer(SortedSet ls) {
	// clear the buffer
	wipeBuffer();
	int v = 0;
	Iterator i = ls.iterator();
	while (i.hasNext()) {
	    v++;
	    addLayerToBuffer((Layer) i.next());
	}
    }


    /**
     *   Initialize the ImageJ image
     *
     */
    private void initNewDisplayImage(String title,
				     int width,
				     int height) 
    {

	// create a new image for outputting results
	mImage =  NewImage.createRGBImage(title,
					  width, 
					  height, 
					  1, NewImage.FILL_WHITE 
					  );

	mImage.show();

	// register the image for mouse events
	mImage.getWindow().getCanvas().addMouseMotionListener(this);
	mImage.getWindow().getCanvas().addMouseListener(this);
    }



    /**
     *  Overwrites the buffer w/ a white background
     *
     */
    private void wipeBuffer() {
	Graphics2D g2d = (Graphics2D) mBuffer.getGraphics();
	g2d.setColor(Color.white);
	g2d.fillRect(0, 0, mBuffer.getWidth(), mBuffer.getHeight());
    }





    /**
     *   Returns a BufferedImage containing
     *   the layer's image after color transform
     *
     *
     */
    private BufferedImage performImageColorConversion(Image img,
						      ILTColorMapper cmap,
						      int image_width, 
						      int image_height
						      ) 
    {	
	BufferedImage tmpbuf = 
	    new BufferedImage(image_width,
			      image_height,
			      BufferedImage.TYPE_INT_RGB);

	int[] pixels = new int[image_width * image_height];
	PixelGrabber pg = new PixelGrabber(img, 0, 0, image_width, 
					   image_height, pixels, 
					   0, image_width);

	try {
	    pg.grabPixels();
	} catch (InterruptedException e) {
	    System.err.println("interrupted waiting for pixels!");
	}
	if ((pg.getStatus() & ImageObserver.ABORT) != 0) {
	    System.err.println("image fetch aborted or errored");
	}

	// pixel encoding
	for (int j = 0; j < image_height; j++) {
	    for (int i = 0; i < image_width; i++) {
		pixels[image_width*j+i] = 
		    cmap.getColorForRGB(pixels[image_width * j + i]).getRGB();
	    }
	}

	// update buffer
	tmpbuf.setRGB(0,0,image_width,image_height,pixels,0,image_width);
	
	return tmpbuf;
    }




    /**
     *   Returns a BufferedImage containing
     *   the layer's image after color transform
     *
     *
     */
    private BufferedImage generateContourImage(ImagePlus i,
					       ILTColorMapper cmap)
    {	
	BufferedImage tmpbuf = 
	    new BufferedImage(i.getWidth(),
			      i.getHeight(),
			      BufferedImage.TYPE_INT_ARGB);
	ImageProcessor ip = i.getProcessor();

	int[] pixels = new int[i.getWidth()*i.getHeight()];
	int width = i.getWidth();

	// pixel encoding
	for (int y = 0; y < i.getHeight(); y++) {
	    for (int x = 0; x < i.getWidth(); x++) {
		pixels[y*width+x] = 
		    cmap.getColorForFloat(ip.getPixelValue(x, y)).getRGB();
	    }
	}

	// update buffer
	tmpbuf.setRGB(0,0,i.getWidth(),i.getHeight(),pixels,0,i.getWidth());
	
	return tmpbuf;
    }





    /** 
     *    Adds the buffered image to the existing layered image
     *    buffer with the effect and alpha specified.
     *
     *    This is done by hand b/c Java2D Compositing doesn't support
     *    the COLOR_BURN effect, the CONTOUR is
     *    experimental.
     *
     *    COLOR_BURN algorithm based on:
     *    http://www.w3.org/TR/2002/WD-SVG11-20020215/masking.html
     *
     *    if Sc + Dc is less than 1
     *       then Dc' = 0
     *       otherwise
     *       (
     *          if Sc is equal to 0
     *          then Dc' = 1
     *          otherwise Dc' = 1 - (1 - Dc) / Sc
     *       )
     *      Da' = Sa + Da - Sa*Da
     *
     *    @param i       The image to be added to the buffer
     *    @param xoffset The x offset of the image 
     *    @param yoffset The y offset of the image
     *    @param alpha   The 0-1 alpha of the image (overrides any pixel a-val, except w/ contours)
     *    @param effect  The type of effect, default = color burn
     *
     */
    private void addBufferToBufferWithAlphaAndEffect(BufferedImage i, 
						     int xoff, 
						     int yoff,
						     float alpha,
						     int  effect) 
    {
	Color csrc, cdst, cnew = null;
	float alpha_src, alpha_dst = 0;
	int r,g,b = 0;
	float[] hsb_arr = new float[3];
	float[] s_rgba_arr = new float[4];
	float[] d_rgba_arr = new float[4];
	float[] n_rgba_arr = new float[4];

	for (int x=0; x<i.getWidth();x++) {
	    for (int y=0; y<i.getHeight();y++) {
		if ((x + xoff) < mBuffer.getWidth() && (x + xoff) > 0 && 
		    (y + yoff) < mBuffer.getHeight() && (y + yoff) > 0) 
		    {
			if (effect == Layer.COLOR_BURN) {
			    csrc = new Color(i.getRGB(x, y));
			    cdst = new Color(mBuffer.getRGB(x+xoff, y+yoff));

			    /*   COLOR_BURN w/ alpha */
			    r = (int) (cdst.getRed() * 
				       (1F - alpha*(255F-csrc.getRed())/255F)); 
			    g = (int) (cdst.getGreen() * 
				       (1F - alpha*(255F-csrc.getGreen())/255F)); 
			    b = (int) (cdst.getBlue() * 
				       (1F - alpha*(255F-csrc.getBlue())/255F));

			    cnew = new Color(r,g,b);
			    mBuffer.setRGB(x+xoff, 
					   y+yoff, 
					   cnew.getRGB());

			} else if (effect == Layer.SRC_OVER) {
			    csrc = new Color(i.getRGB(x, y));
			    cdst = new Color(mBuffer.getRGB(x+xoff, y+yoff));
			    csrc.getRGBComponents(s_rgba_arr);
			    cdst.getRGBComponents(d_rgba_arr);

			    /*  Porter-Duff SRC_OVER */
			    n_rgba_arr[0] = d_rgba_arr[0] + 
				alpha*(s_rgba_arr[0] - d_rgba_arr[0]); 
			    n_rgba_arr[1] = d_rgba_arr[1] + 
				alpha*(s_rgba_arr[1] - d_rgba_arr[1]); 
			    n_rgba_arr[2] = d_rgba_arr[2] + 
				alpha*(s_rgba_arr[2] - d_rgba_arr[2]); 

			    cnew = new Color(n_rgba_arr[0],
					     n_rgba_arr[1],
					     n_rgba_arr[2],
					     n_rgba_arr[3]
					     );
			    mBuffer.setRGB(x+xoff, 
					   y+yoff, 
					   cnew.getRGB());
			} else if (effect == Layer.SRC) {
			    csrc = new Color(i.getRGB(x, y));

			    /*  Porter-Duff SRC */
			    mBuffer.setRGB(x+xoff, 
					   y+yoff, 
					   csrc.getRGB());

			} else if (effect == Layer.SRC_SPECIAL || 
				   effect == Layer.CONTOUR ) {
			    csrc = new Color(i.getRGB(x, y));
			    cdst = new Color(mBuffer.getRGB(x+xoff, y+yoff));
			    csrc.getRGBComponents(s_rgba_arr);
			    cdst.getRGBComponents(d_rgba_arr);

			    /*  Special Implemenation for contour 
				& legend: black is transparent */

			    if (s_rgba_arr[0] == 0 &&
				s_rgba_arr[1] == 0 &&
				s_rgba_arr[2] == 0) 
				{
				// update with dst only
				    n_rgba_arr[0] = d_rgba_arr[0];
				    n_rgba_arr[1] = d_rgba_arr[1];
				    n_rgba_arr[2] = d_rgba_arr[2];
				}
			    else {
				n_rgba_arr[0] = d_rgba_arr[0] + 
				    s_rgba_arr[3]*
				    (s_rgba_arr[0] - d_rgba_arr[0]); 
				n_rgba_arr[1] = d_rgba_arr[1] + 
				    s_rgba_arr[3]*
				    (s_rgba_arr[1] - d_rgba_arr[1]); 
				n_rgba_arr[2] = d_rgba_arr[2] + 
				    s_rgba_arr[3]*
				    (s_rgba_arr[2] - d_rgba_arr[2]); 

			    }
			    cnew = new Color(n_rgba_arr[0],
					     n_rgba_arr[1],
					     n_rgba_arr[2],
					     n_rgba_arr[3]
					     );

			    mBuffer.setRGB(x+xoff, 
					   y+yoff, 
					   cnew.getRGB());


			}

		    }
	    }
	}

    }



    /**
     *   Returns the ImagePlus containing the LayeredImage
     */
    public ImagePlus getIP() {
	return mImage;
    }



    private ILTColorMapper getColorMapper(Layer l) {
	ILTColorMapper retmap = (ILTColorMapper) mColorMappers.get(l);
	if (retmap == null) {
	    // create the color map & store it
	    retmap = new ILTColorMapper(l);
	    mColorMappers.put(l, retmap); 
	}
	else {
	    retmap.checkForUpdates(l); // check if colors have changed, min, max, etc.
	}
	return retmap;
    }



    /**
     *   Adds a layer to the ImageBuffer containing
     *   the layered image
     *
     */
    private void addLayerToBuffer(Layer l) {

	// get the color mapper for this layer
	ILTColorMapper cmap = getColorMapper(l);
	BufferedImage intermed = null;

	if (l.getOverlayMode() == Layer.COLOR_BURN) {
	intermed = 
	    performImageColorConversion(l.getImagePlus().getImage(),
					cmap,
					l.getWidth(),
					l.getHeight());
	
	addBufferToBufferWithAlphaAndEffect(intermed,
					    l.getXOffset(), 
					    l.getYOffset(),
					    l.getAlpha(),
					    l.getOverlayMode());

	} else if (l.getOverlayMode() == Layer.CONTOUR) {

	    intermed = 
		generateContourImage(l.getImagePlus(), cmap);
	    
	    addBufferToBufferWithAlphaAndEffect(intermed,
						l.getXOffset(), 
						l.getYOffset(),
						l.getAlpha(),
						l.getOverlayMode());
	    intermed = 
		createLegendBasedOnLayer(l,
					 (int) (mBuffer.getWidth()*.9),
					 10); 
	    
	    // add the awt image to the buffer w/ SRC effect
	    addBufferToBufferWithAlphaAndEffect(intermed, 
						(mBuffer.getWidth() - 
						 intermed.getWidth())/2, 
						(mBuffer.getHeight() - 
						 intermed.getHeight()),
						1F,
						Layer.SRC_SPECIAL); 
	    
	}

    }




    /**
     *   draws a legend for the layer on based on the color mapper 
     *   used for the layer
     *
     */
    private BufferedImage createLegendBasedOnLayer(Layer l,
						   int bar_width,
						   int bar_height) 
    {
	ILTColorMapper cmap = getColorMapper(l);
	Insets inset = new Insets(25, 5, 10, 5);
	int tick_mark_height = 7;
	int num_labels = 4;
	int h_shift_factor = 35;
	
	float barmin = l.getContourPlotMinVal();		
	float barmax = l.getContourPlotMaxVal();
	Color line_and_font = Color.black;
	Color background = Color.white;
	Font  label_font = new Font("SanSerif", Font.PLAIN, 10);

	BufferedImage tmpbuf = 
	    new BufferedImage(bar_width+inset.left+inset.right,
			      bar_height+inset.top+inset.bottom,
			      BufferedImage.TYPE_INT_RGB);

	Graphics2D g2d = (Graphics2D) tmpbuf.getGraphics();
	g2d.setColor(background);
	g2d.fillRect(0, 0, tmpbuf.getWidth(), tmpbuf.getHeight());

	// draw the bounding box, ticks and labels
	g2d.setColor(line_and_font);
	g2d.setFont(label_font);

	// bounding box
	g2d.drawRect(inset.left, inset.top, bar_width, bar_height);

	// draw labels
	double m = 0;
	int interval = (int) ((float) bar_width / (float) num_labels);

	DecimalFormat df = new DecimalFormat("0.00E0");
	for (int x = 0; x <= bar_width; x++)
	    {
		if ((x % interval) == 0)
		    {
			// draw tick mark
			g2d.drawLine(x+inset.left, 
				     inset.top, 
				     x+inset.left, 
				     inset.top - tick_mark_height);		

			// draw the label
			m = ((float) x / (float) bar_width) 
			    * (barmax - barmin) + barmin;
			g2d.drawString (df.format(m), 
					x+inset.left - 
					(int) ((float)h_shift_factor*
					       ((float)x/(float)bar_width)), 
					inset.top - tick_mark_height-3);
		    }
	    } 


	// fill the box
	for (int x = 1; x<bar_width; x++) {
	    for (int y = 1; y<bar_height; y++) {
		  m =((double) barmin + 
		      (double)(barmax - barmin))*
		    ((double)(x-inset.left + inset.right)) / bar_width;
		  tmpbuf.setRGB(x+inset.left, y+inset.top, cmap.getColorForFloat((float)m).getRGB());
	    }
	}

	return tmpbuf;
    }

    


    /**
     *  pushes the display buffer to
     *  the ImageJ image
     */
    private void displayBuffer() {

	// display the buffer pixels in the mImage window
	int[] rgb = new int[mBuffer.getHeight()*mBuffer.getWidth()];
	
	int n = 0;
	int height = mBuffer.getHeight();
	int width = mBuffer.getWidth();
	for (int y = 0; y < height; y++) {
	    for (int x = 0; x< width; x++) {
		rgb [n] = mBuffer.getRGB(x, y);
		n++;
	    }
	}

	mImage.getProcessor().setPixels(rgb);
	mImage.updateAndDraw();
    }
    


    /**
     *
     *   Short utility class for mapping to indexed & continuous colors
     *  
     */
    class ILTColorMapper {
	public static final int SPECTRUM_256 = 0;
	public static final int CONTINUOUS_2COLOR = 1;
	public static final int ORIGINAL = 2;


	Color[] mLUT;
	float mMin, mMax;
	int mMode;
	Color mC1, mC2;
	float mMaxPos = (float) Math.sqrt(3*Math.pow(255,2));;
	int[] mCDiff;
	int mAlphaThreshold = 2;  // transparency threshold: index below which color is set to black, and displays as transparent


	public ILTColorMapper (Layer l) {
	    if (!l.getUseCustomColors()) 
		{
		    mMode = ORIGINAL;
		}
	    else if (l.getOverlayMode() == Layer.COLOR_BURN) 
		{
		    init(l.getColor1(), l.getColor2());		    
		}
	    else if (l.getOverlayMode() == Layer.CONTOUR)
		{
		    init(l.getContourPlotMinVal(),
			l.getContourPlotMaxVal(), 
			ILTColorMapper.SPECTRUM_256);
		}
	}


	/** continuous linear interpolation btn. c1 & c2**/
	private void init(Color c1, Color c2) 
	{
	    mMode = CONTINUOUS_2COLOR;
	    mCDiff = new int[3];
	    mC1 = c1;
	    mC2 = c2;
	    mCDiff[0] = c2.getRed() - c1.getRed();
	    mCDiff[1] = c2.getGreen() - c1.getGreen();
	    mCDiff[2] = c2.getBlue() - c1.getBlue();
	    initContinuousLut();
	}


	/** discrete case (contour) **/
	private void init(float n, float x, int mode) 
	{
	    mMin = n;
	    mMax = x;
	    float huval = .7F;
	    float hlval = 0F;
	    int depth = 256;
	    mMode = mode;
	    if (mode != SPECTRUM_256) {
		System.out.println("ILTColorMapper error: unknown mode: "+mode);
	    }
	    else if (mode == SPECTRUM_256) {
		mLUT = new Color[depth];
		Color c = null;

		for (int i = 1; i<=depth; i++) {
		    // HSB projection
		    c = 
			Color.getHSBColor((float) 
					  (huval - 
					   ((huval - hlval)*
					    ((float)i/(float)depth)
					    )
					   ),
					  (float) 1,
					  (float) 1);
		    if (i<mAlphaThreshold) {
			// set this to black, so that it's transparent
			mLUT[i-1] = 
			    new Color(0,0,0);
		    }
		    else {
			mLUT[i-1] = 
			    new Color(c.getRed(), 
				      c.getGreen(), 
				      c.getBlue());
		    }



		}
	    }
	}
	

	private void initContinuousLut() {
	    System.out.println("initContinuousLut()");
	    mLUT = new Color[256];
	    double mag;
	    Color cx = null;
	    for (int g = 0; g<256; g++) {
			cx = new Color(g,g,g);
			
			// convert to 8bit greyscale
			mag = 
			    Math.sqrt (Math.pow(cx.getRed(),2) + 
				       Math.pow(cx.getGreen(),2) + 
				       Math.pow(cx.getBlue(),2)) / mMaxPos; 
			
			// color projection
			mLUT[g] =
			    new Color((int) (mC1.getRed() + mag*mCDiff[0]),
				      (int) (mC1.getGreen() + mag*mCDiff[1]),
				      (int) (mC1.getBlue() + mag*mCDiff[2]));
	    }   

	}


	public Color getColorForRGB(int rgb) {	  
	    if (mMode == ORIGINAL) {
		return new Color(rgb);
	    }

	    Color cx = new Color (rgb);
	    double mag = 
		Math.sqrt (cx.getRed()*cx.getRed() + 
			   cx.getGreen()*cx.getGreen() + 
			   cx.getBlue()*cx.getBlue()) / mMaxPos; 

	    return mLUT[(int)(mag*255)];
	}


	public Color getColorForFloat(float f) {
	    int idx =0;
	    f = f > mMax ? mMax : f;
	    f = f < mMin ? mMin : f;
	    idx = (int) (((f-mMin)/(mMax - mMin))*(float) mLUT.length);
	    if (idx >= mLUT.length || idx <0) {
		idx = mLUT.length-1;		
	    }

	    return mLUT[idx];
	}

	public void checkForUpdates(Layer l) {
	    if (!l.getUseCustomColors()) {
		mMode = ORIGINAL;
	    }
	    else if (l.getOverlayMode() == Layer.COLOR_BURN && 
		     mMode != CONTINUOUS_2COLOR) 
		{
		    init(l.getColor1(), l.getColor2());		    
		}
	    else if (l.getOverlayMode() == Layer.CONTOUR && 
		     mMode != SPECTRUM_256) 
		{
		    init(l.getContourPlotMinVal(),
			 l.getContourPlotMaxVal(), 
			 ILTColorMapper.SPECTRUM_256);
		}
	    else if (mMode == CONTINUOUS_2COLOR) 
		{
		    // set colors
		    if (!mC1.equals(l.getColor1()) || 
			!mC2.equals(l.getColor2())) 
			{
			    init (l.getColor1(), l.getColor2());
			}
		}
	    else if (mMode == SPECTRUM_256 && 
		     (mMin != l.getContourPlotMinVal() ||
		      mMax != l.getContourPlotMaxVal())
		     ) 
		{
		    init(l.getContourPlotMinVal(),
			 l.getContourPlotMaxVal(), 
			 ILTColorMapper.SPECTRUM_256);
		}	    
	}
	

    }
   
}













