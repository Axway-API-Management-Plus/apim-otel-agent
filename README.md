# OpenTelemetry API Gateway agent

This project is an implementation of an OpenTelemetry agent extension for Axway API Gateway. It produces two jars:

- **OpenTelemetry agent extension** (`*-extension.jar`) to be registered with the `opentelemetry-javaagent` distro,
- **Runtime support** (`*-helpers.jar`) to be added to the API Gateway classpath (in the `ext/lib` directory).

It produces three kinds of signals:

- **HTTP Server spans** — for both API Gateway HTTP listeners and API Manager calls,
- **HTTP Client spans** — for all external HTTP connections made through the native Vordel client runtime,
- **Log records** — every `Trace.output()` call of the gateway is bridged to the OpenTelemetry logs pipeline, correlated with the active span.

Exceptions raised during policy execution are attached to the enclosing span, with the failing policy name as an attribute.

Additionally, custom attributes can be added to HTTP Server spans using static methods callable from API Gateway scripts, see [MessageAttributes](apigw-otel-runtime/src/main/java/com/axway/otel/shared/service/MessageAttributes.java).

Since the standard OpenTelemetry Java agent is used, it will also produce spans for all supported libraries used during API Gateway processing (Cassandra, Apache HTTP Client, etc.).

## Module layout

| Module | Artifact | Deployed to | Purpose |
| --- | --- | --- | --- |
| `apigw-otel-instrumentation` | `*-extension.jar` (shadow) | next to `opentelemetry-javaagent.jar` | `InstrumentationModule`, `TypeInstrumentation`s, advices and agent-side helpers |
| `apigw-otel-runtime` | `*-helpers.jar` | API Gateway `ext/lib` | API Gateway–side runtime, loaded by the gateway classloader, exposed to the agent through a `ServiceLoader` SPI |

The runtime jar also carries the `com.axway.otel.shared.service` package, which is the contract shared by both sides (`MessageScope`, `MessageAttributes`, `MessageAdviceAttributes`, `MessageAdviceAttributesFactory`).

## Notes for implementers

For implementers who may customize this code, it is important to have the following in mind:

- Agent classpath is **not** API Gateway classpath. Not all classes can be used in instrumentations — this implementation uses a Java service loaded through the API Gateway classloader, see [MessageInstrumentation](apigw-otel-instrumentation/src/main/java/com/axway/otel/agent/message/MessageInstrumentation.java) and [MessageAdviceScope](apigw-otel-instrumentation/src/main/java/com/axway/otel/agent/message/MessageAdviceScope.java).
- Native methods can't be instrumented (there is nothing to intercept or instrument).
- `VirtualField` is used to attach state to instrumented classes at runtime (`Message` → `MessageAdviceScope`, `ServerTransaction` → `HTTPMessageAdviceScope`). While powerful, it requires muzzle preprocessing of compiled classes after compilation.
- OpenTelemetry tooling is not yet stable, expect changes in the build process (which is the hardest part of this project).
- DO NOT mix packages between agent-only classes/instrumentations and API Gateway classes. In this implementation `com.axway.otel.agent.*` is for the agent (and some helper classes), `com.axway.otel.apigw.*` and `com.axway.otel.shared.*` for the gateway runtime.
- AVOID whenever possible instantiating API Gateway classes from the agent side. You can use any already-instantiated class as you wish, but you MUST AVOID instantiating API Gateway classes from the agent (e.g. `Selector` instantiation will fail).
- If you want to modify span lifetime, keep in mind that for a client span to be attached to a server span, the client span must be created within the server span. Also, changing lifetime will change metrics from the collector point of view.
- The advices use `@Advice.Return(readOnly = false)` / `@Advice.Argument` mutation, which requires **inline** advice. If this module is migrated to the `invokedynamic` advice model, these must be rewritten with `@Advice.AssignReturned`.

## Design

The extension uses the provided OpenTelemetry instrumenters, so it can be configured like a regular HTTP server/client (see [HTTP instrumentation configuration](https://opentelemetry.io/docs/zero-code/java/agent/instrumentation/http/) to configure header capture).

Only HTTP headers can be instrumented; bodies can not be included in spans (expect severe performance penalties if trying).

### Message scope and the runtime bridge

A `MessageAdviceScope` is attached to every `com.vordel.circuit.Message` through a `VirtualField`, and pushed into the OpenTelemetry `Context` under the `com.axway.otel.message-advice-scope` context key. It owns:

- the span context of the transaction,
- the stack of currently executing policies (`Circuit`), used to attribute exceptions,
- a weak identity set of already-recorded exceptions, so a given `Throwable` instance is recorded only once per message,
- the `MessageAdviceAttributes` instance, obtained from the gateway-side factory.

The gateway-side implementation is resolved once through `ServiceLoader.load(MessageAdviceAttributesFactory.class, messageClassLoader)`. The reverse direction (gateway code asking for the attributes of a message) goes through `RuntimeMessageAdviceAttributesFactory.getVirtualFieldAttributes()`, a method that returns `null` in the helpers jar and whose **return value is replaced by the agent** ([RuntimeAttributesInstrumentation](apigw-otel-instrumentation/src/main/java/com/axway/otel/agent/message/RuntimeAttributesInstrumentation.java)). This removes the need for a `MessageLocalStorage` slot or for registering a `LoadableModule`.

### Custom attributes from policies

A script can add any attribute to the current span using a static call. This allows a customer to add any computed attribute to API Gateway generated spans. This method has been chosen over regular message attributes to avoid any potential error or mistake by policy implementers.

```groovy
import com.axway.otel.shared.service.MessageAttributes

def invoke(msg) {
	// Add an attribute to the current span
	MessageAttributes.setOpenTelemetryAttribute(msg, "com.example.custom", "value")

	return true
}
```

Typed setters are available on the object returned by `MessageAttributes.getOpenTelemetryAttributes(msg)`:

```groovy
def attributes = MessageAttributes.getOpenTelemetryAttributes(msg)

if (attributes != null) {
	attributes.setStringAttribute("com.example.name", "value")
	attributes.setLongAttribute("com.example.count", 42L)
	attributes.setDoubleAttribute("com.example.ratio", 0.5d)
	attributes.setBooleanAttribute("com.example.flag", true)
}
```

`MessageScope.setAttribute(String, String)` is **deprecated**, use `setStringAttribute(String, String)`.

### Client spans

Client spans are not customizable. A client span is fired each time a call is done through `tryTransaction()` of the internal client state of API Gateway. This ensures that ALL calls using the native Vordel runtime will create spans. Trace context is propagated to the outgoing request through the `State` header set.

The following additional attributes are added to all API Gateway client spans:

- `com.axway.policy` : current policy name at time of client span creation (from the internal state object)
- `com.axway.parent.correlation` : API Gateway correlation ID of the main transaction
- `http.response.status_text` : text representation of the HTTP status code

The following response headers are captured by default:

- `Content-Type`
- `Date`
- `Last-Modified`

> Request `Host` header capture is deliberately disabled for client spans: it receives special handling in the gateway and the value visible from Java is not reliable.

### Server spans

The HTTP server implementation is much more complex, since span creation and commit are not done at the same place. Multiple interception points are used to add information to the running span.

- Span creation is done at the exit of the `HTTPMessage` constructor. Any HTTP transaction started will create a span.
- The `Message` constructor is instrumented as well, which allocates the runtime attributes factory before the first message is created. A thread-local guard (`MessageAdviceScope.disableStartOnce()`, set on entry of `HTTPPlugin.invokeDispose`) prevents the base `Message` constructor from creating a duplicate scope for HTTP transactions.
- Span commit is done when exiting `HTTPPlugin.invokeDispose()`.
- The HTTP route is computed either at the end of policy execution (`NESTED_CONTROLLER` source, API Manager `${api.path}${api.method.path}`) or at the end of `invokeDispose` (`SERVER` source, regular API Gateway path declaration).
- Response attributes (status code/text and headers) are taken either from the message state at the end of processing, or from `ServerTransaction.sendResponse()` when it is called.

Beware that API Manager and dashboard statuses are retrieved from message attributes at the end of policy evaluation. This means that if you put wrong values in attributes during processing, you will get wrong values in the span. While this is normal for the dashboard status (evaluated AFTER processing), it can lead to problems for API Manager (loss of authentication information, wrong API name, etc.).

The following additional attributes are added to all API Gateway server spans:

- `com.axway.version.commit` : long commit ID for this API Gateway instance (for support)
- `com.axway.version.label` : version of this API Gateway instance (for support)
- `com.axway.policy` : first executed policy name
- `com.axway.transaction.correlation` : API Gateway correlation ID for this transaction
- `com.axway.transaction.status` : transaction status in Traffic Monitor / Metrics DB
- `com.axway.transaction.status.original` : overridden transaction status (when the 'Set Response Status' filter is used)
- `com.axway.transaction.subject` : API Gateway subject in Traffic Monitor / Metrics DB (user or application ID)
- `com.axway.transaction.service` : API Gateway service in Traffic Monitor / Metrics DB (API name)
- `com.axway.transaction.operation` : API Gateway operation in Traffic Monitor / Metrics DB (API method)
- `http.response.status_text` : text representation of the HTTP status code
- `network.local.address` : local IP address
- `network.local.port` : local port (e.g. 8080, 8065, 8075, …)
- `network.peer.address` : remote address
- `network.peer.port` : remote port

The following topology attributes are added to all server spans (resolved once per JVM from the `ServiceHelper` service info):

- `com.axway.topology.hostname`
- `com.axway.topology.domainId`
- `com.axway.topology.groupId`
- `com.axway.topology.groupName`
- `com.axway.topology.serviceId`
- `com.axway.topology.serviceName`

The following attributes are added for API Manager spans:

- `com.axway.api.user` : authenticated user (for internal OAuth, when a user is authenticated)
- `com.axway.api.client_id` : application client ID used for authentication
- `com.axway.api.application` : name of the application used for authentication
- `com.axway.api.organization` : organization of the application used for authentication

The following request header is captured by default: `Host`.
The following response headers are captured by default: `Content-Type`, `Date`, `Last-Modified`.

### Event Log attributes

Server spans carry a subset of the API Gateway Event Log model, so that traces remain usable even when the Event Log itself is disabled:

- `com.axway.event.transaction.reception_ms` : message reception timestamp (epoch ms)
- `com.axway.event.transaction.path` : incoming request path
- `com.axway.event.transaction.protocolSrc` : inbound protocol source
- `com.axway.event.transaction.protocol` : inbound protocol type (requires the `statistics` message metrics object)
- `com.axway.event.transaction.status` : Event Log transaction status (requires the `statistics` message metrics object)

Attribute names use the constants declared by `com.vordel.metrics.eventlog.EventLogAttributes`.

When the Event Log is **enabled**, the custom message attributes configured in the Event Log configuration are exported as well, formatted through `MessagePropertiesFormatterRegistry`:

- `com.axway.event.attributes.<attributeName>`

### Exception recording

Two interception points feed span exception events:

- `InvocationEngine.invokeFilter()` — any `Throwable` escaping a filter is recorded on the current span,
- `Trace.output(int, String, Throwable)` — any exception written to the gateway trace is recorded on the current span.

Recorded exceptions carry the `com.axway.exception.policy` attribute (name of the innermost `Circuit` being executed, tracked by instrumenting `InvocationEngine.invokeCircuit()`).

De-duplication is performed per message by `WeakIdentitySet`, so the same `Throwable` instance is recorded once even if it bubbles through several filters and is also logged. Weak references are used so exception instances are not retained by the agent.

### Logs

`com.vordel.trace.Trace.output(int, String, Throwable)` is instrumented and bridged to the OpenTelemetry logs pipeline. Records are emitted only when the level is enabled by the gateway trace configuration.

Each record carries:

- the body (trace message),
- the severity and severity text (see mapping below),
- the exception, when present,
- `com.axway.component` : the API Gateway trace component name.

| API Gateway level | OpenTelemetry severity |
| --- | --- |
| `TRACE_DATA` | `TRACE` |
| `TRACE_DEBUG` | `DEBUG` |
| `TRACE_MIN` | `INFO` |
| `TRACE_INFO` | `INFO2` |
| `TRACE_ERROR` | `ERROR` |
| `TRACE_ALWAYS` | `ERROR3` |
| `TRACE_FATAL` | `FATAL` |

To export logs, do **not** set `otel.logs.exporter=none` (see the configuration example below).

### Event Log / trace correlation

Event Log entries are written outside of the transaction thread context, so the span context would otherwise be lost. `EventLoggingStore` is instrumented to restore it:

- `addTransactionLeg(String)` and `completeLogEntry(String)` make the cached span context current for the duration of the call,
- `transactionFinished(String)` does the same and then evicts the entry,
- `clearCache()` flushes the whole correlation cache.

The cache is keyed by the API Gateway correlation ID (`Message.getIDBase()`), populated at server span creation. The restored context is only applied when no valid span is already current, so it never overrides a real active span.

## Build and install

To build these artifacts, you need the following prerequisites:

- API Gateway jars available locally,
- JDK 17 (needed for the build process, the Java 11 toolchain will be downloaded automatically by the foojay resolver).

Versions currently used:

| Component | Version |
| --- | --- |
| OpenTelemetry SDK / BOM | `1.60.1` |
| OpenTelemetry instrumentation | `2.26.1` (`-alpha` for incubator/javaagent artifacts) |
| Byte Buddy | `1.15.10` |
| Shadow plugin | `9.1.0` |
| Java toolchain | `11` |

### Gradle setup

Ensure your `~/.gradle/gradle.properties` contains the `apigw_vdistdir` variable pointing to an API Gateway installation:

```gradle.properties
# installation directory for api gateway
apigw_vdistdir=/opt/Axway/apigateway
```

### Compile

Once Gradle has a valid API Gateway installation location, you can build the artifacts:

```bash
gradlew clean cleanEclipse eclipse build
```

The `collectAgentArtifacts` task (wired into `build`) gathers both deliverables in the root `build/libs` directory:

- `apigw-otel-instrumentation-<version>-extension.jar`
- `apigw-otel-runtime-<version>-helpers.jar`

### Installation

- Download and install the [opentelemetry-javaagent.jar](https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.26.1/opentelemetry-javaagent.jar) in a directory which IS NOT in the API Gateway classpath.
- Copy the `extension` suffixed jar next to `opentelemetry-javaagent.jar`.
- Copy the `helpers` suffixed jar to the API Gateway `ext/lib` directory.
- Add the OpenTelemetry agent configuration in `jvm.xml`.

```xml
<!-- Example configuration of OpenTelemetry agent -->
<ConfigurationFragment>
  <VMArg name="-javaagent:/path/to/opentelemetry-javaagent.jar"/>
  <VMArg name="-Dotel.javaagent.extensions=/path/to/opentelemetry-apim-agent-extension.jar"/>
  <VMArg name="-Dotel.service.name=axway-apigateway"/>
  <VMArg name="-Dotel.exporter.otlp.endpoint=http://otel-collector:4317"/>
  <VMArg name="-Dotel.exporter.otlp.protocol=grpc"/>
  <VMArg name="-Dotel.logs.exporter=otlp"/>
  <VMArg name="-Dotel.metrics.exporter=none"/>
  <!--<VMArg name="-Dotel.javaagent.debug=true"/>-->
</ConfigurationFragment>
```

> The helpers jar **must** be present in `ext/lib`. Without it, the `ServiceLoader` lookup performed at message creation finds no provider.

## Bugs and caveats

- It is currently not possible to filter out health check probes. At the time the span is created, API Gateway does not supply any information allowing span creation to be skipped.
- Native methods can't be intercepted nor instrumented, so the `Host` HTTP header for HTTP client spans is not reliable.
- `network.*.port` attributes are currently emitted as strings, not integers as required by semantic conventions.
- Client span attributes are read from private fields of `com.vordel.circuit.net.State` by reflection (`ci`, `host`, `port`). This is tied to the internal layout of that class and may break on gateway upgrades.
