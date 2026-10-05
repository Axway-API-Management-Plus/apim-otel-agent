package com.axway.otel.agent.trace;

import static com.axway.otel.agent.message.MessageAdviceScope.MESSAGE_ADVICE_SCOPE;

import java.time.Instant;

import com.axway.otel.agent.AxwayInstrumentationModule;
import com.axway.otel.agent.message.MessageAdviceScope;
import com.vordel.trace.Trace;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;

public class TraceOutputHelper {
	private static class LoggerHolder {
		private static final Logger LOGGER = GlobalOpenTelemetry.get().getLogsBridge()
				.loggerBuilder(AxwayInstrumentationModule.INSTRUMENTATION_NAME).build();

		private LoggerHolder() {
		}
	}
	
	public static final AttributeKey<String> TRACE_COMPONENT_NAME = AttributeKey.stringKey("com.axway.component");

	public static void output(int level, String message, Throwable t) {
		/* do not send logs if level not enabled (DATA/DEBUG/etc... */
		if (level <= Trace.getLevel()) {
			if (t != null) {
				/* record exceptions seen in logs to spans */
				Context context = Context.current();
				MessageAdviceScope advice = context.get(MESSAGE_ADVICE_SCOPE);
				
				if (advice != null) {
					advice.recordException(t);
				}
			}

			LogRecordBuilder builder = LoggerHolder.LOGGER.logRecordBuilder();

			builder.setSeverity(mapLevelToSeverity(level));
			builder.setSeverityText(Trace.getNameForLevel(level));
			builder.setAttribute(TRACE_COMPONENT_NAME, Trace.getComponentName());
			builder.setTimestamp(Instant.now());

			if (t != null) {
				builder.setException(t);
			}

			builder.setBody(message);
			builder.emit();
		}
	}

	private static Severity mapLevelToSeverity(int level) {
		switch (level) {
		case Trace.TRACE_DATA:
			return Severity.TRACE;
		case Trace.TRACE_DEBUG:
			return Severity.DEBUG;
		case Trace.TRACE_MIN:
			return Severity.INFO;
		case Trace.TRACE_INFO:
			return Severity.INFO2;
		case Trace.TRACE_ERROR:
			return Severity.ERROR;
		case Trace.TRACE_ALWAYS:
			return Severity.ERROR3;
		case Trace.TRACE_FATAL:
			return Severity.FATAL;
		default:
			return Severity.UNDEFINED_SEVERITY_NUMBER;
		}
	}
}
