package com.textserv.framework.codecs;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.textserv.framework.DataObject;
import com.textserv.framework.DataObjectException;

public class DataObjectDataInputOutputCodec {
	
	static final char RECORD_SEP = '=';
	static final char GROUP_SEP = ',';
	static final char OPEN_DO = '{';
	static final char CLOSE_DO = '}';
	static final char SPACE = ' ';
	static final char STRING_IND = '\001';
	static final char OPEN_LIST = '[';
	static final char CLOSE_LIST = ']';
	static final char endEncoding = '~';

	public static void encode(DataObject map, DataOutput out) throws IOException {
		encode(map, out, true);
	}

	private static void encode(DataObject map, DataOutput out, boolean endEncodingIndicator ) throws IOException {
		out.writeChar(OPEN_DO);
		Iterator i = map.entrySet().iterator();
		boolean hasNext = i.hasNext();
		while (hasNext) {
			Map.Entry e = (Map.Entry) (i.next());
			Object key = e.getKey();
			Object value = e.getValue();
			encodeString(key.toString(), out);
			out.writeChar(RECORD_SEP);
			if (value instanceof String) {
				encodeString((String) value, out);
			} else if (value instanceof List) {
				wrapList((List)value, out);
			} else if (value instanceof DataObject) {
				encode((DataObject)value, out, false);
			}
			hasNext = i.hasNext();
			if (hasNext) {
				out.writeChar(GROUP_SEP);
			}
		}
		out.writeChar(CLOSE_DO);
		if ( endEncodingIndicator ) {
			out.writeChar(endEncoding);			
		}
	}

	private static void encodeString(String s, DataOutput out) throws IOException {
		out.writeChar(STRING_IND);
		out.writeUTF(s);
		out.writeChar(STRING_IND);
	}

	protected static void wrapList(List list, DataOutput out) throws IOException {
		out.writeChar(OPEN_LIST);
		Iterator i = list.iterator();
		boolean hasNext = i.hasNext();
		while (hasNext) {
			Object o = i.next();
			//only strings and dataobjects supported in lists
			if ( o instanceof String ) {
				encodeString((String)o, out);
			} else if ( o instanceof DataObject ) {
				encode((DataObject)o, out, false);
			}
			hasNext = i.hasNext();
			if (hasNext) {
				out.writeChar(GROUP_SEP);
			}
		}
		out.writeChar(CLOSE_LIST);
	}
	
	public static DataObject decode(DataInput in) throws DataObjectException, IOException {
		DataObject dataObject = new DataObject();
		decode(in, dataObject);
		return dataObject;
	}
	
	public static void decode(DataInput in, DataObject dataObject) throws DataObjectException, IOException {
		DataObjectParseState state = new DataObjectParseState(dataObject);
		boolean finished = false;
		boolean readNext = true;
		char nextChar = in.readChar();
		if (nextChar != OPEN_DO ) {
			throw new DataObjectException("Syntax error reading as DataObject.  Should start with '{'");
		}
		
		while(!finished) {
			if ( readNext ) {
				nextChar = in.readChar();
			} else {
				readNext = true;
			}
			switch (nextChar) {
				case OPEN_DO:
					state.startMap();
					break;
				case OPEN_LIST:
					state.startList();
					break;
				case CLOSE_DO:
					state.endMap();
					break;
				case CLOSE_LIST:
					state.endList();
					break;
				case STRING_IND:  //Reached beginning of String
					String stringValue = in.readUTF();
					char cSInd = in.readChar();
					if (cSInd != STRING_IND){
						throw new DataObjectException("Problem parsing String value "+stringValue+" because it had no closing \001 value");
					}
					state.handleString(stringValue);
					break;
				case RECORD_SEP:
					nextChar = state.onEquals(in);
					readNext = false;
					break;
				case GROUP_SEP:
					state.onComma();
					break;
				case endEncoding:
					finished = true;
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

		public void startMap() {
			StackElement mapElement = new StackElement(new DataObject());
			mapElement.parentElement = currentStackElement;
			currentStackElement = mapElement;
		}

		public void startList() {
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
		public void endMap() throws DataObjectException {
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
		public void endList() throws DataObjectException {
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
		public char onEquals(DataInput in) throws IOException {
			currentStackElement.keyFinished = true;
			char charAfterEquals = in.readChar();
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
			return charAfterEquals;
		}

		/**
		 * Commas tell you you have reached the end of a value (String, List or Map) and are more to come
		 * We have already added the List and Map values to their parent in the endMap and endList methods
		 * we need this to tell us we have reached the end of a String value
		 */
		public void onComma() {
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
