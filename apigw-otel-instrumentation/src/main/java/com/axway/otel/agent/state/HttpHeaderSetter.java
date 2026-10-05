package com.axway.otel.agent.state;

import javax.annotation.Nullable;

import com.vordel.circuit.net.State;
import com.vordel.mime.HeaderSet;

import io.opentelemetry.context.propagation.TextMapSetter;

public class HttpHeaderSetter implements TextMapSetter<State> {
  @Override
  public void set(@Nullable State carrier, String key, String value) {
    if (carrier == null) {
      return;
    }

	HeaderSet headers = carrier.getHeaders();

	headers.setHeader(key, value);
  }
}
