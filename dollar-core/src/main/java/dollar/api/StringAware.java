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

package dollar.api;

import dollar.api.types.DollarFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @author hello@neilellis.me
 */
public interface StringAware {

    /**
     * Remove whitespace before and after the first and last non-whitespace characters.
     *
     * @return
     */
    @NotNull
    default var $trim() {
        return DollarFactory.fromStringValue(toString().trim());
    }


}