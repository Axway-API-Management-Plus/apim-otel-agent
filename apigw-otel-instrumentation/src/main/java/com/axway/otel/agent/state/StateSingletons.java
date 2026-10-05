package com.axway.otel.agent.state;

import static com.axway.otel.agent.AxwayInstrumentationModule.INSTRUMENTATION_NAME;
import static com.axway.otel.agent.state.HttpAttributesGetter.getCorrelationID;
import static com.axway.otel.agent.state.HttpAttributesGetter.getHttpResponseStatusText;
import static com.axway.otel.agent.state.HttpAttributesGetter.getStateCircuitName;

import java.util.Arrays;
import java.util.function.Consumer;

import com.vordel.circuit.net.State;
import com.vordel.dwe.http.Response;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpClientInstrumenters;

public final class StateSingletons {
	private static final Instrumenter<State, Response> instrumenter;

	static {
		HttpClientAttributesGetter<State, Response> attributes = new HttpAttributesGetter();
		TextMapSetter<State> setter = new HttpHeaderSetter();
		
		Consumer<InstrumenterBuilder<State, Response>> builderConsumer = (builder) -> {
			builder.addAttributesExtractor(createGatewayExtractor());
			builder.addAttributesExtractor(createHttpExtractor(attributes));
		};
		
		instrumenter = JavaagentHttpClientInstrumenters.create(INSTRUMENTATION_NAME, attributes, setter, builderConsumer);
	}

	public static Instrumenter<State, Response> instrumenter() {
		return instrumenter;
	}
	
	private static AttributesExtractor<State, Response> createGatewayExtractor() {
		return new AttributesExtractor<State, Response>() {
			@Override
			public void onStart(AttributesBuilder attributes, Context parentContext, State request) {
				String policy = getStateCircuitName(request);
				String correlation = getCorrelationID(request);
				
				if (policy != null) {
					attributes.put("com.axway.policy", policy);
				}
				
				if (correlation != null) {
					attributes.put("com.axway.parent.correlation", correlation);
				}
			}

			@Override
			public void onEnd(AttributesBuilder attributes, Context context, State request,
					Response response, Throwable error) {
				String statusText = getHttpResponseStatusText(response);
				
				if (statusText != null) {
					attributes.put("http.response.status_text", statusText);
				}
			}
		};
	}
	
	private static AttributesExtractor<State, Response> createHttpExtractor(HttpClientAttributesGetter<State, Response> getter) {
		HttpClientAttributesExtractorBuilder<State, Response> builder = HttpClientAttributesExtractor.builder(getter);
		
		// XXX DO NOT try to capture 'host' headers, it is not accurate (it has special handling in APIGW)
		builder.setCapturedResponseHeaders(Arrays.asList("content-type", "date", "last-modified"));

		return builder.build();
	}

	private StateSingletons() {
	}
}
