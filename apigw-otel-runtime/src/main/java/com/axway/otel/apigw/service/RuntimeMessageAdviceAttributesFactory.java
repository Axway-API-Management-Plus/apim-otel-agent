package com.axway.otel.apigw.service;

import com.axway.otel.shared.service.MessageAdviceAttributes;
import com.axway.otel.shared.service.MessageAdviceAttributesFactory;
import com.axway.otel.shared.service.MessageScope;
import com.google.auto.service.AutoService;
import com.vordel.circuit.Message;

@AutoService(MessageAdviceAttributesFactory.class)
public class RuntimeMessageAdviceAttributesFactory implements MessageAdviceAttributesFactory {
	public static MessageAdviceAttributes getVirtualFieldAttributes(Message message) {
		/* this will be instrumented by the OpenTelemetry runtime */
		return null;
	}
	
	@Override
	public MessageAdviceAttributes getMessageAttributes(Message message, MessageScope scope) {
		MessageAdviceAttributes attributes = getVirtualFieldAttributes(message);
		
		return attributes == null ? new RuntimeMessageAttributes(message, scope) : attributes;
	}
}
