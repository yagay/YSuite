package com.yagay.YFloat.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.PointF;
import android.graphics.RectF;

import org.junit.Test;

import java.lang.reflect.Method;

public class GoogleLensDynamicResolverTest {

    private static final class FakeUserSelection {
        String selectedText() { return "hello"; }
        RectF bounds() { return new RectF(); }
        PointF point() { return new PointF(); }
    }

    private static final class FakeMetadata {
        final FakeUserSelection selection = new FakeUserSelection();
    }

    private static final class FakeController {
        void onSelection(FakeMetadata metadata, boolean primary) {}
    }

    private static final class WrongMetadata {
        final Object value = new Object();
    }

    private static final class WrongController {
        int notSelection(WrongMetadata metadata, boolean primary) { return 0; }
    }

    @Test public void structuralSelectionCandidateRequiresSemanticShape() throws Exception {
        Method method = FakeController.class.getDeclaredMethod(
                "onSelection", FakeMetadata.class, boolean.class);
        GoogleLensDynamicResolver.Candidate candidate =
                GoogleLensDynamicResolver.scoreSelectionMethod(method);
        assertNotNull(candidate);
        assertTrue(candidate.score >= GoogleLensDynamicResolver.MIN_SELECTION_CONFIDENCE);
    }

    @Test public void nonVoidLookalikeIsRejected() throws Exception {
        Method method = WrongController.class.getDeclaredMethod(
                "notSelection", WrongMetadata.class, boolean.class);
        assertTrue(GoogleLensDynamicResolver.scoreSelectionMethod(method) == null);
    }
    private static final class FakeRecognizedText {
        final String a = "  Google recognized words  ";
        final java.util.List<String> b = java.util.List.of("Google", "recognized", "words");
    }

    private static final class FakeWordSelection {
        final FakeRecognizedText a = new FakeRecognizedText();
    }

    // Future R8 runs may rename both nested fields independently.
    private static final class RenamedRecognizedText {
        final String renamedText = "  future words  ";
        final java.util.List<String> renamedWords = java.util.List.of("future", "words");
    }

    private static final class RenamedWordSelection {
        final RenamedRecognizedText randomField = new RenamedRecognizedText();
    }

    private static final class AmbiguousRecognizedText {
        final String first = "foo";
        final String second = "bar";
        final java.util.List<String> words = java.util.List.of("foo");
    }

    private static final class AmbiguousWordSelection {
        final AmbiguousRecognizedText data = new AmbiguousRecognizedText();
    }

    private static final class FakeUnrelatedSelection {
        final String a = "should not be treated as nested OCR result";
    }

    @Test public void wordSelectionRecoversGoogleTextWhenPublicAccessorIsBlank() {
        assertEquals("Google recognized words",
                GoogleLensDynamicResolver.nestedSelectedText(new FakeWordSelection()));
        assertEquals("", GoogleLensDynamicResolver.nestedSelectedText(
                new FakeUnrelatedSelection()));
        assertEquals("future words", GoogleLensDynamicResolver.nestedSelectedText(
                new RenamedWordSelection()));
        assertEquals("", GoogleLensDynamicResolver.nestedSelectedText(
                new AmbiguousWordSelection()));
    }

    @Test public void onlyActualGoogleRegionModelCanCommitRegion() {
        assertFalse(GoogleLensDynamicResolver.shouldCommitRegion(false, "", true));
        assertFalse(GoogleLensDynamicResolver.shouldCommitRegion(true, "words", true));
        assertFalse(GoogleLensDynamicResolver.shouldCommitRegion(true, "", false));
        assertTrue(GoogleLensDynamicResolver.shouldCommitRegion(true, "", true));
    }

    @Test public void scoreRemainsIndependentOfObfuscatedNames() throws Exception {
        Method method = FakeController.class.getDeclaredMethod(
                "onSelection", FakeMetadata.class, boolean.class);
        assertNotNull(GoogleLensDynamicResolver.scoreSelectionMethod(method));
    }

}
