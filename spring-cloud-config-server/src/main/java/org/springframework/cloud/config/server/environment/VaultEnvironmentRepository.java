/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.config.server.environment;

import static org.springframework.cloud.config.client.ConfigClientProperties.STATE_HEADER;
import static org.springframework.cloud.config.client.ConfigClientProperties.TOKEN_HEADER;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.core.Ordered;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

/**
 * @author Spencer Gibb
 * @author Mark Paluch
 * @author Harry Martland
 */
@Validated
public class VaultEnvironmentRepository implements EnvironmentRepository, Ordered {

	private static final String VAULT_TOKEN = "X-Vault-Token";

	/** Vault host. Defaults to 127.0.0.1. */
	@NotEmpty
	private String host;

	/** Vault port. Defaults to 8200. */
	@Min(1)
	@Max(65535)
	private int port;

	/** Vault scheme. Defaults to http. */
	private String scheme;

	/** Vault backend. Defaults to secret. */
	@NotEmpty
	private String backend;

	/** The key in vault shared by all applications. Defaults to application. Set to empty to disable. */
	private String defaultKey;

	/** Vault profile separator. Defaults to comma. */
	@NotEmpty
	private String profileSeparator;

	/** Version of the api used by vault. */
	private VaultEnvironmentProperties.Version version;

	private int order;

	private RestTemplate rest;

	// TODO: move to watchState:String on findOne?
	private ObjectProvider<HttpServletRequest> request;

	private EnvironmentWatch watch;

	public VaultEnvironmentRepository(ObjectProvider<HttpServletRequest> request, EnvironmentWatch watch, RestTemplate rest,
									  VaultEnvironmentProperties properties) {
		this.request = request;
		this.watch = watch;
		this.rest = rest;
		this.backend = properties.getBackend();
		this.defaultKey = properties.getDefaultKey();
		this.host = properties.getHost();
		this.order = properties.getOrder();
		this.port = properties.getPort();
		this.profileSeparator = properties.getProfileSeparator();
		this.scheme = properties.getScheme();
		this.version = properties.getVersion();
	}

	@Override
	public Environment findOne(String application, String profile, String label) {

		HttpServletRequest servletRequest = request.getIfAvailable();
		if (servletRequest == null) {
			throw new IllegalStateException("No HttpServletRequest available");
		}

		String state = servletRequest.getHeader(STATE_HEADER);
		String newState = this.watch.watch(state);

		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		List<String> scrubbedProfiles = scrubProfiles(profiles);

		List<String> keys = findKeys(application, scrubbedProfiles);

		Environment environment = new Environment(application, profiles, label, null, newState);

		for (String key : keys) {
			// read raw 'data' key from vault
			String data = read(servletRequest, key);
			if (data != null) {
				// data is in json format of which, yaml is a superset, so parse
				final YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
				yaml.setResources(new ByteArrayResource(data.getBytes()));
				Properties properties = yaml.getObject();

				if (!properties.isEmpty()) {
					environment.add(new PropertySource("vault:" + key, properties));
				}
			}
		}

		return environment;
	}

	private Class<? extends VaultDataProvider> getVaultResponseType() {
		if (VaultEnvironmentProperties.Version.V2.equals(version)) {
			return Vault2Response.class;
		} else {
			return VaultResponse.class;
		}
	}

	private List<String> findKeys(String application, List<String> profiles) {
		List<String> keys = new ArrayList<>();

		if (StringUtils.hasText(this.defaultKey) && !this.defaultKey.equals(application)) {
			keys.add(this.defaultKey);
			addProfiles(keys, this.defaultKey, profiles);
		}

		keys.add(application);
		addProfiles(keys, application, profiles);

		Collections.reverse(keys);
		return keys;
	}

	private List<String> scrubProfiles(String[] profiles) {
		List<String> scrubbedProfiles = new ArrayList<>(Arrays.asList(profiles));
		if (scrubbedProfiles.contains("default")) {
			scrubbedProfiles.remove("default");
		}
		return scrubbedProfiles;
	}

	private void addProfiles(List<String> contexts, String baseContext,
			List<String> profiles) {
		for (String profile : profiles) {
			contexts.add(baseContext + this.profileSeparator + profile);
		}
	}

	String read(HttpServletRequest servletRequest, String key) {
		String url = createRequest();

		HttpHeaders headers = new HttpHeaders();

		String token = servletRequest.getHeader(TOKEN_HEADER);
		if (!StringUtils.hasLength(token)) {
			throw new IllegalArgumentException("Missing required header: " + TOKEN_HEADER);
		}
		headers.add(VAULT_TOKEN, token);
		try {
			ResponseEntity<? extends VaultDataProvider> response = this.rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers),
					getVaultResponseType(), this.backend, key);

			HttpStatus status = response.getStatusCode();
			if (status == HttpStatus.OK) {
				return response.getBody().getData();
			}
		} catch (HttpStatusCodeException e) {
			if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
				return null;
			}
			throw e;
		}

		return null;
	}

	private String createRequest() {
		if (VaultEnvironmentProperties.Version.V2.equals(version)) {
			return String.format("%s://%s:%s/v1/{backend}/data/{key}", this.scheme, this.host, this.port);
		} else {
			return String.format("%s://%s:%s/v1/{backend}/{key}", this.scheme, this.host, this.port);
		}
	}
	public void setHost(String host) {
		this.host = host;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public void setScheme(String scheme) {
		this.scheme = scheme;
	}

	public void setBackend(String backend) {
		this.backend = backend;
	}

	public void setDefaultKey(String defaultKey) {
		this.defaultKey = defaultKey;
	}

	public void setProfileSeparator(String profileSeparator) {
		this.profileSeparator = profileSeparator;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return order;
	}

	interface VaultDataProvider {
		String getData();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	static class VaultResponse implements VaultDataProvider {

		private Object data;

		@Override
		@JsonRawValue
		public String getData() {
			return data == null ? null : data.toString();
		}

		public void setData(JsonNode data) {
			this.data = data;
		}

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	static class Vault2Response implements VaultDataProvider {

		private Vault2ResponseData data;

		@Override
		@JsonRawValue
		public String getData() {
			return data == null ? null : data.getData();
		}

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	static class Vault2ResponseData implements VaultDataProvider {

		private Object data;

		@Override
		@JsonRawValue
		public String getData() {
			return data == null ? null : data.toString();
		}

		public void setData(JsonNode data) {
			this.data = data;
		}

	}
}
