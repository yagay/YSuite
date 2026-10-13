package com.yagay.YFloat.hook;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class GoogleLiveWordTextPolicyTest {
    @Test public void choosesNonemptyWordAndIgnoresInvalidControls() {
        assertEquals("Hello", GoogleLiveWordTextPolicy.choose("Hello", "Hello"));
        assertEquals("World", GoogleLiveWordTextPolicy.choose("", "World"));
        assertEquals("London", GoogleLiveWordTextPolicy.choose("en", "London"));
        assertEquals("", GoogleLiveWordTextPolicy.choose("\u0001bad", ""));
    }

    @Test public void joinsWordsWithoutDuplicatingPunctuationSpaces() {
        StringBuilder out = new StringBuilder();
        for (String word : new String[]{"Hello", ",", "world", "!"}) {
            GoogleLiveWordTextPolicy.append(out, word);
        }
        assertEquals("Hello, world!", out.toString());
    }
}
