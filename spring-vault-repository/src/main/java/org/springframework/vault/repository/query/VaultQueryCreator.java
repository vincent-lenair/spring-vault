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
package org.springframework.vault.repository.query;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import lombok.Value;

import org.springframework.data.domain.Sort;
import org.springframework.data.keyvalue.core.query.KeyValueQuery;
import org.springframework.data.repository.query.ParameterAccessor;
import org.springframework.data.repository.query.parser.AbstractQueryCreator;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.data.repository.query.parser.Part.IgnoreCaseType;
import org.springframework.data.repository.query.parser.Part.Type;

/**
 * Query creator for Vault queries. Vault queries are limited to criterias constraining
 * the {@link org.springframework.data.annotation.Id} property. A query consists of
 * chained {@link Predicate}s that are evaluated for each Id value.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public class VaultQueryCreator extends
		AbstractQueryCreator<KeyValueQuery<VaultQuery>, VaultQuery> {

	/**
	 * Create a new {@link VaultQueryCreator} given {@link PartTree}.
	 *
	 * @param tree must not be {@literal null}.
	 */
	public VaultQueryCreator(PartTree tree) {
		super(tree);
	}

	/**
	 * Create a new {@link VaultQueryCreator} given {@link PartTree} and
	 * {@link ParameterAccessor}.
	 *
	 * @param tree must not be {@literal null}.
	 * @param parameters must not be {@literal null}.
	 */
	public VaultQueryCreator(PartTree tree, ParameterAccessor parameters) {
		super(tree, parameters);
	}

	@Override
	protected VaultQuery create(Part part, Iterator<Object> parameters) {
		return new VaultQuery(createPredicate(part, parameters), part.getProperty());
	}

	@Override
	protected VaultQuery and(Part part, VaultQuery base, Iterator<Object> parameters) {

		if (base == null) {
			return create(part, parameters);
		}
		return base.and(createPredicate(part, parameters), part.getProperty());
	}

	private static Predicate<String> createPredicate(Part part,
			Iterator<Object> parameters) {

		VariableAccessor accessor = getVariableAccessor(part);

		Predicate<String> predicate = from(part, accessor, parameters);

		return it -> predicate.test(accessor.toString(it));
	}

	/**
	 * Return a {@link Predicate} depending on the {@link Part} given.
	 *
	 * @param part
	 * @param parameters
	 * @return
	 */
	private static Predicate<String> from(Part part, VariableAccessor accessor,
			Iterator<Object> parameters) {

		Type type = part.getType();

		switch (type) {
		case AFTER:
		case GREATER_THAN:
			return new Criteria<>(accessor.nextString(parameters),
					(value, it) -> it.compareTo(value) > 0);
		case GREATER_THAN_EQUAL:
			return new Criteria<>(accessor.nextString(parameters),
					(value, it) -> it.compareTo(value) >= 0);
		case BEFORE:
		case LESS_THAN:
			return new Criteria<>(accessor.nextString(parameters),
					(value, it) -> it.compareTo(value) < 0);
		case LESS_THAN_EQUAL:
			return new Criteria<>(accessor.nextString(parameters),
					(value, it) -> it.compareTo(value) <= 0);
		case BETWEEN:

			String from = accessor.nextString(parameters);
			String to = accessor.nextString(parameters);

			return it -> it.compareTo(from) >= 0 && it.compareTo(to) <= 0;
		case NOT_IN:
			return new Criteria<>(accessor.nextAsArray(parameters),
					(value, it) -> Arrays.binarySearch(value, it) < 0);
		case IN:
			return new Criteria<>(accessor.nextAsArray(parameters),
					(value, it) -> Arrays.binarySearch(value, it) >= 0);
		case STARTING_WITH:
			return new Criteria<>(accessor.nextString(parameters),
					(value, it) -> it.startsWith(value));
		case ENDING_WITH:
			return new Criteria<>(accessor.nextString(parameters),
					(value, it) -> it.endsWith(value));
		case CONTAINING:
			return new Criteria<>(accessor.nextString(parameters),
					(value, it) -> it.contains(value));
		case NOT_CONTAINING:
			return new Criteria<>(accessor.nextString(parameters),
					(value, it) -> !it.contains(value));
		case REGEX:
			return Pattern.compile((String) parameters.next(),
					isIgnoreCase(part) ? Pattern.CASE_INSENSITIVE : 0).asPredicate();
		case TRUE:
			return it -> it.equalsIgnoreCase("true");
		case FALSE:
			return it -> it.equalsIgnoreCase("false");
		case SIMPLE_PROPERTY:
			return new Criteria<>(accessor.nextString(parameters),
					(value, it) -> it.equals(value));
		case NEGATING_SIMPLE_PROPERTY:
			return new Criteria<>(accessor.nextString(parameters),
					(value, it) -> !it.equals(value));
		default:
			throw new IllegalArgumentException("Unsupported keyword!");
		}
	}

	@Override
	protected VaultQuery or(VaultQuery vaultQuery, VaultQuery other) {
		return vaultQuery.or(other);
	}

	@Override
	protected KeyValueQuery<VaultQuery> complete(VaultQuery vaultQuery, Sort sort) {

		KeyValueQuery<VaultQuery> query = new KeyValueQuery<>(vaultQuery);

		if (sort != null) {
			query.orderBy(sort);
		}

		return query;
	}

	private static VariableAccessor getVariableAccessor(Part part) {
		return isIgnoreCase(part) ? VariableAccessor.Lowercase : VariableAccessor.AsIs;
	}

	private static boolean isIgnoreCase(Part part) {
		return part.shouldIgnoreCase() != IgnoreCaseType.NEVER;
	}

	@Value
	static class Criteria<T> implements Predicate<String> {

		private T value;
		private BiPredicate<T, String> predicate;

		@Override
		public boolean test(String s) {
			return predicate.test(value, s);
		}
	}

	enum VariableAccessor {

		AsIs {

			@Override
			String nextString(Iterator<Object> parameters) {
				return parameters.next().toString();
			}

			@Override
			String[] nextAsArray(Iterator<Object> iterator) {

				Object next = iterator.next();

				if (next instanceof Collection) {
					return ((Collection<?>) next).toArray(new String[0]);
				}
				else if (next != null && next.getClass().isArray()) {
					return (String[]) next;
				}

				return new String[] { (String) next };
			}

			@Override
			String toString(String value) {
				return value;
			}
		},

		Lowercase {

			@Override
			String nextString(Iterator<Object> parameters) {
				return AsIs.nextString(parameters).toLowerCase();
			}

			@Override
			String[] nextAsArray(Iterator<Object> iterator) {

				String[] original = AsIs.nextAsArray(iterator);
				String[] lowercase = new String[original.length];

				for (int i = 0; i < original.length; i++) {
					lowercase[i] = original[i].toLowerCase();
				}

				return lowercase;
			}

			@Override
			String toString(String value) {
				return value.toLowerCase();
			}
		};

		abstract String[] nextAsArray(Iterator<Object> iterator);

		abstract String nextString(Iterator<Object> iterator);

		abstract String toString(String value);

	}
}
