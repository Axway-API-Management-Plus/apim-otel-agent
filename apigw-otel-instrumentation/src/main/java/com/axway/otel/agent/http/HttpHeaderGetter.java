package com.axway.otel.agent.http;

import java.util.Collection;
import java.util.Collections;

import com.vordel.dwe.http.ServerTransaction;
import com.vordel.mime.HeaderSet;

import io.opentelemetry.context.propagation.TextMapGetter;

public class HttpHeaderGetter implements TextMapGetter<ServerTransaction> {
	private static final Collection<String> EMPTY_COLLECTION = Collections.emptyList();

	@Override
	public Iterable<String> keys(ServerTransaction carrier) {
		HeaderSet headers = carrier.getHeaders();

		return headers == null ? EMPTY_COLLECTION : () ->headers.getHeaderNames();
	}

	@Override
	public String get(ServerTransaction carrier, String key) {
		HeaderSet headers = carrier.getHeaders();

		return headers == null ? null : headers.getHeader(key);
	}
}
