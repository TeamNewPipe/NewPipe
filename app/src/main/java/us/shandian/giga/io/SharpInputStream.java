/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package us.shandian.giga.io;

import androidx.annotation.NonNull;

import org.schabi.newpipe.streams.io.SharpStream;

import java.io.IOException;
import java.io.InputStream;

/**
 * Wrapper for the classic {@link java.io.InputStream}
 *
 * @author kapodamy
 */
public class SharpInputStream extends InputStream {

    private final SharpStream base;

    public SharpInputStream(SharpStream base) throws IOException {
        if (!base.canRead()) {
            throw new IOException("The provided stream is not readable");
        }
        this.base = base;
    }

    @Override
    public int read() throws IOException {
        return base.read();
    }

    @Override
    public int read(@NonNull byte[] bytes) throws IOException {
        return base.read(bytes);
    }

    @Override
    public int read(@NonNull byte[] bytes, int i, int i1) throws IOException {
        return base.read(bytes, i, i1);
    }

    @Override
    public long skip(long l) throws IOException {
        return base.skip(l);
    }

    @Override
    public int available() {
        long res = base.available();
        return res > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) res;
    }

    @Override
    public void close() {
        base.close();
    }
}
