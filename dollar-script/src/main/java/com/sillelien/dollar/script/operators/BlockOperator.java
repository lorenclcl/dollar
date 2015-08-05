/*
 * Copyright (c) 2014-2015 Neil Ellis
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

package com.sillelien.dollar.script.operators;

import com.sillelien.dollar.api.types.DollarFactory;
import com.sillelien.dollar.api.var;
import com.sillelien.dollar.script.DollarScriptSupport;
import com.sillelien.dollar.script.SourceSegmentValue;
import com.sillelien.dollar.script.api.DollarParser;
import com.sillelien.dollar.script.api.Scope;
import org.codehaus.jparsec.Token;
import org.codehaus.jparsec.functors.Map;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.sillelien.dollar.api.DollarStatic.$void;

public class BlockOperator implements Map<Token, var> {
    private final Scope scope;
    private final DollarParser dollarParser;
    private final boolean pure;

    public BlockOperator(DollarParser dollarParser, Scope scope, boolean pure) {
        this.dollarParser = dollarParser;
        this.scope = scope;
        this.pure = pure;
    }

    @Override public var map(@NotNull Token token) {
        List<var> l = (List<var>) token.value();
        return DollarScriptSupport.wrapLambda(new SourceSegmentValue(scope, token), scope,
                                              parallel -> dollarParser.inScope(pure, "block", scope, newScope -> {
                                                  if (l.size() > 0) {
                                                      return DollarFactory.blockCollection(l);
//                        return $(l);
                                                  } else {
                                                      return $void();
                                                  }
                                              }),
                                              l, "block");
    }
}