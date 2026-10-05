package com.axway.otel.agent.http;

import static com.axway.otel.agent.http.HttpMessageInstrumentation.HTTPMessageAdviceScope.fromServerTransaction;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import javax.annotation.Nullable;

import com.axway.otel.agent.http.HttpMessageInstrumentation.HTTPMessageAdviceScope;
import com.axway.otel.agent.message.MessageAdviceScope;
import com.vordel.dwe.http.HTTPPlugin;
import com.vordel.dwe.http.ServerTransaction;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class InvokeDisposeInstrumentation implements TypeInstrumentation {
	@Override
	public ElementMatcher<TypeDescription> typeMatcher() {
		return named("com.vordel.dwe.http.HTTPPlugin");
	}

	@Override
	public void transform(TypeTransformer transformer) {
		transformer.applyAdviceToMethod(
				named("invokeDispose").and(takesArguments(5))
						.and(takesArgument(2, named("com.vordel.dwe.http.ServerTransaction")))
						.and(takesArgument(3, named("com.vordel.dwe.CorrelationID"))),
				this.getClass().getName() + "$InvokeDisposeAdvice");
	}

	public static class InvokeDisposeAdvice {
		@Advice.OnMethodEnter(suppress = Throwable.class)
		public static ServerTransaction onEnter(@Advice.Argument(2) ServerTransaction txn) {
			MessageAdviceScope.disableStartOnce();

			return txn;
		}

		@Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
		public static void onExit(@Advice.This HTTPPlugin plugin, @Advice.Thrown Throwable throwable,
				@Advice.Enter @Nullable ServerTransaction txn) {
			HTTPMessageAdviceScope scope = fromServerTransaction(txn);

			if (scope != null) {
				String uriPrefix = plugin.getUriprefix();

				if (uriPrefix != null) {
					scope.setUriPrefix(uriPrefix);
				}

				scope.end(throwable);
			}
		}
	}
}
