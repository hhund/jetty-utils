module de.rwh.utils.jetty
{
	exports de.rwh.utils.jetty;
	
	requires org.slf4j;
	requires org.apache.logging.log4j;
	requires org.apache.logging.log4j.core;
	
	requires de.rwh.utils.crypto;
	
	requires javax.servlet.api;
	requires jetty.server;
	requires jetty.http;
	requires jetty.util;
	requires jetty.webapp;
	requires jetty.servlet;
	requires jetty.annotations;
	requires jetty.io;
	requires jetty.xml;
	requires jetty.security;
	requires jetty.plus;
	requires jetty.jndi;
	
	requires org.objectweb.asm;
	requires org.objectweb.asm.commons;
	requires org.objectweb.asm.tree;
}