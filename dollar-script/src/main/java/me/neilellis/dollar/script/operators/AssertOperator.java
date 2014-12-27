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

package me.neilellis.dollar.script.operators;

import me.neilellis.dollar.script.DollarScriptSupport;
import me.neilellis.dollar.script.Scope;
import me.neilellis.dollar.script.SourceSegmentValue;
import me.neilellis.dollar.script.exceptions.DollarScriptException;
import me.neilellis.dollar.var;
import org.codehaus.jparsec.Token;
import org.codehaus.jparsec.functors.Map;

import static me.neilellis.dollar.DollarStatic.$void;

/**
 * @author <a href="http://uk.linkedin.com/in/neilellis">Neil Ellis</a>
 */
public class AssertOperator implements Map<Token, var> {
    private final Scope scope;

    public AssertOperator(Scope scope) {this.scope = scope;}

    @Override public var map(Token token) {
        final SourceSegmentValue source = new SourceSegmentValue(scope, token);
        Object[] objects = (Object[]) token.value();
        return DollarScriptSupport.wrapReactive(scope, () -> {
            if (((var) objects[1]).isTrue()) { return $void(); } else {
                throw new DollarScriptException("Assertion failed: " +
                                                (objects[0] != null ? objects[0] : "") +
                                                " : " +
                                                source.getSourceMessage());
            }
        }, source, "assert", (var) objects[1]);
    }
}