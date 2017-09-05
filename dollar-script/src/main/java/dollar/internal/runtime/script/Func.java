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

package dollar.internal.runtime.script;

import dollar.api.Scope;
import dollar.api.Type;
import dollar.api.VarInternal;
import dollar.api.execution.DollarExecutor;
import dollar.api.plugin.Plugins;
import dollar.api.script.ModuleResolver;
import dollar.api.time.Scheduler;
import dollar.api.types.DollarFactory;
import dollar.api.var;
import dollar.internal.runtime.script.api.DollarParser;
import dollar.internal.runtime.script.api.exceptions.DollarAssertionException;
import dollar.internal.runtime.script.api.exceptions.DollarScriptException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jparsec.Token;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static dollar.api.DollarStatic.*;
import static dollar.api.types.meta.MetaConstants.IS_BUILTIN;
import static dollar.api.types.meta.MetaConstants.OPERATION_NAME;
import static dollar.internal.runtime.script.DollarScriptSupport.*;
import static dollar.internal.runtime.script.parser.Symbols.*;
import static java.util.Collections.singletonList;

public final class Func {

    public static final double ONE_DAY = 24.0 * 60.0 * 60.0 * 1000.0;
    @NotNull
    static final DollarExecutor executor = Objects.requireNonNull(Plugins.sharedInstance(DollarExecutor.class));

    @NotNull
    static var reduceFunc(boolean pure, @NotNull var lhs, @NotNull var rhs) {
        assert REDUCE.validForPure(pure);
        return lhs.$list().$stream(currentScope().parallel()).reduce((x, y) -> {
            try {
                return inSubScope(false, pure, REDUCE.name(), newScope -> {
                    newScope.parameter("1", x);
                    newScope.parameter("2", y);
                    return rhs.$fixDeep(false);
                });
            } catch (RuntimeException e) {
                return currentScope().handleError(e);
            }
        }).orElse($null(Type._ANY));
    }

    @NotNull
    static var eachFunc(boolean pure, @NotNull var lhs, @NotNull var rhs) {
        assert EACH.validForPure(pure);
        return lhs.$each(i -> {
            var result = inSubScope(false, pure, EACH.name(),
                                    newScope -> {
                                        newScope.parameter("1", i[0]);
                                        return rhs.$fixDeep(currentScope().parallel());
                                    });
            assert result != null;
            return result;
        });
    }

    @NotNull
    static var elseFunc(@NotNull var lhs, @NotNull var rhs) {
        final var fixLhs = lhs.$fixDeep(currentScope().parallel());
        if (fixLhs.isBoolean() && fixLhs.isFalse()) {
            return rhs.$fix(2, false);
        } else {
            return fixLhs;
        }
    }

    @NotNull
    static var multiplyFunc(@NotNull var lhs, @NotNull var rhs) {
        final var lhsFix = lhs.$fix(false);
        if (Arrays.asList(BLOCK_OP.name(), MAP_OP.name(), LIST_OP.name()).contains(lhs.metaAttribute(OPERATION_NAME))) {
            var newValue = lhsFix.$fixDeep(false);
            Long max = rhs.toLong();
            for (int i = 1; i < max; i++) {
                newValue = newValue.$plus(lhs.$fixDeep());
            }
            return newValue;
        } else {
            return lhsFix.$multiply(rhs);
        }
    }

    @NotNull
    static var errorFunc(@NotNull var v) {
        return currentScope().addErrorHandler(v);
    }

    @NotNull
    static var pairFunc(@NotNull var lhs, @NotNull var rhs) {
        return $(lhs.$S(), rhs);
    }


    @NotNull
    static Future<var> forkFunc(@NotNull var v) {
        return executor.executeInBackground(() -> DollarScriptSupport.fix(v));
    }

    @NotNull
    static var subscribeFunc(@NotNull var lhs, @NotNull var rhs) {
        return lhs.$subscribe(
                i -> inSubScope(true, false,
                                "subscribe-param", newScope -> {
                            final var it = DollarScriptSupport.fix(i[0]);
                            currentScope().parameter("1", it);
                            currentScope().parameter("it", it);
                            return DollarScriptSupport.fix(rhs);
                        }));
    }

    @NotNull
    static var writeFunc(@NotNull var lhs, @NotNull var rhs) {
        return rhs.$write(lhs);
    }

    @NotNull
    static var publishFunc(@NotNull var lhs, @NotNull var rhs) {
        return rhs.$publish(lhs);
    }

    @NotNull
    static var assertEqualsFunc(@NotNull var lhs, @NotNull var rhs) {
        final var lhsFix = lhs.$fixDeep(currentScope().parallel());
        final var rhsFix = rhs.$fixDeep(currentScope().parallel());
        if (lhsFix.equals(rhsFix)) {
            return $(true);
        } else {
            throw new DollarAssertionException(lhsFix.toDollarScript() + " != " + rhsFix.toDollarScript(), lhs);
        }
    }

    @NotNull
    static var listenFunc(@NotNull var lhs, @NotNull var rhs) {
        lhs.$fixDeep(currentScope().parallel());
        return lhs.$listen(var -> lhs.isTrue() ? DollarScriptSupport.fix(rhs) : $void());
    }

    @NotNull
    static var readFunc(@NotNull var from) {
        return DollarFactory.fromURI(from).$read();
    }

    static var inFunc(@NotNull var lhs, @NotNull var rhs) {return rhs.$contains(lhs);}

    @NotNull
    static var serialFunc(@NotNull var v) {return v.$fixDeep(currentScope().parallel());}

    @NotNull
    static var whileFunc(boolean pure, @NotNull var lhs, @NotNull var rhs) {
        assert WHILE_OP.validForPure(pure);

        while (lhs.isTrue()) {
            rhs.$fixDeep(currentScope().parallel());
        }
        return $(false);
    }

    @NotNull
    static var assertFunc(@Nullable var message, @NotNull var condition) {
        if (!condition.isTrue()) {
            throw new DollarAssertionException("Assertion failed: " + (message != null ? message : ""), condition);

        }
        return $void();
    }

    @NotNull
    static var ifFunc(boolean pure, @NotNull var lhs, @NotNull var rhs) {
        final var lhsFix = lhs.$fixDeep(currentScope().parallel());
        boolean isTrue = lhsFix.isBoolean() && lhsFix.isTrue();
        assert IF_OP.validForPure(pure);
        return isTrue ? rhs.$fix(2, currentScope().parallel()) : DollarFactory.FALSE;
    }

    @NotNull
    static var isFunc(@NotNull var lhs, @NotNull List<var> value) {
        return $(value.stream()
                         .map(var::$S)
                         .map(Type::of)
                         .filter(lhs::is).count() > 0);
    }

    @NotNull
    static var forFunc(boolean pure, @NotNull String varName, @NotNull var iterable, @NotNull var block) {
        assert FOR_OP.validForPure(pure);
        return iterable.$each(i -> {
            currentScope()
                    .set(varName,
                         DollarScriptSupport.fix(i[0]),
                         false, null,
                         null, false,
                         false,
                         pure);
            return block.$fixDeep(false);
        });
    }

    @NotNull
    static var castFunc(@NotNull var lhs, @NotNull String typeName) {
        return lhs.$as(Type.of(typeName));
    }

    @NotNull
    static var causesFunc(boolean pure, @NotNull var lhs, @NotNull var rhs) {
        lhs.$fixDeep(currentScope().parallel());
        return lhs.$listen(vars -> rhs.$fix(1, currentScope().parallel()));
    }

    @NotNull
    static var pipeFunc(@NotNull DollarParser parser, boolean pure, @NotNull Token token, @NotNull var rhs, @NotNull var lhs) {
        currentScope().parameter("1", lhs);
        var rhsVal = rhs.$fix(currentScope().parallel());
        String rhsStr = rhsVal.toString();
        if (FUNCTION_NAME_OP.name().equals(rhs.metaAttribute(OPERATION_NAME))) {
            return rhsVal;
        } else {
            return (rhs.metaAttribute(IS_BUILTIN) != null)
                           ? Builtins.execute(rhsStr, singletonList(lhs), pure)
                           : variableNode(pure, rhsStr, false, null, token, parser).$fix(2, currentScope().parallel());
        }
    }

    @NotNull
    static var everyFunc(@NotNull AtomicInteger count,
                         @NotNull var durationVar,
                         @Nullable var until,
                         @Nullable var unless,
                         @NotNull VarInternal block) {
        Scope scope = currentScope();
        Double duration = durationVar.toDouble();
        assert duration != null;
        Scheduler.schedule(i -> {
            count.incrementAndGet();
            return inScope(true, scope, newScope -> {

                newScope.parameter("1", $(count.get()));
                if ((until != null) && until.isTrue()) {
                    Scheduler.cancel(i[0].$S());
                    return i[0];
                } else {
                    if ((unless != null) && unless.isTrue()) {
                        return $void();
                    } else {
                        return block.$fixDeep(currentScope().parallel());
                    }
                }

            });
        }, ((long) (duration * ONE_DAY)));
        return $void();
    }

    @NotNull
    static var moduleFunc(@NotNull DollarParserImpl parser,
                          @NotNull String moduleName,
                          @Nullable Iterable<var> params) throws Exception {
        String[] parts = moduleName.split(":", 2);
        if (parts.length < 2) {
            throw new DollarScriptException("Module " + moduleName + " needs to have a scheme");
        }
        Map<String, var> paramMap = new HashMap<>();
        if (params != null) {
            for (var param1 : params) {
                paramMap.put(param1.metaAttribute(DollarParserImpl.NAMED_PARAMETER_META_ATTR), param1);
            }
        }

        return ModuleResolver
                       .resolveModule(parts[0])
                       .resolve(parts[1], currentScope(), parser)
                       .pipe($(paramMap))
                       .$fix(currentScope().parallel());


    }

    @NotNull
    static var mapFunc(@NotNull List<var> entries) {
        if (entries.size() == 1) {
            return DollarFactory.blockCollection(entries);
        } else {
            Stream<var> stream = currentScope().parallel() ? entries.stream().parallel() : entries.stream();
            return $(stream.map(v -> v.$fix(currentScope().parallel()))
                             .collect(Collectors.toConcurrentMap(
                                     v -> v.pair() ? v.$pairKey() : v.$S(),
                                     v -> v.pair() ? v.$pairValue() : v)));
        }
    }

    @NotNull
    static var blockFunc(@NotNull List<var> l) {
        if (l.isEmpty()) {
            return $void();
        } else {
            IntStream.range(0, l.size() - 1).forEach(i -> l.get(i).$fixDeep(currentScope().parallel()));
            return l.get(l.size() - 1);
        }
    }

    @NotNull
    public static var definitionFunc(@NotNull Token token,
                                     boolean export,
                                     @NotNull var value,
                                     @NotNull var variableName,
                                     @Nullable var constraint,
                                     @Nullable String constraintSource,
                                     @NotNull DollarParser parser,
                                     boolean pure, boolean readonly) {
        String key = variableName.$S();

        setVariable(currentScope(), key, value,
                    readonly, constraint, constraintSource,
                    false, false, pure,
                    true, token, parser);

        if (export) {
            parser.export(key,
                          node(DEFINITION, pure,
                               parser, token, singletonList(value),
                               exportArgs -> value));
        }
        return $void();
    }
}
