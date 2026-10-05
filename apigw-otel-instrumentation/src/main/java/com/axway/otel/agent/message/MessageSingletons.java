package com.axway.otel.agent.message;

import static com.axway.otel.agent.AxwayInstrumentationModule.INSTRUMENTATION_NAME;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.function.Consumer;

import com.axway.otel.agent.http.HttpHeaderGetter;
import com.axway.otel.agent.http.HttpMessageAttributesGetter;
import com.axway.otel.shared.service.MessageAttributes;
import com.vordel.dwe.http.ServerTransaction;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpServerInstrumenters;

public class MessageSingletons {
	private static final Instrumenter<ServerTransaction, MessageAttributes> instrumenter;

	static {
		HttpServerAttributesGetter<ServerTransaction, MessageAttributes> attributes = new HttpMessageAttributesGetter();
		TextMapGetter<ServerTransaction> getter = new HttpHeaderGetter();

		Consumer<InstrumenterBuilder<ServerTransaction, MessageAttributes>> builderConsumer = (builder) -> {
			builder.addAttributesExtractor(createGatewayExtractor());
			builder.addAttributesExtractor(createHttpExtractor(attributes));
		};

		instrumenter = JavaagentHttpServerInstrumenters.create(INSTRUMENTATION_NAME, attributes, getter,
				builderConsumer);
	}

	public static Instrumenter<ServerTransaction, MessageAttributes> instrumenter() {
		return instrumenter;
	}

	private static AttributesExtractor<ServerTransaction, MessageAttributes> createGatewayExtractor() {
		return new AttributesExtractor<ServerTransaction, MessageAttributes>() {
			@Override
			public void onStart(AttributesBuilder attributes, Context parentContext,
					ServerTransaction txn) {
				InetSocketAddress localAddr = txn.getLocalAddr();
				InetSocketAddress remoteAddr = txn.getRemoteAddr();

				attributes.put("network.local.address", localAddr.getAddress().getHostAddress());
				attributes.put("network.local.port", Integer.toString(localAddr.getPort()));
				attributes.put("network.peer.address", remoteAddr.getAddress().getHostAddress());
				attributes.put("network.peer.port", Integer.toString(remoteAddr.getPort()));
			}

			@Override
			public void onEnd(AttributesBuilder attributes, Context context, ServerTransaction txn,
					MessageAttributes response, Throwable error) {
				String statusText = response.getHttpResponseStatusText();

				if (statusText != null) {
					attributes.put("http.response.status_text", statusText);
				}
			}
		};
	}

	private static AttributesExtractor<ServerTransaction, MessageAttributes> createHttpExtractor(HttpServerAttributesGetter<ServerTransaction, MessageAttributes> getter) {
		HttpServerAttributesExtractorBuilder<ServerTransaction, MessageAttributes> builder = HttpServerAttributesExtractor.builder(getter);

		builder.setCapturedRequestHeaders(Arrays.asList("host"));
		builder.setCapturedResponseHeaders(Arrays.asList("content-type", "date", "last-modified"));

		return builder.build();
	}

}
