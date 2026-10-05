package com.axway.otel.apigw.service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.axway.otel.shared.service.MessageAdviceAttributes;
import com.axway.otel.shared.service.MessageScope;
import com.vordel.apiportal.api.portal.model.Organization;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProperties;
import com.vordel.circuit.format.MessagePropertiesFormatterRegistry;
import com.vordel.circuit.oauth.token.OAuth2Authentication;
import com.vordel.common.apiserver.model.Application;
import com.vordel.common.apiserver.service.CoreServiceRegistry;
import com.vordel.coreapireg.runtime.AuthResult;
import com.vordel.el.Selector;
import com.vordel.metrics.eventlog.EventLogAttributes;
import com.vordel.metrics.eventlog.logger.EventLogger;
import com.vordel.metrics.eventlog.logger.EventLoggers;
import com.vordel.mime.Body;
import com.vordel.mime.HeaderSet;
import com.vordel.runtime.interfaces.ServiceHelper;
import com.vordel.runtime.interfaces.ServiceInfo;	
import com.vordel.statistics.IMessageMetrics;
import com.vordel.statistics.MessageMetrics;
import com.vordel.statistics.ResponseStatusRecord;
import com.vordel.version.ProductVersion;

public class RuntimeMessageAttributes extends MessageAdviceAttributes {
	private static final Selector<Integer> RESPONSE_STATUS = new Selector<Integer>(
			String.format("${%s}", MessageProperties.HTTP_RSP_STATUS), Integer.class);

	private static final Selector<String> RESPONSE_TEXT = new Selector<String>(
			String.format("${%s}", MessageProperties.HTTP_RSP_INFO), String.class);

	private static final Selector<HeaderSet> MESSAGE_HEADERS = new Selector<HeaderSet>(
			String.format("${%s}", MessageProperties.HTTP_HEADERS), HeaderSet.class);

	private static final Selector<Body> MESSAGE_BODY = new Selector<Body>(
			String.format("${%s}", MessageProperties.CONTENT_BODY), Body.class);

	private static final Selector<Boolean> USER_MESSAGE_STATUS_ENABLED = new Selector<Boolean>(
			"${user.set.response.status}", Boolean.class);
	private static final Selector<Integer> USER_MESSAGE_STATUS_VALUE = new Selector<Integer>(
			"${circuit.response.status}", Integer.class);

	private static final Selector<String> MESSAGE_SERVICE = new Selector<String>(
			String.format("${%s}", MessageProperties.SERVICE_NAME), String.class);
	private static final Selector<String> MESSAGE_SUBJECT = new Selector<String>(
			String.format("${%s}", MessageProperties.AUTHN_SUBJECT_ID), String.class);
	private static final Selector<String> MESSAGE_OPERATION = new Selector<String>(
			String.format("${%s}", MessageProperties.SOAP_REQUEST_METHOD), String.class);

	private static final Selector<OAuth2Authentication> OAUTH_AUTHN = new Selector<OAuth2Authentication>(
			"${accesstoken.authn}", OAuth2Authentication.class);
	private static final Selector<AuthResult> APIKEY_AUTHN = new Selector<AuthResult>("${apiruntime.authN}",
			AuthResult.class);

	private static final Selector<String> API_PATH = new Selector<String>(
			"${api.path}", String.class);
	private static final Selector<String> API_METHOD_PATH = new Selector<String>(
			"${api.method.path}", String.class);

	private static final Selector<String> HTTP_REQ_INCOMING_PATH = new Selector<String>(
			String.format("${%s}", MessageProperties.HTTP_REQ_INCOMING_PATH), String.class);
	private static final Selector<String> MESSAGE_SOURCE = new Selector<String>(
			String.format("${%s}", MessageProperties.MESSAGE_SOURCE), String.class);

    private static final String hostname;
    private static final String domainId;
    private static final String groupId;
    private static final String groupName;
    private static final String serviceId;
    private static final String serviceName;
    
    static {
    	ServiceHelper serviceHelper = CoreServiceRegistry.getInstance().getService(ServiceHelper.class);
    	ServiceInfo serviceInfo = serviceHelper.getServiceInfo();

    	hostname = serviceInfo.getHostName();
        domainId = serviceInfo.getDomainID();
        groupId = serviceInfo.getGroupID();
        groupName = serviceInfo.getGroupName();
        serviceId = serviceInfo.getID();
        serviceName = serviceInfo.getName();
    }
	
	private final MessageScope scope;
	private final Message message;

	private Integer responseCode = null;

	private String responseText = null;

	private HeaderSet headers = null;

	private Body body = null;

	protected RuntimeMessageAttributes(Message message, MessageScope scope) {
		this.message = message;
		this.scope = scope;

		setStringAttribute("com.axway.version.commit", ProductVersion.getLongCommitID());
		setStringAttribute("com.axway.version.label", ProductVersion.getLabel());
	}

	@Override
	public int getHttpResponseStatusCode() {
		try {
			Integer status = responseCode == null ? RESPONSE_STATUS.substitute(message) : responseCode;

			return status == null ? 500 : status;
		} catch (Exception ex) {
			return 500;
		}
	}

	@Override
	public String getHttpResponseStatusText() {
		String status = responseText == null ? RESPONSE_TEXT.substitute(message) : responseText;

		return status;
	}

	public static final void appendValues(List<String> output, HeaderSet headers, String name) {
		if (headers != null) {
			Iterator<String> values = headers.getHeaders(name);

			while ((values != null) && values.hasNext()) {
				output.add(values.next());
			}
		}
	}

	@Override
	public List<String> getHttpResponseHeader(String name) {
		List<String> output = new ArrayList<String>();
		HeaderSet headers = this.headers == null ? MESSAGE_HEADERS.substitute(message) : this.headers;
		Body body = this.body == null ? MESSAGE_BODY.substitute(message) : this.body;

		appendValues(output, headers, name);

		if (body != null) {
			appendValues(output, body.getHeaders(), name);
		}

		return output;
	}

	@Override
	public void setDashboardStatus(int status) {
		String correlationID = message.correlationId.toString();
		String reported = null;

		switch (status) {
		case ResponseStatusRecord.STATUS_ERROR:
			reported = "exception";
			break;
		case ResponseStatusRecord.STATUS_PASS:
			reported = "pass";
			break;
		case ResponseStatusRecord.STATUS_FAIL:
			reported = "blocked";
			break;
		default:
			reported = "unknown";
		}

		Boolean hasUserStatus = USER_MESSAGE_STATUS_ENABLED.substitute(message);
		Integer userStatus = null;

		if ((hasUserStatus != null) && hasUserStatus.booleanValue()) {
			userStatus = USER_MESSAGE_STATUS_VALUE.substitute(message);
		}

		if (userStatus != null) {
			String userReport = null;

			switch (userStatus.intValue()) {
			case ResponseStatusRecord.STATUS_ERROR:
				userReport = "exception";
				break;
			case ResponseStatusRecord.STATUS_PASS:
				userReport = "pass";
				break;
			case ResponseStatusRecord.STATUS_FAIL:
				userReport = "blocked";
				break;
			default:
				userReport = "unknown";
			}

			if (!userReport.equals(reported)) {
				setStringAttribute("com.axway.transaction.status.original", reported);

				reported = userReport;
			}
		}

		setStringAttribute("com.axway.transaction.status", reported);
		setStringAttribute("com.axway.transaction.correlation", correlationID);

		setStringAttribute("com.axway.transaction.subject", MESSAGE_SUBJECT.substitute(message));
		setStringAttribute("com.axway.transaction.service", MESSAGE_SERVICE.substitute(message));
		setStringAttribute("com.axway.transaction.operation", MESSAGE_OPERATION.substitute(message));
		
		/*
		 * Additional stuff to generate EventLog information (most can be generated without enabling EventLog
		 */
		
		try {
			setLongAttribute("com.axway.event.transaction.reception_ms", (Long) message.get(MessageProperties.MESSAGE_RECEPTION_TIME));
		} catch(Throwable igonre) {
		}

		setStringAttribute(String.format("com.axway.event.transaction.%s", EventLogAttributes.TRANSACTION_PATH), HTTP_REQ_INCOMING_PATH.substitute(message));
		setStringAttribute(String.format("com.axway.event.transaction.%s", EventLogAttributes.TRANSACTION_INBOUND_PROTOCOL_SOURCE), MESSAGE_SOURCE.substitute(message));
		
		MessageMetrics messageMetrics = (MessageMetrics) message.get("statistics");
		
		if (messageMetrics != null) {
			/* generate additional information if metrics object available */
			String metricsStatus = IMessageMetrics.mapStatusResultForEventLog(messageMetrics.getClientResponse().getResponseStatus());

			setStringAttribute(String.format("com.axway.event.transaction.%s", EventLogAttributes.TRANSACTION_INBOUND_PROTOCOL_TYPE), messageMetrics.getInboundProtocolType(message));
			setStringAttribute(String.format("com.axway.event.transaction.%s", EventLogAttributes.TRANSACTION_STATUS), metricsStatus);
		}
		
		EventLogger loggers = EventLoggers.get();
		
		if (loggers.getConfig().isEnabled()) {
			/* add event attributes information if available */
			for (String attributeName : loggers.getConfig().getCustomMessageAttributes()) {
	            Object value = message.get(attributeName);
	            if (value != null) {
	            	setStringAttribute(String.format("com.axway.event.attributes.%s", attributeName), MessagePropertiesFormatterRegistry.apply(message, attributeName));
	            }
	        }
		}
		
		setStringAttribute(String.format("com.axway.topology.%s", "hostname"), hostname);
		setStringAttribute(String.format("com.axway.topology.%s", "domainId"), domainId);
		setStringAttribute(String.format("com.axway.topology.%s", "groupId"), groupId);
		setStringAttribute(String.format("com.axway.topology.%s", "groupName"), groupName);
		setStringAttribute(String.format("com.axway.topology.%s", "serviceId"), serviceId);
		setStringAttribute(String.format("com.axway.topology.%s", "serviceName"), serviceName);
	}

	@Override
	public void sendResponse(int responseCode, String responseText, HeaderSet headers, Body body, int bodyFlags) {
		this.responseCode = responseCode;
		this.responseText = responseText;
		this.headers = headers;
		this.body = body;
	}

	@Override
	public String getAPIManagerUriTemplate() {
		String prefix = API_PATH.substitute(message);
		String method = API_METHOD_PATH.substitute(message);

		return prefix == null || method == null ? null : String.format("%s%s", prefix, method);
	}

	@Override
	public void setAPIManagerAttributes() {
		AuthResult apiKeyAuthN = APIKEY_AUTHN.substitute(message);
		OAuth2Authentication oauthAuthN = OAUTH_AUTHN.substitute(message);

		Organization organization = null;
		Application application = null;
		String clientId = null; /* for OAuth authentication */
		String userName = null; /* for OAuth authentication when a user is authenticated */

		if (apiKeyAuthN != null) {
			application = apiKeyAuthN.getApplication();
			organization = apiKeyAuthN.getOrganization();
		}

		if (oauthAuthN != null) {
			clientId = oauthAuthN.getAuthorizationRequest().getClientId();
			userName = oauthAuthN.getUserAuthentication();
		}

		if (userName != null) {
			setStringAttribute("com.axway.api.user", userName);
		}

		setApplicationAttributes(application == null ? null : application.name,
				organization == null ? null : organization.name);

		if (clientId != null) {
			setStringAttribute("com.axway.api.client_id", clientId);
		}
	}

	private void setApplicationAttributes(String appName, String orgName) {
		if (appName != null) {
			setStringAttribute("com.axway.api.application", appName);
		}

		if (orgName != null) {
			setStringAttribute("com.axway.api.organization", orgName);
		}
	}

	@Override
	@Deprecated
	public final void setAttribute(String key, String value) {
		scope.setStringAttribute(key, value);
	}

	@Override
	public void setStringAttribute(String key, String value) {
		scope.setStringAttribute(key, value);
	}

	@Override
	public void setLongAttribute(String key, long value) {
		scope.setLongAttribute(key, value);
	}

	@Override
	public void setDoubleAttribute(String key, double value) {
		scope.setDoubleAttribute(key, value);
	}

	@Override
	public void setBooleanAttribute(String key, boolean value) {
		scope.setBooleanAttribute(key, value);
	}
}
