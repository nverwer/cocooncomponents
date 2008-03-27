package org.apache.cocoon.transformation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.MimetypesFileTypeMap;
import javax.mail.Authenticator;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.mail.datasource.SourceDataSource;
import org.apache.cocoon.xml.AttributesImpl;
import org.apache.excalibur.source.Source;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * This Transformer is capable of sending emails with inline images, but without attachments.
 * You may parameterize it in several ways.
 * In the <code>map:sitemap/map:components/map:transformers</code> declaration:
 * 
 * <pre>
 * &lt;map:transformer name="mail" src="org.apache.cocoon.transformation.HtmlMailTransformer" &gt;
 *   &lt;smtphost&gt;localhost&lt;/smtphost&gt;
 *   &lt;smtpport&gt;25&lt;/smtpport&gt;
 *   &lt;from-address&gt;someone@somehost.nl&lt;/from-address&gt;
 * &lt;/map:transformer&gt;
 * </pre>
 * 
 * <dl>
 *   <dt>smtphost</dt><dd>Optional. The host name or IP address of your SMTP host, default is 'localhost'.</dd>
 *   <dt>smtpport</dt><dd>Optional. The SMTP port, default is 25.</dd>
 *   <dt>user</dt><dd>Optional. The user to authenticate to the SMTP server. Default is no user.</dd>
 *   <dt>password</dt><dd>Optional. The password for the SMTP user. Default is no password.</dd>
 *   <dt>from-address</dt><dd>Optional. The email address of the sender. Default is no address.</dd>
 * </dl>
 * 
 * The HtmlMailTransformer may be used in a pipeline without parameters:
 * 
 * <pre>
 * &lt;map:transform type="mail"/&gt;
 * </pre>
 * 
 * Optionally, the following sitemap parameters can be used:
 * <dl>
 *   <dt>smtphost</dt><dd>Optional. The host name or IP address of your SMTP host, overrides the component-configuration setting.</dd>
 *   <dt>smtpport</dt><dd>Optional. The SMTP port, overrides the component-configuration setting.</dd>
 *   <dt>from-address</dt><dd>Optional. The email address of the sender, overrides the component-configuration setting.</dd>
 *   <dt>image-base-uri</dt><dd>Optional. The base-uri of included images, defaults to none (use absolute paths).</dd>
 * </dl>
 * 
 * The input document should contain one or more &lt;mail&gt; elements in the
 * "http://apache.org/cocoon/transformation/sendmail" namespace.
 * 
 * <i>Example:</i>
 * <pre>
 * &lt;mail:mail to="recipient@somedomain.com" xmlns:mail="http://apache.org/cocoon/transformation/sendmail"&gt;
 *   &lt;html&gt;
 *     &lt;body&gt;
 *       &lt;b&gt;Place the html-content of the email inside the mail-element.&lt;/b&gt;
 *     &lt;/body&gt;
 *   &lt;/html&gt;
 * &lt;/mail&gt;
 * </pre>
 * 
 * The &lt;mail&gt; element has the following attributes:
 * <dl>
 *   <dt>to</dt><dd>Required. The email address of the recipient of the email.</dd>
 *   <dt>subject</dt><dd>Required. The subject of the email.</dd>
 *   <dt>image-base-uri</dt><dd>Optional. The base-uri of included images, overrides the setting in the sitemap parameter.</dd>
 * </dl>
 * 
 * The result of applying this transformer is a <ok> or <error> element in the "http://apache.org/cocoon/transformation/sendmail" namespace.
 * If an error occurred, the error element has the 'to' and 'subject' attributes of the original <mail> element, plus a 'reason' attribute, which may be:
 * <dl>
 *   <dt>failed</dt><dd>Sending mail failed for no specific reason.</dd>
 * </dl>
 * The error message is in the content of the error element. The message and stacktrace will be logged.
 * 
 * @author <a href='mailto:bfroklage@be-value.nl'>Bart Froklage</a>
 * @author <a href='mailto:nverwer@be-value.nl'>Nico Verwer</a>
 */
public class HtmlMailTransformer extends AbstractSAXTransformer {
  
  public static final String NAMESPACE = "http://apache.org/cocoon/transformation/sendmail";
  public static final String PARAM_SMTPHOST = "smtphost";
  public static final String DEFAULT_SMTPHOST = "localhost";
  public static final String PARAM_SMTPPORT = "smtpport";
  public static final String DEFAULT_SMTPPORT = "25";
  public static final String PARAM_SMTPUSER = "user";
  public static final String PARAM_SMTPPASSWORD = "password";
  public static final String PARAM_FROM_ADDRESS = "from-address";
  public static final String DEFAULT_FROM_ADDRESS = "";
  public static final String PARAM_IMAGE_BASE_URI = "image-base-uri";
  public static final String DEFAULT_IMAGE_BASE_URI = "";
  public static final String PARAM_TOADDRESS = "to";
  public static final String PARAM_SUBJECT = "subject";
  public static final String ELEMENT_MAIL = "mail";
  
  /* Instance variables for the settings/properties of the mail.*/
  private String smtpHost;
  private String smtpPort;
  private String smtpUser;
  private String smtpPassword;
  private String fromAddress;
  private String imageBaseUri;
  protected String toAddress;
  protected String subject;
  
  /* List to store the images. */
  private List<String> images;
  /* The namespace-prefix of the input document. */
  private String namespacePrefix;
  
  @Override
  public void configure(Configuration configuration) throws ConfigurationException {
    /* Get settings from configuration elements or defaults. */
    smtpHost = configuration.getChild(PARAM_SMTPHOST).getValue(DEFAULT_SMTPHOST);
    smtpPort = configuration.getChild(PARAM_SMTPPORT).getValue(DEFAULT_SMTPPORT);
    smtpUser = configuration.getChild(PARAM_SMTPUSER).getValue("");
    smtpPassword = configuration.getChild(PARAM_SMTPPASSWORD).getValue("");
    fromAddress = configuration.getChild(PARAM_FROM_ADDRESS).getValue(DEFAULT_FROM_ADDRESS);
    imageBaseUri = DEFAULT_IMAGE_BASE_URI; // no config for this one
  }
  
  public void setup(SourceResolver resolver, Map objectModel, String src, Parameters par)
  throws ProcessingException, SAXException, IOException {
    super.setup(resolver, objectModel, src, par);
    /* Set the transformer namespace. */
    namespaceURI = NAMESPACE;
    /* Get settings. */
    smtpHost = par.getParameter(PARAM_SMTPHOST, smtpHost);
    smtpPort = par.getParameter(PARAM_SMTPPORT, smtpPort);
    smtpUser = par.getParameter(PARAM_SMTPUSER, smtpUser);
    smtpPassword = par.getParameter(PARAM_SMTPPASSWORD, smtpPassword);
    fromAddress = par.getParameter(PARAM_FROM_ADDRESS, fromAddress);
    imageBaseUri = par.getParameter(PARAM_IMAGE_BASE_URI, imageBaseUri);
    /* Initialize variables. */
    images = new ArrayList<String>();
  }
  
  @Override
  public void startTransformingElement(String namespaceURI, String localName, String qName, Attributes attr)
  throws ProcessingException, IOException, SAXException {
    if (localName.equals(ELEMENT_MAIL)) {
      namespacePrefix = qName.substring(0, qName.indexOf(':')+1);
      toAddress = attr.getValue(PARAM_TOADDRESS);
      subject = attr.getValue(PARAM_SUBJECT);
      imageBaseUri = (attr.getValue(PARAM_IMAGE_BASE_URI) != null ? attr.getValue(PARAM_IMAGE_BASE_URI) : imageBaseUri);
      /* Start recording the body of the email. */
      startSerializedXMLRecording(new Properties());
    } else {
      /* No action required. Throw away all elements in our namespace. Do not send them to super, because the result is infinite recursion. */
    }
  }
  
  public void endTransformingElement(String namespaceURI, String localName, String qName)
  throws ProcessingException, IOException, SAXException {
    if (localName.equals(ELEMENT_MAIL)) {
      try {
        sendEmail(endSerializedXMLRecording());
        contentHandler.startElement(NAMESPACE, "ok", namespacePrefix+"ok", EMPTY_ATTRIBUTES);
        contentHandler.endElement(NAMESPACE, "ok", namespacePrefix+"ok");
        getLogger().info("Sending mail to "+toAddress+" succeeded.");
      } catch (Exception e) {
        AttributesImpl attrs = new AttributesImpl();
        attrs.addCDATAAttribute(PARAM_TOADDRESS, toAddress);
        attrs.addCDATAAttribute(PARAM_SUBJECT, subject);
        attrs.addCDATAAttribute("reason", "failed");
        contentHandler.startElement(NAMESPACE, "error", namespacePrefix+"error", attrs);
        String errorMessage = e.getMessage();
        contentHandler.characters(errorMessage.toCharArray(), 0, errorMessage.length());
        contentHandler.endElement(NAMESPACE, "error", namespacePrefix+"error");
        getLogger().error("Sending mail to "+toAddress+" failed.", e);
      }
    }
  }
  
  @Override
  public void startElement(String namespaceURI, String localName, String qName, Attributes attr) throws SAXException {
    /* Process image, so it can be included with the email. */
    if (localName.equalsIgnoreCase("img")) {
      AttributesImpl newAttr = new AttributesImpl(attr);
      for (int i = 0; i < newAttr.getLength(); ++i) {
        if (newAttr.getQName(i).equalsIgnoreCase("src")) {
          String imageSource = imageBaseUri + "/" + newAttr.getValue(i);
          imageSource.replaceAll("//", "/"); // Remove accidental "//"."
          imageSource.replaceAll("/[^/]+/\\.\\.", ""); // Walk up ".." in the path.
          images.add(imageSource);
          newAttr.setValue(i, "cid:" + String.valueOf(images.size()-1));
        }
      }
      super.startElement(namespaceURI, localName, qName, newAttr);
    } else {
      super.startElement(namespaceURI, localName, qName, attr);
    }
  }
  
  @Override
  public void endElement(String namespaceURI, String localName, String qName)
  throws SAXException {
    super.endElement(namespaceURI, localName, qName);
  }
  
  protected void sendEmail(String body) throws Exception {
    Properties props = new Properties();
    props.setProperty("mail.transport.protocol", "smtp");
    props.setProperty("mail.smtp.host", smtpHost);
    props.setProperty("mail.smtp.port", smtpPort);
    props.setProperty("mail.smtp.localhost","localhost");

    Authenticator auth = null;
    if (smtpUser.length() > 0) {
      props.put("mail.smtp.auth", "true");
      auth = new Authenticator() {
        public PasswordAuthentication getPasswordAuthentication() {
          return new PasswordAuthentication(smtpUser, smtpPassword);
        }
      };
      props.setProperty("mail.smtp.user", smtpUser);
      //props.setProperty("mail.password", smtpPassword);
    }
    Session session = Session.getDefaultInstance(props, auth);
    //session.setDebug(true);
    Message message = new MimeMessage(session);
    /* Fill the headers */
    message.setSubject(subject);
    message.setFrom(new InternetAddress(this.fromAddress));
    message.addRecipient(Message.RecipientType.TO, new InternetAddress(toAddress));
    /* Create new message part. */
    BodyPart messageBodyPart = new MimeBodyPart();
    messageBodyPart.setContent(body, "text/html");
    MimeMultipart multipart = new MimeMultipart("related");
    multipart.addBodyPart(messageBodyPart);
    /* Add the images. */
    for (int i = 0; i < images.size(); i++) {
      BodyPart imageBodyPart = new MimeBodyPart();
      String imageFile = images.get(i).substring(images.get(i).lastIndexOf("/")+1);
      String mimeType = new MimetypesFileTypeMap().getContentType(imageFile);
      Source img = null;
      try {
		img = resolver.resolveURI(images.get(i));
		  DataSource ds = new SourceDataSource(img, mimeType, images.get(i));
		  imageBodyPart.setDataHandler(new DataHandler(ds));
		  imageBodyPart.setHeader("Content-ID", "<" + String.valueOf(i) + ">");
		  multipart.addBodyPart(imageBodyPart);
      }
      finally {
    	  if (img != null)
    		  resolver.release(img);
      }
    }
    message.setContent(multipart);
    Transport.send(message);
    //Transport transport = session.getTransport("smtp");
    //transport.connect();
    //transport.sendMessage(message, message.getRecipients(Message.RecipientType.TO));
    //transport.close();
  }
  
}