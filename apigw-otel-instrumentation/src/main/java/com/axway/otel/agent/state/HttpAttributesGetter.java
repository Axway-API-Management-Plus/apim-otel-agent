package com.axway.otel.agent.state;

import static com.axway.otel.agent.http.HttpMessageAttributesGetter.appendValues;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.vordel.circuit.Message;
import com.vordel.circuit.net.State;
import com.vordel.config.Circuit;
import com.vordel.dwe.http.Response;
import com.vordel.mime.Body;
import com.vordel.trace.Trace;

import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;

public class HttpAttributesGetter
    implements HttpClientAttributesGetter<State, Response> {
	public final static <T> T getClassFieldValue(Object instance, String name, Class<?> clazz, Class<T> type) {
		try {
			Field field = clazz.getDeclaredField(name);

			field.setAccessible(true);

			return type.cast(field.get(instance));
		} catch (NoSuchFieldException e) {
			Trace.error(String.format("Unable to retrieve field '%s' on class '%s'", name, clazz.getName()));

			return null;
		} catch (Exception e) {
			Trace.error("Spurious Exception ", e);

			return null;
		}
	}

	public static final String getStateCircuitName(State state) {
		Circuit circuit = getClassFieldValue(state, "ci", State.class, Circuit.class);

		return circuit == null ? null : circuit.getName();
	}

	@Override
	public String getHttpRequestMethod(State state) {
		String verb = state.getVerb();

		return verb == null ? "_OTHER" : verb;
	}

	@Override
	public List<String> getHttpRequestHeader(State state, String name) {
		List<String> output = new ArrayList<String>();
		Body body = state.getBody();

		appendValues(output, state.getHeaders(), name);

		if (body != null) {
			appendValues(output, body.getHeaders(), name);
		}

		return output;
	}

	@Override
	public Integer getHttpResponseStatusCode(State request, Response response,
			Throwable error) {
		return response.getCode();
	}

	public static final String getHttpResponseStatusText(Response response) {
		return response.getText();
	}

	@Override
	public List<String> getHttpResponseHeader(State request, Response response,
			String name) {
		List<String> output = new ArrayList<String>();
		
		appendValues(output, response.getHeaders(), name);
		
		return output;
	}

	@Override
	public String getUrlFull(State state) {
		URI uri = state.getUri();
		String value = uri == null ? "empty:" : uri.toString();

		return value;
	}

	@Override
	public String getServerAddress(State state) {
		String host = getClassFieldValue(state, "host", State.class, String.class);

		return host;
	}

	@Override
	public Integer getServerPort(State state) {
		String port = getClassFieldValue(state, "port", State.class, String.class);

		return Integer.parseInt(port);
	}

	public static final String getCorrelationID(State state) {
		Message message = state.getMessage();

		return message == null ? null : message.correlationId.toString();
	}
}