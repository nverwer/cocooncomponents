package org.apache.cocoon.transformation;

import java.io.IOException;
import java.util.Map;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * This component sends messages to a logger.
 *
 * There are two ways to send a message to a logger. It is possible to use both at the same time.
 *
 * 1. Specifying the message in the pipeline.
 * Configuration:
 * ```
 * <map:transformer name="my-log" src="org.apache.cocoon.transformation.MessageLoggerTransformer" logger="my-logger"/>
 * ```
 * Usage:
 * ```
 * <map:transform type="log-message">
 *   <map:parameter name="target" value="log"/>
 *   <map:parameter name="level" value="info"/>
 *   <map:parameter name="message" value="Hello, world."/>
 * </map:transform>
 * ```
 * The `target` parameter can be 'log' (default), which writes to a log file,
 * or 'log-stat', which writes document statistics to the log,
 * or 'console', which writes to the standard output or standard error.
 * It can also be a combination like 'console and log'.
 * The level can be 'error', 'warn', 'warning', 'info'. The default level is 'info'.
 *
 * 2. Specify a message in the XML that flows through the pipeline.
 * Configuration:
 * ```
 * <map:transformer name="my-log" src="org.apache.cocoon.transformation.MessageLoggerTransformer" logger="my-logger">
 *   <namespaceURI>my://namespace.uri/message-logger</namespaceURI>
 *   <level>info</level>
 *   <target>log-stat</target>
 * </map:transformer>
 * The default transformer namespace is "http://apache.org/cocoon/message-logger/1.0".
 * The level and target configurations specify defaults, and are optional.
 * ```
 * Usage:
 * ```
 * <map:transform type="log-message">
 *   <map:parameter name="target" value="log"/>
 *   <map:parameter name="level" value="info"/>
 *   <map:parameter name="namespaceURI" value="another://namespace.uri/message-logger"/>
 * </map:transform>
 * ```
 * The level can be 'error', 'warn', 'warning', 'info'. The default level is 'info'.
 * This will look for `message` elements in the transformer namespace and log their contents:
 * ```
 * <log:message target="log" level="info">Hello, world.</log:message>
 * ```
 * The level can be 'error', 'warn', 'warning', 'info'. The default level is 'info'.
 *
 * All configurations and parameters are optional, but you should at least provide a message to log.
 */
public class MessageLoggerTransformer extends AbstractSAXPipelineTransformer {

  public static String MESSAGE_LOGGER_NAMESPACE_URI = "http://apache.org/cocoon/message-logger/1.0";

  private String message;

  private String defaultTarget;
  private String defaultLevel;

  private String target;
  private String level;

  private String currentTarget;
  private String currentLevel;

  private long nrElements;
  private long nrCharacters;

  public MessageLoggerTransformer() {
    // this.defaultNamespaceURI = MESSAGE_LOGGER_NAMESPACE_URI; // see configure().
  }

  /**
   * @see org.apache.avalon.framework.configuration.Configurable#configure(org.apache.avalon.framework.configuration.Configuration)
   */
  @Override
  public void configure(Configuration configuration) throws ConfigurationException {
    super.configure(configuration);
    this.defaultNamespaceURI = configuration.getChild("namespaceURI").getValue(MESSAGE_LOGGER_NAMESPACE_URI);
    this.defaultTarget = configuration.getChild("target").getValue("");
    this.defaultLevel = configuration.getChild("level").getValue("info");
  }

  @Override
  public void setup(SourceResolver resolver, Map objectModel, String src, Parameters params)
      throws ProcessingException, SAXException, IOException {
    target = params.getParameter("target", defaultTarget);
    level = params.getParameter("level", defaultLevel);
    message = params.getParameter("message", "");
    super.setup(resolver, objectModel, src, params);
  }

  @Override
  public void startDocument() throws SAXException {
    nrElements = 0L;
    nrCharacters = 0L;
    // Log message given by the sitemap.
    if (message != null && message.length() > 0) {
      logMessageToTarget(message, "startDocument");
    }
    super.startDocument();
  }

  @Override
  public void startElement(String uri, String name, String raw, Attributes attr) throws SAXException
  {
    ++nrElements;
    super.startElement(uri, name, raw, attr);
  }

  @Override
  public void characters(char[] chars, int start, int length) throws SAXException
  {
    nrCharacters += length;
    super.characters(chars, start, length);
  }

  @Override
  public void endDocument() throws SAXException
  {
    // Log message given by the sitemap.
    if (currentTarget.matches(".*\\blog-stat\\b.*") && message != null && message.length() > 0) {
      logMessageToTarget(message, "endDocument ("+nrCharacters+" characters, "+nrElements+" elements)");
    }
    super.endDocument();
  }

  @Override
  public void startTransformingElement(String uri, String name, String raw, Attributes attr)
      throws ProcessingException, IOException, SAXException {
    currentLevel = attr.getValue("level");
    currentTarget = attr.getValue("target");
    startTextRecording();
  }


  @Override
  public void endTransformingElement(String uri, String name, String raw)
      throws ProcessingException, IOException, SAXException {
    String localMessage = endTextRecording().trim();
    // Log message given by pipeline content.
    logMessageToTarget(localMessage);
  }

  private void logMessageToTarget(String message) {
    logMessageToTarget(message, null);
  }

  private void logMessageToTarget(String message, String extra) {
    String extraMessage = message + (extra != null ? " | "+extra : "");
    if (currentTarget == null) currentTarget = target;
    if (currentLevel == null) currentLevel = level;
    if (currentTarget == null || currentTarget.length() == 0 || currentTarget.matches(".*\\blog\\b.*")) {
      if ("error".equals(currentLevel)) {
        this.getLogger().error(extraMessage);
      } else if (currentLevel.startsWith("warn")) {
        this.getLogger().warn(extraMessage);
      } else {
        this.getLogger().info(extraMessage);
      }
    }
    if (currentTarget.matches(".*\\bconsole\\b.*")) {
      if ("error".equals(currentLevel)) {
        System.err.println(extraMessage);
      } else {
        System.out.println(extraMessage);
      }
    }
  }

}
