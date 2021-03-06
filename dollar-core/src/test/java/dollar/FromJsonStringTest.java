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

package dollar;

import org.junit.jupiter.api.Test;

import static dollar.api.DollarStatic.$json;
import static org.junit.Assert.assertEquals;

public class FromJsonStringTest {


    @Test
    public void testFromJsonString() throws Exception {
        assertEquals((Object) 1L, $json("1").toJavaObject());
        assertEquals(new Double(1.0), $json("1.0").toJavaObject());
        assertEquals(new Double(0.1), $json(".1").toJavaObject());
        assertEquals("1", $json("\"1\"").toJavaObject());
        assertEquals((Object) 1L, $json("{\"a\":1}").toJsonObject().getValue("a"));
    }
}
