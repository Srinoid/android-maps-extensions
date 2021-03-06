/*
 * Copyright (C) 2013 Maciej Górski
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pl.mg6.android.maps.extensions.demo;

import java.text.Collator;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import pl.mg6.android.maps.extensions.Circle;
import pl.mg6.android.maps.extensions.CircleOptions;
import pl.mg6.android.maps.extensions.ClusteringSettings;
import pl.mg6.android.maps.extensions.GoogleMap;
import pl.mg6.android.maps.extensions.GoogleMap.InfoWindowAdapter;
import pl.mg6.android.maps.extensions.GoogleMap.OnInfoWindowClickListener;
import pl.mg6.android.maps.extensions.GoogleMap.OnMapClickListener;
import pl.mg6.android.maps.extensions.Marker;
import pl.mg6.android.maps.extensions.MarkerOptions;
import pl.mg6.android.maps.extensions.SupportMapFragment;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.LatLngBounds.Builder;

public class DemoActivity extends FragmentActivity {

	private static final double[] CLUSTER_SIZES = new double[] { 180, 160, 144, 120, 96 };

	private GoogleMap map;

	private MutableData[] dataArray = { new MutableData(6, new LatLng(-50, 0)), new MutableData(28, new LatLng(-52, 1)),
			new MutableData(496, new LatLng(-51, -2)), };
	private Handler handler = new Handler();
	private Runnable dataUpdater = new Runnable() {

		@Override
		public void run() {
			for (MutableData data : dataArray) {
				data.value = 7 + 3 * data.value;
			}
			onDataUpdate();
			handler.postDelayed(this, 1000);
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.demo);

		FragmentManager fm = getSupportFragmentManager();
		SupportMapFragment f = (SupportMapFragment) fm.findFragmentById(R.id.map);
		map = f.getExtendedMap();

		addCircles();

		map.setOnMapClickListener(new OnMapClickListener() {

			@Override
			public void onMapClick(LatLng position) {
				for (Circle circle : map.getCircles()) {
					if (circle.contains(position)) {
						Toast.makeText(DemoActivity.this, "Clicked " + circle.getData(), Toast.LENGTH_SHORT).show();
						return;
					}
				}
			}
		});

		map.setClustering(new ClusteringSettings().clusterOptionsProvider(new DemoClusterOptionsProvider(getResources())).addMarkersDynamically(true));

		map.setInfoWindowAdapter(new InfoWindowAdapter() {

			private TextView tv;
			{
				tv = new TextView(DemoActivity.this);
				tv.setTextColor(Color.BLACK);
			}

			private Collator collator = Collator.getInstance();
			private Comparator<Marker> comparator = new Comparator<Marker>() {
				public int compare(Marker lhs, Marker rhs) {
					String leftTitle = lhs.getTitle();
					String rightTitle = rhs.getTitle();
					if (leftTitle == null && rightTitle == null) {
						return 0;
					}
					if (leftTitle == null) {
						return 1;
					}
					if (rightTitle == null) {
						return -1;
					}
					return collator.compare(leftTitle, rightTitle);
				}
			};

			@Override
			public View getInfoWindow(Marker marker) {
				return null;
			}

			@Override
			public View getInfoContents(Marker marker) {
				if (marker.isCluster()) {
					List<Marker> markers = marker.getMarkers();
					int i = 0;
					String text = "";
					while (i < 3 && markers.size() > 0) {
						Marker m = Collections.min(markers, comparator);
						String title = m.getTitle();
						if (title == null) {
							break;
						}
						text += title + "\n";
						markers.remove(m);
						i++;
					}
					if (text.length() == 0) {
						text = "Markers with mutable data";
					} else if (markers.size() > 0) {
						text += "and " + markers.size() + " more...";
					} else {
						text = text.substring(0, text.length() - 1);
					}
					tv.setText(text);
					return tv;
				} else {
					Object data = marker.getData();
					if (data instanceof MutableData) {
						MutableData mutableData = (MutableData) data;
						tv.setText("Value: " + mutableData.value);
						return tv;
					}
				}

				return null;
			}
		});

		map.setOnInfoWindowClickListener(new OnInfoWindowClickListener() {

			@Override
			public void onInfoWindowClick(Marker marker) {
				if (marker.isCluster()) {
					List<Marker> markers = marker.getMarkers();
					Builder builder = LatLngBounds.builder();
					for (Marker m : markers) {
						builder.include(m.getPosition());
					}
					LatLngBounds bounds = builder.build();
					map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, getResources().getDimensionPixelSize(R.dimen.padding)));
				}
			}
		});

		MarkerGenerator.addMarkersInPoland(map);
		MarkerGenerator.addMarkersInWorld(map);

		BitmapDescriptor icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE);
		for (MutableData data : dataArray) {
			map.addMarker(new MarkerOptions().position(data.position).icon(icon).data(data));
		}

		setUpClusteringViews();
	}

	@Override
	protected void onResume() {
		super.onResume();
		handler.post(dataUpdater);
	}

	@Override
	protected void onPause() {
		super.onPause();
		handler.removeCallbacks(dataUpdater);
	}

	private void onDataUpdate() {
		Marker m = map.getMarkerShowingInfoWindow();
		if (m != null && !m.isCluster() && m.getData() instanceof MutableData) {
			m.showInfoWindow();
		}
	}

	private void addCircles() {
		float strokeWidth = getResources().getDimension(R.dimen.circle_stroke_width);
		CircleOptions options = new CircleOptions().strokeWidth(strokeWidth);
		map.addCircle(options.center(new LatLng(0.0, 0.0)).data("first circle").radius(2000000));
		map.addCircle(options.center(new LatLng(30.0, 30.0)).data("second circle").radius(1000000));
	}

	private void setUpClusteringViews() {
		CheckBox clusterCheckbox = (CheckBox) findViewById(R.id.checkbox_cluster);
		final SeekBar clusterSizeSeekbar = (SeekBar) findViewById(R.id.seekbar_cluster_size);
		clusterCheckbox.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				clusterSizeSeekbar.setEnabled(isChecked);

				updateClustering(clusterSizeSeekbar.getProgress(), isChecked);
			}
		});
		clusterSizeSeekbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				updateClustering(progress, true);
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}
		});
	}

	void updateClustering(int clusterSizeIndex, boolean enabled) {
		ClusteringSettings clusteringSettings = new ClusteringSettings();
		clusteringSettings.addMarkersDynamically(true);

		if (enabled) {
			clusteringSettings.clusterOptionsProvider(new DemoClusterOptionsProvider(getResources()));

			double clusterSize = CLUSTER_SIZES[clusterSizeIndex];
			clusteringSettings.clusterSize(clusterSize);
		} else {
			clusteringSettings.enabled(false);
		}
		map.setClustering(clusteringSettings);
	}

	private static class MutableData {

		private int value;

		private LatLng position;

		public MutableData(int value, LatLng position) {
			this.value = value;
			this.position = position;
		}
	}
}
