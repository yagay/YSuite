package com.yagay.YFloat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;

/** Compatibility entry point using the one YFloat text/image selection menu host. */
public final class ImageActionMenu {
    public static void show(Context c, Bitmap image, Rect anchor) {
        if(c==null||image==null||image.isRecycled())return;
        Context app=c.getApplicationContext();
        Rect usable=ScreenGeometry.usableBounds(app);
        Rect where=anchor==null||anchor.isEmpty()
                ? new Rect(usable.centerX(),usable.centerY(),
                        usable.centerX()+1,usable.centerY()+1)
                : new Rect(anchor);
        FloatActionMenu.showImageAt(app,where,false,index -> {
            // The source bitmap belongs to the caller; never recycle it here.
            if(image.isRecycled())return;
            if(index==ImageMenuSettings.index(ImageMenuSettings.FIND))
                ImageShareUtils.openWith(app,image);
            else if(index==ImageMenuSettings.index(ImageMenuSettings.COPY))
                ImageShareUtils.copyToClipboard(app,image);
            else if(index==ImageMenuSettings.index(ImageMenuSettings.SHARE))
                ImageShareUtils.share(app,image);
            else if(index==ImageMenuSettings.index(ImageMenuSettings.SAVE))
                ScreenshotController.save(app,image);
        });
    }

    public static void dismiss() {
        FloatActionMenu.dismissImage();
    }

    private ImageActionMenu() {}
}
