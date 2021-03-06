/*
	Copyright 2010

	Author: Google Inc.
	Author: Ilias Bartolini

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at
 	
 		http://www.apache.org/licenses/LICENSE-2.0
 
	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
 */

package it.agileday.ui.sessions;

import it.agileday2011.R;
import it.agileday.data.DatabaseHelper;
import it.agileday.data.Session;
import it.agileday.data.Track;
import it.agileday.data.TrackRepository;
import it.agileday.utils.Dates;

import java.util.ArrayList;

import com.google.android.apps.iosched.ui.widget.BlockView;
import com.google.android.apps.iosched.ui.widget.BlocksLayout;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ScrollView;

public class ScheduleActivity extends Activity implements View.OnClickListener {
	private static final String TAG = ScheduleActivity.class.getName();

	private static final int DISABLED_BLOCK_ALPHA = 160;

	private ScrollView mScrollView;
	private BlocksLayout mBlocks;
	private View mNowView;

	private long mTimeStart;
	private long mTimeEnd;

	private BroadcastReceiver mTimeChangesReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d(TAG, "onReceive time update");
			updateNowView(false);
		}
	};

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.schedule);

		mTimeStart = Dates.newDate(2011, 11, 19, 8).getTime();
		mTimeEnd = Dates.newDate(2011, 11, 19, 19).getTime();

		mScrollView = (ScrollView) findViewById(R.id.blocks_scroll);
		mBlocks = (BlocksLayout) findViewById(R.id.blocks);
		mNowView = findViewById(R.id.blocks_now);

		mBlocks.setDrawingCacheEnabled(true);
		mBlocks.setAlwaysDrawnWithCacheEnabled(true);

		fillData();
	}

	@Override
	protected void onResume() {
		super.onResume();

		// Since we build our views manually instead of using an adapter, we
		// need to manually requery every time launched.
		fillData();

		// Start listening for time updates to adjust "now" bar. TIME_TICK is
		// triggered once per minute, which is how we move the bar over time.
		final IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_TIME_TICK);
		filter.addAction(Intent.ACTION_TIME_CHANGED);
		filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
		registerReceiver(mTimeChangesReceiver, filter, null, new Handler());

		mNowView.post(new Runnable() {
			public void run() {
				updateNowView(false);
			}
		});
	}

	@Override
	protected void onPause() {
		super.onPause();
		unregisterReceiver(mTimeChangesReceiver);
	}

	private void updateNowView(boolean forceScroll) {
		final long now = System.currentTimeMillis();

		final boolean visible = now >= mTimeStart && now <= mTimeEnd;
		mNowView.setVisibility(visible ? View.VISIBLE : View.GONE);

		if (visible && forceScroll) {
			// Scroll to show "now" in center
			final int offset = mScrollView.getHeight() / 2;
			mNowView.requestRectangleOnScreen(new Rect(0, offset, 0, offset), true);
		}

		mBlocks.requestLayout();
	}

	private void fillData() {
		SQLiteDatabase database = new DatabaseHelper(this).getReadableDatabase();
		try {
			ArrayList<Track> tracks = new TrackRepository(database, this).getAll();
			int trackIndex = 0;
			mBlocks.removeAllBlocks();

			for (Track track : tracks) {
				if (!track.isValid()) {
					throw new RuntimeException(track.validationMessage());
				}

				for (Session session : track.getSessions()) {
					final BlockView blockView = buildBlockView(trackIndex, session);
					mBlocks.addBlock(blockView);
				}
				trackIndex++;
			}

		} finally {
			database.close();
		}
	}

	private BlockView buildBlockView(int trackIndex, Session session) {
		final Integer column = trackIndex;
		final long blockId = session.getId();
		final String title = session.title + ((!session.speakers.equals("")) ? " - " + session.speakers : "");
		final long start = session.getStart().getTime();
		final long end = session.getEnd().getTime();
		final boolean isStarred = session.IsStarred();

		int textColor = Color.WHITE;
		int buttonColor = -1;
		switch (column) {
		case 0:
			buttonColor = getResources().getColor(R.color.schedule_block_color_green);
			break;
		case 1:
			buttonColor = getResources().getColor(R.color.schedule_block_color_orange);
			break;
		case 2:
			buttonColor = getResources().getColor(R.color.schedule_block_color_blue);
			break;
		case 3:
			buttonColor = getResources().getColor(R.color.schedule_block_color_red);
			break;
		case 4:
			buttonColor = getResources().getColor(R.color.schedule_block_color_yellow);
			break;
		default:
			buttonColor = getResources().getColor(R.color.schedule_block_color_gray);
			break;
		}

		final BlockView blockView = new BlockView(this, blockId, title, start, end, isStarred, column, buttonColor, textColor);

		if (!session.type.equals("extra")) {
			blockView.setOnClickListener(this);
		} else {
			blockView.setFocusable(false);
			blockView.setEnabled(false);
			LayerDrawable buttonDrawable = (LayerDrawable) blockView.getBackground();
			buttonDrawable.getDrawable(0).setAlpha(DISABLED_BLOCK_ALPHA);
			buttonDrawable.getDrawable(2).setAlpha(DISABLED_BLOCK_ALPHA);
		}
		return blockView;
	}

	@Override
	public void onClick(View v) {
		if (v instanceof BlockView) {
			final long blockId = ((BlockView) v).getBlockId();
			Intent intent = new Intent(this, SessionActivity.class);
			intent.putExtra(SessionActivity.INTENT_EXTRA_KEY_SESSION_ID, blockId);
			startActivity(intent);
		}
	}
}
