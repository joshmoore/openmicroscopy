/*
 * org.openmicroscopy.shoola.agents.viewer.transform.ImageInspectorManager
 *
 *------------------------------------------------------------------------------
 *
 *  Copyright (C) 2004 Open Microscopy Environment
 *      Massachusetts Institute of Technology,
 *      National Institutes of Health,
 *      University of Dundee
 *
 *
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation; either
 *    version 2.1 of the License, or (at your option) any later version.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public
 *    License along with this library; if not, write to the Free Software
 *    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *------------------------------------------------------------------------------
 */

package org.openmicroscopy.shoola.agents.viewer.transform;

//Java imports
import java.awt.Dimension;
import java.awt.image.BufferedImage;

//Third-party libraries

//Application-internal dependencies
import org.openmicroscopy.shoola.agents.viewer.transform.zooming.ZoomBar;
import org.openmicroscopy.shoola.agents.viewer.transform.zooming.ZoomMenu;
import org.openmicroscopy.shoola.agents.viewer.transform.zooming.ZoomPanel;

/** 
 * 
 *
 * @author  Jean-Marie Burel &nbsp;&nbsp;&nbsp;&nbsp;
 * 				<a href="mailto:j.burel@dundee.ac.uk">j.burel@dundee.ac.uk</a>
 * @author  <br>Andrea Falconi &nbsp;&nbsp;&nbsp;&nbsp;
 * 				<a href="mailto:a.falconi@dundee.ac.uk">
 * 					a.falconi@dundee.ac.uk</a>
 * @version 2.2 
 * <small>
 * (<b>Internal version:</b> $Revision$ $Date$)
 * </small>
 * @since OME2.2
 */
public class ImageInspectorManager
{

	public static final double 		MIN_ZOOM_LEVEL = 0.25;
	public static final double 		MAX_ZOOM_LEVEL = 3.0;
	public static final double		ZOOM_DEFAULT = 1.0;
	public static final double 		ZOOM_INCREMENT = 0.25;
	
	private ImageInspector			view;
	private BufferedImage			image;
	private int						imageWidth, imageHeight;
	private Dimension				imageDim;
	private ZoomPanel				zoomPanel;
	private double					curZoomLevel;
	
	public ImageInspectorManager(ImageInspector view, BufferedImage image)
	{
		this.view = view;
		this.image = image;	
		imageWidth = image.getWidth();
		imageHeight = image.getHeight();
		imageDim = new Dimension(imageWidth, imageHeight);
		curZoomLevel = ZOOM_DEFAULT;
	}
	
	void setZoomPanel(ZoomPanel zoomPanel)
	{
		this.zoomPanel = zoomPanel;
		zoomPanel.setPreferredSize(imageDim);
		zoomPanel.setSize(imageDim);
	}
	
	/** Zoom in or out. */
	public void setZoomLevel(double level)
	{
		if (curZoomLevel != level) {
			zoom(level);
			curZoomLevel = level;
		}
	}
	
	/** 
	 * Zoom in or out according to the level.
	 * 
	 * @param level	value in the range MIN_ZOOM_LEVEL and MAX_ZOOM_LEVEL.
	 */
	private void zoom(double level)
	{
		ZoomBar zoomBar = view.toolBar.getZoomBar();
		zoomBar.getManager().setText(level);
		ZoomMenu zoomMenu = view.menuBar.getZoomMenu();
		zoomMenu.getManager().setItemSelected(level);
	   	Dimension d;
	   	//if (level >= ZOOM_DEFAULT)
		   	d = new Dimension((int) (imageWidth*level), 
		   					(int) (imageHeight*level));
	   //	else	d = imageDim;
	   	zoomPanel.setPreferredSize(d);
	   	zoomPanel.setSize(d);
	   	zoomPanel.paintImage(level);
	}
	
}
