/*
 * Copyright 2017 the original author or authors.
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
package org.springframework.vault.repository.core;

import org.springframework.data.keyvalue.core.KeyValueAdapter;
import org.springframework.data.keyvalue.core.KeyValueTemplate;
import org.springframework.vault.repository.mapping.VaultMappingContext;

/**
 * Vault-specific {@link KeyValueTemplate}.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public class VaultKeyValueTemplate extends KeyValueTemplate {

	/**
	 * Create a new {@link VaultKeyValueTemplate} given {@link KeyValueAdapter} and
	 * {@link VaultMappingContext}.
	 *
	 * @param adapter must not be {@literal null}.
	 */
	public VaultKeyValueTemplate(KeyValueAdapter adapter) {
		this(adapter, new VaultMappingContext());
	}

	/**
	 * Create a new {@link VaultKeyValueTemplate} given {@link KeyValueAdapter} and
	 * {@link VaultMappingContext}.
	 * 
	 * @param adapter must not be {@literal null}.
	 * @param mappingContext must not be {@literal null}.
	 */
	public VaultKeyValueTemplate(KeyValueAdapter adapter,
			VaultMappingContext mappingContext) {
		super(adapter, mappingContext);
	}

	@Override
	public void destroy() throws Exception {
		// no-op to prevent clear() call.
	}
}
