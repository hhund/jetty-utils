package de.rwh.utils.jetty;

import org.apache.logging.slf4j.Log4jLoggerFactory;
import org.apache.logging.slf4j.Log4jMDCAdapter;
import org.apache.logging.slf4j.Log4jMarkerFactory;
import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

public class Log4jServiceProvider implements SLF4JServiceProvider
{
	// to avoid constant folding by the compiler, this field must *not* be final
	public static String REQUESTED_API_VERSION = "1.8"; // !final

	private ILoggerFactory loggerFactory;
	private IMarkerFactory markerFactory;
	private MDCAdapter mdcAdapter;

	@Override
	public ILoggerFactory getLoggerFactory()
	{
		return loggerFactory;
	}

	@Override
	public IMarkerFactory getMarkerFactory()
	{
		return markerFactory;
	}

	@Override
	public MDCAdapter getMDCAdapter()
	{
		return mdcAdapter;
	}

	@Override
	public String getRequesteApiVersion()
	{
		return REQUESTED_API_VERSION;
	}

	@Override
	public void initialize()
	{
		loggerFactory = new Log4jLoggerFactory();
		markerFactory = new Log4jMarkerFactory();
		mdcAdapter = new Log4jMDCAdapter();
	}
}
