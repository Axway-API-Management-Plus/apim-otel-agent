package com.axway.otel.shared.service;

import java.util.List;

import com.axway.otel.apigw.service.RuntimeMessageAdviceAttributesFactory;
import com.vordel.circuit.Message;
import com.vordel.trace.Trace;

/**
 * API Gateway/OpenTelemetry shared view of message attributes. It's basically a
 * 'read only' view of attributes to be set in Span. This interface also allows
 * to add any arbitrary attributes to the current span (the one attached to
 * message).
 */
public abstract class MessageAttributes implements MessageScope {
	public static final MessageAttributes getOpenTelemetryAttributes(Message message) {
		return RuntimeMessageAdviceAttributesFactory.getVirtualFieldAttributes(message);
	}

	public static final void setOpenTelemetryAttribute(Message message, String key, String value) {
		MessageAttributes attributes = getOpenTelemetryAttributes(message);

		if (attributes != null) {
			attributes.setStringAttribute(key, value);
		} else {
			Trace.error("Message has no OpenTelemetry span");
		}
	}

	public abstract int getHttpResponseStatusCode();

	public abstract String getHttpResponseStatusText();

	public abstract List<String> getHttpResponseHeader(String name);
}
