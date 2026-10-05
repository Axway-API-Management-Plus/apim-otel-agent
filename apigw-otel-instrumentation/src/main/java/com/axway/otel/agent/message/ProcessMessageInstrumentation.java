package com.axway.otel.agent.message;

import static com.axway.otel.agent.message.MessageAdviceScope.fromMessage;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.axway.otel.shared.service.MessageAdviceAttributes;
import com.vordel.circuit.Message;
import com.vordel.config.Circuit;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ProcessMessageInstrumentation implements TypeInstrumentation {
	@Override
	public ElementMatcher<TypeDescription> typeMatcher() {
		return named("com.vordel.circuit.InvocationEngine");
	}

	@Override
	public void transform(TypeTransformer transformer) {
		transformer.applyAdviceToMethod(
				named("processMessage").and(takesArgument(0, named("com.vordel.circuit.InvocationContext")))
				.and(takesArgument(1, named("com.vordel.circuit.Message"))),
				this.getClass().getName() + "$ProcessMessageAdvice");
		transformer.applyAdviceToMethod(
				named("invokeFilter").and(takesArgument(2, named("com.vordel.circuit.Message"))),
				this.getClass().getName() + "$ProcessExceptionAdvice");
		transformer.applyAdviceToMethod(
				named("invokeCircuit").and(takesArgument(0, named("com.vordel.config.Circuit"))).and(takesArgument(2, named("com.vordel.circuit.Message"))),
				this.getClass().getName() + "$CircuitTrackerAdvice");
	}
	
	public static class CircuitTrackerAdvice {
		@Advice.OnMethodEnter(suppress = Throwable.class)
		public static void onEnter(@Advice.Argument(0) Circuit circuit, @Advice.Argument(2) Message message, @Advice.Local("otelAdvice") MessageAdviceScope advice) {
			advice = fromMessage(message);
			
			if (advice != null) {
				advice.pushCircuit(circuit);
			}
		}
		@Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
		public static void onExit(@Advice.Local("otelAdvice") MessageAdviceScope advice) {
			if (advice != null) {
				advice.popCircuit();
			}
		}
	}

	public static class ProcessExceptionAdvice {
		@Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
		public static void onExit(@Advice.Argument(2) Message message, @Advice.Thrown Throwable thrown) {
			if (thrown != null) {
				MessageAdviceScope advice = fromMessage(message);

				if (advice != null) {
					advice.recordException(thrown);
				}
			}
		}
	}

	public static class ProcessMessageAdvice {
		@Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
		public static void onExit(@Advice.Argument(1) Message message, @Advice.Thrown Throwable throwable,
				@Advice.Return int status) {
			MessageAdviceScope advice = fromMessage(message);

			if (advice != null) {
				MessageAdviceAttributes attributes = advice.getMessageAttributes();
				String methodPath = attributes.getAPIManagerUriTemplate();

				if (methodPath != null) {
					advice.setUriTemplate(methodPath);

					attributes.setAPIManagerAttributes();
				}

				if (throwable == null) {
					attributes.setDashboardStatus(status);
				}
			}
		}
	}
}
