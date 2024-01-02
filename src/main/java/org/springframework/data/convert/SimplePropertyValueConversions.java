/*
 * Copyright 2022-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.convert;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.convert.PropertyValueConverterFactories.ChainedPropertyValueConverterFactory;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * {@link PropertyValueConversions} implementation allowing a {@link PropertyValueConverterFactory} creating
 * {@link PropertyValueConverter converters} to be chosen. Activating {@link #setConverterCacheEnabled(boolean) cahing}
 * allows converters to be reused.
 * <p>
 * Providing a {@link SimplePropertyValueConverterRegistry} adds path configured converter instances.
 * <p>
 * This class should be {@link #afterPropertiesSet() initialized}. If not, {@link #init()} will be called on the first
 * attempt of {@link PropertyValueConverter converter} retrieval.
 *
 * @author Christoph Strobl
 * @author Mark Paluch
 * @see PropertyValueConversions
 * @see InitializingBean
 * @since 2.7
 */
public class SimplePropertyValueConversions implements PropertyValueConversions, InitializingBean {

	private static final String NO_CONVERTER_FACTORY_ERROR_MESSAGE = "PropertyValueConverterFactory is not set; Make sure to either set the converter factory or call afterPropertiesSet() to initialize the object";

	private boolean converterCacheEnabled = true;

	private @Nullable PropertyValueConverterFactory converterFactory;

	private @Nullable ValueConverterRegistry<?> valueConverterRegistry;

	/**
	 * Set the {@link PropertyValueConverterFactory} responsible for creating the actual {@link PropertyValueConverter}.
	 *
	 * @param converterFactory {@link PropertyValueConverterFactory} used to create the actual
	 *          {@link PropertyValueConverter}.
	 * @see PropertyValueConverterFactory
	 */
	public void setConverterFactory(@Nullable PropertyValueConverterFactory converterFactory) {
		this.converterFactory = converterFactory;
	}

	/**
	 * Returns the configured {@link PropertyValueConverterFactory} responsible for creating the actual
	 * {@link PropertyValueConverter}.
	 *
	 * @return the configured {@link PropertyValueConverterFactory}; can be {@literal null}.
	 * @see PropertyValueConverterFactory
	 */
	@Nullable
	public PropertyValueConverterFactory getConverterFactory() {
		return converterFactory;
	}

	private PropertyValueConverterFactory requireConverterFactory() {

		PropertyValueConverterFactory factory = getConverterFactory();

		Assert.state(factory != null, NO_CONVERTER_FACTORY_ERROR_MESSAGE);

		return factory;
	}

	/**
	 * Set the {@link ValueConverterRegistry converter registry} used for path configured converters.
	 * <p>
	 * This is short for adding a
	 * {@link org.springframework.data.convert.PropertyValueConverterFactories.ConfiguredInstanceServingValueConverterFactory}
	 * at the end of a {@link ChainedPropertyValueConverterFactory}.
	 *
	 * @param valueConverterRegistry registry of {@link PropertyValueConverter PropertyValueConverters}.
	 * @see ValueConverterRegistry
	 */
	public void setValueConverterRegistry(@Nullable ValueConverterRegistry<?> valueConverterRegistry) {
		this.valueConverterRegistry = valueConverterRegistry;
	}

	/**
	 * Get the {@link ValueConverterRegistry} used for path configured converters.
	 *
	 * @return the configured {@link ValueConverterRegistry}; can be {@literal null}.
	 * @see ValueConverterRegistry
	 */
	@Nullable
	public ValueConverterRegistry<?> getValueConverterRegistry() {
		return valueConverterRegistry;
	}

	/**
	 * Configure whether to use converter the cache. Enabled by default.
	 *
	 * @param converterCacheEnabled set to {@literal true} to enable caching of {@link PropertyValueConverter converter}
	 *          instances.
	 */
	public void setConverterCacheEnabled(boolean converterCacheEnabled) {
		this.converterCacheEnabled = converterCacheEnabled;
	}

	/**
	 * Determines whether a {@link PropertyValueConverter} has been registered for the given {@link PersistentProperty
	 * property}.
	 *
	 * @param property {@link PersistentProperty} to evaluate.
	 * @return {@literal true} if a {@link PropertyValueConverter} has been registered for the given
	 *         {@link PersistentProperty property}.
	 * @see PersistentProperty
	 */
	@Override
	public boolean hasValueConverter(PersistentProperty<?> property) {
		return requireConverterFactory().getConverter(property) != null;
	}

	@Override
	public <DV, SV, P extends PersistentProperty<P>, D extends ValueConversionContext<P>> PropertyValueConverter<DV, SV, D> getValueConverter(
			P property) {

		PropertyValueConverter<DV, SV, D> converter = requireConverterFactory().getConverter(property);

		Assert.notNull(converter, String.format("No PropertyValueConverter registered for %s", property));

		return converter;
	}

	/**
	 * May be called just once to initialize the underlying factory with its values.
	 */
	public void init() {

		List<PropertyValueConverterFactory> factoryList = new ArrayList<>(3);

		factoryList.add(resolveConverterFactory());

		resolveConverterRegistryAsConverterFactory().ifPresent(factoryList::add);

		PropertyValueConverterFactory targetFactory = factoryList.size() > 1
				? PropertyValueConverterFactory.chained(factoryList)
				: factoryList.iterator().next();

		this.converterFactory = converterCacheEnabled ? PropertyValueConverterFactory.caching(targetFactory)
				: targetFactory;
	}

	private PropertyValueConverterFactory resolveConverterFactory() {

		PropertyValueConverterFactory converterFactory = getConverterFactory();

		return converterFactory != null ? converterFactory : PropertyValueConverterFactory.simple();
	}

	private Optional<PropertyValueConverterFactory> resolveConverterRegistryAsConverterFactory() {

		return Optional.ofNullable(getValueConverterRegistry()).filter(it -> !it.isEmpty())
				.map(PropertyValueConverterFactory::configuredInstance);
	}

	/**
	 * Initializes this {@link SimplePropertyValueConversions} instance.
	 *
	 * @see #init()
	 */
	@Override
	public void afterPropertiesSet() {
		init();
	}
}
