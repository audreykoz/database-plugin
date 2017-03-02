package org.archicontribs.database.canvas;

import org.archicontribs.database.DBLogger;
import org.archicontribs.database.DBMetadata;
import org.archicontribs.database.IDBMetadata;

/**
 * extends AccessRelationship<br>
 * implements IHasDBMetadata
 * 
 * @author Herve Jouin 
 * @see com.archimatetool.canvas.model.impl.CanvasModelImage
 * @see org.archicontribs.database.IDBMetadata
 */
public class CanvasModelImage extends com.archimatetool.canvas.model.impl.CanvasModelImage implements IDBMetadata {
	private static final DBLogger logger = new DBLogger(CanvasModelImage.class);
	private DBMetadata dbMetadata;
	
	public CanvasModelImage() {
		super();
		if ( logger.isTraceEnabled() ) logger.trace("Creating new CanvasModelImage");
		
		dbMetadata = new DBMetadata(this);
	}
	
	/**
	 * Gets the DBMetadata of the object
	 */
	public DBMetadata getDBMetadata() {
		return dbMetadata;
	}
}