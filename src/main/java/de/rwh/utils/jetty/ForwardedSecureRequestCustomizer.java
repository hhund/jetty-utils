package de.rwh.utils.jetty;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConfiguration.Customizer;
import org.eclipse.jetty.server.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.rwh.utils.crypto.io.PemIo;

public class ForwardedSecureRequestCustomizer implements Customizer
{
	public static final String X_CLIENT_CERT_HEADER = "X-ClientCert";

	private static final String CERT_BEGIN = "-----BEGIN CERTIFICATE-----";
	private static final String CERT_END = "-----END CERTIFICATE-----";

	private static final Logger logger = LoggerFactory.getLogger(ForwardedSecureRequestCustomizer.class);

	@Override
	public void customize(Connector connector, HttpConfiguration channelConfig, Request request)
	{
		X509Certificate clientCert = getClientCert(request);
		
		if (clientCert != null)
			request.setAttribute("javax.servlet.request.X509Certificate", new X509Certificate[] { clientCert });
	}

	private X509Certificate getClientCert(Request request)
	{
		String clientCertString = request.getHeader(X_CLIENT_CERT_HEADER);

		if (clientCertString == null)
		{
			logger.warn("No {} header found", X_CLIENT_CERT_HEADER);
			return null;
		}
		if (clientCertString.isEmpty())
		{
			logger.warn("{} header empty", X_CLIENT_CERT_HEADER);
			return null;
		}
		if (!clientCertString.startsWith(CERT_BEGIN))
		{
			logger.warn("{} header does not start with {}", X_CLIENT_CERT_HEADER, CERT_BEGIN);
			return null;
		}
		if (!clientCertString.endsWith(CERT_END))
		{
			logger.warn("{} header does not end with {}", X_CLIENT_CERT_HEADER, CERT_END);
			return null;
		}

		String s = CERT_BEGIN + clientCertString.replace(CERT_BEGIN, "").replace(CERT_END, "").replaceAll(" ", "\n")
				+ CERT_END;

		try
		{
			return PemIo.readX509CertificateFromPem(s);
		}
		catch (CertificateException | IOException e)
		{
			return null;
		}
	}
}
