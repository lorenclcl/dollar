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

import java.util.Set;

/**
 * A type prediction, type prediction is used to estimate the possible evaluated types of some dynamic values. For
 * static types this will be the actual type.
 *
 * @author <a href="http://uk.linkedin.com/in/neilellis">Neil Ellis</a>
 */
public interface TypePrediction {

    /**
     * True if no prediction could be made.
     *
     * @return true if no prediction
     */
    boolean empty();

    /**
     * Probability of this type being correct (0.0 - 1.0). This does not need to be accurate, just indicative. The sum
     * of the probabilities does not need to equal 1.0.
     *
     * @param type the type you would like to know the probability for.
     *
     * @return the probability as a double
     */
    Double probability(Type type);

    /**
     * The most likely type based on the evidence supplied.
     *
     * @return the most probable type
     */
    Type probableType();

    /**
     * All possible types.
     *
     * @return the set of possible types
     */
    Set<Type> types();
}