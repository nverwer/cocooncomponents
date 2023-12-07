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
 * There are two ways to send a message to a logger.
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
 * The `target` parameter can be 'log' (default), which writes to a log file, or 'console', which writes to the standard output or standard error.
 * It can also be a combination like 'console and log'.
 * The level can be 'error', 'warn', 'warning', 'info'. The default level is 'info'.
 *
 * 2. Specify a message in the XML that flows through the pipeline.
 * Configuration:
 * ```
 * <map:transformer name="my-log" src="org.apache.cocoon.transformation.MessageLoggerTransformer" logger="my-logger">
 *   <namespaceURI>my://namespace.uri/message-logger</namespaceURI>
 * </map:transformer>
 * The default transformer namespace is "http://apache.org/cocoon/message-logger/1.0".
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
  private String target;
  private String level;

  private String currentTarget;
  private String currentLevel;

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
  }

  @Override
  public void setup(SourceResolver resolver, Map objectModel, String src, Parameters params)
      throws ProcessingException, SAXException, IOException {
    target = params.getParameter("target", "");
    level = params.getParameter("level", "info");
    message = params.getParameter("message", "");
    super.setup(resolver, objectModel, src, params);
  }

  @Override
  public void startDocument() throws SAXException {
    if (message != null && message.length() > 0) {
      logMessageToTarget();
    }
    super.startDocument();
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
    message = endTextRecording();
    logMessageToTarget();
  }

  private void logMessageToTarget() {
    if (currentTarget == null) currentTarget = target;
    if (currentLevel == null) currentLevel = level;
    if (currentTarget == null || currentTarget.length() == 0 || currentTarget.matches(".*\\blog\\b.*")) {
      if ("error".equals(currentLevel)) {
        this.getLogger().error(message);
      } else if (currentLevel.startsWith("warn")) {
        this.getLogger().warn(message);
      } else {
        this.getLogger().info(message);
      }
    }
    if (currentTarget.matches(".*\\bconsole\\b.*")) {
      if ("error".equals(currentLevel)) {
        System.err.println(message);
      } else {
        System.out.println(message);
      }
    }
  }

}
