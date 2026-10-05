package com.axway.otel.agent.http;

import static com.axway.otel.agent.http.HttpMessageInstrumentation.HTTPMessageAdviceScope.fromServerTransaction;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.axway.otel.agent.http.HttpMessageInstrumentation.HTTPMessageAdviceScope;
import com.axway.otel.shared.service.MessageAdviceAttributes;
import com.vordel.dwe.http.ServerTransaction;
import com.vordel.mime.Body;
import com.vordel.mime.HeaderSet;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ServerTransactionInstrumentation implements TypeInstrumentation {
	@Override
	public ElementMatcher<TypeDescription> typeMatcher() {
		return named("com.vordel.dwe.http.ServerTransaction");
	}

	@Override
	public void transform(TypeTransformer transformer) {
		transformer.applyAdviceToMethod(
				named("sendResponse").and(takesArguments(5)).and(takesArgument(0, named("int")))
						.and(takesArgument(1, named("java.lang.String")))
						.and(takesArgument(2, named("com.vordel.mime.HeaderSet")))
						.and(takesArgument(3, named("com.vordel.mime.Body"))).and(takesArgument(4, named("int"))),
				this.getClass().getName() + "$SendResponseAdvice");
	}

	public static class SendResponseAdvice {
		@Advice.OnMethodEnter(suppress = Throwable.class)
		public static void onEnter(@Advice.Argument(0) int responseCode, @Advice.Argument(1) String responseText,
				@Advice.Argument(2) HeaderSet headers, @Advice.Argument(3) Body body,
				@Advice.Argument(4) int bodyFlags, @Advice.This ServerTransaction txn) {
			HTTPMessageAdviceScope scope = fromServerTransaction(txn);
			
			if (scope != null) {
				MessageAdviceAttributes attributes = scope.getMessageAttributes();

				attributes.sendResponse(responseCode, responseText, headers, body, bodyFlags);
			}
		}

	}
}
