package com.yagay.YFloat;

import static org.junit.Assert.*;
import org.junit.Test;

public class GoogleLensImageInputContractTest {
    @Test public void chromiumImageUriUsesLosslessUrlEncoding() {
        String source="content://com.yagay.YSuite.files/shared_images/a b&c.png";
        String uri=GoogleLensImageInputContract.buildLensUri(source,12345L,false);
        assertTrue(uri.startsWith("googleapp://lens?LensBitmapUriKey="));
        assertTrue(uri.contains("content%3A%2F%2Fcom.yagay.YSuite.files%2F"));
        assertTrue(uri.contains("a+b%26c.png"));
        assertTrue(uri.contains("ActivityLaunchTimestampNanos=12345"));
        assertTrue(GoogleLensImageInputContract.buildLensUri(source,12345L,true)
                .startsWith("google://lens?"));
    }
    @Test public void onlyHostOwnedImageUrisAreAccepted() {
        assertTrue(GoogleLensImageInputContract.isHostImageUri(
                "content://com.yagay.YSuite.files/shared_images/test.png","com.yagay.YSuite"));
        assertFalse(GoogleLensImageInputContract.isHostImageUri(
                "file:///sdcard/secret.png","com.yagay.YSuite"));
        assertFalse(GoogleLensImageInputContract.isHostImageUri(
                "content://another.package.files/secret.png","com.yagay.YSuite"));
        assertFalse(GoogleLensImageInputContract.isHostImageUri(
                "content://com.yagay.YSuite.files","com.yagay.YSuite"));
    }
    @Test public void acceptExplicitGoogleLensShareComponentWithoutDeclaredPackage() {
        assertTrue(GoogleLensImageInputContract.isGoogleTarget(
                null,"com.google.android.googlequicksearchbox",
                "com.google.android.googlequicksearchbox"));
        assertTrue(GoogleLensImageInputContract.isGoogleTarget(
                "com.google.android.googlequicksearchbox",
                "com.google.android.googlequicksearchbox",
                "com.google.android.googlequicksearchbox"));
    }
    @Test public void acceptGooglePackageOnlyLegacyDeepLink() {
        assertTrue(GoogleLensImageInputContract.isGoogleTarget(
                "com.google.android.googlequicksearchbox",null,
                "com.google.android.googlequicksearchbox"));
    }
    @Test public void rejectForeignOrUnspecifiedTargets() {
        String google="com.google.android.googlequicksearchbox";
        assertFalse(GoogleLensImageInputContract.isGoogleTarget(null,null,google));
        assertFalse(GoogleLensImageInputContract.isGoogleTarget(
                null,"com.example.fake",google));
        assertFalse(GoogleLensImageInputContract.isGoogleTarget(
                "com.example.fake",google,google));
        assertFalse(GoogleLensImageInputContract.isGoogleTarget(
                google,"com.example.fake",google));
    }

    @Test public void noEmptyInputOrTimestamp() {
        assertThrows(IllegalArgumentException.class,
                ()->GoogleLensImageInputContract.buildLensUri("",1234L,false));
        assertThrows(IllegalArgumentException.class,
                ()->GoogleLensImageInputContract.buildLensUri("content://test/a",0L,false));
    }
}
