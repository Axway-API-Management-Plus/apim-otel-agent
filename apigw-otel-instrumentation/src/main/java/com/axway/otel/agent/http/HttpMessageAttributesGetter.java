package com.axway.otel.agent.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.axway.otel.shared.service.MessageAttributes;
import com.vordel.dwe.http.ServerTransaction;
import com.vordel.mime.HeaderSet;

import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;


public class HttpMessageAttributesGetter implements HttpServerAttributesGetter<ServerTransaction, MessageAttributes> {
	private static final Collection<String> EMPTY_COLLECTION = Collections.emptyList();
	
	@Override
	public String getHttpRequestMethod(ServerTransaction txn) {
		return txn.getMethod();
	}
	
	public Iterable<String> getHttpRequestHeaderNames(ServerTransaction txn) {
		HeaderSet headers = txn.getHeaders();

		return headers == null ? EMPTY_COLLECTION : () ->headers.getHeaderNames();
	}

	public static final void appendValues(List<String> output, HeaderSet headers, String name) {
		if (headers != null) {
			Iterator<String> values = headers.getHeaders(name);

			while ((values != null) && values.hasNext()) {
				output.add(values.next());
			}
		}
	}

	@Override
	public List<String> getHttpRequestHeader(ServerTransaction txn,
			String name) {
		List<String> output = new ArrayList<String>();
		HeaderSet headers = txn.getHeaders();

		appendValues(output, headers, name);

		return output;
	}

	public String getFirstHttpRequestHeader(ServerTransaction txn,
			String name) {
		HeaderSet headers = txn.getHeaders();

		return headers == null ? null : headers.getHeader(name);
	}

	@Override
	public Integer getHttpResponseStatusCode(ServerTransaction request,
			MessageAttributes response, Throwable error) {
		return response.getHttpResponseStatusCode();
	}

	@Override
	public List<String> getHttpResponseHeader(ServerTransaction request,
			MessageAttributes response, String name) {
		return response.getHttpResponseHeader(name);
	}

	@Override
	public String getUrlScheme(ServerTransaction txn) {
		return txn.getCipherName() == null ? "http" : "https";
	}

	@Override
	public String getUrlPath(ServerTransaction txn) {
		try {
			URI uri = txn.getTransactionURI();

			return uri == null ? "" : uri.getPath();
		} catch (URISyntaxException e) {
			return "";
		}
	}

	@Override
	public String getUrlQuery(ServerTransaction txn) {
		try {
			URI uri = txn.getTransactionURI();

			return uri == null ? "" : uri.getQuery();
		} catch (URISyntaxException e) {
			return "";
		}
	}
}
