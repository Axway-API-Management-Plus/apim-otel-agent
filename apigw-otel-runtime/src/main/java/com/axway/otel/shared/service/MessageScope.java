package com.axway.otel.shared.service;

/**
 * Special interface to set attributes in the current Span from an API Gateway
 * context without depending on OpenTelemetry libraries and runtime.
 */
public interface MessageScope {
	@Deprecated
	void setAttribute(String key, String value);
	
	void setStringAttribute(String key, String value);
	void setLongAttribute(String key, long value);
	void setDoubleAttribute(String key, double value);
	void setBooleanAttribute(String key, boolean value);
}
