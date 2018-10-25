package de.rwh.utils.jetty;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;

import org.eclipse.jetty.annotations.AnnotationConfiguration;
import org.eclipse.jetty.annotations.AnnotationParser;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConfiguration.Customizer;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ContextHandler.Context;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.util.resource.PathResource;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebInfConfiguration;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.rwh.utils.auth.certificate.CertificateChecker;
import de.rwh.utils.auth.certificate.CertificateCheckerImpl;
import de.rwh.utils.crypto.CertificateHelper;
import de.rwh.utils.crypto.io.CertificateReader;

public class JettyServer extends Server
{
	private static final String PROPERTY_JETTY_HOST = "jetty.host";
	private static final String PROPERTY_JETTY_HOST_DEFAULT = "localhost";
	private static final String PROPERTY_JETTY_PORT = "jetty.port";
	private static final String PROPERTY_JETTY_PORT_HTTP_DEFAULT = "8080";
	private static final String PROPERTY_JETTY_PORT_HTTPS_DEFAULT = "8443";

	private static final String PROPERTY_JETTY_TRUSTSTORE_PEM = "jetty.truststore.pem";
	private static final String PROPERTY_JETTY_KEYSTORE_P12 = "jetty.keystore.p12";
	private static final String PROPERTY_JETTY_KEYSTORE_PASSWORD = "jetty.keystore.password";
	private static final String PROPERTY_JETTY_NEEDCLIENTAUTH = "jetty.needclientauth";
	private static final String PROPERTY_JETTY_NEEDCLIENTAUTH_DEFAULT = "false";

	private static final Logger logger = LoggerFactory.getLogger(JettyServer.class);

	public static Function<Server, ServerConnector> httpsConnector(HttpConfiguration httpConfiguration,
			Properties properties)
	{
		try
		{
			String httpsHost = properties.getProperty(PROPERTY_JETTY_HOST, PROPERTY_JETTY_HOST_DEFAULT);
			int httpsPort = Integer
					.parseInt(properties.getProperty(PROPERTY_JETTY_PORT, PROPERTY_JETTY_PORT_HTTPS_DEFAULT));

			Path trustStorePath = Paths.get(properties.getProperty(PROPERTY_JETTY_TRUSTSTORE_PEM));
			Path keyStorePath = Paths.get(properties.getProperty(PROPERTY_JETTY_KEYSTORE_P12));

			String keyStorePassword = properties.getProperty(PROPERTY_JETTY_KEYSTORE_PASSWORD);
			boolean needClientAuth = Boolean.parseBoolean(
					properties.getProperty(PROPERTY_JETTY_NEEDCLIENTAUTH, PROPERTY_JETTY_NEEDCLIENTAUTH_DEFAULT));

			KeyStore trustStore = CertificateReader.allFromCer(trustStorePath);
			KeyStore keyStore = CertificateReader.fromPkcs12(keyStorePath, keyStorePassword);

			checkServerCert(trustStore, keyStore);

			return httpsConnector(httpConfiguration, httpsHost, httpsPort, trustStore, keyStore, keyStorePassword,
					needClientAuth);
		}
		catch (NumberFormatException | NoSuchAlgorithmException | CertificateException | KeyStoreException
				| IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	private static void checkServerCert(KeyStore trustStore, KeyStore keyStore) throws KeyStoreException
	{
		CertificateChecker cc = new CertificateCheckerImpl(
				Executors.newScheduledThreadPool(1, r -> new Thread(r, "LoggerScheduledExecutorServiceJetty")));

		for (Enumeration<String> aliases = keyStore.aliases(); aliases.hasMoreElements();)
		{
			String alias = aliases.nextElement();
			Certificate certificate = keyStore.getCertificate(alias);
			if (certificate instanceof X509Certificate)
				cc.checkServerCertificateAndScheduleWarning(trustStore, (X509Certificate) certificate);
		}
	}

	private static void logCertificateConfig(int httpsPort, KeyStore trustStore, KeyStore keyStore)
	{
		if (!logger.isDebugEnabled())
			return;

		try
		{
			logger.debug("Using TrustStore for https connector {} with: {}", httpsPort,
					CertificateHelper.listCertificateSubjectNames(trustStore));
			logger.debug("Using KeyStore for https connector {} with: {}", httpsPort,
					CertificateHelper.listCertificateSubjectNames(keyStore));
		}
		catch (KeyStoreException e)
		{
			logger.warn("Error while printing TrustStore/KeyStore config", e);
		}
	}

	public static Function<Server, ServerConnector> httpsConnector(HttpConfiguration httpConfiguration,
			String httpsHost, int httpsPort, KeyStore trustStore, KeyStore keyStore, String keyStorePassword,
			boolean needClientAuth)
	{
		return server ->
		{
			logCertificateConfig(httpsPort, trustStore, keyStore);

			SslContextFactory sslContextFactory = new SslContextFactory();
			sslContextFactory.setTrustStore(trustStore);
			sslContextFactory.setKeyStore(keyStore);
			sslContextFactory.setKeyStorePassword(keyStorePassword);
			sslContextFactory.setNeedClientAuth(needClientAuth);

			SslConnectionFactory sslConnectionFactory = new SslConnectionFactory(sslContextFactory,
					HttpVersion.HTTP_1_1.asString());

			HttpConnectionFactory connectionFactory = new HttpConnectionFactory(httpConfiguration);

			ServerConnector tlsConnector = new ServerConnector(server, sslConnectionFactory, connectionFactory);
			tlsConnector.setHost(httpsHost);
			tlsConnector.setPort(httpsPort);

			return tlsConnector;
		};
	}

	public static Function<Server, ServerConnector> httpConnector(HttpConfiguration httpConfiguration,
			Properties properties)
	{
		String httpHost = properties.getProperty(PROPERTY_JETTY_HOST, PROPERTY_JETTY_HOST_DEFAULT);
		int httpPort = Integer.parseInt(properties.getProperty(PROPERTY_JETTY_PORT, PROPERTY_JETTY_PORT_HTTP_DEFAULT));

		return httpConnector(httpConfiguration, httpHost, httpPort);
	}

	public static Function<Server, ServerConnector> httpConnector(HttpConfiguration httpConfiguration, String httpHost,
			int httpPort)
	{
		return server ->
		{
			HttpConnectionFactory connectionFactory = new HttpConnectionFactory(httpConfiguration);

			ServerConnector connector = new ServerConnector(server, connectionFactory);
			connector.setHost(httpHost);
			connector.setPort(httpPort);

			return connector;
		};
	}

	public static HttpConfiguration httpConfiguration()
	{
		return httpConfiguration(null);
	}

	public static HttpConfiguration httpConfiguration(Customizer customizer)
	{
		HttpConfiguration configuration = new HttpConfiguration();
		configuration.setSendServerVersion(false);
		configuration.setSendXPoweredBy(false);
		configuration.setSendDateHeader(false);

		if (customizer != null)
			configuration.addCustomizer(customizer);

		return configuration;
	}

	public static SecureRequestCustomizer secureRequestCustomizer()
	{
		return new SecureRequestCustomizer();
	}

	public static ForwardedSecureRequestCustomizer forwardedSecureRequestCustomizer()
	{
		return new ForwardedSecureRequestCustomizer();
	}

	public static Stream<String> webInfJars(Predicate<String> filter)
	{
		return classPathEntries().filter(e -> e.endsWith(".jar")).filter(filter);
	}

	public static Stream<String> webInfClassesDirs(Predicate<String> filter)
	{
		return classPathEntries().filter(e -> e.contains("classes") || e.contains("test-classes")).filter(filter);
	}

	public static Stream<String> classPathEntries()
	{
		Set<String> entries = new HashSet<>();

		// ClassLoader cl = JettyServer.class.getClassLoader();
		// URL[] urls = ((URLClassLoader) cl).getURLs();
		// entries.addAll(Arrays.stream(urls).map(u -> u.toExternalForm()).map(JettyServer::pathStringFromURI)
		// .collect(Collectors.toList()));
		//
		// logger.debug("ClassLoader entries: {}",
		// Arrays.stream(urls).map(u -> u.toExternalForm()).collect(Collectors.toList()));

		if (Thread.currentThread().getContextClassLoader().getResourceAsStream(JarFile.MANIFEST_NAME) != null)
		{
			logger.info("Processing Manifest file");
			Manifest manifest = readManifest();
			Attributes mainAttributes = manifest.getMainAttributes();
			String manifestClassPath = mainAttributes.getValue(Attributes.Name.CLASS_PATH);

			if (manifestClassPath != null)
			{
				List<String> manifestEntries = Arrays.asList(manifestClassPath.split(" "));

				logger.debug("Manifest entries: {}", manifestEntries);
				entries.addAll(
						manifestEntries.stream().map(JettyServer::pathStringFromURI).collect(Collectors.toList()));
			}
			else
				logger.warn("Class-Path entry in Manifest file not found");
		}
		else
			logger.warn("Manifest file not found");

		entries.addAll(Arrays.asList(System.getProperty("java.class.path").split(File.pathSeparator)));

		return entries.stream();
	}

	public static String pathStringFromURI(String uriString)
	{
		try
		{
			URI uri = new URI(uriString);
			return uri.getScheme() == null ? Paths.get(uriString).toString() : Paths.get(uri).toString();
		}
		catch (URISyntaxException e)
		{
			throw new RuntimeException(e);
		}
	}

	private static Manifest readManifest()
	{
		try
		{
			return new Manifest(
					Thread.currentThread().getContextClassLoader().getResourceAsStream(JarFile.MANIFEST_NAME));
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	public static ErrorHandler defaultErrorHandler()
	{
		return new ErrorHandler();
	}

	public static ErrorHandler statusCodeOnlyErrorHandler()
	{
		return new ErrorHandler()
		{
			protected void writeErrorPage(javax.servlet.http.HttpServletRequest request, java.io.Writer writer,
					int code, String message, boolean showStacks) throws IOException
			{
			}
		};
	}

	private final Context servletContext;
	private final WebAppContext webAppContext;

	@SafeVarargs
	public JettyServer(Function<Server, ServerConnector> connector, ErrorHandler errorHandler, String contextPath,
			List<Class<?>> initializers, Properties initParameter, Stream<String> webInfClassesDirs,
			Stream<String> webInfJars, Class<? extends Filter>... additionalFilters)
	{
		this(Collections.singletonList(connector), errorHandler, contextPath, initializers, initParameter,
				webInfClassesDirs, webInfJars, additionalFilters);
	}

	@SafeVarargs
	public JettyServer(List<Function<Server, ServerConnector>> connectors, ErrorHandler errorHandler,
			String contextPath, List<Class<?>> initializers, Properties initParameter, Stream<String> webInfClassesDirs,
			Stream<String> webInfJars, Class<? extends Filter>... additionalFilters)
	{
		WebAppContext context = new WebAppContext();
		context.setLogUrlOnStart(true);
		context.setThrowUnavailableOnStartupException(true);

		initParameter.forEach((k, v) -> context.setInitParameter(Objects.toString(k), Objects.toString(v)));
		logger.debug("InitParams: {}", context.getInitParams());

		context.setContextPath(contextPath);
		context.setAttribute(AnnotationConfiguration.SERVLET_CONTAINER_INITIALIZER_ORDER,
				initializers.stream().map(c -> c.getName()).collect(Collectors.joining(", ")) + ", *");
		context.setConfigurations(new Configuration[] { new AnnotationConfiguration()
		{
			@Override
			protected org.eclipse.jetty.annotations.AnnotationParser createAnnotationParser(int javaPlatform)
			{
				return new AnnotationParser(javaPlatform, Opcodes.ASM7);
			}
		} });
		context.setAttribute(WebInfConfiguration.WEBINF_JAR_PATTERN, "");

		webInfJars.map(e -> Paths.get(e)).filter(p ->
		{
			boolean readable = Files.isReadable(p);
			if (!readable)
				logger.warn("Classpath entry '{}' not readable", p);
			return readable;
		}).map(PathResource::new).forEach(r -> context.getMetaData().addWebInfJar(r));

		context.getMetaData().setWebInfClassesDirs(webInfClassesDirs.map(e -> Paths.get(e)).filter(Files::isReadable)
				.map(PathResource::new).collect(Collectors.toList()));

		logger.info("Web inf classes: dirs {}", context.getMetaData().getWebInfClassesDirs());
		logger.info("Web inf classes: jars {}", context.getMetaData().getWebInfJars());
		//
		for (Class<? extends Filter> f : additionalFilters)
		{
			logger.info("Adding filter: {}", f.getName());
			context.addFilter(f, "/*", EnumSet.allOf(DispatcherType.class));
		}

		connectors.forEach(c -> addConnector(c.apply(this)));

		setHandler(context);
		setStopAtShutdown(true);

		addBean(errorHandler);
		context.setErrorHandler(errorHandler);

		servletContext = context.getServletContext();
		webAppContext = context;
	}

	public Context getServletContext()
	{
		return servletContext;
	}

	public WebAppContext getWebAppContext()
	{
		return webAppContext;
	}

	public static void start(JettyServer server)
	{
		try
		{
			server.start();
			server.join();
		}
		catch (Throwable e)
		{
			e.printStackTrace();
			try
			{
				server.stop();
				System.exit(1);
			}
			catch (Exception e1)
			{
				e1.printStackTrace();
				System.exit(2);
			}
		}
	}
}
