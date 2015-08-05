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

import com.sillelien.dollar.api.time.Scheduler;
import com.sillelien.dollar.api.types.DollarFactory;
import com.sillelien.dollar.api.var;
import com.sillelien.dollar.script.api.DollarParser;
import com.sillelien.dollar.script.api.Scope;
import org.codehaus.jparsec.functors.Map;

import static com.sillelien.dollar.api.DollarStatic.$;
import static com.sillelien.dollar.api.DollarStatic.$void;

public class EveryOperator implements Map<Object[], var> {
    private final Scope scope;
    private final DollarParser dollarParser;
    private final boolean pure;

    public EveryOperator(DollarParser dollarParser, Scope scope, boolean pure) {
        this.dollarParser = dollarParser;
        this.scope = scope;
        this.pure = pure;
    }

    @Override public var map(Object[] objects) {
        final int[] count = new int[]{-1};
        Scheduler.schedule(i -> {
            count[0]++; // William Gibson
//                System.out.println("COUNT "+count[0]);
            return dollarParser.inScope(pure, "every", scope, newScope -> {
                try {
//                    System.err.println(newScope);
                    newScope.setParameter("1", $(count[0]));
                    if (objects[1] instanceof var && ((var) objects[1]).isTrue()) {
                        Scheduler.cancel(i[0].$S());
                        return i[0];
                    } else if (objects[2] instanceof var && ((var) objects[2]).isTrue()) {
                        return $void();
                    } else {
                        return ((var) objects[3])._fixDeep();
                    }
                } catch (Exception e) {
                    return DollarFactory.failure(e);
                }

            });
        }, ((long) (((var) objects[0]).toDouble() * 24.0 * 60.0 * 60.0 * 1000.0)));
        return $void();
    }
}