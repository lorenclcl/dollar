/*
 *    Copyright (c) 2014-2017 Neil Ellis
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package dollar.internal.runtime.script.parser.scope;

import com.google.common.base.Objects;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import dollar.api.ClassName;
import dollar.api.DollarClass;
import dollar.api.DollarException;
import dollar.api.Pipeable;
import dollar.api.Scope;
import dollar.api.SubType;
import dollar.api.Value;
import dollar.api.VarFlags;
import dollar.api.VarKey;
import dollar.api.Variable;
import dollar.api.exceptions.LambdaRecursionException;
import dollar.api.script.Source;
import dollar.api.types.NotificationType;
import dollar.internal.runtime.script.ErrorHandlerFactory;
import dollar.internal.runtime.script.api.DollarUtil;
import dollar.internal.runtime.script.api.exceptions.DollarAssertionException;
import dollar.internal.runtime.script.api.exceptions.DollarExitError;
import dollar.internal.runtime.script.api.exceptions.DollarScriptException;
import dollar.internal.runtime.script.api.exceptions.PureFunctionException;
import dollar.internal.runtime.script.api.exceptions.VariableNotFoundException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jparsec.Parser;
import org.jparsec.error.ParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static dollar.api.DollarException.unravel;
import static dollar.api.DollarStatic.*;
import static dollar.api.types.meta.MetaConstants.IMPURE;
import static dollar.internal.runtime.script.DollarUtilFactory.util;

public class ScriptScope implements Scope {

    @NotNull
    private static final AtomicInteger counter = new AtomicInteger();
    @NotNull
    private static final Logger log = LoggerFactory.getLogger(ScriptScope.class);
    @NotNull
    final String id;
    private final boolean classScope;
    @NotNull
    private final ConcurrentHashMap<ClassName, DollarClass> classes = new ConcurrentHashMap<>();
    @NotNull
    private final List<Value> errorHandlers = new CopyOnWriteArrayList<>();
    @NotNull
    private final Multimap<VarKey, Listener> listeners = ArrayListMultimap.create();
    private final boolean root;
    @NotNull
    private final UUID uuid = UUID.randomUUID();
    @NotNull
    private final Map<VarKey, Variable> variables = Collections.<VarKey, Variable>synchronizedMap(new LinkedHashMap());
    @Nullable
    Scope parent;
    @Nullable
    String source;
    private boolean destroyed;
    private boolean parameterScope;
    @Nullable
    private Parser<Value> parser;

    public ScriptScope(@NotNull String name, boolean root, boolean classScope) {
        this.root = root;
        this.classScope = classScope;
        parent = null;
        source = null;
        id = name + ":" + counter.incrementAndGet();
    }

    public ScriptScope(@NotNull String source, @NotNull String name, boolean root, boolean classScope) {
        this.root = root;
        this.classScope = classScope;
        parent = null;
        this.source = source;
        id = name + ":" + counter.incrementAndGet();
    }


    public ScriptScope(@NotNull Scope parent, @NotNull String name, boolean root, boolean classScope) {
        this.parent = parent;
        this.root = root;
        source = parent.source();
        id = name + ":" + counter.incrementAndGet();
        this.classScope = classScope;
        checkPure(parent);

    }

    public ScriptScope(@NotNull Scope parent,
                       @Nullable String source,
                       @NotNull String name,
                       boolean root, boolean classScope) {
        this.parent = parent;
        this.root = root;
        this.classScope = classScope;
        if (source == null) {
            throw new NullPointerException("No source for " + parent);
        } else {
            this.source = source;

        }

        id = name + ":" + counter.incrementAndGet();
        checkPure(parent);
    }


    private ScriptScope(@NotNull Scope parent,
                        @NotNull String id,
                        boolean parameterScope,
                        @NotNull Map<VarKey, Variable> variables,
                        @NotNull List<Value> errorHandlers,
                        @NotNull Multimap<VarKey, Listener> listeners,
                        @NotNull String source,
                        @NotNull Parser<Value> parser, boolean root, boolean classScope) {
        this.parent = parent;
        this.id = id;
        this.parameterScope = parameterScope;
        this.classScope = classScope;
        this.variables.putAll(variables);
        this.errorHandlers.addAll(errorHandlers);
        for (Map.Entry<VarKey, Collection<Listener>> entry : listeners.asMap().entrySet()) {
            this.listeners.putAll(entry.getKey(), entry.getValue());
        }
        this.source = source;
        this.parser = parser;
        this.root = root;
        checkPure(parent);

    }

    @NotNull
    @Override
    public Value addErrorHandler(@NotNull Value handler) {
        checkDestroyed();

        errorHandlers.add(handler);
        return $void();

    }

    @Override
    public void addListener(@NotNull VarKey key, @NotNull Scope.Listener listener) {
        listeners.put(key, listener);
    }

    @Override
    public void clear() {
        checkDestroyed();

        if (getConfig().debugScope()) {
            log.info("Clearing scope {}", this);
        }
        variables.clear();
        listeners.clear();
    }

    @Nullable
    @Override
    public Value constraintOf(@NotNull VarKey key) {
        checkDestroyed();


        Scope scope = scopeForKey(key);
        if (scope == null) {
            scope = this;
        }
        if (getConfig().debugScope()) {
            log.info("Getting constraint for {} in {}", key, scope);
        }
        if (scope.variables().containsKey(key) && (scope.variables().get(
                key).getConstraint() != null)) {
            return scope.variables().get(key).getConstraint();
        }
        return null;
    }

    @NotNull
    @Override
    public Scope copy() {
        checkDestroyed();

        return new ScriptScope(parent, "*" + id.split(":")[0] + ":" + counter.incrementAndGet(),
                               parameterScope,
                               variables, errorHandlers, listeners, source, parser, root, classScope);
    }

    @Override
    public void destroy() {
        clear();
        destroyed = true;
    }

    @NotNull
    @Override
    public DollarClass dollarClassByName(@NotNull ClassName name) {
        DollarClass clazz = classes.get(name);
        if (clazz != null) {
            return clazz;
        }
        if (parent != null) {
            return parent.dollarClassByName(name);
        }
        throw new DollarScriptException("No class found with name " + name + " in scope " + this);
    }

    @Override
    public String file() {
        if (parent != null) {
            return parent.file();
        } else {
            return null;
        }
    }

    @NotNull
    @Override
    public Value get(@NotNull VarKey key, boolean mustFind) {
        checkDestroyed();
        if (key.isNumeric()) {
            throw new DollarAssertionException("Cannot get numerical keys, use parameter");
        }
        if (getConfig().debugScope()) {
            log.info("Looking up {} in {}", key, this);
        }
        Scope scope = scopeForKey(key);
        if (scope == null) {
            scope = this;
        } else {
            if (getConfig().debugScope()) {
                log.info("{} in {}", util().highlight("FOUND " + key, DollarUtil.ANSI_CYAN), scope);
            }
        }
        Variable result = scope.variables().get(key);

        if (mustFind) {
            if (result == null) {
                throw new VariableNotFoundException(key, this);
            } else {
                return result.getValue();
            }
        } else {
            return (result != null) ? result.getValue() : $void();
        }
    }

    @NotNull
    @Override
    public Value get(@NotNull VarKey key) {
        return get(key, false);
    }

    @NotNull
    @Override
    public Value handleError(@NotNull Exception t) throws RuntimeException {
        Exception unravelled = unravel(t);
        if (!(unravelled instanceof DollarException)) {
            if (unravelled.getCause() instanceof DollarException) {
                return handleError((Exception) unravelled.getCause());
            }
        }
        if (unravelled instanceof DollarAssertionException) {
            throw (DollarAssertionException) unravelled;
        }
        if (errorHandlers.isEmpty()) {
            log.info("No error handlers in {} so passing up.", this);
            if (parent == null) {
                log.info("No parent so handling error in {}", this);

                log.error(unravelled.getMessage(), unravelled);
                if (getConfig().failFast()) {
                    log.info("Fail-fast option is set");

                    String filename = file();
                    if (filename != null) {
                        ErrorHandlerFactory.instance().handleTopLevel(unravelled, id, new File(filename));
                    } else {
                        ErrorHandlerFactory.instance().handleTopLevel(unravelled, id, null);

                    }

//                    System.exit(1);
                    throw new DollarExitError(unravelled);
                } else {
                    log.info("Fail-fast option is not set");
                    if (unravelled instanceof ParserException) {
                        if (unravelled.getCause() instanceof DollarException) {
                            return handleError((Exception) unravelled.getCause());
                        } else {
                            throw (ParserException) unravelled;
                        }
                    }

                    if (unravelled instanceof DollarException) {
                        throw (DollarException) unravelled;
                    }
                    throw new DollarScriptException(unravelled);
                }
            } else {
                return parent.handleError(unravelled);
            }
        } else {
            return util().inSubScope(true, pure(), "error-scope", newScope -> {
                log.info("Error handler in {}", this);
                parameter(VarKey.TYPE, $(unravelled.getClass().getName()));
                parameter(VarKey.MSG, $(unravelled.getMessage()));
                try {
                    for (Value handler : errorHandlers) {
                        handler.$fixDeep(false);
                    }
                } finally {
                    parameter(VarKey.TYPE, $void());
                    parameter(VarKey.MSG, $void());
                }
                return $void();
            }).orElseThrow(() -> new AssertionError("Optional should not be null here"));

        }

    }

    @NotNull
    @Override
    public Value handleError(@NotNull Exception t, @NotNull Value context) throws RuntimeException {
        return handleError(new DollarScriptException(t, context));
    }

    @NotNull
    @Override
    public Value handleError(@NotNull Exception t, @NotNull Source source) throws RuntimeException {
        if (t instanceof LambdaRecursionException) {
            return handleError(new DollarException(
                                                          "Excessive recursion detected, this is usually due to a recursive definition of lazily defined " +
                                                                  "expressions. The simplest way to solve this is to use the 'fix' operator or the '=' operator to " +
                                                                  "reduce the amount of lazy evaluation. The error occured at " +
                                                                  source));
        }
        Exception unravel = unravel(t);
        if (unravel instanceof DollarException) {
            ((DollarException) unravel).addSource(source);
            return handleError(unravel);
        } else {
            return handleError(new DollarScriptException(unravel, source));
        }
    }

    @Override
    public boolean has(@NotNull VarKey key) {
        checkDestroyed();

        Scope scope = scopeForKey(key);
        if (scope == null) {
            scope = this;
        }
        if (getConfig().debugScope()) {
            log.info("Checking for {} in {}", key, scope);
        }

        Variable val = scope.variables().get(key);
        return val != null;

    }

    @Override
    public boolean hasParameter(@NotNull VarKey key) {
        Variable variable = variable(key);
        return variable != null && variable.isParameter();
    }

    @Override
    public boolean hasParent(Scope scope) {
        checkDestroyed();

        if (parent() == null) {
            return false;
        }
        return parent().equals(scope) || parent().hasParent(scope);
    }

    @Override
    public boolean isClassScope() {
        return classScope;
    }

    @Override
    public boolean isRoot() {
        return root || (parent == null);
    }

    @Override
    public void listen(@NotNull VarKey key, @NotNull String id, @NotNull Value listener) {
        checkDestroyed();

        listen(key, id, in -> {
                   if (getConfig().debugEvents()) {
                       log.info("Notifying {} in scope {}", listener.source().getSourceMessage(), this);
                   }
            listener.$notify(NotificationType.UNARY_VALUE_CHANGE, in[1]);
                   return $void();
               }
        );

    }

    @Override
    public void listen(@NotNull VarKey key, @NotNull String id, @NotNull Pipeable pipe) {
        if (getConfig().debugEvents()) {
            log.info("listen called on scope {} with id {}", this, id);
        }

        checkDestroyed();


        Listener listener = new Listener() {
            @NotNull
            @Override
            public String getId() {
                return id;
            }

            @NotNull
            @Override
            public Value pipe(Value... vars) throws Exception {
                if (getConfig().debugEvents()) {
                    log.info("Listener triggered on scope {} for key {} and value {}", ScriptScope.this, vars[0], vars[1]
                                                                                                                          .dynamic() ? vars[1].source() : vars[1]);
                }

                return pipe.pipe(vars);
            }
        };

        if (key.isNumeric()) {
            if (getConfig().debugEvents()) {
                log.info("Cannot listen to positional parameter ${} in {}", key, this);
            }
            return;
        }
        Scope scopeForKey = scopeForKey(key);
        if (scopeForKey == null) {
            if (getConfig().debugEvents()) {
                log.info("Key {} not found in {}", key, this);
            }
            throw new DollarException("Cannot find " + key + " in scope " + this);
        }

        if (getConfig().debugEvents()) {
            log.info("Listening for {} in {}", key, scopeForKey);
        }


        if (listeners.get(key).stream().filter(i -> i.getId().equals(id)).count() == 0) {
            scopeForKey.addListener(key, listener);
        } else {
            if (getConfig().debugEvents()) {
                log.info("Listener {} for {} in {} already exists", id, key, scopeForKey);
            }
        }
    }

    @Nullable
    @Override
    public Value notify(@NotNull VarKey key) {
        checkDestroyed();
        Value value = get(key);
        notifyScope((key), value);
        return value;
    }

    @Override
    public void notifyScope(@NotNull VarKey key, @NotNull Value value) {
        checkDestroyed();


        if (value == null) {
            throw new NullPointerException();
        }
        if (getConfig().debugEvents()) {

            log.info("Scope {} notified for {}", this, key);
        }
        if (listeners.containsKey(key)) {
            if (getConfig().debugEvents()) {
                log.debug("Scope {} notified for {} with {} listeners", this, key, listeners.get(key).size());
            }
            new ArrayList<>(listeners.get(key)).forEach(
                    listener -> {
                        try {
                            if (getConfig().debugEvents()) {
                                log.debug("Listener {} notified in scope {} for key {}", listener.getId(), this, key);
                            }
                            listener.pipe($(key), value);
                        } catch (Exception e) {
                            try {
                                handleError(e);
                            } catch (Exception e1) {
                                e1.printStackTrace();
                            }
                        }
                    });
        } else {
            if (getConfig().debugEvents()) {
                log.info("Scope {} notified for {} NO LISTENERS", this, key);
            }
        }
    }

    @NotNull
    @Override
    public Variable parameter(@NotNull VarKey key) {
        checkDestroyed();
        Scope scope = scopeForKey(key);
        if (scope == null) {
            throw new VariableNotFoundException(key, this);
        }
        Variable variable = scope.variable(key);
        if (!variable.isParameter()) {
            throw new DollarScriptException(
                                                   "Attempted to access a non-parameter variable " + key + "as a parameter in scope " + this);
        }
        return variable;
    }

    @NotNull
    @Override
    public Variable parameter(@NotNull VarKey key, @NotNull Value value) {
        checkDestroyed();

        if (getConfig().debugScope()) {
            log.info("Setting parameter {} in {}", key, this);
        }
        if (key.isNumeric() && variables.containsKey(key)) {
            throw new DollarScriptException("Cannot change the value of positional variable $" + key + " in scope " + this);
        }
        parameterScope = true;
        Variable variable = new Variable(value, value.meta(IMPURE) == null, key.isNumeric(), true);
        variables.put(key, variable);
        notifyScope(key, value);
        return variable;
    }

    @NotNull
    @Override
    public List<Value> parametersAsVars() {
        return variables.entrySet().stream()
                       .filter(i -> i.getValue().isParameter())
                       .filter(i -> i.getKey().isNumeric())
                       .sorted(Comparator.comparing(i -> Integer.parseInt(i.getKey().asString())))
                       .map(Map.Entry::getValue)
                       .map(Variable::getValue)
                       .collect(Collectors.toList());
    }

    @Override
    public Scope parent() {
        return parent;
    }

    @Override
    public boolean pure() {
        return false;
    }

    @Override
    public void registerClass(@NotNull ClassName name, @NotNull DollarClass dollarClass) {
        log.info("Registering class {} in {}", name, this);
        classes.put(name, dollarClass);
    }

    @Nullable
    @Override
    public Scope scopeForKey(@NotNull VarKey key) {
        checkDestroyed();


        if (variables.containsKey(key)) {
            return this;
        }
        if (parent != null) {
            return parent.scopeForKey(key);
        } else {
//            if (DollarStatic.getConfig().debugScope()) { log.info("Scope not found for " + key); }
            return null;
        }
    }

    @NotNull
    @Override
    public Variable set(@NotNull VarKey key,
                        @NotNull Value value,
                        @Nullable Value constraint, SubType constraintSource, @NotNull VarFlags varFlags) {

        if ((parent != null) && parent.isClassScope()) {
            return parent.set(key, value, constraint, constraintSource, varFlags);
        }
        checkDestroyed();


        if (key.isNumeric()) {
            throw new DollarAssertionException("Cannot set numerical keys, use parameter");
        }

        Scope scope = scopeForKey(key);
        if (pure() && (scope != null) && !java.util.Objects.equals(scope, this)) {
            throw new DollarScriptException("Cannot modify variables outside of a pure scope");
        }
        if (scope != null && scope != this) {
            return scope.set(key, value, constraint, constraintSource, varFlags);
        }

        if (pure()) {
            if (!varFlags.isPure()) {
                throw new DollarScriptException(
                                                       "Cannot have impure variables in a pure expression, variable was " + key + ", (" + this + ")",
                                                       value);
            }
            if (varFlags.isVolatile()) {
                throw new DollarScriptException("Cannot have volatile variables in a pure expression");
            }
            if (key.isNumeric()) {
                throw new AssertionError("Cannot set numerical keys, use parameter");
            }
        }

        if (variables.containsKey(key) && variables.get(key).isReadonly()) {
            throw new DollarScriptException("Cannot change the value of variable " + key + " it is readonly");
        }

        if (variables.containsKey(key)) {
            final Variable variable = variables.get(key);
            if (!variable.isVolatile() && (variable.getThread() != Thread.currentThread().getId())) {
                handleError(
                        new DollarScriptException("Concurrency Error: Cannot change the variable " +
                                                          key +
                                                          " in a different thread from that which is created in."));
            }
            if (variable.getConstraint() != null) {
                if (constraint != null) {
                    handleError(
                            new DollarScriptException(
                                                             "Cannot change the constraint on a variable, attempted to redeclare for " + key));
                }
            }

            if (getConfig().debugScope()) {
                log.info("Setting {} in {}", key, this);
            }
            variable.setValue(value);
            notifyScope(key, value);
            return variable;
        } else {
            if (getConfig().debugScope()) {
                log.info("Adding {} in {}", key, this);
            }
            Variable valueVariable = new Variable(value, varFlags, constraint, constraintSource, false);
            variables.put(key, valueVariable);
            notifyScope(key, value);
            return valueVariable;
        }
    }

    @Nullable
    @Override
    public String source() {
        return source;
    }

    @Nullable
    @Override
    public SubType subTypeOf(@NotNull VarKey key) {
        checkDestroyed();


        Scope scope = scopeForKey(key);
        if (scope == null) {
            scope = this;
        }
        if (getConfig().debugScope()) {
            log.info("Getting constraint for {} in {}", key, scope);
        }
        if (scope.variables().containsKey(key) && (scope.variables().get(
                key).getConstraintLabel() != null)) {
            return scope.variables().get(key).getConstraintLabel();
        }
        return null;
    }

    @NotNull
    @Override
    public Variable variable(@NotNull VarKey key) {
        return variables.get(key);
    }

    @NotNull
    @Override
    public Map<VarKey, Variable> variables() {
        return new LinkedHashMap<>(variables);
    }

// --Commented out by Inspection START (10/09/2017, 14:29):
//    private boolean checkConstraint(@NotNull Value value,
//                                    @Nullable Variable oldValue,
//                                    @NotNull Value constraint) {
//        checkDestroyed();
//
//        parameter("it", value);
//        log.debug("SET it={}", value);
//        if (oldValue != null) {
//            parameter("previous", oldValue.getValue());
//        }
//        final boolean fail = constraint.isFalse();
//        parameter("it", $void());
//        parameter("previous", $void());
//        return fail;
//    }
// --Commented out by Inspection STOP (10/09/2017, 14:29)

    private void checkDestroyed() {
        if (destroyed) {
            throw new IllegalStateException("Attempted to use a destroyed scope " + this);
        }
    }

    private void checkPure(@NotNull Scope parent) {
        if (parent.pure() && !pure()) {
            log.debug("Impure child {} of pure parent {}", id, parent);
            handleError(new PureFunctionException());
        }
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(uuid);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScriptScope that = (ScriptScope) o;
        return Objects.equal(uuid, that.uuid);
    }

    @Nullable
    @Override
    public String toString() {
        if (parent != null) {
            return id + "->" + parent;
        } else {
            return id;
        }
    }

// --Commented out by Inspection START (10/09/2017, 14:29):
//    public @NotNull
//    Multimap<String, Listener> listeners() {
//        return listeners;
//    }
// --Commented out by Inspection STOP (10/09/2017, 14:29)

// --Commented out by Inspection START (10/09/2017, 14:29):
//    public void parser(@NotNull Parser<Value> parser) {
//        this.parser = parser;
//    }
// --Commented out by Inspection STOP (10/09/2017, 14:29)

// --Commented out by Inspection START (10/09/2017, 14:29):
//    @NotNull
//    public Parser<Value> parser() {
//        return parser;
//    }
// --Commented out by Inspection STOP (10/09/2017, 14:29)


}
