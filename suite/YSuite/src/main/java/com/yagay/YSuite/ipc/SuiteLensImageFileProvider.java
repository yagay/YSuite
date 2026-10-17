package com.yagay.YSuite.ipc;

import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.Binder;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.yagay.YFloat.DiagnosticLog;

import java.io.FileNotFoundException;

/**
 * Replaces the existing host FileProvider without changing its authority,
 * paths or permission policy. Logs only YFloat's explicitly shared PNG read
 * requests. It never accesses or copies the image contents.
 *
 * The provider may be called on a binder thread from a different process:
 * this observation is stronger than hooking ContentResolver methods in AGSA.
 */
public final class SuiteLensImageFileProvider extends FileProvider {
    @Override public ParcelFileDescriptor openFile(
            @NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        boolean probe=isYFloatImage(uri);
        if(probe)record("host_file_open_attempt",uri,mode,false,"");
        try {
            ParcelFileDescriptor result=super.openFile(uri,mode);
            if(probe)record("host_file_open_result",uri,mode,result!=null,"");
            return result;
        } catch (FileNotFoundException exception) {
            if(probe)record("host_file_open_failed",uri,mode,false,"FileNotFoundException");
            throw exception;
        } catch (RuntimeException exception) {
            if(probe)record("host_file_open_failed",uri,mode,false,
                    exception.getClass().getSimpleName());
            throw exception;
        }
    }

    private boolean isYFloatImage(Uri uri) {
        String last=uri==null?null:uri.getLastPathSegment();
        return last!=null&&last.startsWith("YFloat_")&&last.endsWith(".png");
    }

    private void record(String kind,Uri uri,String mode,boolean success,String error) {
        android.content.Context ctx=getContext();
        if(ctx==null)return;
        DiagnosticLog.i(ctx,"LENS_IMAGE_INPUT",kind
                +" authority="+uri.getAuthority()
                +" callerUid="+Binder.getCallingUid()
                +" readMode="+mode
                +" success="+success
                +(error.isBlank()?"":" error="+error));
    }
}
