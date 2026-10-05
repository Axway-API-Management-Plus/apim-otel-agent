package com.axway.otel.agent.state;

import static com.axway.otel.agent.state.StateSingletons.instrumenter;
import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import javax.annotation.Nullable;

import com.vordel.circuit.net.State;
import com.vordel.dwe.http.Response;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class StateInstrumentation implements TypeInstrumentation {
	@Override
	public ElementMatcher<TypeDescription> typeMatcher() {
		return named("com.vordel.circuit.net.State");
	}

	@Override
	public void transform(TypeTransformer transformer) {
		transformer.applyAdviceToMethod(named("tryTransaction").and(takesArguments(0)), this.getClass().getName() + "$ExecuteAdvice");
	}
	
	public static class AdviceScope {
		private final Context context;
		private final Scope scope;
		private final State request;

		public AdviceScope(Context context, Scope scope, State request) {
			this.context = context;
			this.scope = scope;
			this.request = request;
		}

		@Nullable
		public static AdviceScope start(State request) {
			Context parentContext = currentContext();
			if (!instrumenter().shouldStart(parentContext, request)) {
				return null;
			}

			Context context = instrumenter().start(parentContext, request);
			return new AdviceScope(context, context.makeCurrent(), request);
		}

		public void end(Throwable throwable) {
			Response response = request.getResponse();
			
			scope.close();
			instrumenter().end(context, request, response, throwable);
		}
	}

	public static class ExecuteAdvice {
		@Nullable
		@Advice.OnMethodEnter(suppress = Throwable.class)
		public static AdviceScope methodEnter(@Advice.This State state) {
			return AdviceScope.start(state);
		}

		@Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
		public static void methodExit(@Advice.Thrown Throwable throwable,
				@Advice.Enter @Nullable AdviceScope scope) {
			if (scope != null) {
				scope.end(throwable);
			}
		}
	}
}