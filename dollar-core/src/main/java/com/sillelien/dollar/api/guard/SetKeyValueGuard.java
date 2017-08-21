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

package com.sillelien.dollar.api.guard;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Collection;

public class SetKeyValueGuard implements Guard {
    @NotNull
    @Override
    public String description() {
        return "Set K/V Guard";
    }

    @Override
    public void postCondition(Object guarded, Method method, Object[] args, Object result) {
        if (result instanceof Collection) {
            assertNotNull("return value", result, method);
        }
    }

    @Override
    public void preCondition(Object guarded, Method method, Object[] args) {

        assertNotNull("first parameter ", args[0], method);
    }

}