package com.athaydes.rawhttp.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;
import static java.util.stream.Collectors.toMap;

/**
 * A Collection of HTTP headers.
 * <p>
 * Header names are case-insensitive, as per <a href="https://tools.ietf.org/html/rfc7230#section-3">Section 3</a>
 * of RFC-7230, so lookup methods return header values regardless of the case of the name.
 * <p>
 * The headers are also kept in the order that they were added, except in cases where the same header is added multiple
 * times, in which case the new values are grouped together with the previous ones.
 *
 * @see HttpMessage
 */
public class RawHttpHeaders {

    private final Map<String, Header> headersByCapitalizedName;

    private static final Header NULL_HEADER = new Header("").freeze();

    private RawHttpHeaders(Map<String, Header> headersByCapitalizedName) {
        Map<String, Header> headers = new LinkedHashMap<>(headersByCapitalizedName);
        headers.entrySet().forEach(entry -> entry.setValue(entry.getValue().freeze()));
        this.headersByCapitalizedName = unmodifiableMap(headers);
    }

    /**
     * @param headerName case-insensitive header name
     * @return values for the header, or the empty list if this header is not present.
     */
    public List<String> get(String headerName) {
        return headersByCapitalizedName.getOrDefault(headerName.toUpperCase(), NULL_HEADER).values;
    }

    /**
     * @param headerName case-insensitive header name
     * @return the first value of the header, if any.
     */
    public Optional<String> getFirst(String headerName) {
        List<String> values = get(headerName);
        if (values.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(values.get(0));
        }
    }

    /**
     * @return the names of all headers (in the case and order that they were inserted). As headers may appear more
     * than once, this method may return duplicates.
     * @see #getUniqueHeaderNames()
     */
    public List<String> getHeaderNames() {
        List<String> result = new ArrayList<>(headersByCapitalizedName.size());
        forEach((name, v) -> result.add(name));
        return result;
    }

    /**
     * @return the unique names of all headers (names are upper-cased).
     */
    public Set<String> getUniqueHeaderNames() {
        return headersByCapitalizedName.keySet();
    }

    /**
     * Check if the given header name is present in this set of headers.
     *
     * @param headerName case-insensitive header name
     * @return true if the header is present, false otherwise.
     */
    public boolean contains(String headerName) {
        return getUniqueHeaderNames().contains(headerName.toUpperCase());
    }

    /**
     * @return a {@link Map} representation of this set of headers.
     */
    public Map<String, List<String>> asMap() {
        return headersByCapitalizedName.entrySet().stream()
                .collect(toMap(Map.Entry::getKey, e -> e.getValue().values));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RawHttpHeaders that = (RawHttpHeaders) o;

        boolean sameKeys = headersByCapitalizedName.keySet().equals(that.headersByCapitalizedName.keySet());

        if (!sameKeys) {
            return false;
        }

        // check all values
        for (Map.Entry<String, Header> entry : headersByCapitalizedName.entrySet()) {
            if (!that.headersByCapitalizedName.get(entry.getKey()).values.equals(entry.getValue().values)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Iterate over all entries in this set of headers.
     * <p>
     * The consumer is called for each value of a header. If a header has multiple values, each value is consumed
     * once, so the header name may be consumed more than once.
     *
     * @param consumer accepts the header name and value
     */
    public void forEach(BiConsumer<String, String> consumer) {
        headersByCapitalizedName.forEach((k, v) ->
                v.values.forEach(value ->
                        consumer.accept(v.originalHeaderName, value)));
    }

    @Override
    public int hashCode() {
        return headersByCapitalizedName.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        forEach((name, value) ->
                builder.append(name).append(": ").append(value).append("\r\n"));
        return builder.append("\r\n").toString();
    }

    /**
     * Builder of {@link RawHttpHeaders}.
     */
    public static class Builder {

        /**
         * Create a new builder containing all values of the given headers.
         *
         * @param headers to start from
         * @return new builder
         */
        public static Builder newBuilder(RawHttpHeaders headers) {
            Builder builder = new Builder();
            headers.headersByCapitalizedName.forEach((k, v) ->
                    builder.headersByCapitalizedName.put(k, v.unfreeze()));
            return builder;
        }

        /**
         * Create a new, empty builder.
         *
         * @return new builder
         */
        public static Builder newBuilder() {
            return new Builder();
        }

        /**
         * Create a new, empty, immutable instance of {@link RawHttpHeaders}.
         *
         * @return empty headers
         */
        public static RawHttpHeaders emptyRawHttpHeaders() {
            return new RawHttpHeaders(emptyMap());
        }

        private Builder() {
            // hide
        }

        private final Map<String, Header> headersByCapitalizedName = new LinkedHashMap<>();

        /**
         * Include the given header in this builder.
         *
         * @param headerName header name
         * @param value      header value
         * @return this
         */
        public Builder with(String headerName, String value) {
            headersByCapitalizedName.computeIfAbsent(headerName.toUpperCase(),
                    (ignore) -> new Header(headerName)).values.add(value);
            return this;
        }

        /**
         * Overwrite the given header with the single value provided.
         *
         * @param headerName header name
         * @param value      single value for the header
         * @return this
         */
        public Builder overwrite(String headerName, String value) {
            String key = headerName.toUpperCase();
            headersByCapitalizedName.put(key, new Header(headerName, value));
            return this;
        }

        /**
         * Remove the header with the given name (including all values).
         *
         * @param headerName case-insensitive header name
         */
        public void remove(String headerName) {
            headersByCapitalizedName.remove(headerName.toUpperCase());
        }

        /**
         * Merge this builder's headers with the ones provided.
         *
         * @param headers to merge with this builder
         * @return this
         */
        public Builder merge(RawHttpHeaders headers) {
            headers.forEach(this::with);
            return this;
        }

        /**
         * @return new instance of {@link RawHttpHeaders} with all headers added to this builder.
         */
        public RawHttpHeaders build() {
            return new RawHttpHeaders(headersByCapitalizedName);
        }

        /**
         * @return the names of all headers added to this builder.
         */
        public List<String> getHeaderNames() {
            return unmodifiableList(headersByCapitalizedName.values().stream()
                    .map(h -> h.originalHeaderName).collect(Collectors.toList()));
        }

    }

    private static final class Header {
        private final String originalHeaderName;
        private final List<String> values;

        Header(String originalHeaderName) {
            this.originalHeaderName = originalHeaderName;
            this.values = new ArrayList<>(3);
        }

        Header(String originalHeaderName, String value) {
            this(originalHeaderName);
            this.values.add(value);
        }

        Header(String originalHeaderName, List<String> values) {
            this.originalHeaderName = originalHeaderName;
            this.values = values;
        }

        Header freeze() {
            return new Header(originalHeaderName, unmodifiableList(values));
        }

        Header unfreeze() {
            return new Header(originalHeaderName, new ArrayList<>(values));
        }
    }

}
