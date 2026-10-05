package com.axway.otel.agent.message;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.ServiceLoader;

import com.axway.otel.shared.service.MessageAdviceAttributes;
import com.axway.otel.shared.service.MessageAdviceAttributesFactory;
import com.axway.otel.shared.service.MessageScope;
import com.vordel.circuit.Message;
import com.vordel.config.Circuit;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import io.opentelemetry.instrumentation.api.util.VirtualField;

public class MessageAdviceScope implements MessageScope {
	private static final AttributeKey<String> CIRCUIT_EXCEPTION_NAME = AttributeKey.stringKey("com.axway.exception.policy");
	private static final ThreadLocal<Boolean> SKIP_START = new ThreadLocal<>();

	private static final VirtualField<Message, MessageAdviceScope> MESSAGE_SCOPE = VirtualField.find(Message.class,
			MessageAdviceScope.class);
	public static final ContextKey<MessageAdviceScope> MESSAGE_ADVICE_SCOPE =
			ContextKey.named("com.axway.otel.message-advice-scope");	
	private final Deque<Circuit> policies = new ArrayDeque<Circuit>();

	private static final HttpServerRouteGetter<String> URIPREFIX_GETTER = new HttpServerRouteGetter<String>() {
		@Override
		public String get(Context context, String arg) {
			return arg;
		}			
	};

	private static MessageAdviceAttributesFactory messageAttributesFactory = null;
	private static final Object SYNC = new Object();

	public static final MessageAdviceAttributesFactory getMessageAdviceAttributesFactory(ClassLoader loader) {
		synchronized (SYNC) {
			if (messageAttributesFactory == null) {
				ServiceLoader<MessageAdviceAttributesFactory> service = ServiceLoader
						.load(MessageAdviceAttributesFactory.class, loader);
				messageAttributesFactory = service.findFirst().get();
			}
		}

		return messageAttributesFactory;
	}

	private static MessageAdviceAttributes getMessageAttributes(Message instance, MessageAdviceScope scope) {
		ClassLoader loader = instance.getClass().getClassLoader();
		MessageAdviceAttributesFactory factory = getMessageAdviceAttributesFactory(loader);

		return factory.getMessageAttributes(instance, scope);
	}

	private final WeakIdentitySet<Throwable> recorded = new WeakIdentitySet<Throwable>();
	private final MessageAdviceAttributes attributes;

	public final Context context;
	public final Scope scope;


	public MessageAdviceScope(Context context, Message message) {
		this.context = context.with(MESSAGE_ADVICE_SCOPE, this);
		this.scope = this.context.makeCurrent();
		this.attributes = getMessageAttributes(message, this);

		MESSAGE_SCOPE.set(message, this);
	}

	public static void disableStartOnce() {
		SKIP_START.set(Boolean.TRUE);
	}

	public static MessageAdviceScope start(Message message) {
		MessageAdviceScope existing = MESSAGE_SCOPE.get(message);
		Boolean skip = SKIP_START.get();

		if ((skip != null) && skip) {
			SKIP_START.remove();
		} else if (existing == null) {
			Context parentContext = currentContext();

			existing = new MessageAdviceScope(parentContext, message);
		}

		return existing;
	}

	public void setUriPrefix(String prefix) {
		HttpServerRoute.update(context, HttpServerRouteSource.SERVER, URIPREFIX_GETTER, prefix);
	}

	public void setUriTemplate(String prefix) {
		HttpServerRoute.update(context, HttpServerRouteSource.NESTED_CONTROLLER, URIPREFIX_GETTER, prefix);
	}

	public void end(Throwable throwable) {
		scope.close();
	}

	public MessageAdviceAttributes getMessageAttributes() {
		return attributes;
	}

	public void pushCircuit(Circuit circuit) {
		policies.push(circuit);
	}

	public void popCircuit() {
		policies.pop();
	}

	public Circuit peekCirduit() {
		return policies.peek();
	}

	public void recordException(Throwable t) {
		if ((t != null) && recorded.add(t)) {
			Span span = Span.fromContext(currentContext());

			if (span.isRecording()) {
				Attributes attributes = Attributes.empty();
				Circuit circuit = peekCirduit();
				
				if (circuit != null) {
					attributes = Attributes.of(CIRCUIT_EXCEPTION_NAME, circuit.getName());
				}

				span.recordException(t, attributes);
			}
		}
	}

	@Override
	public void setAttribute(String key, String value) {
		setStringAttribute(key, value);
	}

	@Override
	public void setStringAttribute(String key, String value) {
		Span span = Span.fromContextOrNull(context);

		if (span != null) {
			span.setAttribute(key, value);
		}
	}

	@Override
	public void setLongAttribute(String key, long value) {
		Span span = Span.fromContextOrNull(context);

		if (span != null) {
			span.setAttribute(key, value);
		}
	}

	@Override
	public void setDoubleAttribute(String key, double value) {
		Span span = Span.fromContextOrNull(context);

		if (span != null) {
			span.setAttribute(key, value);
		}
	}

	@Override
	public void setBooleanAttribute(String key, boolean value) {
		Span span = Span.fromContextOrNull(context);

		if (span != null) {
			span.setAttribute(key, value);
		}
	}

	public static MessageAdviceScope fromMessage(Message message) {
		return MESSAGE_SCOPE.get(message);
	}
}
