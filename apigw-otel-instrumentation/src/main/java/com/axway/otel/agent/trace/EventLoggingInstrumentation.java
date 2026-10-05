package com.axway.otel.agent.trace;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class EventLoggingInstrumentation implements TypeInstrumentation {
	@Override
	public ElementMatcher<TypeDescription> typeMatcher() {
		return named("com.vordel.metrics.eventlog.logger.EventLoggingStore");
	}

	@Override
	public void transform(TypeTransformer transformer) {
		transformer.applyAdviceToMethod(named("clearCache").and(takesNoArguments()),
				this.getClass().getName() + "$ClearAdvice");
		transformer.applyAdviceToMethod(named("addTransactionLeg").and(takesArgument(0, named("java.lang.String"))),
				this.getClass().getName() + "$ApplyAdvice");
		transformer.applyAdviceToMethod(named("transactionFinished").and(takesArgument(0, named("java.lang.String"))),
				this.getClass().getName() + "$ConsumeAdvice");
		transformer.applyAdviceToMethod(named("completeLogEntry").and(takesArgument(0, named("java.lang.String"))),
				this.getClass().getName() + "$ApplyAdvice");
	}
	
	public static class ClearAdvice {
		@Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
		public static void onExit() {
			EventLoggingHelper.clearLogSpanContext();
		}
	}
	
	public static class ApplyAdvice {
		@Advice.OnMethodEnter(suppress = Throwable.class)
		public static void onEnter(@Advice.Argument(0) String correlationId, @Advice.Local("otelScope") Scope scope) {
			scope = EventLoggingHelper.wrapContext(correlationId, Context.current(), false);
		}

		@Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
		public static void onExit(@Advice.Local("otelScope") Scope scope) {
			if (scope != null) {
				scope.close();
			}
		}
	}
	
	public static class ConsumeAdvice {
		@Advice.OnMethodEnter(suppress = Throwable.class)
		public static void onEnter(@Advice.Argument(0) String correlationId, @Advice.Local("otelScope") Scope scope) {
			scope = EventLoggingHelper.wrapContext(correlationId, Context.current(), true);
		}

		@Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
		public static void onExit(@Advice.Local("otelScope") Scope scope) {
			if (scope != null) {
				scope.close();
			}
		}
	}
}
