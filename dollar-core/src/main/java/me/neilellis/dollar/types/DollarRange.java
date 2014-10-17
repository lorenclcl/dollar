/*
 * Copyright (c) 2014 Neil Ellis
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.neilellis.dollar.types;

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;
import me.neilellis.dollar.AbstractDollar;
import me.neilellis.dollar.DollarStatic;
import me.neilellis.dollar.var;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.vertx.java.core.json.JsonObject;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author <a href="http://uk.linkedin.com/in/neilellis">Neil Ellis</a>
 */
public class DollarRange extends AbstractDollar {

    private final Range<Long> range;

    public DollarRange(@NotNull List<Throwable> errors, long start, long finish) {
        super(errors);
        range = Range.closed(start, finish);
    }

    public DollarRange(@NotNull List<Throwable> errors, Range range) {
        super(errors);
        this.range = range;
    }

    @NotNull
    @Override
    public var $(@NotNull String age, long l) {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public var $append(Object value) {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Stream<var> $children() {
        return $list().stream();
    }

    @NotNull
    @Override
    public Stream $children(@NotNull String key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean $has(@NotNull String key) {
        return false;
    }

    @NotNull
    @Override
    public List<var> $list() {
        return ContiguousSet.create(range, DiscreteDomain.longs())
                            .stream()
                            .map(DollarStatic::$)
                            .collect(Collectors.toList());
    }

    @NotNull
    @Override
    public Map<String, var> $map() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public String string(@NotNull String key) {
        return range.toString();
    }

    @NotNull
    @Override
    public var $rm(@NotNull String value) {
        throw new UnsupportedOperationException();

    }

    @NotNull
    @Override
    public var $(@NotNull String key, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean $void() {
        return false;
    }

    @Override
    public Integer I() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Integer I(@NotNull String key) {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public var decode() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public var $(@NotNull String key) {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public JsonObject json() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public JsonObject json(@NotNull String key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Stream<String> keyStream() {
        throw new UnsupportedOperationException();

    }

    @Override
    public Number number(@NotNull String key) {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public JSONObject orgjson() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> strings() {
        return ContiguousSet.create(range, DiscreteDomain.longs())
                            .stream()
                            .map(Object::toString)
                            .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> toMap() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Range<Long> $() {
        return range;
    }

    @Override
    public int hashCode() {
        return range.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof var) {
            var unwrapped = ((var) obj)._unwrap();
            if (unwrapped instanceof DollarRange) {
                return range.equals(((DollarRange) unwrapped).range);
            }
            if (unwrapped instanceof DollarList) {
                return unwrapped.$list().equals($list());
            }
        }
        return false;

    }

    @Override
    public Stream<Map.Entry<String, var>> kvStream() {
        throw new UnsupportedOperationException();

    }

    @NotNull
    @Override
    public Stream<var> $stream() {
        return $list().stream();
    }

    @NotNull
    @Override
    public var $copy() {
        return DollarFactory.fromValue(errors(), range);
    }

    @Override
    public int size() {
        return (int) (range.upperEndpoint()-range.lowerEndpoint());
    }

    @Override
    public boolean containsValue(Object value) {
        if(value instanceof Number) {
            return range.contains(((Number) value).longValue());
        }
        return range.contains(Long.valueOf(value.toString()));
    }

    @NotNull
    @Override
    public var remove(Object value) {
        throw new UnsupportedOperationException();

    }
}
