package com.wikonos.models;

import com.wikonos.fingerprint.R;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class SimpleListAdapter extends BaseAdapter {

	private Context mCtx;
	private ArrayList<?> mData;

	public SimpleListAdapter(Context ctx) {
		mCtx = ctx;
	}

	@Override
	public int getCount() {
		return mData == null ? 0 : mData.size();
	}

	@Override
	public Object getItem(int position) {
		return mData.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View result = convertView;
		if (result == null) {
			result = LayoutInflater.from(mCtx).inflate(R.layout.simple_cell, null);
		}
		TextView tv = (TextView) result.findViewById(R.id.tSimpleCellText);
		tv.setText(mData.get(position).toString());
		return result;
	}

	public void setData(ArrayList<?> data) {
		mData = data;
		notifyDataSetInvalidated();
	}

}
