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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * @author Harry Martland
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Vault2Response implements AbstractVaultEnvironmentRepository.VaultDataAccess {

    @JsonProperty("data")
    private Vault2ResponseData data;

    @Override
    public String getData() {
        return data.getData();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Vault2ResponseData {

        @JsonRawValue
        private Object data;

        @JsonRawValue
        public String getData() {
            return data == null ? null : data.toString();
        }

        public void setData(JsonNode data) {
            this.data = data;
        }
    }

}
