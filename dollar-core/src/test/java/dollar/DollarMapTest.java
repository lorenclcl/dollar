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

import dollar.api.var;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static dollar.api.DollarStatic.$;
import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;

public class DollarMapTest {
    private static var list;
    private static var map;

    @BeforeAll
    public static void setUp() {
        map = $("color", "red").$("size", "large");
    }

    @Test
    public void testBasics() {
        assertTrue(map.$has("color").isTrue());
        assertTrue($(0).integer());
        System.out.println(map.$get($(0)));
        assertEquals($("color", "red"), map.$get($(0)));
    }

}
