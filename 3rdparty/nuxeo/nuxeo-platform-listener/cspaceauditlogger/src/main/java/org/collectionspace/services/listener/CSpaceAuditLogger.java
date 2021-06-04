package org.collectionspace.services.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.event.DocumentEventTypes;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.impl.ArrayProperty;
import org.nuxeo.ecm.core.api.model.impl.ListProperty;
import org.nuxeo.ecm.core.api.model.impl.primitives.BlobProperty;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.schema.types.ListType;
import org.nuxeo.ecm.platform.audit.api.AuditLogger;
import org.nuxeo.ecm.platform.audit.api.ExtendedInfo;
import org.nuxeo.ecm.platform.audit.api.LogEntry;
import org.nuxeo.runtime.api.Framework;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.collectionspace.authentication.AuthN;
import org.collectionspace.services.nuxeo.listener.AbstractCSEventSyncListenerImpl;

	public class CSpaceAuditLogger extends AbstractCSEventSyncListenerImpl {

	private static final Logger logger = LoggerFactory.getLogger(CSpaceAuditLogger.class);
	
	private static final List<String> SYSTEM_PROPS = Arrays.asList("dc:created", "dc:creator", "dc:modified",
			"dc:contributors", "dc:title");
	
	public static final String EVENT_ID = "Property Modification";
	public static final String FIELD_NAME = "fieldname";
	public static final String OLD_VALUE = "oldValue";
	public static final String NEW_VALUE = "newValue";
	public static final String EMPTY_VALUE = "[EMPTY]";
	
	@Override
	public void handleCSEvent(Event event) {
		EventContext ectx = event.getContext();
		if (!(ectx instanceof DocumentEventContext)) {
			return;
		}
	
		AuditLogger logger = Framework.getLocalService(AuditLogger.class);
		if (logger == null) {
			getLogger().error("No AuditLogger implementation is available");
			return;
		}
	
		try {
			DocumentEventContext docCtx = (DocumentEventContext) ectx;
			DocumentModel newDoc = docCtx.getSourceDocument();
		
			DocumentModel oldDoc = newDoc.getCoreSession().getDocument(newDoc.getRef());
		
			Context context = new Context(newDoc, oldDoc, event, logger);
			processDocument(context);
		} catch (Throwable t) {
			getLogger().error("Error processing audit event", t);
		}
	}
	
	private List<LogEntry> processNewDocument(Context context) {
		List<LogEntry> entries = new ArrayList<>();

		String[] schemas = context.newDoc.getSchemas();
		for (String schema : schemas) {
			Collection<Property> properties = context.newDoc.getPropertyObjects(schema);
			for (Property property : properties) {
				String fieldName = property.getName();
				// skip system properties
				if (SYSTEM_PROPS.contains(fieldName)) {
					continue;
				}

				List<LogEntry> subEntries = processProperty(context, null, property);
				if (subEntries != null && !subEntries.isEmpty()) {
					entries.addAll(subEntries);
				}
			}
		}
		
		return entries;
	}
	
	protected void processDocument(Context context) {
		List<LogEntry> entries = new ArrayList<>();
	
		if (context.event.getName().equalsIgnoreCase(DocumentEventTypes.DOCUMENT_CREATED)) {
			List<LogEntry> subEntries = processNewDocument(context);
			if (subEntries != null && !subEntries.isEmpty()) {
				entries.addAll(subEntries);
			}
		} else {
			String[] schemas = context.newDoc.getSchemas();
			for (String schema : schemas) {
				Collection<Property> properties = context.newDoc.getPropertyObjects(schema);
				for (Property property : properties) {
					String fieldName = property.getName();
					// skip system properties
					if (SYSTEM_PROPS.contains(fieldName)) {
						continue;
					}
		
					if (property.isDirty()) {
						Property oldProperty = context.oldDoc.getProperty(fieldName);
						List<LogEntry> subEntries = processProperty(context, oldProperty, property);
						if (subEntries != null && !subEntries.isEmpty()) {
							entries.addAll(subEntries);
						}
					}
				}
			}
		}

		if (entries.size() > 0) {
			context.logger.addLogEntries(entries);
		}
	}
	
	private boolean isScalar(Property oldProperty, Property newProperty) {
		boolean result = false;
		
		if (oldProperty != null && oldProperty.isScalar()) {
			return true;
		}
		
		if (newProperty != null && newProperty.isScalar()) {
			return true;
		}
		
		return result;
	}
	
	private boolean isComplex(Property oldProperty, Property newProperty) {
		boolean result = false;
		
		if (oldProperty != null && oldProperty.isComplex()) {
			return true;
		}
		
		if (newProperty != null && newProperty.isComplex()) {
			return true;
		}
		
		return result;
	}
	
	private boolean isList(Property oldProperty, Property newProperty) {
		boolean result = false;
		
		if (oldProperty != null && oldProperty.isList()) {
			return true;
		}
		
		if (newProperty != null && newProperty.isList()) {
			return true;
		}
		
		return result;
	}
	
	private boolean isBlob(Property oldProperty, Property newProperty) {
		boolean result = false;
		
		if (oldProperty != null && oldProperty instanceof BlobProperty) {
			return true;
		}
		
		if (newProperty != null && newProperty instanceof BlobProperty) {
			return true;
		}
		
		return result;
	}
	
	private boolean isArray(Property oldProperty, Property newProperty) {
		boolean result = false;
		
		if (oldProperty != null && oldProperty instanceof ArrayProperty) {
			return true;
		}
		
		if (newProperty != null && newProperty instanceof ArrayProperty) {
			return true;
		}
		
		return result;
	}
	
	private String getXPath(Property oldProperty, Property newProperty) {
		String result = null;

		if (oldProperty != null) {
			return oldProperty.getXPath();
		}
		
		if (newProperty != null) {
			return newProperty.getXPath();
		}

		return result;
	}
	
	private String getName(Property oldProperty, Property newProperty) {
		String result = null;

		if (oldProperty != null) {
			return oldProperty.getName();
		}
		
		if (newProperty != null) {
			return newProperty.getName();
		}

		return result;
	}

	
	protected List<LogEntry> processProperty(Context context, Property oldProperty, Property newProperty) {
	
		List<LogEntry> entries = new ArrayList<>();
	
		// Handle Scalar Properties
		if (isScalar(oldProperty, newProperty)) {
			LogEntry entry = processScalarProperty(context, oldProperty, newProperty);
			if (entry != null) {
				entries.add(entry);
			}
		}

		// Handle Complex Properties
		if (isComplex(oldProperty, newProperty) && !isList(oldProperty, newProperty)) {
			if (isBlob(oldProperty, newProperty)) {
				LogEntry entry = processBlobProperty(context, oldProperty, newProperty);
				if (entry != null) {
					entries.add(entry);
				}
			} else {
				List<LogEntry> subEntries = processComplexProperty(context, oldProperty, newProperty);
				if (subEntries != null && !subEntries.isEmpty()) {
					entries.addAll(subEntries);
				}
			}
		}

		if (isList(oldProperty, newProperty)) {
			if (isArray(oldProperty, newProperty)) {
				List<LogEntry> subEntries = 
						processScalarList(context, getXPath(oldProperty, newProperty),
								oldProperty != null ? oldProperty.getValue() : null, 
								newProperty != null ? newProperty.getValue() : null);
				if (subEntries != null && !subEntries.isEmpty()) {
					entries.addAll(subEntries);
				}
			} else {
				List<LogEntry> subEntries = processComplexProperty(context, oldProperty, newProperty);
				if (subEntries != null && !subEntries.isEmpty()) {
					entries.addAll(subEntries);
				}
			}
		}

		// org.nuxeo.ecm.core.api.model.Property
		return entries;
	}
	
	protected LogEntry processScalarProperty(Context context, Property oldProperty, Property newProperty) {
		return getEntry(context, getXPath(oldProperty, newProperty),
				oldProperty != null ? oldProperty.getValue() : null,
				newProperty != null ? newProperty.getValue() : null);
	}

	/*
	 * 
	 */
	protected List<LogEntry> processScalarList(Context context, String fieldName, Serializable oldValue, Serializable newValue) {
		List<LogEntry> entries = new ArrayList<>();
	
		ArrayList<?> oldList = null;
		if (oldValue != null) {
			oldList = (ArrayList<?>) ((ArrayList<?>)oldValue).clone();
		}

		ArrayList<?> newList = null;
		if (newValue !=null) {
			newList = (ArrayList<?>) ((ArrayList<?>)newValue).clone();
		}

	
		fieldName = normalizeFieldName(fieldName);
	
		// log New/Added list items
		if (newList != null) {
			ArrayList<?> added = new ArrayList<>(newList);
			if (oldList != null) {
				added.removeAll(oldList);
			}
			for (Object addedValue : added) {
				LogEntry entry = getEntry(context, fieldName, null, (Serializable) addedValue);
				if (entry != null) {
					entry.setComment(fieldName + " : Added " + formatPropertyValue((Serializable) addedValue));
					entries.add(entry);
				}
			}
		}

		// log Old/Removed list items
		if (oldList != null) {
			ArrayList<?> removed = new ArrayList<>(oldList);
			removed.removeAll(newList);
			for (Object removedValue : removed) {
				LogEntry entry = getEntry(context, fieldName, (Serializable) removedValue, null);
				if (entry != null) {
					entry.setComment(fieldName + " : Removed " + formatPropertyValue((Serializable) removedValue));
					entries.add(entry);
				}
			}
		}

		return entries;
	}
	
	private boolean isListOfComplexType(Property property) {
		boolean result = false;
		
		if (property != null && property.isList()) {
			ListType listType = (ListType) property.getType();
			if (listType.isScalarList() == false) {
				result = true;
			}
		}

		return result;
	}
	
	private boolean isListOfScalarType(Property property) {
		boolean result = false;

		if (property != null && property.isList()) {
			ListType listType = (ListType) property.getType();
			result = listType.isScalarList();
		}

		return result;
	}
	
	private ArrayList<Property> getListAsArray(ListProperty listProperty) {
		ArrayList<Property> resultList = new ArrayList<Property>();

		if (listProperty != null) {
			Iterator<Property> properties = listProperty.listIterator();
			while (properties.hasNext()) {
				resultList.add(properties.next());
			}
		}

		return resultList;
	}

	protected List<LogEntry> processComplexProperty(Context context, Property oldProperty, Property newProperty) {
		List<LogEntry> entries = new ArrayList<>();
		
		if (isListOfComplexType(oldProperty) || isListOfComplexType(newProperty)) {
			ArrayList<Property> oldPropertyList = getListAsArray((ListProperty)oldProperty);
			long oldListSize = oldPropertyList.size();

			ArrayList<Property> newPropertyList = getListAsArray((ListProperty)newProperty);
			long newListSize = newPropertyList.size();
			
			String commentTemplate = "List %s.  %d -> %d";
			if (newListSize > oldListSize) {
				String comment = String.format(commentTemplate,
						"grew", oldPropertyList.size(), newPropertyList.size());
				entries.add(getEntry(context, getXPath(oldProperty, newProperty), comment, oldPropertyList.size(), newPropertyList.size()));
			} else if (newListSize < oldListSize) {
				String comment = String.format(commentTemplate,
						"shrank", oldPropertyList.size(), newPropertyList.size());
				entries.add(getEntry(context, getXPath(oldProperty, newProperty), comment, oldPropertyList.size(), newPropertyList.size()));
			}

			long listSize = Math.max(oldListSize, newListSize);
			for (int i = 0; i < listSize; i++) {
				Property oldListItem = (i < oldListSize) ? oldPropertyList.get(i) : null;
				Property newListItem = (i < newListSize) ? newPropertyList.get(i) : null;
				List<LogEntry> subEntries = processComplexProperty(context, oldListItem, newListItem);
				if (subEntries != null && !subEntries.isEmpty()) {
					entries.addAll(subEntries);
				}
			}

		} else if (isListOfScalarType(oldProperty) || isListOfScalarType(newProperty)) {
			entries.addAll(processScalarList(context, getXPath(oldProperty, newProperty), 
					oldProperty != null ? oldProperty.getValue() : null,
					newProperty != null ? newProperty.getValue() : null));	
		} else {
			if (newProperty != null) {
				Iterator<Property> childProperties = null;
				if (oldProperty == null) {
					childProperties = newProperty.getChildren().iterator();
				} else {
					newProperty.getDirtyChildren();
				}
				while (childProperties.hasNext()) {
					Property dirtyProperty = childProperties.next();
					entries.addAll(processProperty(context, 
							oldProperty != null ? oldProperty.get(dirtyProperty.getName()) : null,
									dirtyProperty));
				}
			} else {
				Iterator<Property> childProperties = oldProperty.getChildren().iterator();
				while (childProperties.hasNext()) {
					Property childPropery = childProperties.next();
					entries.addAll(processProperty(context, childPropery, null));
				}
			}
		}

		return entries;
	}

	protected LogEntry processBlobProperty(Context context, Property oldProperty, Property newProperty) {
		Blob oldBlob = (Blob) oldProperty.getValue();
		String oldFilename = oldBlob != null ? oldBlob.getFilename() : null;
		Blob newBlob = (Blob) newProperty.getValue();
		String newFilename = newBlob != null ? newBlob.getFilename() : null;
		return getEntry(context, oldProperty.getXPath(), oldFilename, newFilename);
	}

	protected LogEntry getEntry(Context context, String fieldName, String comment, Serializable oldValue, Serializable newValue) {
		String formatedOldValue = formatPropertyValue(oldValue);
		String formatedNewValue = formatPropertyValue(newValue);

		if (formatedOldValue == null && formatedNewValue == null) {
			// no values to log
			return null;
		}

		if (formatedOldValue != null && formatedNewValue != null) {
			if (formatedOldValue.trim().equals(formatedNewValue.trim())) {
				// old and new values are the same, so nothing to log
				return null;
			}
		}

		LogEntry entry = null;
		AuditLogger logger = context.logger;
		Event event = context.event;
		DocumentModel doc = context.newDoc;

		String cspaceUser = AuthN.get().getUserId();
		entry = logger.newLogEntry();
		entry.setEventId(context.eventID.toString());
		entry.setCategory(context.event.getName());
		entry.setEventDate(new Date(event.getTime()));
		entry.setDocUUID(doc.getRef());
		entry.setDocLifeCycle(doc.getCurrentLifeCycleState());
		entry.setPrincipalName(cspaceUser);
		entry.setRepositoryId(doc.getRepositoryName());

		fieldName = normalizeFieldName(fieldName);

		Map<String, ExtendedInfo> extended = new HashMap<>();
		extended.put(FIELD_NAME, logger.newExtendedInfo(fieldName));

		extended.put(OLD_VALUE, logger.newExtendedInfo(formatedOldValue != null ? formatedOldValue : EMPTY_VALUE));
		extended.put(NEW_VALUE, logger.newExtendedInfo(formatedNewValue != null ? formatedNewValue : EMPTY_VALUE));
		entry.setExtendedInfos(extended);

		if (comment == null) {
			entry.setComment(fieldName + " : " + (formatedOldValue != null ? formatedOldValue : EMPTY_VALUE) + " -> " +
					(formatedNewValue != null ? formatedNewValue : EMPTY_VALUE));
		} else {
			entry.setComment(comment);
		}

		return entry;
	}

	protected LogEntry getEntry(Context context, String fieldName, Serializable oldValue, Serializable newValue) {
		return getEntry(context, fieldName, null /*no comment*/, oldValue, newValue);
	}

	protected String formatPropertyValue(Serializable value) {
		String result = null;

		if (value instanceof Calendar) {
			Calendar calendar = (Calendar) value;
			Instant instant = calendar.getTime().toInstant();
			result = instant.toString();
		} else if (value != null) {
			result = value.toString();
		}

		return result;
	}

	protected String normalizeFieldName(String fieldName) {
		if (fieldName.startsWith("/")) {
			return fieldName.substring(1);
		} else {
			return fieldName;
		}
	}
	
	class Context {
		private UUID eventID;
		private DocumentModel newDoc;
		private DocumentModel oldDoc;
		private Event event;
		private AuditLogger logger;
	
		public Context(DocumentModel newDoc, DocumentModel oldDoc, Event event, AuditLogger logger) {
			this.newDoc = newDoc;
			this.oldDoc = oldDoc;
			this.event = event;
			this.logger = logger;
			this.eventID = UUID.randomUUID();
		}
	}
	
	@Override
	public boolean shouldHandleEvent(Event event) {
		return true;
	}
	
	@Override
	protected Logger getLogger() {
		return logger;
	}
}