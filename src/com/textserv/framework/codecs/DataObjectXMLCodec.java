package com.textserv.framework.codecs;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jdom.Attribute;
import org.jdom.CDATA;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import com.textserv.framework.DataObject;
import com.textserv.framework.DataObjectException;

public class DataObjectXMLCodec {
	protected static Log logger = LogFactory.getLog(DataObjectXMLCodec.class.getName());
	protected static boolean useAttributes = false;
	
	private static boolean getDefaultAttributeMode() {
		return useAttributes;
	}
	
	public static void saveAsXML (DataObject dO, Writer out) throws Exception {
		// create a JDOM
		Document jdomDocument = new Document();
		Element root = new Element("DataObject");

		// depth first traversal of DO, creating JDOM nodes

		Iterator i = dO.keySet().iterator();

		while (i.hasNext()) {
			String tag = (String) i.next();
			Object value = dO.get(tag);
			Object el = getXmlForm (tag, value, getDefaultAttributeMode());
			if (el == null) {
				// ignore
			} else if (el instanceof Element) {
				root.addContent((Element)el);
			} else if ( el instanceof Attribute ) {
				root.setAttribute((Attribute)el);
			} else // assume it's a CDATA
			{
				root.addContent((CDATA)el);
			}

		}

		jdomDocument.setRootElement(root);

		// write to file
		XMLOutputter output = new XMLOutputter(Format.getPrettyFormat());

		BufferedWriter bw = new BufferedWriter(out);
		output.output(jdomDocument, bw);

		bw.close(); // this is required, otherwise the file can't be deleted until the gc frees it
	}

	public static Object getXmlForm(String tag, Object o, boolean useAttributes) {
		if (tag == null || o == null) {
			return null;
		}

		if (o instanceof String) {
			try {
				if( useAttributes ) {
					Attribute s = new Attribute(tag, (String)o);
					return s;
				} else {
					Element s = new Element(tag);
					s.setText((String)o);
					return s;					
				}
			} catch (Exception ex) {
				// assume it's because of character encoding
				//for now ignore fields which fail this validation
				// replace the unprintable 0x1 character with a |

				CDATA s = new CDATA(tag);
				String norm = (String)o;
				s.setText(norm.replace('\001', '|'));
				return (s);
			}
		} else if (o instanceof List) {
			Element s = new Element(tag);
			s.setAttribute(new Attribute("type", "list"));
			List l = (List)o;
			Iterator i = l.iterator();
			int counter = 1;
			while (i.hasNext()) {
				Object ox = getXmlForm(tag + ".child." + counter, i.next(), false);//never use attributes for list elements
				if (ox instanceof Element) {
					s.addContent((Element)ox);
				} else if ( ox instanceof Attribute ) {
					s.setAttribute((Attribute)ox);					
				} else {
					s.addContent((CDATA)ox);
				}
				counter++;
			}
			return (s);
		} else if (o instanceof DataObject) {
			Element s = new Element(tag);
			s.setAttribute(new Attribute("type", "dataObject"));
			DataObject m = (DataObject)o;
			Iterator i = m.keySet().iterator();
			while (i.hasNext()) {
				String t = (String)i.next();
				Object ox = getXmlForm(t, m.get(t), getDefaultAttributeMode());
				if (ox == null) {
					// do nothing
				}
				else if (ox instanceof Element) {
					s.addContent((Element)ox);
				} else if ( ox instanceof Attribute ) {
					s.setAttribute((Attribute)ox);					
				} else // assume it's a CDATA
				{
					s.addContent((CDATA)ox);
				}
			}
			return (s);
		} else {
			logger.error("DataObjectXMLCodec.getXmlForm Unsupported element in dataobject type " + o.getClass().toString());
			return (new Element("Error"));
		}

	}


	/**
	 *	Creates a DataObject from an XML InputStream source
	 *	@param	File
	 *	@return	DataObject
	 *	@throws	Exception
	 */
	public static DataObject createFromXML (Reader reader) throws DataObjectException {
		// create JDOM from file
		DataObject dO = new DataObject();
		return createFromXML(reader, dO);
	}
	
	public static DataObject createFromXML (Reader reader, DataObject dO) throws DataObjectException {
		// create JDOM from file
		try {
			SAXBuilder builder = new SAXBuilder(false);  // should be true for validation
			BufferedReader br = new BufferedReader(reader);
			Document doc = builder.build(br);

			// breadth first traversal of DOM, creating DO features
			Element rootElement = doc.getRootElement();
			// name is the name of the root element
			loadAttributes(rootElement, dO);

			// iterate across elements and get format for each of them
			List l = rootElement.getChildren();

			Iterator i = l.iterator();

			while (i.hasNext()) {
				Element e = (Element)i.next();
				Object o = getDOForm(e);
				if (o != null) {
					dO.put(e.getName(), o);
				}
			}
			br.close();
		}
		catch(Exception e) {
			logger.error("DataObjectXMLCodec.CreateFromXML Exception in creating DataObject from XML file "+reader,e);
			throw new DataObjectException(e);
		}
		return dO;
	}

	private static void loadAttributes(Element el, DataObject dO) throws Exception {
		List attributes = el.getAttributes();
		for ( Iterator i = attributes.iterator(); i.hasNext(); ) {
			Attribute attribute = (Attribute)i.next();
			String attributeName = attribute.getName();
			if ( !attributeName.equals("type") ) {
				dO.put(attribute.getName(), attribute.getValue());
			}
		}
	}
	/**
	 *	Creates an Object that can be embedded in a DataObject from an XML element
	 */
	@SuppressWarnings("unchecked")
	private static Object getDOForm (Element el) throws Exception {
		String type = el.getAttributeValue("type");
		if ( type == null && !el.getAttributes().isEmpty()) {
			type = "dataObject";
		}
		if (type == null || type.equalsIgnoreCase("string")) {
			// it's a leaf node, default to string
			return (el.getText());
		}
		if (type.equalsIgnoreCase("list")) {
			// go through each element
			Vector newList = new Vector();
			Iterator i = el.getChildren().iterator();
			while (i.hasNext()) {
				Element c = (Element)i.next();
				newList.add(getDOForm(c));
			}
			return (newList);
		}
		if (type.equalsIgnoreCase("dataObject")) {
			// go through each element
			DataObject subDO = new DataObject();
			loadAttributes(el, subDO);
			Iterator i = el.getChildren().iterator();
			while (i.hasNext()) {
				Element c = (Element)i.next();
				subDO.put(c.getName(), getDOForm(c));
			}
			return (subDO);
		}
		throw (new Exception("Unknown data type"));
	}
}
