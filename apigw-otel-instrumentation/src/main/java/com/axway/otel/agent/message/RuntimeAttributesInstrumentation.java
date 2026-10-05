package com.axway.otel.agent.message;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.axway.otel.shared.service.MessageAdviceAttributes;
import com.vordel.circuit.Message;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class RuntimeAttributesInstrumentation implements TypeInstrumentation {
	@Override
	public ElementMatcher<TypeDescription> typeMatcher() {
		return named("com.axway.otel.apigw.service.RuntimeMessageAdviceAttributesFactory");
	}

	@Override
	public void transform(TypeTransformer transformer) {
		transformer.applyAdviceToMethod(
				named("getVirtualFieldAttributes").and(takesArgument(0, named("com.vordel.circuit.Message"))),
				this.getClass().getName() + "$GetterAdvice");
	}

	public static class GetterAdvice {
		@Advice.OnMethodExit(suppress = Throwable.class)
		public static void onExit(@Advice.Argument(0) Message message,
				@Advice.Return(readOnly = false) MessageAdviceAttributes attributes) {
			MessageAdviceScope advice = MessageAdviceScope.fromMessage(message);
			
			if (advice != null) {
				attributes = advice.getMessageAttributes();
			}
		}
	}
}
