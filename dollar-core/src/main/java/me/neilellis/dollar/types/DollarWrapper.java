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

import com.google.common.collect.ImmutableList;
import me.neilellis.dollar.*;
import me.neilellis.dollar.collections.ImmutableMap;
import me.neilellis.dollar.json.JsonObject;
import me.neilellis.dollar.monitor.DollarMonitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * The DollarWrapper class exists to provide basic AOP style constructs without having actual AOP magic.
 *
 * Currently the DollarWrapper class provides monitoring and state tracing functions.
 *
 * @author <a href="http://uk.linkedin.com/in/neilellis">Neil Ellis</a>
 */
public class DollarWrapper implements var {

    private DollarMonitor monitor;
    private StateTracer tracer;
    private ErrorLogger errorLogger;
    private var value;

    DollarWrapper(var value, DollarMonitor monitor, StateTracer tracer, ErrorLogger errorLogger) {
//        tracer.trace(DollarNull.INSTANCE,value, StateTracer.Operations.CREATE);
        this.value = value;
        this.monitor = monitor;
        this.tracer = tracer;
        this.errorLogger = errorLogger;
    }

    @NotNull
    private static String sanitize(@NotNull String location) {
        return location.replaceAll("[^\\w.]+", "_");

    }

    @NotNull
    @Override
    public var $(@NotNull String key, long l) {
        return tracer.trace(this, getValue().$(key, l), StateTracer.Operations.SET, key, l);
    }

    @NotNull
    @Override
    public var $(@NotNull String key, double value) {
        return tracer.trace(this, getValue().$(key, value), StateTracer.Operations.SET, key, value);
    }

    @Override
    public var $(@NotNull String key, Pipeable value) {
        return tracer.trace(this, getValue().$(key, value), StateTracer.Operations.SET, key, value);
    }

    @NotNull
    @Override
    public var $append(Object value) {
        return getValue().$append(value);
    }

    @NotNull
    @Override
    public Stream<var> $children() {
        return getValue().$children();
    }

    @Override
    public var $default(Object o) {
        return getValue().$default(o);
    }

    @NotNull
    @Override
    public Stream $children(@NotNull String key) {
        return getValue().$children(key);
    }

    @Override
    public var $choose(var map) {
        return tracer.trace(this, getValue().$choose(map), StateTracer.Operations.CHOOSE);
    }

    @NotNull
    @Override
    public var $copy() {
        return getValue().$copy();
    }

    @Override
    public boolean $has(@NotNull String key) {
        return getValue().$has(key);
    }

    @NotNull
    @Override
    public ImmutableMap<String, var> $map() {
        return getValue().$map();
    }

    @Override
    public boolean $match(@NotNull String key, String value) {
        return getValue().$match(key, value);
    }

    @NotNull
    @Override
    public String S(@NotNull String key) {
        return getValue().S(key);
    }

    @NotNull
    @Override
    public String $mimeType() {
        return getValue().$mimeType();
    }

    @Override
    public var $post(String url) {
        return tracer.trace(this,
                monitor.run("$post",
                        "dollar.post",
                        "Posting to " + url,
                        () -> getValue().$post(url)),
                StateTracer.Operations.EVAL,
                url);
    }

    @NotNull
    @Override
    public var $rm(@NotNull String key) {
        return tracer.trace(this, getValue().$rm(key), StateTracer.Operations.REMOVE_BY_KEY, key);
    }

    @NotNull
    @Override
    public var $set(@NotNull String key, Object value) {
        return tracer.trace(this, getValue().$set(key, value), StateTracer.Operations.SET, key, value);
    }

    @NotNull
    @Override
    public var $(@NotNull String key, Object value) {
        return tracer.trace(this, getValue().$(key, value), StateTracer.Operations.SET, key, value);
    }

    @NotNull
    @Override
    public Stream<var> $stream() {
        return getValue().$stream();
    }

    @NotNull
    @Override
    public var $void(@NotNull Callable<var> handler) {
        return getValue().$void(handler);
    }

    @NotNull
    @Override
    public var get(@NotNull Object key) {
        return getValue().get(key);
    }

    @NotNull
    @Override
    public var $(@NotNull String key) {
        return getValue().$(key);
    }

    @NotNull
    @Override
    public <R> R $() {
        return getValue().$();
    }

    @Override
    public Stream<String> keyStream() {
        return getValue().keyStream();
    }

    @Override
    public Stream<Map.Entry<String, var>> kvStream() {
        return getValue().kvStream();
    }

    @Override
    public <R> R val() {
        return getValue().val();
    }

    @Override
    public void err() {
        getValue().err();
    }

    @Override
    public void $out() {
        getValue().$out();
    }

    @Override
    public var $(Pipeable lambda) {
        return getValue().$(lambda);
    }

    var getValue() {
        return value;
    }

    @Override
    public var $dec(String key, long amount) {
        return tracer.trace(this, getValue().$dec(key, amount), StateTracer.Operations.DEC, key, amount);
    }

    @Override
    public var $dec(long amount) {
        return tracer.trace(this, getValue().$dec(amount), StateTracer.Operations.DEC, amount);
    }

    @Override
    public var $inc(String key, long amount) {
        return tracer.trace(this, getValue().$inc(key, amount), StateTracer.Operations.INC, key, amount);
    }

    @Override
    public var $inc(long amount) {
        return tracer.trace(this, getValue().$inc(amount), StateTracer.Operations.INC, amount);
    }

    @NotNull
    @Override
    public var $error(@NotNull String errorMessage) {
        assert errorMessage != null;
        errorLogger.log(errorMessage);
        return getValue().$error(errorMessage);
    }

    @NotNull
    @Override
    public var $error(@NotNull Throwable error) {
        assert error != null;
        errorLogger.log(error);
        return getValue().$error(error);
    }

    @NotNull
    @Override
    public var $error() {
        errorLogger.log();
        return getValue().$error();
    }

    @NotNull
    @Override
    public var $errors() {
        return getValue().$errors();
    }

    @NotNull
    @Override
    public var $fail(@NotNull Consumer<List<Throwable>> handler) {
        return getValue().$fail(handler);
    }

    @NotNull
    @Override
    public var $invalid(@NotNull String errorMessage) {
        errorLogger.log();
        return getValue().$invalid(errorMessage);
    }

    @NotNull
    @Override
    public var $error(@NotNull String errorMessage, @NotNull ErrorType type) {
        errorLogger.log(errorMessage, type);
        return getValue().$error(errorMessage, type);
    }

    @NotNull
    @Override
    public ImmutableList<Throwable> errors() {
        return getValue().errors();
    }

    @NotNull
    @Override
    public var $pipe(@NotNull String label, @NotNull String js) {
        return tracer.trace(this,
                monitor.run("$pipe",
                        "dollar.pipe.js." + sanitize(label),
                        "Evaluating: " + js,
                        () -> getValue().$pipe(label, js)),
                StateTracer.Operations.EVAL,
                label,
                js);
    }

    @Override
    public boolean hasErrors() {
        return getValue().hasErrors();
    }

    @Override
    public var clearErrors() {
        return tracer.trace(this, getValue().clearErrors(), StateTracer.Operations.CLEAR_ERRORS);
    }

    @NotNull
    @Override
    public List<String> errorTexts() {
        return getValue().errorTexts();
    }

    @NotNull
    @Override
    public var $load(@NotNull String location) {
        return tracer.trace(DollarVoid.INSTANCE,
                monitor.run("save",
                        "dollar.persist.temp.load." + sanitize(location),
                        "Loading value at " + location,
                        () -> getValue().$load(location)),
                StateTracer.Operations.LOAD,
                location);
    }

    @NotNull
    @Override
    public var $pipe(@NotNull String label, @NotNull Pipeable pipe) {
        return tracer.trace(this,
                monitor.run("$pipe",
                        "dollar.pipe.pipeable." + sanitize(label), "",
                        () -> getValue().$pipe(label, pipe)),
                StateTracer.Operations.EVAL, label,
                pipe.getClass().getName());
    }

    @NotNull
    @Override
    public var $pop(@NotNull String location, int timeoutInMillis) {
        return tracer.trace(DollarVoid.INSTANCE,
                monitor.run("pop",
                        "dollar.persist.temp.pop." + sanitize(location),
                        "Popping value from " + location,
                        () -> getValue().$pop(location, timeoutInMillis)),
                StateTracer.Operations.POP,
                location);
    }

    @NotNull
    @Override
    public var $pub(@NotNull String... locations) {
        return tracer.trace(this,
                monitor.run("pub",
                        "dollar.message.pub." + sanitize(Arrays.toString(locations)),
                        "Publishing value to " + Arrays.toString(locations),
                        () -> getValue().$pub(
                                locations)),
                StateTracer.Operations.PUBLISH,
                locations);
    }

    @NotNull
    @Override
    public var $push(@NotNull String location) {
        return tracer.trace(this,
                monitor.run("push",
                        "dollar.persist.temp.push." + sanitize(location),
                        "Pushing value to " + location,
                        () -> getValue().$push(location)),
                StateTracer.Operations.PUSH,
                location);
    }

    @Override
    public var $read(File file) {
        return getValue().$read(file);
    }

    @NotNull
    @Override
    public var $pipe(@NotNull String js) {
        return $pipe("anon", js);
    }

    @Override
    public var $read(InputStream in) {
        return getValue().$read(in);
    }

    @NotNull
    @Override
    public var $save(@NotNull String location) {
        tracer.trace(this, this, StateTracer.Operations.SAVE);
        return monitor.run("save",
                "dollar.persist.temp.save." + sanitize(location),
                "Saving value at " + location,
                () -> getValue().$save(location));
    }

    @NotNull
    @Override
    public var $save(@NotNull String location, int expiryInMilliseconds) {
        tracer.trace(this, this, StateTracer.Operations.SAVE);
        return monitor.run("save",
                "dollar.persist.temp.save." + sanitize(location),
                "Saving value at " + location + " with expiry " + expiryInMilliseconds,
                () -> getValue().$save(
                        location,
                        expiryInMilliseconds));
    }

    @Override
    public var $write(File file) {
        return getValue().$write(file);
    }

    @NotNull
    @Override
    public var $pipe(@NotNull Class<? extends Pipeable> clazz) {
        return tracer.trace(this,
                monitor.run("pipe",
                        "dollar.run.pipe." + sanitize(clazz),
                        "Piping to " + clazz.getName(),
                        () -> getValue().$pipe(clazz)),
                StateTracer.Operations.PIPE,
                clazz.getName());
    }

    @Override
    public var $write(OutputStream out) {
        return getValue().$write(out);
    }

    @Override
    public var $send(String uri) {
        return tracer.trace(DollarVoid.INSTANCE,
                monitor.run("send",
                        "dollar.integration.send." + sanitize(uri),
                        "Sending to " + uri,
                        () -> getValue().$send(uri)),
                StateTracer.Operations.SEND,
                uri);
    }

    @Override
    public void $listen(String uri, Consumer<var> handler) {
        getValue().$listen(uri, handler);
    }

    @Override
    public void $publish(String uri) {
        monitor.run("dispatch",
                "dollar.integration.dispatch." + sanitize(uri),
                "Writing to " + uri,
                () -> getValue().$publish(uri));
    }

    @Override
    public void $dispatch(String uri) {
        monitor.run("dispatch",
                "dollar.integration.dispatch." + sanitize(uri),
                "Writing to " + uri,
                () -> getValue().$dispatch(uri));
    }

    @Override
    public String S() {
        return getValue().S();
    }

    @NotNull
    @Override
    public ImmutableList<var> toList() {
        return getValue().toList();
    }

    @Override
    public boolean isVoid() {
        return getValue().isVoid();
    }

    @Nullable
    @Override
    public Double D() {
        return getValue().D();
    }

    @Override
    public Integer I() {
        return getValue().I();
    }

    @Nullable
    @Override
    public Long L() {
        return getValue().L();
    }

    @Override
    public Integer I(@NotNull String key) {
        return getValue().I(key);
    }

    @Override
    public boolean isDecimal() {
        return getValue().isDecimal();
    }

    @Override
    public boolean isInteger() {
        return getValue().isInteger();
    }

    @Override
    public boolean isList() {
        return getValue().isList();
    }

    @Override
    public boolean isMap() {
        return getValue().isMap();
    }

    @Override
    public boolean isNumber() {
        return getValue().isNumber();
    }

    @Override
    public boolean isSingleValue() {
        return getValue().isSingleValue();
    }

    @Override
    public boolean isString() {
        return getValue().isString();
    }

    @Override
    public boolean isLambda() {
        return getValue().isLambda();
    }

    @NotNull
    @Override
    public JsonObject json(@NotNull String key) {
        return getValue().json(key);
    }

    @Override
    public Number number(@NotNull String key) {
        return getValue().number(key);
    }

    @NotNull
    @Override
    public JSONObject orgjson() {
        return getValue().orgjson();
    }

    @NotNull
    @Override
    public JsonObject json() {
        return getValue().json();
    }

    @Override
    public ImmutableList<String> strings() {
        return getValue().strings();
    }

    @Override
    public Map<String, Object> toMap() {
        return getValue().toMap();
    }

    @NotNull
    @Override
    public Number N() {
        return getValue().N();
    }

    @NotNull
    @Override
    public var _unwrap() {
        return value;
    }

    @NotNull
    @Override
    public var copy(@NotNull ImmutableList<Throwable> errors) {
        return getValue().$copy();
    }

    @Override
    public var decode() {
        return getValue().decode();
    }

    @Override
    public int hashCode() {
        return getValue().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return getValue().equals(obj);
    }

    @NotNull
    @Override
    public String toString() {
        return getValue().toString();
    }

    @Override
    public int size() {
        return getValue().size();
    }

    @Override
    public boolean isEmpty() {
        return getValue().isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return getValue().containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return getValue().containsValue(value);
    }

    @Override
    public var put(String key, var value) {
        return getValue().put(key, value);
    }

    @Override
    public var remove(Object value) {
        return tracer.trace(this, getValue().remove(value), StateTracer.Operations.REMOVE_BY_VALUE, value);
    }

    @Override
    public var eval(@NotNull String label, DollarEval lambda) {
        return tracer.trace(this,
                monitor.run("$fun",
                        "dollar.pipe.java." + sanitize(label),
                        "Evaluating Lambda",
                        () -> getValue().eval(label, lambda)),
                StateTracer.Operations.EVAL,
                label,
                lambda);
    }

    @Override
    public void putAll(@NotNull Map<? extends String, ? extends var> m) {
        getValue().putAll(m);
    }

    @Override
    public void clear() {
        getValue().clear();
    }

    @NotNull
    @Override
    public Set<String> keySet() {
        return getValue().keySet();
    }

    @NotNull
    @Override
    public Collection<var> values() {
        return getValue().values();
    }

    @Override
    public var eval(DollarEval lambda) {
        return eval("anon", lambda);
    }

    @NotNull
    @Override
    public Set<Entry<String, var>> entrySet() {
        return getValue().entrySet();
    }

    @Override
    public var getOrDefault(Object key, var defaultValue) {
        return getValue().getOrDefault(key, defaultValue);
    }

    @Override
    public void forEach(@NotNull BiConsumer<? super String, ? super var> action) {
        getValue().forEach(action);
    }

    @Override
    public void replaceAll(@NotNull BiFunction<? super String, ? super var, ? extends var> function) {
        getValue().replaceAll(function);
    }

    @Override
    public var eval(@NotNull Class clazz) {
        return monitor.run("save",
                "dollar.run." + clazz.getName(),
                "Running class " + clazz,
                () -> getValue().eval(clazz));
    }

    @Override
    public var putIfAbsent(String key, var value) {
        return getValue().putIfAbsent(key, value);
    }

    @Override
    public boolean remove(Object key, Object value) {
        return getValue().remove(key, value);
    }

    @Override
    public boolean replace(String key, var oldValue, var newValue) {
        return getValue().replace(key, oldValue, newValue);
    }

    @Override
    public var replace(String key, var value) {
        return getValue().replace(key, value);
    }

    @Override
    public var computeIfAbsent(String key, @NotNull Function<? super String, ? extends var> mappingFunction) {
        return getValue().computeIfAbsent(key, mappingFunction);
    }

    @Override
    public var computeIfPresent(String key,
                                @NotNull BiFunction<? super String, ? super var, ? extends var> remappingFunction) {
        return getValue().computeIfPresent(key, remappingFunction);
    }

    @Override
    public var compute(String key, @NotNull BiFunction<? super String, ? super var, ? extends var> remappingFunction) {
        return getValue().compute(key, remappingFunction);
    }

    @Override
    public var merge(String key, @NotNull var value,
                     @NotNull BiFunction<? super var, ? super var, ? extends var> remappingFunction) {
        return getValue().merge(key, value, remappingFunction);
    }


    @NotNull
    private String sanitize(@NotNull Class clazz) {
        return clazz.getName().toLowerCase();
    }

}