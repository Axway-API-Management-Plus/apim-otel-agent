package com.axway.otel.agent.trace;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.axway.otel.agent.message.MessageAdviceScope;
import com.vordel.circuit.Message;
import com.vordel.dwe.CorrelationID;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

public class EventLoggingHelper {
	private static final Map<String, SpanContext> LOG_ENTRY_SPANS = new ConcurrentHashMap<>();

	public static void cacheLogSpanContext(Message message) {
		CorrelationID correlationId = message.getIDBase();

		if (correlationId != null) {
			MessageAdviceScope advice = MessageAdviceScope.fromMessage(message);

			if (advice != null) {
				Span span = Span.fromContext(advice.context);

				if (span.getSpanContext().isValid()) {
					SpanContext context = span.getSpanContext();
					String key = correlationId.toString();

					if (context.isValid()) {
						LOG_ENTRY_SPANS.put(key, context);
					}
				}
			}
		}
	}

	public static Scope wrapContext(String correlationId, Context current, boolean consume) {
		if (correlationId != null) {
			SpanContext context = consume ? removeLogSpanContext(correlationId) : getLogSpanContext(correlationId);

			if (context != null) {
				Span span = Span.fromContext(current);

				if (!span.getSpanContext().isValid()) {
					return current.with(Span.wrap(context)).makeCurrent();
				}
			}
		}

		return null;
	}

	public static SpanContext getLogSpanContext(String correlationId) {
		return LOG_ENTRY_SPANS.get(correlationId);
	}

	public static SpanContext removeLogSpanContext(String correlationId) {
		return LOG_ENTRY_SPANS.remove(correlationId);
	}

	public static void clearLogSpanContext() {
		LOG_ENTRY_SPANS.clear();
	}
}
