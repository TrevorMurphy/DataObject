package com.textserv.framework.codecs;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.textserv.framework.DataObject;
import com.textserv.framework.DataObjectException;
import com.textserv.framework.DataObject.StringEncodedDataObjectList;

public class DataObjectStringCodec {
	
		static final char RECORD_SEP = '=';
		static final char GROUP_SEP = '|';
		private static final Log logger = LogFactory.getLog(DataObjectStringCodec.class.getName());

		public static void saveAsString (DataObject dO, Writer out) throws Exception {
			BufferedWriter bw = new BufferedWriter(out);
			out.write(toString(dO));
			bw.close(); // this is required, otherwise the file can't be deleted until the gc frees it			
		}
		
		public static DataObject createFromString (Reader reader) throws Exception {
			DataObject dO = new DataObject();
			return createFromString(reader, dO);
		}
		
		public static DataObject createFromString (Reader reader, DataObject dO) throws Exception {
			BufferedReader br = new BufferedReader(reader);						
			StringWriter stringWriter = new StringWriter();
			String inputLine = null;
			while ((inputLine = br.readLine()) != null ) {
				stringWriter.write(inputLine);
			}
			return fromString(stringWriter.toString());
		}
		
		public static String toString(DataObject map) {
			StringBuffer buf = new StringBuffer();
			buf.append("{");
			Iterator i = map.entrySet().iterator();
			boolean hasNext = i.hasNext();
			while (hasNext) {
				Map.Entry e = (Map.Entry) (i.next());
				Object key = e.getKey();
				Object value = e.getValue();
				encodeString(key.toString(), buf);
				buf.append(RECORD_SEP);
				if (value instanceof String) {
					encodeString((String) value, buf);
				} else if (value instanceof List) {
					buf.append(wrapList((List) value));
				} else if (value instanceof DataObject) {
					buf.append(value == map ? "(this Map)" : toString((DataObject) value));
				} else if ( value instanceof StringEncodedDataObjectList ) {
					buf.append(wrapStringEncodedDataObjectList((StringEncodedDataObjectList) value));					
				}
				hasNext = i.hasNext();
				if (hasNext) {
					buf.append(Character.toString(GROUP_SEP) + " ");
				}
			}
			buf.append("}");
			return buf.toString();
		}

		private static void encodeString(String s, StringBuffer buf) {
			buf.append('\001');
			buf.append(s);
			buf.append('\001');

		}

		protected static String wrapList(List list) {
			StringBuffer buf = new StringBuffer();
			buf.append("[");
			Iterator i = list.iterator();
			boolean hasNext = i.hasNext();
			while (hasNext) {
				Object o = i.next();
				//only strings and dataobjects supported in lists
				if ( o instanceof String ) {
					encodeString((String)o, buf);
				} else if ( o instanceof DataObject ) {
					buf.append(toString((DataObject) o));
				}
				hasNext = i.hasNext();
				if (hasNext)
					buf.append(Character.toString(GROUP_SEP) + " ");
			}
			buf.append("]");
			return buf.toString();
		}

		protected static String wrapStringEncodedDataObjectList(StringEncodedDataObjectList list) {
			StringBuffer buf = new StringBuffer();
			buf.append("[");
			Iterator<String> i = list.encodedDataObjects.iterator();
			boolean hasNext = i.hasNext();
			while (hasNext) {
				String o = i.next();
				buf.append(o);
				hasNext = i.hasNext();
				if (hasNext)
					buf.append(Character.toString(GROUP_SEP) + " ");
			}
			buf.append("]");
			return buf.toString();
		}
		
		public static DataObject fromString(String readString) throws DataObjectException {
			DataObject dataObject = new DataObject();
			fromString(readString, dataObject);
			return dataObject;
		}
		public static void fromString(String readString, DataObject dataObject) throws DataObjectException {
			if (!readString.startsWith("{")) {
				throw new DataObjectException("Syntax error reading " + readString + " as DataObject.  Should start with '{'");
			}
			DataObjectParseState state = new DataObjectParseState(dataObject);
			StringBuffer reader = new StringBuffer(readString);

			for (int i = 1; i < readString.length(); i++) {
				char c = reader.charAt(i);
				switch (c) {
					case '{':
						state.startMap(reader, i + 1);
						break;
					case '[':
						state.startList(reader, i + 1);
						break;
					case '}':
						state.endMap(reader, i + 1);
						break;
					case ']':
						state.endList(reader, i + 1);
						break;
					case '\001':  //Reached beginning of String
						int beginString = i + 1;
						int endString = readString.indexOf('\001', beginString);
						if (endString<0){
							throw new DataObjectException("Problem parsing String value "+readString.substring(beginString)+" because it had no closing \001 value");
						}
						String stringValue = reader.substring(beginString,endString);
						//todo: remove this in production
						if (stringValue.indexOf(GROUP_SEP) > 0){
							logger.warn("this string: "+stringValue+" could be trouble");
						}
						state.handleString(reader.substring(beginString, endString));
						i = endString;
						break;
					case RECORD_SEP:
						state.onEquals(reader, i + 1);
						break;
					case GROUP_SEP:
						state.onComma(reader, i + 1);
						i++; //skip the space
						break;
				}
			}
		}

		static class DataObjectParseState {
			DataObject root = null;
			StackElement currentStackElement = null;
			
			DataObjectParseState(DataObject root) {
				this.root = root;
				StackElement rootElement = new StackElement(root);
				currentStackElement = rootElement;
			}

			public void startMap(StringBuffer reader, int i) {
				StackElement mapElement = new StackElement(new DataObject());
				mapElement.parentElement = currentStackElement;
				currentStackElement = mapElement;
			}

			public void startList(StringBuffer reader, int i) {
				StackElement listElement = new StackElement(new ArrayList());
				listElement.parentElement = currentStackElement;
				currentStackElement = listElement;
			}

			public DataObject getDataObject() {
				return root;
			}

			/**
			 * Reached the end of the map
			 * if our last value is a String we need to add it to the map
			 * in all cases we need to add the completed map to its parent
			 */
			@SuppressWarnings("unchecked")
			public void endMap(StringBuffer reader, int i) throws DataObjectException {
				if (currentStackElement.lastValueType == StackElement.STRING_TYPE) {
					currentStackElement.currentDO.setString(currentStackElement.currentKey.toString(), currentStackElement.currentStringValue.toString());
				}
				StackElement parent = currentStackElement.parentElement;
				if (parent == null) {
					return;       //reached top level
				}
				if (parent.type == StackElement.MAP_TYPE) {
					parent.currentDO.setDataObject(parent.currentKey.toString(), currentStackElement.currentDO);
				} else {
					//List, just add it to list
					parent.currentList.add(currentStackElement.currentDO);
				}
				parent.resetKey();
				currentStackElement = parent;
			}

			/**
			 * at the end of a list we need to add it to the parent map
			 * (Lists can only appear in maps, not in other lists)
			 */
			@SuppressWarnings("unchecked")
			public void endList(StringBuffer reader, int i) throws DataObjectException {
				List listToEnd = currentStackElement.currentList;
				StackElement parent = currentStackElement.parentElement;
				if ( listToEnd.isEmpty()) {
					parent.currentDO.put(parent.currentKey.toString(), listToEnd);				
				} else {				
					if ( listToEnd.get(0) instanceof String ) {
						parent.currentDO.setStringList(parent.currentKey.toString(), listToEnd);
					} else {
						parent.currentDO.setDataObjectList(parent.currentKey.toString(), listToEnd);					
					}
				}
				currentStackElement = parent;
				parent.resetKey();
			}

			/**
			 * Equals tells us we're reached the end of a key and to prepare for the value
			 * if we look at the next character, we can tell what kind of value to expect
			 *
			 * @param reader
			 * @param i
			 */
			public void onEquals(StringBuffer reader, int i) {
				currentStackElement.keyFinished = true;
				char charAfterEquals = reader.charAt(i);
				switch (charAfterEquals) {
					case '{':
						currentStackElement.lastValueType = StackElement.MAP_TYPE;
						break;
					case '[':
						currentStackElement.lastValueType = StackElement.LIST_TYPE;
						break;
					default:
						currentStackElement.lastValueType = StackElement.STRING_TYPE;
				}
			}

			/**
			 * Commas tell you you have reached the end of a value (String, List or Map) and are more to come
			 * We have already added the List and Map values to their parent in the endMap and endList methods
			 * we need this to tell us we have reached the end of a String value
			 */
			public void onComma(StringBuffer reader, int i) {
				if (currentStackElement.type == StackElement.MAP_TYPE) {
					if (currentStackElement.lastValueType == StackElement.STRING_TYPE) {
						currentStackElement.currentDO.setString(currentStackElement.currentKey.toString(), currentStackElement.currentStringValue.toString());
					}
				}
				currentStackElement.resetKey();
			}

			@SuppressWarnings("unchecked")
			public void handleString(String s) {
				if (currentStackElement.type == StackElement.LIST_TYPE) {
					currentStackElement.currentList.add(s);
				} else {
					if (!currentStackElement.keyFinished) {
						if (currentStackElement.currentKey == null) {
							currentStackElement.currentKey = s;
						}
					} else {
						// append to value
						if (currentStackElement.currentStringValue == null) {
							currentStackElement.currentStringValue = s;
						}
					}
				}
			}
		}

		static class StackElement {
			static final int MAP_TYPE = 1;
			static final int LIST_TYPE = 2;
			static final int STRING_TYPE = 3;

			List currentList = null;
			DataObject currentDO = null;
			StackElement parentElement = null;
			boolean keyFinished = false;

			int type = 0;
			String currentKey = null;
			String currentStringValue = null;
			Object lastValue;
			int lastValueType = -1;

			StackElement(DataObject dO) {
				this.currentDO = dO;
				this.type = MAP_TYPE;
			}

			StackElement(List list) {
				this.currentList = list;
				this.type = LIST_TYPE;
			}

			public void resetKey() {
				this.keyFinished = false;
				this.currentKey = null;
				this.currentStringValue = null;
			}
		}
	}
