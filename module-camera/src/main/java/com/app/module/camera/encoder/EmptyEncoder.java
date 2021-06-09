package com.app.module.camera.encoder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.util.Log;

public class EmptyEncoder extends DrawEncoder{

    private volatile boolean empty = false;

    public EmptyEncoder(final Context context) {

    }

    @Override
    public synchronized void setFrameConfiguration(final int width, final int height) {

    }

    @Override
    public synchronized void draw(final Canvas canvas) {
        if(!empty) return ;
        if(canvas != null){
            canvas.drawColor(0 , PorterDuff.Mode.CLEAR);
        }
    }

    @Override
    public synchronized void processResults(Object rects) {
        empty = rects == null ;
    }
}
