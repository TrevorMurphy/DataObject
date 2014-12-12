package com.textserv.framework.codecs;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.textserv.framework.DataObject;
import com.textserv.framework.DataObjectException;

public class DataObjectXPath {
	private DataObject thisDO;

	protected static Log logger = LogFactory.getLog(DataObjectXPath.class
			.getName());

	public DataObjectXPath(DataObject dO) {
		thisDO = dO;
	}

	public String getValueByPath(String path) throws DataObjectException {
		boolean tolerateBadPaths = false;
		return getValueByPath(thisDO, path, tolerateBadPaths);
	}

	/**
	 * Pulls out an element an arbitrary level into the parent DO the notation
	 * "Grandparent|Parent:2|Child|Name will return the Name field of the Child
	 * DO under the THIRD (subscript starts at 0) element of the Parent list of
	 * the Grandparent DO. "|" allows DO access, ":" allows list access
	 * 
	 * @param dO
	 * @param path
	 * @return Object
	 * @throws DataObjectException
	 *             If the Path doesn't exist in the DO
	 */
	public static Object getObjectByPath(DataObject dO, String path)
			throws DataObjectException {
		boolean tolerateBadPaths = false;
		return getObjectByPath(dO, path, tolerateBadPaths);
	}

	/**
	 * Pulls out an element an arbitrary level into the parent map the notation
	 * "Grandparent|Parent:2|Child|Name will return the Name field of the Child
	 * DO under the THIRD (subscript starts at 0) element of the Parent list of
	 * the Grandparent DO. "|" allows DO access, ":" allows list access
	 * 
	 * @param dO
	 * @param path
	 * @param tolerateBadPaths
	 *            Should we throw a DataObjectException if the path doesn't
	 *            exist in the DO
	 * @return Object
	 * @throws DataObjectException
	 */
	public static Object getObjectByPath(DataObject dO, String path,
			boolean tolerateBadPaths) throws DataObjectException {
		StringTokenizer st = new StringTokenizer(path, "|");
		Object o = dO;

		while (st.hasMoreTokens()) {
			String node = st.nextToken();

			int offset = node.lastIndexOf(':');
			int subscript = -1;
			String nodeName = node;

			// parse List node and set vars offset, subscript and nodeName
			if (offset != -1) {
				// it's a compound list field
				// format is listname.#

				String num = node.substring(offset + 1);
				subscript = Integer.parseInt(num);
				String listName = node.substring(0, offset);
				nodeName = listName;
			}
			// Get the proper type from the DO as the nodeName
			DataObject ma = (DataObject) o;
			if (ma == null) {
				return processBadPath("Attempt to access non-existent node "
						+ nodeName, ma, tolerateBadPaths);

			}
			o = ma.getObject(nodeName);
			if (o == null) {
				return processBadPath("Could not find value " + nodeName, ma,
						tolerateBadPaths);

			}
			if (o instanceof String) {
				if (st.hasMoreTokens()) {
					return processBadPath("dO getValueByPath invalid path "
							+ path, dO, tolerateBadPaths);
				}
			} else if (o instanceof List) {
				if (offset > -1) { // We want to get an element out of the list
					try {
						List l = (List) o;
						o = l.get(subscript);
					} catch (Exception e) {
						return processBadPath(
								"Unable to find list element in path " + path,
								dO, tolerateBadPaths);
					}
				}

			} else if (o instanceof DataObject) {
				if (offset > -1) { // treat keyset of obj as array
					try {
						DataObject l = (DataObject) o;
						Object[] keys = l.keySet().toArray();
						o = l.get(keys[subscript]);
					} catch (Exception e) {
						processBadPath("Unable to find DataObject element in path "
							+ path, ma, tolerateBadPaths);
						return null;
					}
				}

			} else {
			}
		}
		return o;
	}

	/**
	 * Helper method to throw an Exception if we don't tolerate bad paths
	 * 
	 * @param s
	 * @param tolerateBadPaths
	 * @return
	 * @throws DataObjectException
	 */
	private static Object processBadPath(String s, DataObject DO,
			boolean tolerateBadPaths) throws DataObjectException {
		if (!tolerateBadPaths) {
			throw new DataObjectException(s);
		}
		return null;
	}

	public static String getValueByPath(DataObject dO, String path)
			throws DataObjectException {
		boolean tolerateBadPaths = false;
		return getValueByPath(dO, path, tolerateBadPaths);
	}

	/**
	 * Retrieves a String value from a DataObject by a specified path
	 * 
	 * @param dO
	 * @param path
	 * @param tolerateBadPaths
	 *            whether to return null(true) or throw and exception if the
	 *            value isn't found
	 * @return
	 * @throws DataObjectException
	 */
	public static String getValueByPath(DataObject dO, String path,
			boolean tolerateBadPaths) throws DataObjectException {
		StringTokenizer st = new StringTokenizer(path, "|");
		Object o = dO;
		String node = st.nextToken();

		while (true) {
			int offset = node.lastIndexOf(':');
			int subscript = -1;
			String nodeName = node;

			// parse List node and set vars offset, subscript and nodeName
			if (offset != -1) {
				// it's a compound list field
				// format is listname.#

				String num = node.substring(offset + 1);
				subscript = Integer.parseInt(num);

				String listName = node.substring(0, offset);
				nodeName = listName;

			}
			// Get the proper type from the DO as the nodeName
			DataObject ma = null;
			try { 
				ma = (DataObject) o;
			}	catch ( Exception e ) {		
					processBadPath("Attempt to access non-existent node "
					+ nodeName, ma, tolerateBadPaths);
				return null;
			}
			if (ma == null) {
				processBadPath("Attempt to access non-existent node "
						+ nodeName, ma, tolerateBadPaths);
				return null;
			}
			o = ma.getObject(nodeName);

			if (o instanceof String) {
				if (st.hasMoreTokens()) {
					processBadPath("dO getValueByPath invalid path " + path,
							ma, tolerateBadPaths);
					return null;
				}
				return ((String) o);
			} else if (o instanceof List) {
				try {
					List l = (List) o;
					o = l.get(subscript);
					if (st.hasMoreTokens()) {
						node = st.nextToken();
					}
				} catch (Exception e) {
					processBadPath("Unable to find list element in path "
							+ path, ma, tolerateBadPaths);
					return null;

				}

			} else if (o instanceof DataObject) {
				if (offset > -1) { // treat keyset of obj as array
					try {
						DataObject l = (DataObject) o;
						Object[] keys = l.keySet().toArray();
						o = l.get(keys[subscript]);
					} catch (Exception e) {
						processBadPath("Unable to find DataObject element in path "
							+ path, ma, tolerateBadPaths);
						return null;
					}
				}
				if (st.hasMoreTokens()) {
					node = st.nextToken();
				}
			} else if (!st.hasMoreTokens()) {
				processBadPath("using getValueByPath, couldn't find path "
						+ path, dO, tolerateBadPaths);
				return null;
			}
		}
	}

	public static String getValueByPath(DataObject dO, String path,
			String defaultValue) {
		try {
			String value = getValueByPath(dO, path, true);
			if (value == null) {
				return (defaultValue);
			}
			return value;
		} catch (Exception ex) {
			return (defaultValue);
		}
	}

	/**
	 * sets a value in an arbitrary location in a DataObject check out
	 * getValueByPath for more details on notation
	 * 
	 * @param path
	 * @param value
	 * @throws DataObjectException
	 */
	public void setValueByPath(String path, String value)
			throws DataObjectException {
		setValueByPath(thisDO, path, value);
	}

	public static void setValueByPath(DataObject dO, String path, String value)
			throws DataObjectException {
		setObjectByPath(dO, path, value);
	}

	@SuppressWarnings("unchecked")
	public static void setObjectByPath(DataObject dO, String path, Object value)
			throws DataObjectException {
		StringTokenizer st = new StringTokenizer(path, "|");
		Object o = dO;
		String node = st.nextToken();

		while (true) {
			if (!st.hasMoreTokens()) // Stopping condition
			{
				if (!(o instanceof DataObject)) {
					throw (new DataObjectException(
							"Last element in dO setValueByPath is not a DO"));
				}
				DataObject n = (DataObject) o;
				n.put(node, value);
				return;
			}

			int dotSubscript = node.indexOf(':');
			if (dotSubscript == -1) {
				// the next node to be got is a DO
				DataObject mo = (DataObject) o;
				DataObject p = mo.getDataObject(node);

				if (p == null) {
					// there's no DO with this name, so we need to create it
					p = new DataObject();
					mo.setDataObject(node, p);
				}
				o = p;
				node = st.nextToken();
			} else {
				// the next node to be got is a list

				String nodeName = node.substring(0, dotSubscript);
				int subscript = Integer.parseInt(node
						.substring(dotSubscript + 1));
				DataObject mo = (DataObject) o;
				List p = mo.getDataObjectList(nodeName);

				if (p == null) {
					// // there's no List with this name, so we need to create
					// it
					p = new ArrayList();

				}
				mo.setDataObjectList(nodeName, p);
				int size = p.size();
				if (subscript + 1 > size) {
					for (int i = size; i < subscript + 1; i++) {
						p.add(i, new DataObject());
					}
				}
				o = p.get(subscript);
				node = st.nextToken();
			}
		}
	}

}
