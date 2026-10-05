package com.axway.otel.shared.service;

import com.vordel.mime.Body;
import com.vordel.mime.HeaderSet;

public abstract class MessageAdviceAttributes extends MessageAttributes {
	public abstract void setDashboardStatus(int status);

	public abstract void sendResponse(int responseCode, String responseText, HeaderSet headers, Body body, int bodyFlags);

	public abstract void setAPIManagerAttributes();

	public abstract String getAPIManagerUriTemplate();
}
