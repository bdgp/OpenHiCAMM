package org.bdgp.MMSlide;

import java.awt.Container;
import java.io.ByteArrayInputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;

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

import org.swixml.jsr296.SwingApplication;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import cookxml.cookswing.CookSwing;

public class XML {
    private static DocumentBuilderFactory dbfac;
    private static DocumentBuilder docBuilder;
    private static Document doc;
    static {
        try {
            dbfac = DocumentBuilderFactory.newInstance();
            docBuilder = dbfac.newDocumentBuilder();
            doc = docBuilder.newDocument();
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
     * Helper function to define attributes as key/value pair maps.
     * @param values
     * @return
     */
    public static Object[] attr(Object ... values) {
        if (values.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "Odd number of key/value arguments passed to attr!");
        }
        Attr[] attrs = new Attr[values.length/2];
        for (int i=0; i<values.length-1; i+=2) {
            Attr attr = doc.createAttribute(values[i].toString());
            attr.setValue(values[i+1].toString());
            attrs[i/2] = attr;
        }
        return attrs;
    }
    public static Object[] a(Object ... values) { return attr(values); }
    public static Object[] _(Object ... values) { return attr(values); }
    
    private static Element addToTag(Element element, Object ... contents) {
        for (Object content : contents) {
            if (Object[].class.isAssignableFrom(content.getClass())) {
                addToTag(element, Object[].class.cast(content));
            }
            else if (Attr.class.isAssignableFrom(content.getClass())) {
                element.setAttributeNodeNS(Attr.class.cast(content));
            }
            else if (Node.class.isAssignableFrom(content.getClass())) {
                 element.appendChild(Node.class.cast(content));
            }
            else {
                Text text = doc.createTextNode(content.toString());
                element.appendChild(text);
            }
        }
        return element;
    }
    
    /**
     * Create an element node.
     * @param name The tag name
     * @param attrs The attribute key/values.
     * @param contents The contents of the tag.
     * @return
     */
    public static Node tag(String name, Object ... contents) {
        Element element = doc.createElement(name);
        return addToTag(element, contents);
    }
    public static Node $(String name, Object ... contents) {
        return tag(name, contents);
    }
    
    /**
     * Create a new XML document from the provided nodes.
     * @param nodes The list of nodes to append to the document.
     * @return
     */
    public static Document xml(Node ... nodes) {
        Document doc = docBuilder.newDocument();
        for (Node node : nodes) {
            doc.appendChild(doc.importNode(node, true));
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
            TransformerFactory transfac= TransformerFactory.newInstance();
            transfac.setAttribute("indent-number", new Integer(2));
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
    
    /**
     * Create a swing container using the swixml2 XML description language.
     * @param container The container to fill.
     * @param nodes The XML nodes to convert to Swing objects.
     * @return the container
     */
    public static <S extends Container> S swixml2(S container, Node ... nodes) {
        return swixml2(container, xmlreader(nodes));
    }
    /**
     * Create a swing container using the swixml2 XML description language.
     * @param container The container to fill.
     * @param doc The XML document to convert to Swing objects.
     * @return the container
     */
    public static <S extends Container> S swixml2(S container, Document doc) {
        return swixml2(container, xmlreader(doc));
    }
    /**
     * Create a swing container using the swixml2 XML description language.
     * @param container The container to fill.
     * @param doc The XML StringReader to convert to Swing objects.
     * @return the container
     */
    public static <S extends Container> S swixml2(S container, Reader reader) {
        try {
            return new SwingApplication() {
                protected void startup() { }
            }.render(container, reader);
        } 
        catch (InstantiationException e) {throw new RuntimeException(e);} 
        catch (IllegalAccessException e) {throw new RuntimeException(e);} 
        catch (Exception e) {throw new RuntimeException(e);}
    }
    
    
    public static Container cookswing(Object varObj, Node ... nodes) {
        return cookswing(xml(nodes));
    }
    public static Container cookswing(Object varObj, Document doc) {
        return cookswing(xml(doc));
    }
    public static Container cookswing(Object varObj, String string) {
        return new CookSwing(varObj).render(new ByteArrayInputStream(string.getBytes()));
    }
}
