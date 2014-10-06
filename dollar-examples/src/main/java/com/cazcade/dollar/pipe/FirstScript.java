package com.cazcade.dollar.pipe;

import com.cazcade.dollar.Script;
import com.cazcade.dollar.var;

import java.util.Date;

/**
 * @author <a href="http://uk.linkedin.com/in/neilellis">Neil Ellis</a>
 */
public class FirstScript extends Script {
    static {
        $THIS = FirstScript.class;
    }

    {
        var profile = $("name", "Neil")
                .$("age", new Date().getYear() + 1900 - 1970)
                .$("gender", "male")
                .$("projects", $jsonArray("snapito", "dollar_vertx"))
                .$("location",
                        $("city", "brighton")
                                .$("postcode", "bn1 6jj")
                                .$("number", 343)
                );
        profile.pipe(ExtractName.class).pipe(WelcomeMessage.class).out();
    }
}