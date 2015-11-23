package de.rwh.utils.jetty;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import org.apache.log4j.xml.DOMConfigurator;

public final class Log4jInitializer
{
	public static final String PROPERTY_JETTY_LOG4J_CONFIG = "jetty.log4j.config";
	public static final String PROPERTY_JETTY_LOG4J_WATCH = "jetty.log4j.watch";
	public static final String PROPERTY_JETTY_LOG4J_WATCH_DEFAULT = "false";

	private Log4jInitializer()
	{
	}

	public static void initializeLog4j(Properties properties)
	{
		String configLocation = properties.getProperty(PROPERTY_JETTY_LOG4J_CONFIG);

		boolean watchConfig = Boolean
				.parseBoolean(properties.getProperty(PROPERTY_JETTY_LOG4J_WATCH, PROPERTY_JETTY_LOG4J_WATCH_DEFAULT));

		initializeLog4j(configLocation, watchConfig);
	}

	public static void initializeLog4j(String configLocation, boolean watchConfig)
	{
		if (configLocation == null || configLocation.isEmpty())
			throw new IllegalArgumentException("Property '" + PROPERTY_JETTY_LOG4J_CONFIG + "' not found or empty");

		if (!Files.isReadable(Paths.get(configLocation)))
			throw new IllegalArgumentException("Log4j config file '" + configLocation + "' not readable");

		if (watchConfig)
			DOMConfigurator.configureAndWatch(configLocation);
		else
			DOMConfigurator.configure(configLocation);
	}
}
