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
public abstract class AbstractVaultEnvironmentRepository<D extends AbstractVaultEnvironmentRepository.VaultDataAccess> implements EnvironmentRepository, Ordered {

    public static final String VAULT_TOKEN = "X-Vault-Token";

    /**
     * Vault host. Defaults to 127.0.0.1.
     */
    @NotEmpty
    protected String host;

    /**
     * Vault port. Defaults to 8200.
     */
    @Min(1)
    @Max(65535)
    protected int port;

    /**
     * Vault scheme. Defaults to http.
     */
    protected String scheme;

    /**
     * Vault backend. Defaults to secret.
     */
    @NotEmpty
    private String backend;

    /**
     * The key in vault shared by all applications. Defaults to application. Set to empty to disable.
     */
    private String defaultKey;

    /**
     * Vault profile separator. Defaults to comma.
     */
    @NotEmpty
    private String profileSeparator;

    private int order;

    private RestTemplate rest;

    // TODO: move to watchState:String on findOne?
    private ObjectProvider<HttpServletRequest> request;

    private EnvironmentWatch watch;

    public AbstractVaultEnvironmentRepository(ObjectProvider<HttpServletRequest> request, EnvironmentWatch watch, RestTemplate rest,
                                              AbstractVaultEnvironmentProperties properties) {
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
        String url = createVaultUrl();

        HttpHeaders headers = new HttpHeaders();

        String token = servletRequest.getHeader(TOKEN_HEADER);
        if (!StringUtils.hasLength(token)) {
            throw new IllegalArgumentException("Missing required header: " + TOKEN_HEADER);
        }
        headers.add(VAULT_TOKEN, token);
        try {
            ResponseEntity<D> response = this.rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers),
                    getVaultDataAccessClass(), this.backend, key);

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

    abstract protected String createVaultUrl();

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

    @Override
    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    protected abstract Class<D> getVaultDataAccessClass();

    interface VaultDataAccess {
        String getData();
    }

}
