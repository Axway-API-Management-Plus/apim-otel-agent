package com.axway.otel.shared.service;

import com.vordel.circuit.Message;

/**
 * Helper service to ease usage of API Gateway internals within OpenTelemetry
 * agent. Some classes just can't be loaded from the agent side. So we keep a
 * minimum set of already loaded classes (instrumentation advices and helper).
 * for all other cases, stick to API Gateway loaded runtime (outside the
 * OpenTelemetry agent context).
 */
public interface MessageAdviceAttributesFactory {
	MessageAdviceAttributes getMessageAttributes(Message value, MessageScope scope);
}
