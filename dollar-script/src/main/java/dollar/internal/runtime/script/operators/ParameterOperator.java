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

package dollar.internal.runtime.script.operators;

import com.sillelien.dollar.api.var;
import dollar.internal.runtime.script.Builtins;
import dollar.internal.runtime.script.DollarParserImpl;
import dollar.internal.runtime.script.DollarScriptSupport;
import dollar.internal.runtime.script.SourceSegmentValue;
import dollar.internal.runtime.script.api.DollarParser;
import dollar.internal.runtime.script.api.Scope;
import dollar.internal.runtime.script.api.exceptions.DollarScriptException;
import org.jparsec.Token;
import org.jparsec.functors.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.Callable;

import static com.sillelien.dollar.api.DollarStatic.$;

public class ParameterOperator implements Map<Token, Map<? super var, ? extends var>> {
    private final Scope scope;
    private final DollarParser dollarParser;
    private final boolean pure;

    public ParameterOperator(DollarParser dollarParser, Scope scope, boolean pure) {
        this.dollarParser = dollarParser;
        this.scope = scope;
        this.pure = pure;
    }

    @Nullable @Override public Map<? super var, ? extends var> map(@NotNull Token token) {
        List<var> rhs = (List<var>) token.value();
        return lhs -> {
            if (!lhs.dynamic()) {
                String lhsString = lhs.toString();
                if (pure && Builtins.exists(lhsString) && !Builtins.isPure(lhsString)) {
                    throw new DollarScriptException(
                            "Cannot call the impure function '" + lhsString + "' in a pure expression.");
                }
            }

            String constraintSource = null;
            Callable<var> callable = () -> dollarParser.inScope(pure, "parameter", scope,
                                                                new Function(rhs, lhs, token,
                                                                             constraintSource));
            var lambda =
                    DollarScriptSupport.toLambda(scope, callable, new SourceSegmentValue(scope, token), rhs,
                                                 "parameter");
            //reactive links
            lhs.$listen(i -> lambda.$notify());
            for (var param : rhs) {
                param.$listen(i -> lambda.$notify());
            }
            return lambda;
        };
    }

    private class Function implements java.util.function.Function<Scope, var> {
        private final List<var> rhs;
        private final var lhs;
        private final Token token;
        private final String constraintSource;

        public Function(List<var> rhs, var lhs, Token token, String constraintSource) {
            this.rhs = rhs;
            this.lhs = lhs;
            this.token = token;
            this.constraintSource = constraintSource;
        }

        @Override
        public var apply(
                @NotNull Scope newScope) {
            //Add the special $*
            // value for all the
            // parameters
            newScope.setParameter(
                    "*",
                    $(rhs));
            int count = 0;
            for (var param : rhs) {
                newScope.setParameter(String.valueOf(++count), param);
                //If the parameter is a named parameter then use the name (set as metadata on the value).
                if (param.getMetaAttribute(DollarParserImpl.NAMED_PARAMETER_META_ATTR) != null) {
                    newScope.set(param.getMetaAttribute(DollarParserImpl.NAMED_PARAMETER_META_ATTR), param, true, null,
                                 constraintSource, false, false, pure);
                }
            }
            var result;
            if (lhs.dynamic()) { result = lhs._fix(2, false); } else {
                String lhsString = lhs.toString();
                //The lhs is a
                // string, so
                // let's
                // see if it's a
                // builtin function
                //if not then
                // assume
                // it's a variable.
                if (Builtins.exists(lhsString)) { result = Builtins.execute(lhsString, rhs, newScope, pure); } else {
                    final var valueUnfixed = DollarScriptSupport.getVariable(
                            pure, newScope, lhsString, false, null, new SourceSegmentValue(scope, token));
                    result = valueUnfixed._fix(2, false);
                }
            }

            return result;
        }
    }
}