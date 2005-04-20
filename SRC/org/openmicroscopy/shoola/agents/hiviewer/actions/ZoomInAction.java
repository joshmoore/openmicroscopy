/*
 * org.openmicroscopy.shoola.agents.hiviewer.actions.ZoomInAction
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

package org.openmicroscopy.shoola.agents.hiviewer.actions;


//Java imports
import java.awt.event.ActionEvent;
import javax.swing.Action;

//Third-party libraries

//Application-internal dependencies
import org.openmicroscopy.shoola.agents.hiviewer.HiViewerCtrl;
import org.openmicroscopy.shoola.agents.hiviewer.IconManager;
import org.openmicroscopy.shoola.agents.hiviewer.browser.ImageDisplay;
import org.openmicroscopy.shoola.env.data.model.CategoryData;
import org.openmicroscopy.shoola.env.data.model.DatasetSummary;
import org.openmicroscopy.shoola.util.ui.UIUtilities;

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
public class ZoomInAction
    extends BrowserAction
{

    private static final String NAME = "Zoom in";
    
    private static final String DESCRIPTION = "Zoom in the selected dataset" +
            "or category.";
    
    public ZoomInAction(HiViewerCtrl agentCtrl)
    {
        super(agentCtrl);
        setEnabled(false);
        putValue(Action.NAME, NAME);
        putValue(Action.SHORT_DESCRIPTION, 
                UIUtilities.formatToolTipText(DESCRIPTION));
        IconManager im = IconManager.getInstance();
        putValue(Action.SMALL_ICON, im.getIcon(IconManager.ZOOM_IN));
    }
    
    /** Handle the action. */
    public void actionPerformed(ActionEvent e)
    {
        if (browser.getSelectedDisplay() == null) return;
        Object ho = browser.getSelectedDisplay().getHierarchyObject();
        agentCtrl.zoomIn(ho, browser);
    }


    /* (non-Javadoc)
     * @see org.openmicroscopy.shoola.agents.hiviewer.actions.BrowserAction#onDisplayChange(org.openmicroscopy.shoola.agents.hiviewer.browser.ImageDisplay)
     */
    protected void onDisplayChange(ImageDisplay selectedDisplay)
    {
        if (selectedDisplay == null) setEnabled(false);
        else {
            Object ho = selectedDisplay.getHierarchyObject();
            if ((ho instanceof CategoryData) || 
                    (ho instanceof DatasetSummary)) setEnabled(true);
            else setEnabled(false);
        }
    }

}
