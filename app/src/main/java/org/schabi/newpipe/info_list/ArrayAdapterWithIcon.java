package org.schabi.newpipe.info_list;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ArrayAdapterWithIcon extends ArrayAdapter<String> {

    private final List<Integer> images;

    public ArrayAdapterWithIcon(Context context, List<String> items, List<Integer> images) {
        super(context, android.R.layout.select_dialog_item, items);
        this.images = images;
    }

    public ArrayAdapterWithIcon(final Context context, final String[] items, final Integer[] images) {
        super(context, android.R.layout.select_dialog_item, items);
        this.images = Arrays.asList(images);
    }

    public ArrayAdapterWithIcon(final Context context, final int items, final int images) {
        super(context, android.R.layout.select_dialog_item, (String[]) context.getResources().getTextArray(items));

        final TypedArray imgs = context.getResources().obtainTypedArray(images);
        this.images = new ArrayList<Integer>() {{
            for (int i = 0; i < imgs.length(); i++) {
                add(imgs.getResourceId(i, -1));
            }
        }};

        // recycle the array
        imgs.recycle();
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        final View view = super.getView(position, convertView, parent);
        final TextView textView = (TextView) view.findViewById(android.R.id.text1);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            textView.setCompoundDrawablesRelativeWithIntrinsicBounds(images.get(position), 0, 0, 0);
        } else {
            textView.setCompoundDrawablesWithIntrinsicBounds(images.get(position), 0, 0, 0);
        }
        textView.setCompoundDrawablePadding(
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12,
                        getContext().getResources().getDisplayMetrics()));
        return view;
    }

}
