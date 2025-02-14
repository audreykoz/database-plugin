/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */

package org.archicontribs.database.model.commands;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

import org.archicontribs.database.DBLogger;
import org.archicontribs.database.DBPlugin;
import org.archicontribs.database.connection.DBDatabaseImportConnection;
import org.archicontribs.database.connection.DBSelect;
import org.archicontribs.database.data.DBImportMode;
import org.archicontribs.database.data.DBProperty;
import org.archicontribs.database.data.DBVersion;
import org.archicontribs.database.model.DBArchimateFactory;
import org.archicontribs.database.model.DBArchimateModel;
import org.archicontribs.database.model.DBCanvasFactory;
import org.archicontribs.database.model.DBMetadata;
import org.archicontribs.database.model.IDBMetadata;
import org.eclipse.gef.commands.Command;

import com.archimatetool.editor.ui.services.EditorManager;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IProperties;
import com.archimatetool.model.IProperty;
import com.archimatetool.model.util.Logger;

/**
 * Command for importing a view from it's ID.
 * 
 * @author Herve Jouin
 */
public class DBImportViewFromIdCommand extends Command implements IDBImportFromIdCommand {
	private static final DBLogger logger = new DBLogger(DBImportViewFromIdCommand.class);

	private IDiagramModel importedView= null; 

	private boolean commandHasBeenExecuted = false;		// to avoid being executed several times
	private List<IDBImportFromIdCommand> importViewContentCommands = new ArrayList<IDBImportFromIdCommand>();
	private Exception exception;

	private DBArchimateModel model = null;

	private String id = null;
	private boolean mustCreateCopy;
	private boolean mustImportViewContent;
	private boolean isNew;

	// new values that are retrieved from the database
	private HashMap<String, Object> newValues = null;
	private IFolder newFolder = null;

	// old values that need to be retain to allow undo
	private DBVersion oldInitialVersion;
	private DBVersion oldCurrentVersion;
	private DBVersion oldDatabaseVersion;
	private DBVersion oldLatestDatabaseVersion;
	private String oldName = null;
	private String oldDocumentation = null;
	private Integer oldConnectionRouterType = null;
	private String oldViewpoint = null;
	private Integer oldBackground = null;
	private IFolder oldFolder = null;
	private ArrayList<DBProperty> oldProperties = null;


	/**
	 * Imports a view into the model<br>
	 * @param connection connection to the database
	 * @param model model into which the view will be imported
	 * @param view if a view is provided, then an ArchimateObject will be automatically created
	 * @param folder if a folder is provided, the view will be created inside this folder. Else, we'll check in the database if the view has already been part of this model in order to import it in the same folder.
	 * @param id id of the view to import
	 * @param importMode specifies if the view must be copied or shared
	 * @param mustCreateCopy true if a copy must be imported (i.e. if a new id must be generated) or false if the view should be its original id 
	 */
	@SuppressWarnings("unchecked")
	public DBImportViewFromIdCommand(DBDatabaseImportConnection importConnection, DBArchimateModel model, IFolder folder, String id, int version, DBImportMode importMode, boolean mustImportViewContent) {
		this.model = model;
		this.id = id;
		this.mustImportViewContent = mustImportViewContent;
		
		if ( logger.isDebugEnabled() )
			logger.debug("   Importing view id " + this.id + " version " + version + " in " + importMode.getLabel() + (mustImportViewContent ? " including its content" : "") + ".");
		
		importConnection.declareAsImported(id);

		try {
			// we get the new values from the database to allow execute and redo
			this.newValues = importConnection.getObject(id, "IDiagramModel", version);

			this.mustCreateCopy = importMode.shouldCreateCopy((ArrayList<DBProperty>)this.newValues.get("properties"));
			
			if ( this.mustCreateCopy ) {
				String newId = this.model.getIDAdapter().getNewID();
				this.model.registerCopiedView((String)this.newValues.get("id"), newId);
				this.newValues.put("id", newId);
				this.newValues.put("name", (String)this.newValues.get("name") + DBPlugin.INSTANCE.getPreferenceStore().getString("copySuffix"));
			}

			if ( (folder != null) && (((IDBMetadata)folder).getDBMetadata().getRootFolderType() == DBMetadata.getDefaultFolderType((String)this.newValues.get("class"))) )
			    this.newFolder = folder;
			else
			    this.newFolder = importConnection.getLastKnownFolder(this.model, "IDiagramModel", this.id);

			if ( this.mustImportViewContent ) {
				// we import the objects and create the corresponding elements if they do not exist yet
				//    we use the importFromId method in order to allow undo and redo
				try (DBSelect result = (version == 0)
						? new DBSelect(importConnection.getDatabaseEntry().getName(), importConnection.getConnection(), "SELECT object_id, object_version, rank FROM "+importConnection.getSchema()+"views_objects_in_view WHERE view_id = ? AND view_version = (SELECT MAX(view_version) FROM "+importConnection.getSchema()+"views_objects_in_view WHERE view_id = ?) ORDER BY rank", id, id)
						: new DBSelect(importConnection.getDatabaseEntry().getName(), importConnection.getConnection(), "SELECT DISTINCT object_id, object_version, rank FROM "+importConnection.getSchema()+"views_objects_in_view WHERE view_id = ? AND view_version = ? ORDER BY rank", id, version) ) {
					while ( result.next() ) {
					    DBImportViewObjectFromIdCommand command = new DBImportViewObjectFromIdCommand(importConnection, model, result.getString("object_id"), (version == 0) ? 0 : result.getInt("object_version"), this.mustCreateCopy, importMode);
					    if ( command.getException() != null )
					        throw command.getException();
						this.importViewContentCommands.add(command);
					}
				}

				// we import the connections and create the corresponding relationships if they do not exist yet
				//    we use the importFromId method in order to allow undo and redo
				try (DBSelect result = (version == 0)
						? new DBSelect(importConnection.getDatabaseEntry().getName(), importConnection.getConnection(), "SELECT DISTINCT connection_id, connection_version, rank FROM "+importConnection.getSchema()+"views_connections_in_view WHERE view_id = ? AND view_version = (SELECT MAX(view_version) FROM "+importConnection.getSchema()+"views_connections_in_view WHERE view_id = ?) ORDER BY rank", id, id)
						: new DBSelect(importConnection.getDatabaseEntry().getName(), importConnection.getConnection(), "SELECT DISTINCT connection_id, connection_version, rank FROM "+importConnection.getSchema()+"views_connections_in_view WHERE view_id = ? AND view_version = ? ORDER BY rank", id, version) ) {
					while ( result.next() ) {
					    DBImportViewConnectionFromIdCommand command = new DBImportViewConnectionFromIdCommand(importConnection, model, result.getString("connection_id"), (version == 0) ? 0 : result.getInt("connection_version"), this.mustCreateCopy, importMode);
					    if ( command.getException() != null )
					        throw command.getException();
						this.importViewContentCommands.add(command);
					}
				}
			}

			if ( DBPlugin.isEmpty((String)this.newValues.get("name")) ) {
				setLabel("import view");
			} else {
				if ( ((String)this.newValues.get("name")).length() > 20 )
					setLabel("import \""+((String)this.newValues.get("name")).substring(0,16)+"...\"");
				else
					setLabel("import \""+(String)this.newValues.get("name")+"\"");
			}
		} catch (Exception err) {
            Logger.logError("Got Exception "+err.getMessage());
			this.importedView = null;
			this.exception = err;
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void execute() {
		if ( this.commandHasBeenExecuted )
			return;		// we do not execute it twice

		this.commandHasBeenExecuted = true;

		try {
			this.importedView = this.model.getAllViews().get(this.id);

			if ( this.importedView == null ) {
				if ( DBPlugin.areEqual((String)this.newValues.get("class"), "CanvasModel") )
					this.importedView = (IDiagramModel) DBCanvasFactory.eINSTANCE.create((String)this.newValues.get("class"));
				else
					this.importedView = (IDiagramModel) DBArchimateFactory.eINSTANCE.create((String)this.newValues.get("class"));

				this.isNew = true;
			} else {
				// we must save the old values to allow undo
				DBMetadata metadata = ((IDBMetadata)this.importedView).getDBMetadata();

				this.oldInitialVersion = metadata.getInitialVersion();
				this.oldCurrentVersion = metadata.getCurrentVersion();
				this.oldDatabaseVersion = metadata.getDatabaseVersion();
				this.oldLatestDatabaseVersion = metadata.getLatestDatabaseVersion();

				this.oldName = metadata.getName();
				this.oldDocumentation = metadata.getDocumentation();
				this.oldConnectionRouterType = metadata.getConnectionRouterType();
				this.oldViewpoint = metadata.getViewpoint();
				this.oldBackground = metadata.getBackground();

				this.oldProperties = new ArrayList<DBProperty>();
				for ( IProperty prop: this.importedView.getProperties() ) {
					this.oldProperties.add(new DBProperty(prop.getKey(), prop.getValue()));
				}

				this.oldFolder = metadata.getParentFolder();

				this.isNew = false;
			}
			DBMetadata metadata = ((IDBMetadata)this.importedView).getDBMetadata();

			if ( this.mustCreateCopy )
				metadata.getInitialVersion().set(0, null, new Timestamp(Calendar.getInstance().getTime().getTime()));
			else
				metadata.getInitialVersion().set((int)this.newValues.get("version"), (String)this.newValues.get("checksum"), (Timestamp)this.newValues.get("created_on"));

			metadata.setId((String)this.newValues.get("id"));
			metadata.setName((String)this.newValues.get("name"));
			metadata.getCurrentVersion().set(metadata.getInitialVersion());
			metadata.getDatabaseVersion().set(metadata.getInitialVersion());
			metadata.getLatestDatabaseVersion().set(metadata.getInitialVersion());

			metadata.setDocumentation((String)this.newValues.get("documentation"));
			metadata.setConnectionRouterType((Integer)this.newValues.get("connection_router_type"));
			metadata.setViewpoint((String)this.newValues.get("viewpoint"));
			metadata.setBackground((Integer)this.newValues.get("background"));

			this.importedView.getProperties().clear();
			if ( this.newValues.get("properties") != null ) {
    			for ( DBProperty newProperty: (ArrayList<DBProperty>)this.newValues.get("properties")) {
    				IProperty prop = DBArchimateFactory.eINSTANCE.createProperty();
    				prop.setKey(newProperty.getKey());
    				prop.setValue(newProperty.getValue());
    				this.importedView.getProperties().add(prop);
    			}
			}

			if ( this.newFolder == null )
				metadata.setParentFolder(this.model.getDefaultFolderForObject(this.importedView));
			else
				metadata.setParentFolder(this.newFolder);

			if ( this.isNew )
				this.model.countObject(this.importedView, false, null);

			// if some content must be imported
			for (IDBImportFromIdCommand childCommand: this.importViewContentCommands) {
				childCommand.execute();

				if ( childCommand.getException() != null )
					throw childCommand.getException();
			}

		} catch (Exception err) {
		    Logger.logError("Got Exception "+err.getMessage());
			this.exception = err;
		}
	}

	@Override
	public boolean canUndo() {
		return this.commandHasBeenExecuted;
	}

	@Override
	public void undo() {
		if ( !this.commandHasBeenExecuted )
			return;

		// if some content has been imported
		for (int i = this.importViewContentCommands.size() - 1 ; i >= 0 ; --i) {
			if ( this.importViewContentCommands.get(i).canUndo() ) 
				this.importViewContentCommands.get(i).undo();
		}

		if ( this.importedView != null ) {
			if ( this.isNew ) {
				// if the view has been created by the execute method, we just delete it
				EditorManager.closeDiagramEditor(this.importedView);

				IFolder parentFolder = (IFolder)this.importedView.eContainer();
				if ( parentFolder != null )
					parentFolder.getElements().remove(this.importedView);

				this.model.getAllFolders().remove(this.importedView.getId());
			} else {
				// else, we need to restore the old properties
				DBMetadata metadata = ((IDBMetadata)this.importedView).getDBMetadata();

				metadata.getInitialVersion().set(this.oldInitialVersion);
				metadata.getCurrentVersion().set(this.oldCurrentVersion);
				metadata.getDatabaseVersion().set(this.oldDatabaseVersion);
				metadata.getLatestDatabaseVersion().set(this.oldLatestDatabaseVersion);

				metadata.setName(this.oldName);
				metadata.setDocumentation(this.oldDocumentation);
				metadata.setConnectionRouterType(this.oldConnectionRouterType);
				metadata.setViewpoint(this.oldViewpoint);
				metadata.setBackground(this.oldBackground);

				metadata.setParentFolder(this.oldFolder);

				this.importedView.getProperties().clear();
				((IProperties)this.importedView).getProperties().clear();
				for ( DBProperty oldPropery: this.oldProperties ) {
					IProperty newProperty = DBArchimateFactory.eINSTANCE.createProperty();
					newProperty.setKey(oldPropery.getKey());
					newProperty.setValue(oldPropery.getValue());
					((IProperties)this.importedView).getProperties().add(newProperty);
				}
			}
		}

		// we allow the command to be executed again
		this.commandHasBeenExecuted = false;
	}

	/**
	 * @return the element that has been imported by the command (of course, the command must have been executed before)<br>
	 * if the value is null, the exception that has been raised can be get using {@link getException}
	 */
	@Override
	public IDiagramModel getImported() {
		return this.importedView;
	}

	/**
	 * @return the exception that has been raised during the import process, if any.
	 */
	@Override
	public Exception getException() {
		return this.exception;
	}
}
