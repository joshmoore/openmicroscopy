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

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.JOptionPane;
import javax.swing.text.JTextComponent;

import tree.DataField;
import ui.components.AttributeMemoEditor;

public class FieldEditorTable extends FieldEditor {
	
	AttributeMemoEditor tableColumnsEditor;
	
	public FieldEditorTable (DataField dataField) {
		
		super(dataField);
	
		//	 comma-delimited list of options
		String tableColumns = dataField.getAttribute(DataField.TABLE_COLUMN_NAMES);
		
		// attributeMemoEditor has listener to update dataField - and adds change to undo-redo.
		tableColumnsEditor = new TableColumnsEditor
			(dataField, "Columns: (separate with commas)", DataField.TABLE_COLUMN_NAMES, tableColumns);
		//tableColumnsEditor.removeFocusListener();
		//tableColumnsEditor.getTextArea().addFocusListener(new TableColumnsFocusListener());
		
		tableColumnsEditor.setToolTipText("Add columns names, separated by commas");
		tableColumnsEditor.setTextAreaRows(4);
		attributeFieldsPanel.add(tableColumnsEditor);	
	}
	
	public class TableColumnsEditor extends AttributeMemoEditor {
		
		// constructor creates a new panel and adds a name and text area to it.
		public TableColumnsEditor(DataField dataField, String attribute, String value) {
			this(dataField, attribute, attribute, value);
		}
		public TableColumnsEditor(DataField dataField, String label, String attribute, String value) {
			super(dataField, label, attribute, value);
		}
		
		// called to update dataField with attribute
		protected void setDataFieldAttribute(String attributeName, String newColumnNames, boolean notifyUndoRedo) {
			
			// if not planning to update TABLE_COLUMN_NAME attribute, just setAttribute (eg Name, Description..)
			if (!attributeName.equals(DataField.TABLE_COLUMN_NAMES)) {
				super.setDataFieldAttribute(attributeName, newColumnNames, true);
				return;
			}
			
			String[] newCols = newColumnNames.split(",");
			
			String oldColumnNames = dataField.getAttribute(DataField.TABLE_COLUMN_NAMES);
			if (oldColumnNames != null) {
				String[] oldCols = oldColumnNames.split(",");
			
				int deletedCols = oldCols.length - newCols.length;
			
				if (deletedCols > 0) {
					int confirm = JOptionPane.showConfirmDialog(tableColumnsEditor, 
							"You have removed " + deletedCols + (deletedCols == 1? " column":" columns" ) + " from the table.\n" +
							"This will delete all the data in " + (deletedCols == 1? "this column":"these columns.") + "\n" +
							"Are you sure you want to continue?", "Delete Columns?", JOptionPane.OK_CANCEL_OPTION);
					if (confirm != JOptionPane.OK_OPTION) {
						// user has canceled update, simply reset textBox
						tableColumnsEditor.setTextAreaText(oldColumnNames);
						return;
					}
				} 
			} 
			// the user didn't get asked, or chose not to cancel, therefore update!
			super.setDataFieldAttribute(attributeName, newColumnNames, true);
		}
	}
	

}
