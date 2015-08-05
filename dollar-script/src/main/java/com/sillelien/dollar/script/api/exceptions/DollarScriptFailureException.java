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

package com.sillelien.dollar.script.api.exceptions;

import com.sillelien.dollar.api.types.ErrorType;
import org.jetbrains.annotations.NotNull;

public class DollarScriptFailureException extends DollarScriptException {
    public DollarScriptFailureException(@NotNull ErrorType errorType) {
        super(errorType.toString());
    }

    public DollarScriptFailureException(@NotNull ErrorType errorType, String message) {
        super(errorType.toString() + ":" + message);
    }

    public DollarScriptFailureException(@NotNull Throwable t, @NotNull ErrorType errorType) {
        super(t, errorType.toString() + ": " + t.getMessage());
    }

}