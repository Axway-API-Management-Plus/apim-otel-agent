package com.axway.otel.agent.message;

import static com.axway.otel.agent.message.MessageAdviceScope.getMessageAdviceAttributesFactory;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.vordel.circuit.Message;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class MessageInstrumentation implements TypeInstrumentation {
	@Override
	public ElementMatcher<TypeDescription> typeMatcher() {
		return named("com.vordel.circuit.Message");
	}

	@Override
	public void transform(TypeTransformer transformer) {
		transformer.applyAdviceToMethod(isConstructor(), this.getClass().getName() + "$MessageAdvice");
	}

	public static class MessageAdvice {
		@Advice.OnMethodEnter
		public static void onEnter(@Advice.Origin Class<?> clazz) {
			ClassLoader loader = clazz.getClassLoader();

			// This allows API Gateway scripts to add any attribute to the span
			getMessageAdviceAttributesFactory(loader);
		}

		@Advice.OnMethodExit(suppress = Throwable.class)
		public static void onExit(@Advice.This Message message) {
			MessageAdviceScope.start(message);
		}
	}
}
