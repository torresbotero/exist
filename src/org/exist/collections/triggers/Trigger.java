/*
 *  Trigger.java - eXist Open Source Native XML Database
 *  Copyright (C) 2003 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist-db.org
 * 
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 * $Id$
 *
 */
package org.exist.collections.triggers;

import java.util.Map;

import org.apache.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.storage.DBBroker;
import org.w3c.dom.Document;
import org.xml.sax.ContentHandler;
import org.xml.sax.ext.LexicalHandler;

/**
 * Defines the interface for collection triggers. Triggers are registered through the
 * collection configuration file, called "collection.xconf", which should be
 * stored in the corresponding database collection. If a collection configuration file is
 * found in the collection, it will be parsed and any triggers will be created and configured.
 * The {@link #configure(DBBroker, Collection, Map) configure} method is called once on each trigger.
 * 
 * Triggers listen to events. Currently, there are three events to which triggers may be
 * attached:
 * 
 * <table border="0">
 * 	<tr>
 * 		<td>{@link #STORE_DOCUMENT_EVENT STORE_DOCUMENT_EVENT}</td>
 * 		<td>Fired, if a new document is inserted into the collection.</td>
 * 	</tr>
 * 	<tr>
 * 		<td>{@link #UPDATE_DOCUMENT_EVENT UPDATE_DOCUMENT_EVENT}</td>
 * 		<td>Fired, whenever an existing document is updated, i.e. replaced
 * 		with a new version.</td>
 * 	</tr>
 * 	<tr>
 * 		<td>{@link #REMOVE_DOCUMENT_EVENT REMOVE_DOCUMENT_EVENT}</td>
 * 		<td>Fired, whenever a document is removed from the collection.</td>
 * 	</tr>
 * </table>
 * 
 * The collection configuration file looks as follows:
 * 
 * <pre>
 * &lt;?xml version="1.0" encoding="ISO-8859-1"?&gt;
 * &lt;exist:collection xmlns:exist="http://exist-db.org/collection-config/1.0"&gt;
 *	&lt;exist:triggers&gt;
 *		&lt;exist:trigger event="store"
 *		class="fully qualified classname of the trigger"&gt;
 *			&lt;exist:parameter name="parameter-name"
 *				value="parameter-value"/&gt;
 *		&lt;/exist:trigger&gt;
 *	&lt;/exist:triggers&gt;
 * &lt;/exist:collection&gt;
 * </pre>
 * 
 * Triggers may have two roles:
 * 
 * <ol>
 * 	<li>before the document is stored, updated or removed, the trigger's {@link #prepare(int, DBBroker, String, Document) prepare} 
 * 	method is called. The trigger code may take any action desired, for example, to ensure referential
 * 	integrity on the database, issue XUpdate commands on other documents in the database...</li>
 * 	<li>the trigger also functions as a filter: the trigger interface extends SAX {@link org.xml.sax.ContentHandler content handler} and
 * 	{@link org.xml.sax.LexicalHandler lexical handler}. It will thus receive any SAX events generated by the SAX parser. The default
 * 	implementation just forwards the SAX events to the indexer, i.e. the output content handler. However,
 * 	a trigger may also alter the received SAX events before it forwards them to the indexer, for example,
 * 	by applying a stylesheet.</li>
 * </ol>
 * 
 * The general contract for a trigger is as follows:
 * 
 * <ol>
 * 	<li>configuration phase: whenever the collection loads its configuration file, the trigger's 
 * 	{@link #configure(DBBroker, Collection, Map) configure} method
 * 	will be called once.</li>
 * 	<li>pre-parse phase: before parsing the source document, the collection will call the trigger's
 * 	{@link #prepare(int, DBBroker, String, Document) prepare} 
 * 	method once for each document to be stored, removed or updated. The trigger may
 * 	throw a TriggerException if the current action should be aborted.</li>
 * 	<li>validation phase: during the validation phase, the document is parsed once by the SAX parser. During this
 * 	phase, the trigger may decide to throw a SAXException to report a problem. Validation will fail and the action
 * 	is aborted.</li>
 * 	<li>storage phase: the document is again parsed by the SAX parser. The trigger will still receive all SAX events,
 * 	but it is not allowed to throw an exception. Throwing an exception during the storage phase will result in an
 * 	invalid document in the database. Use {@link #isValidating() isValidating} in your code to check that you're
 * 	in validation phase.</li>
 * </ol>
 * 
 * @author wolf
 */
public interface Trigger extends ContentHandler, LexicalHandler {

	public final static int STORE_DOCUMENT_EVENT = 0;
	public final static int UPDATE_DOCUMENT_EVENT = 1;
	public final static int REMOVE_DOCUMENT_EVENT = 2;

	/**
	 * The configure method is called once whenever the collection configuration is loaded. Use it to
	 * initialize the trigger, probably by looking at the parameters.
	 * 
	 * @param broker the database instance used to load the collection configuration. The broker object is
	 * 	required for all database actions. Please note: the broker instance used for configuration is probably
	 * 	different from the one passed to the prepare method. Don't store the broker object in your class.
	 * @param parent the collection to which this trigger belongs. 
	 * @param parameters a Map containing any key/value parameters defined in the configuration file.
	 * @throws CollectionConfigurationException if the trigger cannot be initialized.
	 */
	public void configure(DBBroker broker, Collection parent, Map parameters)
		throws CollectionConfigurationException;

	/**
	 * This method is called once before the database will actually parse the input data. You may take any action
	 * here, using the supplied broker instance.
	 * 
	 * @param event the type of event that triggered this call (see the constants defined in this interface).
	 * @param broker the database instance used to process the current action.
	 * @param documentName the name of the document currently processed (relative to the collection path).
	 * @param existingDocument optional: if event is a {@link #UPDATE_DOCUMENT_EVENT},
	 * 	existingDocument will contain the Document object for the old document, which will be overwritten. Otherwise, the parameter
	 * 	is null.
	 * @throws TriggerException throwing a TriggerException will abort the current action.
	 */
	public void prepare(
		int event,
		DBBroker broker,
		String documentName,
		Document existingDocument)
		throws TriggerException;

	/**
	 * Returns true if the SAX parser is currently in validation phase. During validation phase, the trigger
	 * may safely throw a SAXException. However, if is {@link #isValidating() isValidating} returns false, no exceptions should be
	 * thrown.
	 * 
	 * @return true if the parser is in validation phase.
	 */
	public boolean isValidating();

	/**
	 * Called by the database to report that it is entering validation phase.
	 * 
	 * @param validating
	 */
	public void setValidating(boolean validating);

	/**
	 * Called by the database to set the output content handler for this trigger.
	 * 
	 * @param handler
	 */
	public void setOutputHandler(ContentHandler handler);

	/**
	 * Called by the database to set the lexical output content handler for this trigger.
	 * 
	 * @param handler
	 */
	public void setLexicalOutputHandler(LexicalHandler handler);

	/**
	 * Returns the output handler to which SAX events should be forwarded.
	 * 
	 * @return
	 */
	public ContentHandler getOutputHandler();

	/**
	 * Returns the input content handler. Usually, this method should just return
	 * the trigger object itself, i.e. <b>this</b>. However, the trigger may choose to provide
	 * a different content handler.
	 * 
	 * @return the ContentHandler to be called by the database.
	 */
	public ContentHandler getInputHandler();

	/**
	 * Called by the database to set the lexical output handler for this trigger.
	 * 
	 * @return
	 */
	public LexicalHandler getLexicalOutputHandler();

	/**
	 * Returns the lexical input handler for this trigger. See {@see #getInputHandler() getInputHandler}.
	 * 
	 * @return
	 */
	public LexicalHandler getLexicalInputHandler();

	/**
	 * Returns a Logger object. Use this to log debugging information.
	 * 
	 * @return
	 */
	public Logger getLogger();
}
