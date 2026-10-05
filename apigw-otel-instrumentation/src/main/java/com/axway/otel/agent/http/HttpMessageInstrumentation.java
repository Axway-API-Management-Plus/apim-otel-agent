package com.axway.otel.agent.http;


import static com.axway.otel.agent.message.MessageSingletons.instrumenter;
import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import javax.annotation.Nullable;

import com.axway.otel.agent.message.MessageAdviceScope;
import com.axway.otel.agent.trace.EventLoggingHelper;
import com.axway.otel.shared.service.MessageAttributes;
import com.vordel.circuit.Message;
import com.vordel.dwe.http.ServerTransaction;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class HttpMessageInstrumentation implements TypeInstrumentation {
	@Override
	public ElementMatcher<TypeDescription> typeMatcher() {
		return named("com.vordel.dwe.http.HTTPMessage");
	}

	@Override
	public void transform(TypeTransformer transformer) {
		transformer.applyAdviceToMethod(
				isConstructor().and(takesArgument(2, named("com.vordel.dwe.http.ServerTransaction")))
				.and(takesArgument(3, named("com.vordel.dwe.CorrelationID"))),
				this.getClass().getName() + "$HTTPMessageAdvice");
	}

	public static class HTTPMessageAdviceScope extends MessageAdviceScope {
		private static final VirtualField<ServerTransaction, HTTPMessageAdviceScope> TRANSACTION_SCOPE = VirtualField
				.find(ServerTransaction.class, HTTPMessageAdviceScope.class);

		private final Instrumenter<ServerTransaction, ? super MessageAttributes> instrumenter;
		private final ServerTransaction request;

		public HTTPMessageAdviceScope(Instrumenter<ServerTransaction, ? super MessageAttributes> instrumenter,
				Context context, ServerTransaction request, Message message) {
			super(context, message);

			this.instrumenter = instrumenter;
			this.request = request;
			
			TRANSACTION_SCOPE.set(request, this);
		}

		@Nullable
		public static HTTPMessageAdviceScope start(ServerTransaction txn, Message message) {
			Instrumenter<ServerTransaction, ? super MessageAttributes> instrumenter = instrumenter();
			Context parentContext = currentContext();

			if (!instrumenter.shouldStart(parentContext, txn)) {
				return null;
			}

			Context context = instrumenter.start(parentContext, txn);
			HTTPMessageAdviceScope scope = new HTTPMessageAdviceScope(instrumenter, context, txn,
					message);
			
			/* cache the CorrelationID for event log */
			EventLoggingHelper.cacheLogSpanContext(message);

			return scope;
		}

		@Override
		public void end(Throwable throwable) {
			super.end(throwable);

			instrumenter.end(context, request, getMessageAttributes(), throwable);
		}

		public static HTTPMessageAdviceScope fromServerTransaction(ServerTransaction txn) {
			return TRANSACTION_SCOPE.get(txn);
		}
	}

	public static class HTTPMessageAdvice {
		@Advice.OnMethodExit(suppress = Throwable.class)
		public static void onExit(@Advice.Argument(2) ServerTransaction txn, @Advice.This Message message) {
			HTTPMessageAdviceScope.start(txn, message);
		}
	}
}
