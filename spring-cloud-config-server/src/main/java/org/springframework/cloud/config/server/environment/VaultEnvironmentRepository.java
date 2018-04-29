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

import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestTemplate;

/**
 * @author Spencer Gibb
 * @author Mark Paluch
 * @author Harry Martland
 */
@Validated
public class VaultEnvironmentRepository extends AbstractVaultEnvironmentRepository<VaultResponse> {

    public VaultEnvironmentRepository(ObjectProvider<HttpServletRequest> request, EnvironmentWatch watch, RestTemplate rest,
                                      VaultEnvironmentProperties properties) {
        super(request, watch, rest, properties);
    }

    @Override
    protected String createVaultUrl() {
        return String.format("%s://%s:%s/v1/{backend}/{key}", this.scheme, this.host, this.port);
    }

    @Override
    protected Class<VaultResponse> getVaultDataAccessClass() {
        return VaultResponse.class;
    }

}
