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

public class RecordPluginStartInstrumentation implements TypeInstrumentation {
	@Override
	public ElementMatcher<TypeDescription> typeMatcher() {
		return named("com.vordel.circuit.InvocationEngine");
	}

	@Override
	public void transform(TypeTransformer transformer) {
		transformer.applyAdviceToMethod(
				named("recordPluginStart").and(takesArgument(0, named("com.vordel.config.Circuit")))
						.and(takesArgument(1, named("com.vordel.circuit.Message"))),
				this.getClass().getName() + "$RecordPluginStartAdvice");
	}

	public static class RecordPluginStartAdvice {
		@Advice.OnMethodEnter(suppress = Throwable.class)
		public static void onEnter(@Advice.Argument(0) Circuit circuit, @Advice.Argument(1) Message message) {
			MessageAdviceScope scope = fromMessage(message);

			if (scope != null) {
				MessageAdviceAttributes attributes = scope.getMessageAttributes();
				String policy = circuit.getName();
				
				attributes.setStringAttribute("com.axway.policy", policy);
			}
		}
	}
}
