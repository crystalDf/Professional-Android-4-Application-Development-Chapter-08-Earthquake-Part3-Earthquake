package com.star.earthquake;


import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.widget.SimpleCursorAdapter;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class EarthquakeListFragment extends ListFragment {

    private static final int EARTHQUAKE_LOADER = 0;

    private SimpleCursorAdapter simpleCursorAdapter;

    private LoaderManager.LoaderCallbacks<Cursor> loaderCallbacks =
            new LoaderManager.LoaderCallbacks<Cursor>() {
        @Override
        public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
            String[] projection = new String[] {
                    EarthquakeProvider.KEY_ID,
                    EarthquakeProvider.KEY_SUMMARY
            };

            EarthquakeActivity earthquakeActivity = (EarthquakeActivity) getActivity();

            String selection = EarthquakeProvider.KEY_MAGNITUDE + " > " +
                    earthquakeActivity.getMinMag();

            CursorLoader cursorLoader = new CursorLoader(getActivity(),
                    EarthquakeProvider.CONTENT_URI, projection, selection, null, null);

            return cursorLoader;
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
            simpleCursorAdapter.swapCursor(cursor);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            simpleCursorAdapter.swapCursor(null);
        }
    };

    private static final String TAG = "EARTHQUAKE";
    private Handler handler = new Handler();

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        simpleCursorAdapter = new SimpleCursorAdapter(getActivity(),
                android.R.layout.simple_list_item_1, null,
                new String[] { EarthquakeProvider.KEY_SUMMARY },
                new int[] { android.R.id.text1 }, 0);

        setListAdapter(simpleCursorAdapter);

        getLoaderManager().initLoader(EARTHQUAKE_LOADER, null, loaderCallbacks);

        new Thread(new Runnable() {
            @Override
            public void run() {
                refreshEarthquakes();
            }
        }).start();

    }

    public void refreshEarthquakes() {

        handler.post(new Runnable() {
            @Override
            public void run() {
                getLoaderManager().restartLoader(EARTHQUAKE_LOADER, null, loaderCallbacks);
            }
        });

        URL url;

        try {
            url = new URL(getString(R.string.quake_feed));

            HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();

            int responseCode = httpURLConnection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = httpURLConnection.getInputStream();

                DocumentBuilderFactory documentBuilderFactory =
                        DocumentBuilderFactory.newInstance();

                DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

                Document document = documentBuilder.parse(inputStream);
                Element element = document.getDocumentElement();

                NodeList nodeList = element.getElementsByTagName("entry");
                if ((nodeList != null) && (nodeList.getLength() > 0)) {
                    for (int i = 0; i < nodeList.getLength(); i++) {
                        Element entry = (Element) nodeList.item(i);

                        Element title = (Element)
                                entry.getElementsByTagName("title").item(0);
                        Element point = (Element)
                                entry.getElementsByTagName("georss:point").item(0);
                        Element when = (Element)
                                entry.getElementsByTagName("updated").item(0);
                        Element link = (Element)
                                entry.getElementsByTagName("link").item(0);

                        if (entry != null && point != null && when != null && link != null) {
                            String details = title.getFirstChild().getNodeValue();

                            String linkString = link.getAttribute("href");

                            String pointString = point.getFirstChild().getNodeValue();

                            String whenString = when.getFirstChild().getNodeValue();

                            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
                                    "yyyy-MM-dd'T'hh:mm:ss'Z'"
                            );

                            Date quakeDate = new Date();

                            try {
                                quakeDate = simpleDateFormat.parse(whenString);
                            } catch (ParseException e) {
                                Log.d(TAG, "ParseException");
                            }

                            String[] points = pointString.split(" ");
                            Location location = new Location("dummyGPS");
                            location.setLatitude(Double.parseDouble(points[0]));
                            location.setLongitude(Double.parseDouble(points[0]));

                            String magnitudeString = details.split(" ")[1];
                            int end = magnitudeString.length() - 1;
                            double magnitude = Double.parseDouble(magnitudeString.substring(0, end));

                            details = details.split(",")[1].trim();

                            final Quake quake = new Quake(
                                    quakeDate, details, location, magnitude, linkString
                            );

                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    addNewQuake(quake);
                                }
                            });
                        }
                    }
                }
            }

        } catch (MalformedURLException e) {
            Log.d(TAG, "MalformedURLException");
        } catch (IOException e) {
            Log.d(TAG, "IOException");
        } catch (ParserConfigurationException e) {
            Log.d(TAG, "ParserConfigurationException");
        } catch (SAXException e) {
            Log.d(TAG, "SAXException");
        }

    }

    private void addNewQuake(Quake quake) {

        ContentResolver contentResolver = getActivity().getContentResolver();

        String selection = EarthquakeProvider.KEY_DATE + " = " + quake.getDate().getTime();

        Cursor cursor = contentResolver.query(EarthquakeProvider.CONTENT_URI, null, selection,
                null, null);

        if (cursor.getCount() == 0) {
            ContentValues contentValues = new ContentValues();

            contentValues.put(EarthquakeProvider.KEY_DATE, quake.getDate().getTime());
            contentValues.put(EarthquakeProvider.KEY_DETAILS, quake.getDetails());
            contentValues.put(EarthquakeProvider.KEY_SUMMARY, quake.toString());

            contentValues.put(EarthquakeProvider.KEY_LOCATION_LAT,
                    quake.getLocation().getLatitude());
            contentValues.put(EarthquakeProvider.KEY_LOCATION_LON,
                    quake.getLocation().getLongitude());

            contentValues.put(EarthquakeProvider.KEY_LINK, quake.getLink());
            contentValues.put(EarthquakeProvider.KEY_MAGNITUDE, quake.getMagnitude());

            contentResolver.insert(EarthquakeProvider.CONTENT_URI, contentValues);
        }

        cursor.close();
    }
}
