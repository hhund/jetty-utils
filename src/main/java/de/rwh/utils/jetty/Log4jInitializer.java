package de.rwh.utils.jetty;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;

public final class Log4jInitializer
{
	public static final String PROPERTY_JETTY_LOG4J_CONFIG = "jetty.log4j.config";

	private Log4jInitializer()
	{
	}

	public static LoggerContext initializeLog4j(Properties properties)
	{
		String configLocation = properties.getProperty(PROPERTY_JETTY_LOG4J_CONFIG);

		try
		{
			return initializeLog4j(configLocation);
		}
		catch (IOException e)
		{
			throw new RuntimeException("Error while initilizing log4j", e);
		}
	}

	public static LoggerContext initializeLog4j(String configLocation) throws IOException
	{
		if (configLocation == null || configLocation.isEmpty())
			throw new IllegalArgumentException("Property '" + PROPERTY_JETTY_LOG4J_CONFIG + "' not found or empty");

		if (!Files.isReadable(Paths.get(configLocation)))
			throw new IllegalArgumentException("Log4j config file '" + configLocation + "' not readable");

		ConfigurationSource configuration = new ConfigurationSource(Files.newInputStream(Paths.get(configLocation)),
				new File(configLocation));

		return Configurator.initialize(null, configuration);
	}
}
