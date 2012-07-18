package org.bdgp.MMSlide;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

public class XML {
    private static DocumentBuilderFactory dbfac;
    private static DocumentBuilder docBuilder;
    private static Document doc;
    private static TransformerFactory transfac;
    static {
        try {
            dbfac = DocumentBuilderFactory.newInstance();
            docBuilder = dbfac.newDocumentBuilder();
            doc = docBuilder.newDocument();
            transfac = TransformerFactory.newInstance();
        } 
        catch (ParserConfigurationException e) {throw new RuntimeException(e);}
    }
    
    /**
     * Create a comment node.
     * @param comment The comment text.
     * @return
     */
    public static Node comment(String comment) {
        return doc.createComment(comment);
    }
    /**
     * Create a text node.
     * @param text The text.
     * @return
     */
    public static Text text(String text) {
        return doc.createTextNode(text);
    }
    /**
     * Create an element node.
     * @param name The tag name.
     * @param attrs The attribute key/values.
     * @param contents The contents of the tag.
     * @return
     */
    public static Node tag(String name, String[][] attrs, Object ... contents) {
        Map<String,String> attrmap = new HashMap<String,String>();
        for (String[] attr : attrs) {
            if (attr.length == 2) {
                attrmap.put(attr[0], attr[1]);
            }
            else {
                throw new IllegalArgumentException(
                        "Malformed attribute pair!");
            }
        }
        return tag(name, attrmap, contents);
    }
    /**
     * Create an element node.
     * @param name the tag name
     * @param attrs The attribute key/values.
     * @param contents The contents of the tag.
     * @return
     */
    public static Element tag(String name, String[] attrs, Object ... contents) {
        Map<String,String> attrmap = new HashMap<String,String>();
        if (attrs.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "Attribute pair is missing value!");
        }
        for (int i=0; i<attrs.length-1; i+=2) {
            attrmap.put(attrs[i], attrs[i+1]);
        }
        return tag(name, attrmap, contents);
    }
    /**
     * Create an element node.
     * @param name The tag name
     * @param attrs The attribute key/values.
     * @param contents The contents of the tag.
     * @return
     */
    public static Element tag(String name, Map<String,String> attrs, Object ... contents) {
        Element element = doc.createElement(name);
        for (Map.Entry<String,String> attr : attrs.entrySet()) {
            element.setAttribute(attr.getKey(), attr.getValue());
        }
        for (Object content : contents) {
            if (Node.class.isAssignableFrom(content.getClass())) {
                 element.appendChild(Node.class.cast(content));
            }
            else if (String.class.isAssignableFrom(content.getClass())) {
                Text text = doc.createTextNode(String.class.cast(content));
                element.appendChild(text);
            }
            else if (Number.class.isAssignableFrom(content.getClass())) {
                Text text = doc.createTextNode(content.toString());
                element.appendChild(text);
            }
            else {
                throw new IllegalArgumentException(
                        "Cannot determine type of content "+content.toString());
            }
        }
        return element;
    }
    
    /**
     * Create a new XML document from the provided nodes.
     * @param nodes The list of nodes to append to the document.
     * @return
     */
    public static Document xml(Node ... nodes) {
        Document doc = docBuilder.newDocument();
        for (Node node : nodes) {
            doc.appendChild(node);
        }
        return doc;
    }
    /**
     * Convert a list of nodes into an XML document string.
     * @param nodes The list of nodes.
     * @return
     */
    public static String xmlstring(Node ... nodes) {
        return xmlstring(xml(nodes));
    }
    /**
     * Convert a document into an XML document string.
     * @param doc The XML document.
     * @return
     */
    public static String xmlstring(Document doc) {
        try {
            Transformer trans = transfac.newTransformer();
            trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            trans.setOutputProperty(OutputKeys.INDENT, "yes");
            
            StringWriter sw = new StringWriter();
            StreamResult result = new StreamResult(sw);
            DOMSource source = new DOMSource(doc);
            trans.transform(source, result);
            String xmlString = sw.toString();
            return xmlString;
        } 
        catch (TransformerConfigurationException e) {throw new RuntimeException(e);}
        catch (TransformerException e) {throw new RuntimeException(e);}
    }
    /**
     * Convert a list of nodes into an XML document string reader.
     * @param nodes The list of nodes to convert.
     * @return
     */
    public static Reader xmlreader(Node ... nodes) {
        return xmlreader(xml(nodes));
    }
    /**
     * Convert an XML document into an XML document string reader.
     * @param doc The xml document.
     * @return
     */
    public static Reader xmlreader(Document doc) {
        return new StringReader(xmlstring(doc));
    }
}
