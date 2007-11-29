/*
 *------------------------------------------------------------------------------
 *  Copyright (C) 2006-2007 University of Dundee. All rights reserved.
 *
 *
 * 	This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *------------------------------------------------------------------------------
 *	author Will Moore will@lifesci.dundee.ac.uk
 */

package ui;

import java.awt.Dimension;

import javax.swing.*;
import javax.swing.border.EtchedBorder;

import tree.DataField;

public class FormFieldProtocol extends FormField {
	
	JLabel fileName;
	JLabel fileNameLabel;
	
	public FormFieldProtocol(DataField dataField) {
		
		super(dataField);
		
		nameLabel.setFont(XMLView.FONT_H1);
		
		horizontalBox.add(horizontalBox.createGlue());
		
		fileName = new JLabel("Protocol File: ");
		visibleAttributes.add(fileName);
		fileName.setFont(XMLView.FONT_SMALL);
		horizontalBox.add(fileName);
		
		fileNameLabel = new JLabel(dataField.getAttribute(DataField.PROTOCOL_FILE_NAME));
		fileNameLabel.setFont(XMLView.FONT_SMALL);
		fileNameLabel.setMaximumSize(new Dimension(200, 20));
		horizontalBox.add(fileNameLabel);
		
		horizontalBox.add(Box.createHorizontalStrut(5));
		
		this.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
	}
	
	// overridden by subclasses if they have other attributes to retrieve from dataField
	public void dataFieldUpdated() {
		super.dataFieldUpdated();
		fileNameLabel.setText(dataField.getAttribute(DataField.PROTOCOL_FILE_NAME));
	}

}
