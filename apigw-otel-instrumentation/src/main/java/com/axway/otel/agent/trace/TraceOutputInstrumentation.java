package com.axway.otel.agent.trace;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class TraceOutputInstrumentation implements TypeInstrumentation {
	@Override
	public ElementMatcher<TypeDescription> typeMatcher() {
		return named("com.vordel.trace.Trace");
	}

	@Override
	public void transform(TypeTransformer transformer) {
		transformer.applyAdviceToMethod(named("output").and(takesArguments(int.class, String.class, Throwable.class)),
				this.getClass().getName() + "$OutputAdvice");
	}

	public static class OutputAdvice {
		@Advice.OnMethodEnter(suppress = Throwable.class)
		public static void onEnter(@Advice.Argument(0) int level, @Advice.Argument(1) String message,
				@Advice.Argument(2) Throwable t) {
			TraceOutputHelper.output(level, message, t);
		}
	}
}
