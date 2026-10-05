package com.axway.otel.agent;

import java.util.Arrays;
import java.util.List;

import com.axway.otel.agent.http.HttpMessageInstrumentation;
import com.axway.otel.agent.http.InvokeDisposeInstrumentation;
import com.axway.otel.agent.http.ServerTransactionInstrumentation;
import com.axway.otel.agent.message.MessageInstrumentation;
import com.axway.otel.agent.message.ProcessMessageInstrumentation;
import com.axway.otel.agent.message.RecordPluginStartInstrumentation;
import com.axway.otel.agent.message.RuntimeAttributesInstrumentation;
import com.axway.otel.agent.state.StateInstrumentation;
import com.axway.otel.agent.trace.EventLoggingInstrumentation;
import com.axway.otel.agent.trace.TraceOutputInstrumentation;
import com.google.auto.service.AutoService;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;

@AutoService(InstrumentationModule.class)
public class AxwayInstrumentationModule extends InstrumentationModule {
	public static final String INSTRUMENTATION_NAME = "axway-apim";

	public AxwayInstrumentationModule() {
		super(INSTRUMENTATION_NAME);
	}

	@Override
	public List<TypeInstrumentation> typeInstrumentations() {
		return Arrays.asList(
				new ServerTransactionInstrumentation(),
				new MessageInstrumentation(),
				new HttpMessageInstrumentation(),
				new InvokeDisposeInstrumentation(),
				new ProcessMessageInstrumentation(),
				new RecordPluginStartInstrumentation(),
				new StateInstrumentation(),
				new TraceOutputInstrumentation(),
				new EventLoggingInstrumentation(),
				new RuntimeAttributesInstrumentation());
	}

	@Override
	public boolean isHelperClass(String className) {
		return className.startsWith("com.axway.otel.agent");
	}
}
