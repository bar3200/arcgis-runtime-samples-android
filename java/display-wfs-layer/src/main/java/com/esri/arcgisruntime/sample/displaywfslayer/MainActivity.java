/*
 *  Copyright 2019 Esri
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.esri.arcgisruntime.sample.displaywfslayer;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.data.Feature;
import com.esri.arcgisruntime.data.FeatureQueryResult;
import com.esri.arcgisruntime.data.QueryParameters;
import com.esri.arcgisruntime.data.ServiceFeatureTable;
import com.esri.arcgisruntime.geometry.Envelope;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.layers.FeatureLayer;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.Viewpoint;
import com.esri.arcgisruntime.mapping.view.Callout;
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.ogc.wfs.WfsFeatureTable;
import com.esri.arcgisruntime.symbology.SimpleLineSymbol;
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol;
import com.esri.arcgisruntime.symbology.SimpleRenderer;

import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

  private MapView mMapView;
  private Callout mCallout;

  @SuppressLint("ClickableViewAccessibility")
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    mMapView = findViewById(R.id.mapView);

    // create a map with topographic basemap and set it to the map view
    ArcGISMap map = new ArcGISMap(Basemap.Type.TOPOGRAPHIC,32.083549, 34.815498,14);
    mMapView.setMap(map);

    // create an initial extent to load
    Point topLeft = new Point(-13618106.950944, 6042391.201455);
    Point bottomRight = new Point(-13617513.444292, 6041961.243171);
    Envelope initialExtent = new Envelope(topLeft, bottomRight);
    //mMapView.setViewpoint(new Viewpoint(initialExtent));

    // create a FeatureTable from the WFS service URL and name of the layer
    WfsFeatureTable wfsFeatureTable = new WfsFeatureTable("https://geodb.azurewebsites.net/geoserver/ows?service=wfs&request=getcapabilities"
            ,"postGIS:RG_D");

    // set the feature request mode to manual in this mode, you must manually populate the table - panning and zooming
    // won't request features automatically
    wfsFeatureTable.setFeatureRequestMode(ServiceFeatureTable.FeatureRequestMode.MANUAL_CACHE);

    // create a feature layer to visualize the WFS features
    FeatureLayer wfsFeatureLayer = new FeatureLayer(wfsFeatureTable);

    // apply a renderer to the feature layer
    SimpleRenderer renderer = new SimpleRenderer(new SimpleMarkerSymbol(SimpleMarkerSymbol.Style.CIRCLE, Color.RED, 15));
    wfsFeatureLayer.setRenderer(renderer);

    // add the layer to the map's operational layers
    map.getOperationalLayers().add(wfsFeatureLayer);

    // make an initial call to load the initial extent's data from the WFS, using the WFS spatial reference
    populateFromServer(wfsFeatureTable, initialExtent);

    // use the navigation completed event to populate the table with the features needed for the current extent
    mMapView.addNavigationChangedListener(navigationChangedEvent -> {
      // once the map view has stopped navigating
      if (!navigationChangedEvent.isNavigating()) {
        populateFromServer(wfsFeatureTable, mMapView.getVisibleArea().getExtent());
      }
    });
    mCallout = mMapView.getCallout();
    mMapView.setOnTouchListener(new DefaultMapViewOnTouchListener(this, mMapView) {
      @Override
      public boolean onSingleTapConfirmed(MotionEvent e) {
        // remove any existing callouts
        if (mCallout.isShowing()) {
          mCallout.dismiss();
        }
        // get the point that was clicked and convert it to a point in map coordinates
        final Point clickPoint = mMapView
                .screenToLocation(new android.graphics.Point(Math.round(e.getX()), Math.round(e.getY())));
        // create a selection tolerance
        int tolerance = 10;
        double mapTolerance = tolerance * mMapView.getUnitsPerDensityIndependentPixel();
        // use tolerance to create an envelope to query
        Envelope envelope = new Envelope(clickPoint.getX() - mapTolerance, clickPoint.getY() - mapTolerance,
                clickPoint.getX() + mapTolerance, clickPoint.getY() + mapTolerance, map.getSpatialReference());
        QueryParameters query = new QueryParameters();
        query.setGeometry(envelope);
        ProgressDialog dialog = ProgressDialog.show(mMapView.getContext(), "",
                "Loading. Please wait...", true);
        // request all available attribute fields
        final ListenableFuture<FeatureQueryResult> future = wfsFeatureTable.queryFeaturesAsync(query);
        // add done loading listener to fire when the selection returns
        future.addDoneListener(new Runnable() {
          @Override
          public void run() {
            try {
              //call get on the future to get the result
              dialog.hide();
              FeatureQueryResult result = future.get();
              // create an Iterator
              Iterator<Feature> iterator = result.iterator();
              // create a TextView to display field values
              final LayoutInflater factory = getLayoutInflater();
              final View entry = factory.inflate(R.layout.feature_layout, null);
              TextView featureTitle = entry.findViewById(R.id.feature_distress);
              ImageView featureImage = entry.findViewById(R.id.feature_image);
              Feature feature;
              while (iterator.hasNext()) {
                feature = iterator.next();
                // create a Map of all available attributes as name value pairs
                Map<String, Object> attr = feature.getAttributes();
                Set<String> keys = attr.keySet();
                Object value = attr.get("distress");
                Object photo = attr.get("imageurl");
                featureTitle.setText(value.toString());
                  Glide.with(mMapView.getContext()).load(photo.toString()).into(featureImage);
                // center the mapview on selected feature
                Envelope envelope = feature.getGeometry().getExtent();
                mMapView.setViewpointGeometryAsync(envelope, 200);
                // show CallOut
                mCallout.setLocation(clickPoint);
                mCallout.setContent(entry.findViewById(R.id.feature_layout));
                mCallout.show();
              }
            } catch (Exception e) {
              Log.e(getResources().getString(R.string.app_name), "Select feature failed: " + e.getMessage());
            }
          }
        });
        return super.onSingleTapConfirmed(e);
      }
    });
  }

  /**
   * Create query parameters using the given extent to populate the WFS table from service.
   *
   * @param wfsFeatureTable the WFS feature table to populate
   * @param extent          the extent used to define the QueryParameters' geometry
   */
  private void populateFromServer(WfsFeatureTable wfsFeatureTable, Envelope extent) {
    // create a query based on the current visible extent
    QueryParameters visibleExtentQuery = new QueryParameters();
    visibleExtentQuery.setGeometry(extent);
    visibleExtentQuery.setSpatialRelationship(QueryParameters.SpatialRelationship.INTERSECTS);
    // populate the WFS feature table based on the current extent
    wfsFeatureTable.populateFromServiceAsync(visibleExtentQuery, false, null);
  }

  @Override
  protected void onPause() {
    mMapView.pause();
    super.onPause();
  }

  @Override
  protected void onResume() {
    super.onResume();
    mMapView.resume();
  }

  @Override protected void onDestroy() {
    mMapView.dispose();
    super.onDestroy();
  }
}
