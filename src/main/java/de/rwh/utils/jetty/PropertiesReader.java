package de.rwh.utils.jetty;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class PropertiesReader
{
	private PropertiesReader()
	{
	}

	public static Properties read(Path propertiesFile, Charset encoding)
	{
		Properties properties = new Properties();

		try (Reader reader = new InputStreamReader(Files.newInputStream(propertiesFile), encoding))
		{
			properties.load(reader);
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}

		return properties;
	}
}
