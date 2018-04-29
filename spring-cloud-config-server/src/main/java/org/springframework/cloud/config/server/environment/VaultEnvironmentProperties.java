/*
 * Copyright 2018 the original author or authors.
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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.config.server.support.EnvironmentRepositoryProperties;
import org.springframework.core.Ordered;

/**
 * @author Dylan Roberts
 * @author Harry Martland
 */
@ConfigurationProperties("spring.cloud.config.server.vault")
public class VaultEnvironmentProperties implements EnvironmentRepositoryProperties {
    /** Vault host. Defaults to 127.0.0.1. */
    private String host = "127.0.0.1";
    /** Vault port. Defaults to 8200. */
    private Integer port = 8200;
    /** Vault scheme. Defaults to http. */
    private String scheme = "http";
    /** Vault backend. Defaults to secret. */
    private String backend = "secret";
    /** The key in vault shared by all applications. Defaults to application. Set to empty to disable. */
    private String defaultKey = "application";
    /** Vault profile separator. Defaults to comma. */
    private String profileSeparator = ",";
    private int order = Ordered.LOWEST_PRECEDENCE;
    /** Version of the api used by vault */
    private Version version;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getBackend() {
        return backend;
    }

    public void setBackend(String backend) {
        this.backend = backend;
    }

    public String getDefaultKey() {
        return defaultKey;
    }

    public void setDefaultKey(String defaultKey) {
        this.defaultKey = defaultKey;
    }

    public String getProfileSeparator() {
        return profileSeparator;
    }

    public void setProfileSeparator(String profileSeparator) {
        this.profileSeparator = profileSeparator;
    }

    public int getOrder() {
        return order;
    }

    public Version getVersion() {
        return version;
    }

    public void setVersion(Version version) {
        this.version = version;
    }

    @Override
    public void setOrder(int order) {
        this.order = order;
    }

    public enum Version{
        V1,V2
    }
}
