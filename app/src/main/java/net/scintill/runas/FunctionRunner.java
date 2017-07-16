/*
 * Copyright 2017 Joey Hewitt <joey@joeyhewitt.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom
 * the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.scintill.runas;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.text.TextUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;

public class FunctionRunner {

    private String mClassPath;
    private String mCodeCacheDir;

    /**
     * Initialization; needs a context to find the classpath.
     * @param ctx
     */
    public FunctionRunner(Context ctx) {
        /*
         * It would be nicer if we could just look at the classloaders for this class and
         * the one being run, but the details we need aren't publicly exposed there as far as
         * I can see. I'd rather do this than hack around with reflection or parsing classloaders'
         * toString().
         */
        ApplicationInfo appInfo = ctx.getApplicationInfo();
        mClassPath = appInfo.sourceDir + File.pathSeparator +
                TextUtils.join(File.pathSeparator, appInfo.splitSourceDirs);

        // and I guess there's really no better way for this either
        // TODO let caller optionally configure this?
        mCodeCacheDir = ctx.getCodeCacheDir().getPath();
    }

    /**
     * Run the given function, as the given Linux user ID, via `su` (superuser).
     * Return what the method returned.
     *
     * @param uid the user id
     * @param func function to run
     * @return what the function returned
     */
    public Serializable runAs(int uid, SerializableFunction func)
        throws IOException, ClassNotFoundException {

        ProcessBuilder pb = new ProcessBuilder(
                "su", String.valueOf(uid),
                "env",
                    "CLASSPATH="+mClassPath,
                    /*
                     * ANDROID_DATA is where dex2oat will put its output. Point it away from
                     * the default, so we don't clobber cache files for the rest of the
                     * app, or be readable/writable by someone else. It's a bit slow the first
                     * time because of recompiling boot oat though...
                     */
                    "ANDROID_DATA="+mCodeCacheDir,
                "app_process", "/", FunctionRunner.class.getName()
        );
        Process p = pb.start();
        copyAsync(p.getErrorStream(), System.err); // System.err will go to logcat

        // Pipe in the function
        ObjectOutputStream oos = new ObjectOutputStream(p.getOutputStream());
        oos.writeObject(func);

        // Read the result
        ObjectInputStream ois = new ObjectInputStream(p.getInputStream());
        Serializable ret = (Serializable) ois.readObject();

        // End the process
        p.destroy();

        return ret;
    }

    private static void copyAsync(final InputStream src, final OutputStream dest) {
        // https://stackoverflow.com/a/1570269
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] buffer = new byte[1024];
                    for (int n = 0; n != -1; n = src.read(buffer)) {
                        dest.write(buffer, 0, n);
                    }
                } catch (IOException e) { // just exit
                }
            }
        }).start();
    }

    private static final int EXIT_IOEXCEPTION = 1;
    private static final int EXIT_UNKNOWNPOSTEXECUTION = 2;

    /**
     * This is run as the privileged user via `su`.
     * Not for public consumption.
     * @param args args
     */
    public static void main(String[] args) {
        Serializable ret;

        try {
            // Read in function
            ObjectInputStream ois = new ObjectInputStream(System.in);
            SerializableFunction func = (SerializableFunction)ois.readObject();
            // Execute function
            ret = func.execute(new CliContext());
        } catch (Throwable e) {
            SerializableFunction.Exception wrapper = new SerializableFunction.Exception();
            wrapper.e = e;
            ret = wrapper;
        }

        // Return result
        try {
            ObjectOutputStream oos = new ObjectOutputStream(System.out);
            oos.writeObject(ret);
        } catch (IOException e) {
            System.exit(EXIT_IOEXCEPTION);
        } catch (Throwable e) {
            System.exit(EXIT_UNKNOWNPOSTEXECUTION);
        }
    }

}
