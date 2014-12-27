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

package me.neilellis.dollar;

import me.neilellis.dollar.collections.ImmutableList;
import me.neilellis.dollar.guard.ChainGuard;
import me.neilellis.dollar.guard.Guarded;
import me.neilellis.dollar.guard.NotNullCollectionGuard;
import me.neilellis.dollar.guard.NotNullGuard;
import me.neilellis.dollar.script.SourceSegment;
import org.jetbrains.annotations.NotNull;

/**
 * @author <a href="http://uk.linkedin.com/in/neilellis">Neil Ellis</a>
 */
public interface VarInternal {
    /**
     * Returns a deep copy of this object. You should never need to use this operation as all {@link
     * me.neilellis.dollar.var} objects are immutable. Therefore they can freely be shared between threads.
     *
     * @return a deep copy of this object
     */
    @NotNull
    @Guarded(ChainGuard.class) var _copy();

    @Guarded(ChainGuard.class)
    @Guarded(NotNullGuard.class)
    @Guarded(NotNullCollectionGuard.class)
    @NotNull var _copy(@NotNull ImmutableList<Throwable> errors);

    @NotNull
    /**
     * Like _unwrap() except it causes lambda evaluation but does not propagate through lists and maps.
     */
    @Guarded(ChainGuard.class) var _fix(boolean parallel);

    @Guarded(ChainGuard.class) var _fix(int depth, boolean parallel);

    @Guarded(ChainGuard.class)
    default var _fixDeep() { return _fixDeep(false);}

    @Guarded(ChainGuard.class) var _fixDeep(boolean parallel);

    /**
     * Attempt to predict what type this object is. For static types this will predict the the same value as returned by
     * {@link var#$type()}
     *
     * @return a prediction of what type this object may be.
     */
    TypePrediction _predictType();

    default SourceSegment _source() {
        return null;
    }



    /**
     * Unwraps any wrapper classes around the actual type class.
     *
     * @return an unwrapped class.
     */
    @Guarded(ChainGuard.class)
    @NotNull var _unwrap();

}
