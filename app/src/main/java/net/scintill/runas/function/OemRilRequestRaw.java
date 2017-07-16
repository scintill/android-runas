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

package net.scintill.runas.function;

import android.content.Context;
import android.telephony.TelephonyManager;

import net.scintill.runas.SerializableFunction;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class OemRilRequestRaw implements SerializableFunction {

    private byte[] mRawReq;

    public OemRilRequestRaw(byte[] rawReq) {
        mRawReq = rawReq;
    }

    @Override
    public Serializable execute(Context ctx) throws Throwable {
        TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
        try {
            // this method is public but hidden with @hide
            Method m = tm.getClass().getMethod("invokeOemRilRequestRaw", byte[].class, byte[].class);
            return (int)m.invoke(tm, mRawReq, new byte[0]);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException("Unable to find RilRequestRaw method", e);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
