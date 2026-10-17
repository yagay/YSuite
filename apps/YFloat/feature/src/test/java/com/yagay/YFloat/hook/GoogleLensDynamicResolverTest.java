package com.yagay.YFloat.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.PointF;
import android.graphics.RectF;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

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

    private static class InheritedUserSelection {
        String selectedText() {return "inherited";}
        RectF bounds() {return new RectF();}
    }
    private static final class DerivedUserSelection extends InheritedUserSelection {}
    private static final class InheritedMetadata {
        final DerivedUserSelection selection=new DerivedUserSelection();
    }
    private static final class InheritedController {
        void changed(InheritedMetadata metadata,boolean active) {}
    }

    @Test public void inheritedSelectionAccessorsAreStillValidated() throws Exception {
        Method method=InheritedController.class.getDeclaredMethod(
                "changed",InheritedMetadata.class,boolean.class);
        GoogleLensDynamicResolver.Candidate candidate=
                GoogleLensDynamicResolver.scoreSelectionMethod(method);
        assertNotNull(candidate);
        assertTrue(candidate.score>=GoogleLensDynamicResolver.MIN_SELECTION_CONFIDENCE);
    }

    @Test public void selectionTypeProbeDoesNotLogRawOcrContent() throws Exception {
        Method method=FakeController.class.getDeclaredMethod(
                "onSelection",FakeMetadata.class,boolean.class);
        GoogleLensDynamicResolver.Candidate candidate=
                GoogleLensDynamicResolver.scoreSelectionMethod(method);
        assertNotNull(candidate);
        GoogleLensDynamicResolver.SelectionBinding binding=
                new GoogleLensDynamicResolver.SelectionBinding(method,
                        candidate.userSelectionField,candidate.textMethod,
                        candidate.boundsMethod,candidate.pointMethod,
                        candidate.score,"test");
        String types=GoogleLensDynamicResolver.describeSelectionShape(
                binding,new FakeMetadata());
        assertTrue(types.contains("metadata="));
        assertTrue(types.contains("selection="));
        assertFalse(types.contains("hello"));
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

    @Test public void symbolWindowStartsNearOldControllerNotAtDexBeginning() {
        // Old code incorrectly selected the first 6000 of 145k DEX classes.
        Set<String> nearby=GoogleLensDynamicResolver.nearestObfuscatedNames(
                List.of("aaaa","aaab","dsxa","dsxb","dsxc","zzza"),
                "dsxb",1);
        assertTrue(nearby.contains("dsxa"));
        assertTrue(nearby.contains("dsxb"));
        assertTrue(nearby.contains("dsxc"));
        assertFalse(nearby.contains("aaaa"));
        assertFalse(nearby.contains("zzza"));
    }

    @Test public void symbolWindowCanResolveMissingExactOldName() {
        Set<String> nearby=GoogleLensDynamicResolver.nearestObfuscatedNames(
                List.of("dsxa","dsxc","dsxd"),"dsxb",1);
        assertTrue(nearby.contains("dsxa"));
        assertTrue(nearby.contains("dsxc"));
    }

    @Test public void targetedDexAnchorPolicyIncludesOnlyLensCategories() {
        assertTrue(GoogleLensDynamicResolver.isRelevantDexAnchor(
                GoogleLens1760Profile.FROZEN_IMAGE_VIEW));
        assertTrue(GoogleLensDynamicResolver.isRelevantDexAnchor(
                GoogleLens1760Profile.TEXT_SELECTION_VIEW));
        assertTrue(GoogleLensDynamicResolver.isRelevantDexAnchor(
                "com.google.android.libraries.lens.view.frozenimage.RenamedFrozenView"));
        assertTrue(GoogleLensDynamicResolver.isRelevantDexAnchor(
                "com.google.android.libraries.lens.common.text.selection.ui.SelectionLayer"));
        assertFalse(GoogleLensDynamicResolver.isRelevantDexAnchor("com.google.android.apps.search.feed.FeedView"));
        assertFalse(GoogleLensDynamicResolver.isRelevantDexAnchor("com.google.android.apps.search.omnient.MiniApp"));
    }

}
