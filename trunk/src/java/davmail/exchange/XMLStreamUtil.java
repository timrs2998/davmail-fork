package davmail.exchange;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * XmlStreamReader utility methods
 */
public class XMLStreamUtil {
    private XMLStreamUtil() {
    }

    public static XMLInputFactory getXmlInputFactory() {
        XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        inputFactory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        inputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.TRUE);
        return inputFactory;
    }

    public static String getElementContentByLocalName(InputStream inputStream, String localName) throws IOException {
        String elementContent = null;
        XMLStreamReader reader = null;
        try {
            XMLInputFactory inputFactory = getXmlInputFactory();

            reader = inputFactory.createXMLStreamReader(inputStream);
            boolean inElement = false;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && localName.equals(reader.getLocalName())) {
                    inElement = true;
                } else if (event == XMLStreamConstants.CHARACTERS && inElement) {
                    elementContent = reader.getText();
                    inElement = false;
                }
            }
        } catch (XMLStreamException e) {
            throw new IOException(e.getMessage());
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (XMLStreamException e) {
                ExchangeSession.LOGGER.error(e);
            }
        }
        return elementContent;
    }

    public static Map<String,Map<String,String>> getElementContentsAsMap(InputStream inputStream, String rowName, String idName) throws IOException {
        Map<String,Map<String,String>> results = new HashMap<String,Map<String,String>>();
        Map<String, String> item = null;
        String currentElement = null;
        XMLStreamReader reader = null;
        try {
            XMLInputFactory inputFactory = getXmlInputFactory();
            reader = inputFactory.createXMLStreamReader(inputStream);
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && rowName.equals(reader.getLocalName())) {
                    item = new HashMap<String, String>();
                } else if (event == XMLStreamConstants.END_ELEMENT && rowName.equals(reader.getLocalName())) {
                    results.put(item.get(idName),item);
                    item = null;
                } else if (event == XMLStreamConstants.START_ELEMENT && item != null) {
                    currentElement = reader.getLocalName();
                } else if (event == XMLStreamConstants.CHARACTERS && currentElement != null) {
                    item.put(currentElement, reader.getText());
                    currentElement = null;
                }
            }
        } catch (XMLStreamException e) {
            throw new IOException(e.getMessage());
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (XMLStreamException e) {
                ExchangeSession.LOGGER.error(e);
            }
        }
        return results;
    }
}
