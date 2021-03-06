package de.rwth.idsg.adapter.manage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.wsdl.Definition;
import javax.wsdl.Port;
import javax.wsdl.Service;
import javax.wsdl.WSDLException;
import javax.wsdl.extensions.soap.SOAPAddress;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.util.CastUtils;
import org.apache.ws.commons.schema.XmlSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.ibm.wsdl.extensions.schema.SchemaImpl;

import de.rwth.idsg.adapter.common.MappingRoute;
import java.util.ArrayList;
import javax.wsdl.Operation;
import javax.wsdl.Part;
import javax.xml.namespace.QName;

import org.apache.ws.commons.schema.XmlSchemaAll;
import org.apache.ws.commons.schema.XmlSchemaChoice;
import org.apache.ws.commons.schema.XmlSchemaCollection;
import org.apache.ws.commons.schema.XmlSchemaComplexType;
import org.apache.ws.commons.schema.XmlSchemaElement;
import org.apache.ws.commons.schema.XmlSchemaEnumerationFacet;
import org.apache.ws.commons.schema.XmlSchemaFacet;
import org.apache.ws.commons.schema.XmlSchemaParticle;
import org.apache.ws.commons.schema.XmlSchemaSequence;
import org.apache.ws.commons.schema.XmlSchemaSimpleType;
import org.apache.ws.commons.schema.XmlSchemaSimpleTypeRestriction;
import org.apache.ws.commons.schema.XmlSchemaType;
import org.w3c.dom.Element;


/**
 * This class calls methods during initialization of the adapter to
 * obtain the WSDL interface from the URL, parse it and extract 
 * required information to specify endpoint details. Additionally, it creates 
 * a HTML page to help clients create JSON-RPC request messages. 
 * 
 * @author Sevket Gökay <sevket.goekay@rwth-aachen.de>
 * @author Lars C. Gleim <lars.gleim@rwth-aachen.de>
 *
 */

public class WSDLParser {

	final static Logger LOG = LoggerFactory.getLogger(WSDLParser.class);
	public String wsdlUrl, serviceUrl, serviceName, wsNamespace, soapPortName, dirPath;
	private Port soapPort;
	private XmlSchema schema;
	private CodeWriter code;

	/*
	public static void main(String[] args){
		WSDLParser wp = new WSDLParser();
		//wp.wsdlUrl = "http://www.xignite.com/xquotes.asmx?WSDL";
		//wp.wsdlUrl = "http://www.gcomputer.net/webservices/dilbert.asmx?WSDL";
		//wp.wsdlUrl = "http://www.ripedev.com/webservices/localtime.asmx?WSDL";
		//wp.wsdlUrl = "http://www.ripedevelopment.com/webservices/ZipCode.asmx?WSDL";
		//wp.wsdlUrl = "http://soapclient.com/xml/SQLDataSoap.WSDL";
		//wp.wsdlUrl = "http://www.thomas-bayer.com/axis2/services/CSV2XMLService?wsdl";
		//wp.wsdlUrl = "http://www.thomas-bayer.com/axis2/services/BLZService?wsdl";
		//wp.wsdlUrl = "http://graphical.weather.gov/xml/DWMLgen/wsdl/ndfdXML.wsdl";
		//wp.wsdlUrl = "http://www.webservicex.net/ConvertSpeed.asmx?WSDL";
		wp.wsdlUrl = "http://services.aonaware.com/DictService/DictService.asmx?WSDL";
		//wp.wsdlUrl = "http://footballpool.dataaccess.eu/data/info.wso?wsdl";
		// Malformed & Req. License: wp.wsdlUrl = "http://v1.fraudlabs.com/ip2locationwebservice.asmx?wsdl";
		// None Existent: wp.wsdlUrl = "http://soap.amazon.com/schemas2/AmazonWebServices.wsdl";
		//wp.wsdlUrl = "http://developer.ebay.com/webservices/latest/ebaysvc.wsdl";
		//wp.wsdlUrl = "http://s3.amazonaws.com/ec2-downloads/ec2.wsdl";
		//wp.wsdlUrl = "http://webservices.amazon.com/AWSECommerceService/DE/AWSECommerceService.wsdl"; //Fails to parse Output
		// https://www.paypalobjects.com/wsdl/PayPalSvc.wsdl
		wp.readWSDL();
	}*/
	
	/**
	 * Main method that starts the WSDL parsing process and calls other helper methods.
	 */
	public void readWSDL(){
		try {
			// Read the WSDL URL and docBase from config file
			InitialContext ctx = new InitialContext();
			wsdlUrl = (String) ctx.lookup("java:comp/env/wsdlUrl");
			//wsdlUrl = "http://services.aonaware.com/DictService/DictService.asmx?WSDL";
			LOG.info("WSDL URL: " + wsdlUrl);
			dirPath = (String) ctx.lookup("java:comp/env/dirPath");
			//dirPath = "C:\\Users\\New\\Desktop\\output\\";
			ctx.close();

			// Set up the reader
			WSDLReader reader = WSDLFactory.newInstance().newWSDLReader();
			reader.setFeature("javax.wsdl.verbose", false);
			reader.setFeature("javax.wsdl.importDocuments", true);

			// Get WSDL as a document
			Document wsdlDoc = getWSDLAsDocument();

			// Convert the WSDL document to a WSDL definition
			Definition def = reader.readWSDL(null, wsdlDoc);

			// Initialize the necessary variables for Cxf
			initializeVariables(def);

			// Create a HTML page from the WSDL document
			if(dirPath != null) convertWSDLtoHTML(wsdlDoc);
			
			// Get the XML Schema
			List<?> extensions = def.getTypes().getExtensibilityElements();
			for (Object extension : extensions) {			
				if (extension instanceof SchemaImpl){
					Element schElement = ((SchemaImpl) extension).getElement();
					schema = new XmlSchemaCollection().read(schElement);			
				}
			}
			
			getOperations();
			
		} catch (WSDLException e) {
			e.printStackTrace();
		} catch (NamingException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Gets the WSDL content from URL and creates a document from it.
	 */
	private Document getWSDLAsDocument(){
		LOG.info("Retrieving document from the WSDL URL...");
		Document doc = null;
		try{
			// Get the content from URL
			InputStream inputStream = new URL(wsdlUrl).openStream();
			InputSource inputSource = new InputSource(inputStream);
			inputSource.setEncoding("UTF-8");
			System.out.println(inputSource.getEncoding());
			inputSource.setSystemId(wsdlUrl);
			if (inputStream == null) throw new IllegalArgumentException("No content at URL.");

			// Set up the factory
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setValidating(false);

			// Read the content into a document
			DocumentBuilder builder = factory.newDocumentBuilder();
			doc = builder.parse(inputSource);
			inputStream.close();
		
		} catch (RuntimeException e){
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
	    } catch (IOException e) {
			e.printStackTrace();
		}
		return doc;
	}
	
	/**
	 * Reads the details from the WSDL definition.
	 */
	private void initializeVariables(Definition def){
		
		// Read the WSDL namespace 
		wsNamespace = def.getTargetNamespace();
		LOG.info("Service namespace: " + wsNamespace);

		// Set the constants for processing of SOAP requests
		MappingRoute.WS_NAMESPACE = wsNamespace;

		// Get the details to specify the service endpoint
		Collection<Service> services = CastUtils.cast(def.getAllServices().values());
		for (Service service : services) {
			Collection<Port> ports = CastUtils.cast(service.getPorts().values());
			for (Port port : ports) {
				List<?> extensions = port.getExtensibilityElements();
				for (Object extension : extensions) {
					if (extension instanceof SOAPAddress) {	
						serviceUrl = ((SOAPAddress) extension).getLocationURI();
						serviceName = service.getQName().getLocalPart();
						soapPortName = port.getName();	
						soapPort = port;
						LOG.info("Service address: " + serviceUrl);
						LOG.info("Service name: " + serviceName);
						LOG.info("Port name: " + soapPortName);
					}
				}
			}
		}
	}

	/**
	 * Creates a HTML page for clients using XSLT that displays information 
	 * how to access/use the Web service.
	 */
	private void convertWSDLtoHTML(Document wsdlDoc){
		LOG.info("Creating the HTML page from WSDL...");
		try {
			// Read the XSLT file
			InputStream xsltStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("wsdl-viewer.xsl");			
			if (xsltStream == null){
				LOG.info("XSLT file could not be read. Skipping the creation of the HTML page.");
				return;
			}
			Source xsltSource = new StreamSource(xsltStream);
			xsltSource.setSystemId(wsdlUrl);		
			
			// Create a transformer from the XSLT
			Transformer transformer = TransformerFactory.newInstance().newTransformer(xsltSource);
			
			// Do the transformation
			DOMSource domSource = new DOMSource(wsdlDoc);
			transformer.transform(domSource, new StreamResult(new File(dirPath, "service-details.html")));
			xsltStream.close();
			
		} catch (TransformerFactoryConfigurationError e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (TransformerConfigurationException e) {
			e.printStackTrace();
		} catch (TransformerException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Extracts the operations offered by a web service from 
	 * the WSDL definition and calls the routines for code 
	 * stub creation and respective usage documentation.
	 */
	private void getOperations(){
		
		code = new CodeWriter(wsdlUrl);
		LOG.info("**** Listing methods ****");
		
		// Iterate over all operations defined for a given soap port
		for ( Object op : soapPort.getBinding().getPortType().getOperations()) {
			Operation operation = (Operation) op;
			System.out.println(" - "+operation.getName());
			
			// Get parts from WSDL that describe the operation's parameters
			Collection<Part> inParts  = CastUtils.cast(operation.getInput().getMessage().getParts().values());
			
			// Get parts from WSDL that describe the operation's return value
			Collection<Part> outParts = CastUtils.cast(operation.getOutput().getMessage().getParts().values());
			
			String params = "", output = "";
			for (Part inPart : inParts) 
				params += getParameterDetails(inPart.getName(), inPart.getElementName(), inPart.getTypeName());	
			for (Part outPart : outParts) {
				output += getParameterDetails(outPart.getName(), outPart.getElementName(), outPart.getTypeName());	
			}
			code.addOperation(operation, params, output);
		}
		code.write(dirPath);
	}

	/**
	 * Fetches details (names and types) for a given operation parameter or return value
	 * @param name        A String representing the name of the part within the WSDL
	 * @param elementName The qualified name of the actual part to identify it uniquely if possible
	 * @param typeName    The qualified name of the parts type to use it directly if elementName is not set
	 * @return            A String encoding the parts details
	 */
	private String getParameterDetails(String name, QName elementName, QName typeName){

		XmlSchemaType param =  (elementName != null) 
				? schema.getElementByName(elementName).getSchemaType() 
				: schema.getTypeByName(typeName);
				
		if 		(param instanceof XmlSchemaComplexType) return processComplexType((XmlSchemaComplexType) param, name );
		else if (param instanceof XmlSchemaSimpleType) 	return processSimpleType( (XmlSchemaSimpleType)  param, name, isOptional(schema.getElementByName(elementName)) );
        else											return CodeWriter.keyVal( name, typeName.getLocalPart(), "");
	}
	

	
	/**
	 * Processes an XML Schema Complex Type entity. 
	 * 
	 * Due to limitations of the WSDL4J project there are various schema extensions that cannot be correctly processed. 
	 * Therefore a check for such elements is inevitable. In case unhandled attributes are detected, 
	 * a remark is added to the source code and the function returns.
	 * If no issues are detected the nested type elements of the complex type are determined and processed analogously 
	 * to the processing in the getParameterDetails(String name, QName elementName, QName typeName) function. 
	 * 
	 * @param elemType The complex entity to parse
	 * @param name     The name of this entity to use for generation
	 * @return         A string detailing the type for usage in Objective-C code
	 */
	private String processComplexType( XmlSchemaComplexType elemType, String name) {
		XmlSchemaParticle particle = elemType.getParticle();
		String ret = "";
		
		if ( particle == null ) { // WSDL4J fails parsing some advanced definitions
			if (elemType.getUnhandledAttributes() != null) {
				return CodeWriter.keyVal( name, "NIL", "Unprocessed complex type - Please refer to documentation");
			} else { // aka. no such element in the WSDL definition
				return "";
			}
		}
		
		List<?> elementList = null;
		if 			( particle instanceof XmlSchemaSequence ) 	elementList = ((XmlSchemaSequence) particle).getItems();    
		else if 	( particle instanceof XmlSchemaAll ) 		elementList = ((XmlSchemaAll) particle).getItems();
		else if		( particle instanceof XmlSchemaChoice) 		elementList = ((XmlSchemaChoice) particle).getItems();
		
		for (Object member : elementList) {
			if (member instanceof XmlSchemaElement) {
				XmlSchemaElement element = ((XmlSchemaElement) member);
				XmlSchemaType elementType = element.getSchemaType();
				
				if(elementType instanceof XmlSchemaSimpleType){
					ret += processSimpleType((XmlSchemaSimpleType) elementType, element.getName(), isOptional(element));
				} 
				else if (elementType instanceof XmlSchemaComplexType) {
					ret += "    @\"" + element.getName() + "\" : @{" + (isOptional(element)?"    // {Optional}\n":"\n") ;
					ret += processComplexType((XmlSchemaComplexType) elementType, element.getName());
					ret += "    },\n";
				} 
				
				
			}
		}
		return ret;
	}

	/**
	 * Determines the type name of an XML Schema Simple Type entity
	 * 
	 * @param type       The entity to check
	 * @param name       The name of that entity
	 * @param isOptional Whether this is defined as optional in the WSDL or not
	 * @return           The type of the parameter type as a string
	 */
	private String processSimpleType(XmlSchemaSimpleType type, String name, boolean isOptional) {
		String tmp = "";
		if( isEnumeration(type) ){
			tmp += (enumeratorValues(type));
		}else{
			tmp = (type.getName());
		}
		return CodeWriter.keyVal(name, tmp, (isOptional) ? "{Optional}" : "");
	}

	/**
	 * Return true if a simple type is a straightforward XML Schema representation of an enumeration.
	 * If we discover schemas that are 'enum-like' with more complex structures, we might
	 * make this deal with them.
	 * 
	 * @param type Simple type, possible an enumeration.
	 * @return true for an enumeration.
	 */
	public static boolean isEnumeration(XmlSchemaSimpleType type) {
		try {
		XmlSchemaSimpleTypeRestriction restriction = (XmlSchemaSimpleTypeRestriction) type.getContent();
		List<XmlSchemaFacet> facets = restriction.getFacets();
		for (XmlSchemaFacet facet : facets) {
			if (facet instanceof XmlSchemaEnumerationFacet) {
				return true;
			}
		}
		}catch(Exception e){}
		return false;
	}

	/**
	 * Retrieve the string values for an enumeration.
	 * 
	 * @param type
	 * @return
	 */  
	private static List<String> enumeratorValues(XmlSchemaSimpleType type) {
		XmlSchemaSimpleTypeRestriction restriction = (XmlSchemaSimpleTypeRestriction) type.getContent();
		List<XmlSchemaFacet> facets = restriction.getFacets();
		List<String> values = new ArrayList<String>(); 
		for (XmlSchemaFacet facet : facets) {
			XmlSchemaEnumerationFacet enumFacet = (XmlSchemaEnumerationFacet) facet;
			values.add(enumFacet.getValue().toString());
		}
		return values;
	}

	/**
	 * Determines if an element is optional
	 * @param element The element to check
	 * @return		  True if it is optional, false if not
	 */
	private static boolean isOptional(XmlSchemaElement element) {
		return !(element.getMinOccurs() != 0);
	}
	
	/**
	 * Getter for SOAP port name
	 * @return The port name
	 */
	public String getPortName(){
		return soapPortName;		
	}
	
	
	
}
