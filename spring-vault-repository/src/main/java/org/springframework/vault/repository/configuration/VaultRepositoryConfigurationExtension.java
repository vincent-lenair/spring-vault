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
package org.springframework.vault.repository.configuration;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.keyvalue.repository.config.KeyValueRepositoryConfigurationExtension;
import org.springframework.data.keyvalue.repository.config.QueryCreatorType;
import org.springframework.data.keyvalue.repository.query.KeyValuePartTreeQuery;
import org.springframework.data.keyvalue.repository.query.SpelQueryCreator;
import org.springframework.data.repository.config.AnnotationRepositoryConfigurationSource;
import org.springframework.data.repository.config.RepositoryConfigurationExtension;
import org.springframework.data.repository.config.RepositoryConfigurationSource;
import org.springframework.vault.repository.core.VaultKeyValueAdapter;
import org.springframework.vault.repository.core.VaultKeyValueTemplate;
import org.springframework.vault.repository.mapping.Secret;
import org.springframework.vault.repository.mapping.VaultMappingContext;

/**
 * {@link RepositoryConfigurationExtension} for Vault.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public class VaultRepositoryConfigurationExtension extends
		KeyValueRepositoryConfigurationExtension {

	private static final String VAULT_ADAPTER_BEAN_NAME = "vaultKeyValueAdapter";
	private static final String VAULT_MAPPING_CONTEXT_BEAN_NAME = "vaultMappingContext";

	@Override
	public String getModuleName() {
		return "Vault";
	}

	@Override
	protected String getModulePrefix() {
		return "vault";
	}

	@Override
	protected String getDefaultKeyValueTemplateRef() {
		return "vaultKeyValueTemplate";
	}

	// TODO: Remove this as soon as KeyValue provides overrides for the mapping context
	// name.
	@Override
	public void postProcess(BeanDefinitionBuilder builder,
			AnnotationRepositoryConfigurationSource config) {

		AnnotationAttributes attributes = config.getAttributes();

		builder.addPropertyReference("keyValueOperations",
				attributes.getString(KEY_VALUE_TEMPLATE_BEAN_REF_ATTRIBUTE));
		builder.addPropertyValue("queryCreator", getQueryCreatorType(config));
		builder.addPropertyValue("queryType", getQueryType(config));
		builder.addPropertyReference("mappingContext", VAULT_MAPPING_CONTEXT_BEAN_NAME);
	}

	/**
	 * Detects the query creator type to be used for the factory to set. Will lookup a
	 * {@link QueryCreatorType} annotation on the {@code @Enable}-annotation or use
	 * {@link SpelQueryCreator} if not found.
	 *
	 * @param config
	 * @return
	 */
	private static Class<?> getQueryCreatorType(
			AnnotationRepositoryConfigurationSource config) {

		AnnotationMetadata metadata = config.getEnableAnnotationMetadata();

		Map<String, Object> queryCreatorAnnotationAttributes = metadata
				.getAnnotationAttributes(QueryCreatorType.class.getName());

		if (queryCreatorAnnotationAttributes == null) {
			return SpelQueryCreator.class;
		}

		AnnotationAttributes queryCreatorAttributes = new AnnotationAttributes(
				queryCreatorAnnotationAttributes);
		return queryCreatorAttributes.getClass("value");
	}

	/**
	 * Detects the query creator type to be used for the factory to set. Will lookup a
	 * {@link QueryCreatorType} annotation on the {@code @Enable}-annotation or use
	 * {@link SpelQueryCreator} if not found.
	 *
	 * @param config
	 * @return
	 */
	private static Class<?> getQueryType(AnnotationRepositoryConfigurationSource config) {

		AnnotationMetadata metadata = config.getEnableAnnotationMetadata();

		Map<String, Object> queryCreatorAnnotationAttributes = metadata
				.getAnnotationAttributes(QueryCreatorType.class.getName());

		if (queryCreatorAnnotationAttributes == null) {
			return KeyValuePartTreeQuery.class;
		}

		AnnotationAttributes queryCreatorAttributes = new AnnotationAttributes(
				queryCreatorAnnotationAttributes);
		return queryCreatorAttributes.getClass("repositoryQueryType");
	}

	@Override
	public void registerBeansForRoot(BeanDefinitionRegistry registry,
			RepositoryConfigurationSource configurationSource) {

		Optional<String> vaultTemplateRef = configurationSource
				.getAttribute("vaultTemplateRef");

		RootBeanDefinition mappingContextDefinition = createVaultMappingContext(configurationSource);
		mappingContextDefinition.setSource(configurationSource.getSource());

		registerIfNotAlreadyRegistered(mappingContextDefinition, registry,
				VAULT_MAPPING_CONTEXT_BEAN_NAME, configurationSource);

		// register Adapter
		RootBeanDefinition vaultKeyValueAdapterDefinition = new RootBeanDefinition(
				VaultKeyValueAdapter.class);

		ConstructorArgumentValues constructorArgumentValuesForVaultKeyValueAdapter = new ConstructorArgumentValues();

		constructorArgumentValuesForVaultKeyValueAdapter.addIndexedArgumentValue(0,
				new RuntimeBeanReference(vaultTemplateRef.orElse("vaultTemplate")));

		vaultKeyValueAdapterDefinition
				.setConstructorArgumentValues(constructorArgumentValuesForVaultKeyValueAdapter);

		registerIfNotAlreadyRegistered(vaultKeyValueAdapterDefinition, registry,
				VAULT_ADAPTER_BEAN_NAME, configurationSource);

		Optional<String> keyValueTemplateName = configurationSource
				.getAttribute(KEY_VALUE_TEMPLATE_BEAN_REF_ATTRIBUTE);

		// No custom template reference configured and no matching bean definition found
		if (keyValueTemplateName.isPresent()
				&& getDefaultKeyValueTemplateRef().equals(keyValueTemplateName.get())
				&& !registry.containsBeanDefinition(keyValueTemplateName.get())) {

			AbstractBeanDefinition beanDefinition = getDefaultKeyValueTemplateBeanDefinition(configurationSource);

			if (beanDefinition != null) {
				registerIfNotAlreadyRegistered(beanDefinition, registry,
						keyValueTemplateName.get(), configurationSource.getSource());
			}
		}
	}

	private RootBeanDefinition createVaultMappingContext(
			RepositoryConfigurationSource configurationSource) {

		ConstructorArgumentValues mappingContextArgs = new ConstructorArgumentValues();

		RootBeanDefinition mappingContextBeanDef = new RootBeanDefinition(
				VaultMappingContext.class);
		mappingContextBeanDef.setConstructorArgumentValues(mappingContextArgs);

		return mappingContextBeanDef;
	}

	@Override
	protected AbstractBeanDefinition getDefaultKeyValueTemplateBeanDefinition(
			RepositoryConfigurationSource configurationSource) {

		RootBeanDefinition keyValueTemplateDefinition = new RootBeanDefinition(
				VaultKeyValueTemplate.class);

		ConstructorArgumentValues constructorArgumentValuesForKeyValueTemplate = new ConstructorArgumentValues();
		constructorArgumentValuesForKeyValueTemplate.addIndexedArgumentValue(0,
				new RuntimeBeanReference(VAULT_ADAPTER_BEAN_NAME));

		constructorArgumentValuesForKeyValueTemplate.addIndexedArgumentValue(1,
				new RuntimeBeanReference(VAULT_MAPPING_CONTEXT_BEAN_NAME));

		keyValueTemplateDefinition
				.setConstructorArgumentValues(constructorArgumentValuesForKeyValueTemplate);

		return keyValueTemplateDefinition;
	}

	@Override
	protected Collection<Class<? extends Annotation>> getIdentifyingAnnotations() {
		return Collections.<Class<? extends Annotation>> singleton(Secret.class);
	}
}
