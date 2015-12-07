package de.rwh.utils.jetty;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Only for Testing
 * 
 * @author hhund
 */
public class LoggingFilter implements Filter
{
	private static final Logger logger = LoggerFactory.getLogger(LoggingFilter.class);

	private static final Pattern CONTROL_CHARACTERS_PATTERN = Pattern.compile("\\p{Cc}");

	private static final class RequestWrapper extends HttpServletRequestWrapper
	{
		private byte[] body;

		public RequestWrapper(HttpServletRequest request)
		{
			super(request);
		}

		@Override
		public ServletInputStream getInputStream() throws IOException
		{
			return new ServletInputStream()
			{
				private final InputStream in = new ByteArrayInputStream(getBody());

				@Override
				public int read() throws IOException
				{
					return in.read();
				}

				@Override
				public boolean isFinished()
				{
					return false;
				}

				@Override
				public boolean isReady()
				{
					return true;
				}

				@Override
				public void setReadListener(ReadListener readListener)
				{
				}
			};
		}

		@Override
		public BufferedReader getReader() throws IOException
		{
			throw new UnsupportedOperationException("Method not supported by this wrapper");
		}

		public byte[] getBody() throws IOException
		{
			if (body == null)
			{
				ServletInputStream s = super.getInputStream();
				body = toByteArray(s);
			}

			return body;
		}

		public String bodyAsString() throws IOException
		{
			String body = new String(getBody());

			int controlCharactersCount = 0;
			Matcher matcher = CONTROL_CHARACTERS_PATTERN.matcher(body);
			while (matcher.find())
				controlCharactersCount++;

			if (controlCharactersCount > 0)
				logger.warn("{} control character{} removed from body string representation", controlCharactersCount,
						controlCharactersCount > 1 ? "s" : "");

			return CONTROL_CHARACTERS_PATTERN.matcher(body).replaceAll("");
		}
	}

	private static final class ResponseWrapper extends HttpServletResponseWrapper
	{
		private ByteArrayOutputStream out = new ByteArrayOutputStream();

		public ResponseWrapper(HttpServletResponse response)
		{
			super(response);
		}

		@Override
		public ServletOutputStream getOutputStream() throws IOException
		{
			return new ServletOutputStream()
			{
				@Override
				public void write(int b) throws IOException
				{
					out.write(b);
					ResponseWrapper.super.getOutputStream().write(b);
				}

				@Override
				public boolean isReady()
				{
					return true;
				}

				@Override
				public void setWriteListener(WriteListener writeListener)
				{
				}
			};
		}

		@Override
		public PrintWriter getWriter() throws IOException
		{
			throw new UnsupportedOperationException("Method not supported by this wrapper");
		}

		public String getBody()
		{
			return new String(out.toByteArray());
		}
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException
	{
	}

	private static byte[] toByteArray(ServletInputStream input) throws IOException
	{
		ByteArrayOutputStream output = new ByteArrayOutputStream();

		byte[] buffer = new byte[4096];

		int n = 0;
		while (-1 != (n = input.read(buffer)))
			output.write(buffer, 0, n);

		return output.toByteArray();
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException
	{
		logger.debug("{} doFilter ...", LoggingFilter.class.getName());

		if (request instanceof HttpServletRequest && response instanceof HttpServletResponse)
		{
			RequestWrapper reqW = new RequestWrapper((HttpServletRequest) request);
			ResponseWrapper repW = new ResponseWrapper((HttpServletResponse) response);

			logger.info("Request to: " + reqW.getRequestURI());
			logger.debug("Request parameter: " + toParameter(reqW));
			logger.info("Request method: " + reqW.getMethod());
			logger.debug("Request header: " + toHeaders(reqW));
			logger.debug("Request body: " + reqW.bodyAsString());

			chain.doFilter(reqW, repW);

			logger.info("Response status: " + repW.getStatus());
			logger.debug("Response header: " + toHeaders(repW));
			logger.debug("Response body: " + repW.getBody());
		}
		else
		{
			logger.warn("Not a HttpServletRequest: no logging.");
			chain.doFilter(request, response);
			logger.warn("Not a HttpServletResponse: no logging.");
		}
	}

	private String toParameter(RequestWrapper reqW)
	{
		StringBuilder b = new StringBuilder();

		boolean first = true;
		for (Enumeration<String> names = reqW.getParameterNames(); names.hasMoreElements();)
		{
			if (first)
				first = false;
			else
				b.append("; ");

			String paramName = names.nextElement();
			b.append(paramName);
			b.append("=");
			b.append(reqW.getParameter(paramName));
		}

		return b.toString();
	}

	private String toHeaders(RequestWrapper reqW)
	{
		StringBuilder b = new StringBuilder();

		boolean first = true;
		for (Enumeration<String> names = reqW.getHeaderNames(); names.hasMoreElements();)
		{
			if (first)
				first = false;
			else
				b.append("; ");

			String headerName = names.nextElement();
			b.append(headerName);
			b.append(": ");
			b.append(reqW.getHeader(headerName));
		}

		return b.toString();
	}

	private String toHeaders(ResponseWrapper repW)
	{
		StringBuilder b = new StringBuilder();

		boolean first = true;
		for (String headerName : repW.getHeaderNames())
		{
			if (first)
				first = false;
			else
				b.append("; ");

			b.append(headerName);
			b.append(": ");
			b.append(repW.getHeader(headerName));
		}

		return b.toString();
	}

	@Override
	public void destroy()
	{
	}
}
