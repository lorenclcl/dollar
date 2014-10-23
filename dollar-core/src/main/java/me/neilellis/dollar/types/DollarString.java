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

import me.neilellis.dollar.var;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author <a href="http://uk.linkedin.com/in/neilellis">Neil Ellis</a>
 */
public class DollarString extends AbstractDollarSingleValue<String> {


    public DollarString(@NotNull List<Throwable> errors, @NotNull String value) {
        super(errors, value);
    }

    @Override
    public var $dec(long amount) {
        return this;
    }

    @Override
    public var $inc(long amount) {
        return this;
    }

    @Override
    public Integer I() {
        return Integer.parseInt(value);
    }

    @Override
    public boolean isString() {
        return true;
    }

    @NotNull
    @Override
    public <R> R $() {
        return (R) value;
    }

    @Override
    public Number number(@NotNull String key) {
        return new BigDecimal(key);
    }

    @NotNull
    @Override
    public Number N() {
        return D();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        return obj != null && value.equals(obj.toString());
    }
}