/*
 * $Id: TrackControl.java,v 1.69 2007/08/21 11:32:08 jeffmc Exp $
 *
 * Copyright 1997-2004 Unidata Program Center/University Corporation for
 * Atmospheric Research, P.O. Box 3000, Boulder, CO 80307,
 * support@unidata.ucar.edu.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */



package ucar.unidata.idv.control.storm;


import ucar.unidata.ui.drawing.*;
import ucar.unidata.ui.symbol.*;

import ucar.visad.*;
import ucar.visad.display.*;
import ucar.unidata.data.point.PointOb;
import ucar.unidata.data.point.PointObFactory;

import  ucar.unidata.ui.colortable.ColorTableDefaults;
import ucar.unidata.data.DataChoice;
import ucar.unidata.data.DataUtil;
import ucar.unidata.data.storm.*;

import ucar.unidata.idv.ControlContext;
import ucar.unidata.idv.DisplayConventions;
import ucar.unidata.idv.control.DisplayControlImpl;

import ucar.unidata.ui.TreePanel;
import ucar.unidata.util.ColorTable;

import ucar.unidata.util.DateUtil;
import ucar.unidata.util.GuiUtils;

import ucar.unidata.util.LogUtil;
import ucar.unidata.util.Misc;
import ucar.unidata.util.Range;
import ucar.unidata.util.TwoFacedObject;

import ucar.visad.Util;


import ucar.visad.display.*;
import ucar.visad.display.Animation;
import ucar.visad.display.DisplayableData;
import ucar.visad.display.SelectRangeDisplayable;
import ucar.visad.display.SelectorPoint;
import ucar.visad.display.StationModelDisplayable;
import ucar.visad.display.TrackDisplayable;



import visad.*;
import visad.bom.Radar2DCoordinateSystem;

import visad.georef.EarthLocation;

import visad.georef.EarthLocationLite;

import visad.georef.EarthLocationTuple;
import visad.georef.LatLonPoint;
import visad.georef.LatLonTuple;

import visad.util.DataUtility;

import java.awt.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.*;

import java.beans.*;

import java.rmi.RemoteException;

import java.util.ArrayList;


import java.util.Arrays;


import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Hashtable;
import java.util.List;

import javax.swing.*;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.*;



/**
 *
 * @author Unidata Development Team
 * @version $Revision: 1.69 $
 */

public class StormDisplayState {

    /** _more_          */
    private Object MUTEX = new Object();

    /** _more_ */
    private static final Data DUMMY_DATA = new Real(0);


    /** _more_ */
    private CompositeDisplayable forecastHolder;


    private CompositeDisplayable holder;

    private StationModelDisplayable indicator;


    /** _more_          */
    private StormInfo stormInfo;


    /** _more_          */
    private boolean forecastVisible = false;


    private boolean forecastRingsVisible = false;

    /** _more_          */
    private boolean changed = false;


    /** _more_          */
    private boolean active = false;


    /** _more_ */
    private StormTrackCollection trackCollection;

    /** _more_ */
    //    private List<StormTrack> tracks;

    /** _more_ */
    private JTable trackTable;

    /** _more_ */
    private AbstractTableModel trackModel;

    /** _more_          */
    private StormTrackControl stormTrackControl;


    private WayDisplayState obsDisplayState;            


    /** time holder */
    private DisplayableData timesHolder = null;

    /** _more_          */
    private JComponent contents;

    private JComponent originalContents;


    
    private Hashtable<Way,WayDisplayState> wayDisplayStateMap = new Hashtable<Way,WayDisplayState>();



    /**
     * _more_
     */
    public StormDisplayState() {}

    /**
     * _more_
     *
     * @param stormInfo _more_
     */
    public StormDisplayState(StormInfo stormInfo) {
        this.stormInfo = stormInfo;
    }

    /**
     * _more_
     *
     * @return _more_
     */
    public JComponent getContents() {
        if (contents == null) {
            contents = doMakeContents();
        }
        return contents;
    }

    private List<WayDisplayState> getWayDisplayStates() {
        return  (List<WayDisplayState>)Misc.toList(wayDisplayStateMap.elements());
    }

    private WayDisplayState getWayDisplayState(Way way) {
        WayDisplayState wayState = wayDisplayStateMap.get(way);
        if(wayState == null) {
            wayDisplayStateMap.put(way,wayState = new WayDisplayState(this, way));
        }
        if(wayState.getColor() == null) {
            wayState.setColor(DisplayConventions.getColor());
        }
        return wayState;
    }


    public void deactivate() {
        try {
            trackCollection = null;
            active = false;
            stormTrackControl.removeDisplayable(holder);
            holder = null;
            forecastHolder = null;
            contents.removeAll();
            contents.add(BorderLayout.NORTH, originalContents);
            List<WayDisplayState> wayDisplayStates  = getWayDisplayStates();
            for(WayDisplayState wayDisplayState: wayDisplayStates) {
                wayDisplayState.deactivate();
            }
            contents.repaint(1);
            stormTrackControl.stormChanged(
                                           StormDisplayState.this);

        } catch(Exception exc) {
            stormTrackControl.logException("Deactivating storm", exc);
        }
    }


    /**
     * _more_
     *
     * @return _more_
     */
    private JComponent doMakeContents() {
        JButton loadBtn = new JButton("Load Tracks:");
        JLabel  topLabel   = GuiUtils.cLabel("  " + stormInfo);
        loadBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                active = true;
                showStorm();
            }
        });

        JComponent top      = GuiUtils.hbox(loadBtn, topLabel);
        originalContents = GuiUtils.inset(top, 5);
        JComponent contents = GuiUtils.top(originalContents);
        contents = new JPanel(new BorderLayout()) {
                public String toString() { return "storm:" + xxx();}
                public boolean equals(Object o) {
                    boolean result = super.equals(o);
                    if(result) {
                        //                        System.err.println (this + " == " + o);
                    }
                    return result;
                }
            };

        contents.add(BorderLayout.NORTH, originalContents);
        return contents;
    }

    public String xxx() {
        return stormInfo.toString();
    }


    /**
     * _more_
     */
    public void initDone() {
        if (getActive()) {
            showStorm();
        }

    }



    /**
     * _more_
     */
    private void initCenterContents() {
        contents.removeAll();
        JButton unloadBtn = GuiUtils.makeImageButton("/auxdata/ui/icons/Cut16.gif",this,"deactivate");
        unloadBtn.setToolTipText("Remove this storm");

        JComponent top = GuiUtils.inset(GuiUtils.leftRight(GuiUtils.lLabel("Storm: " + stormInfo),unloadBtn), new Insets(0,0,5,0));

        final JCheckBox showForecastCbx =
            new JCheckBox("Visible",getForecastVisible());
        showForecastCbx.setToolTipText("Show Forecast Tracks");
        showForecastCbx.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                setForecastVisible(showForecastCbx.isSelected());
                showStorm();
            }
        });
        final JCheckBox showForecastRingsCbx =
            new JCheckBox("Show Forecast Rings", getForecastVisible());
        showForecastRingsCbx.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                setForecastRingsVisible(showForecastRingsCbx.isSelected());
                showStorm();
            }
        });


        List components = new ArrayList();
        List<Way>  ways    = trackCollection.getWayList();
        boolean haveDoneForecast = false;
        for (Way way : ways) {
            WayDisplayState wayDisplayState = getWayDisplayState(way);
            JComponent swatch  =GuiUtils.filler(10,10);
            swatch.setBackground(wayDisplayState.getColor());
            JLabel wayLabel = new JLabel(way.toString());
            JComponent buttons = GuiUtils.hbox(
                                              wayDisplayState.getVisiblityCheckBox(),
                                              wayDisplayState.getRingsVisiblityCheckBox());
            buttons = GuiUtils.left(buttons);
            if (way.isObservation()) {
                components.add(0, wayLabel);
                components.add(1,buttons);
                components.add(2,GuiUtils.left(GuiUtils.wrap(swatch)));

            } else {
                if(!haveDoneForecast) {
                    haveDoneForecast = true;
                    //                    components.add(GuiUtils.hbox(showForecastCbx,new JLabel("Forecasts")));
                    components.add(GuiUtils.filler(2,5));
                    components.add(GuiUtils.filler(2,5));
                    components.add(GuiUtils.filler(2,5));

                    components.add(GuiUtils.lLabel("Forecasts:"));
                    components.add(GuiUtils.left(showForecastCbx));
                    components.add(GuiUtils.filler());
                }
                components.add(wayLabel);
                components.add(buttons);
                components.add(GuiUtils.left(GuiUtils.wrap(swatch)));
            }
        }

        GuiUtils.tmpInsets = new Insets(2,2,0,2);
        JComponent wayComp = GuiUtils.top(GuiUtils.doLayout(components,3,GuiUtils.WT_NNY,GuiUtils.WT_N));
        //Put the list of ways into a scroller if there are lots of them
        if(ways.size()>10) {
        int width  = 300;
        int height = 400;
        JScrollPane scroller = GuiUtils.makeScrollPane(wayComp, width,
                                   height);
        scroller.setBorder(BorderFactory.createLoweredBevelBorder());
        scroller.setPreferredSize(new Dimension(width, height));
        scroller.setMinimumSize(new Dimension(width, height));
        wayComp = scroller;
        }

        JComponent inner = GuiUtils.topCenter(top,
                                              GuiUtils.inset(wayComp, new Insets(0,5,0,0)));

        contents.add(BorderLayout.CENTER, GuiUtils.inset(inner, 5));
        contents.invalidate();
        contents.validate();
        contents.repaint();
    }

    /**
     * _more_
     *
     * @param way _more_
     *
     * @return _more_
     */
    protected boolean canShowWay(Way way) {
        return getWayDisplayState(way).getVisible();
    }

    /**
     * _more_
     *
     * @param stormTrackControl _more_
     */
    protected void setStormTrackControl(StormTrackControl stormTrackControl) {
        this.stormTrackControl = stormTrackControl;
    }

    /**
     * _more_
     */
    protected void showStorm() {
        Misc.run(new Runnable() {
            public void run() {
                try {
                    synchronized (MUTEX) {
                        showStormInner();
                        stormTrackControl.stormChanged(
                            StormDisplayState.this);
                    }
                } catch (Exception exc) {
                    stormTrackControl.logException("Showing storm", exc);
                }

            }
        });
    }


    /**
     * _more_
     *
     *
     * @throws Exception _more_
     */
    private void showStormInner() throws Exception {
        if (trackCollection == null) {
            contents.removeAll();
            contents.add(
                GuiUtils.top(
                    GuiUtils.inset(new JLabel("Loading Tracks..."), 5)));
            contents.invalidate();
            contents.validate();
            contents.repaint();
            trackCollection =
                stormTrackControl.getStormDataSource().getTrackCollection(
                    stormInfo);
            //            tracks = trackCollection.getTracks();
            initCenterContents();
            holder = new CompositeDisplayable();
            stormTrackControl.addDisplayable(holder);
        }

        DisplayMaster displayMaster = stormTrackControl.getDisplayMaster(holder);
        boolean wasActive = displayMaster.ensureInactive();

        obsDisplayState = getWayDisplayState(Way.OBSERVATION);

        long t1 = System.currentTimeMillis();
        if (obsDisplayState.getTracks().size()==0) {
            StormTrack obsTrack = trackCollection.getObsTrack();
            if(obsTrack!=null) {
                FieldImpl field = makeField(obsTrack, false);
                obsDisplayState.addTrack(obsTrack,  field);

                TrackDisplayable trackDisplay = new TrackDisplayable("track_"
                                                                     + stormInfo.getStormId());
                obsDisplayState.addDisplayable(trackDisplay);

                trackDisplay.setLineWidth(3);
                trackDisplay.setTrack(field);
                holder.addDisplayable(trackDisplay);

                makeRingField(obsTrack, obsDisplayState, holder, StormDataSource.ATTR_MODERATEGALE);

                indicator = new StationModelDisplayable("indicator");
                indicator.setShouldUseAltitude(false);
                holder.addDisplayable(indicator);


                timesHolder = new LineDrawing("track_time" +  stormInfo.getStormId());
                timesHolder.setManipulable(false);
                timesHolder.setVisible(false);
                List times    = obsTrack.getTrackTimes();
                timesHolder.setData(ucar.visad.Util.makeTimeSet(times));
                holder.addDisplayable(timesHolder);


                StationModelDisplayable dots  = new StationModelDisplayable("dots");
                obsDisplayState.addDisplayable(dots);
                StationModel model = new StationModel("TrackLocation");
                ShapeSymbol shapeSymbol = new ShapeSymbol(0, 0);
                shapeSymbol.setShape(ucar.visad.ShapeUtility.FILLED_CIRCLE);
                shapeSymbol.setScale(0.8f);
                shapeSymbol.bounds = new java.awt.Rectangle(-15, -15, 30, 30);
                shapeSymbol.setRectPoint(Glyph.PT_MM);
                model.addSymbol(shapeSymbol);

                dots.setScale(1.0f);
                holder.addDisplayable(dots);
                dots.setStationModel(model);
                //                dots.setStationData(PointObFactory.makeTimeSequenceOfPointObs( obsDisplayState.getPointObs(),
                //                                                                               24*60,-1));
                dots.setStationData(PointObFactory.makeTimeSequenceOfPointObs( obsDisplayState.getPointObs(),
                                                                               -1,-1));

            }

        }



        //Don't load them until we need to
        if(getForecastVisible() || forecastHolder !=null) {
        List<WayDisplayState> wayDisplayStates  = getWayDisplayStates();
        if (forecastHolder != null) {
            forecastHolder.setVisible(getForecastVisible());
            for(WayDisplayState wayDisplayState: wayDisplayStates) {
                wayDisplayState.setVisible(wayDisplayState.getVisible());
            }
        } else {
            forecastHolder = new CompositeDisplayable();
            holder.addDisplayable(forecastHolder);
            forecastHolder.setVisible(true);
            for (StormTrack track : trackCollection.getTracks()) {
                if (track.isObservation()) {
                    continue;
                }
                //                if (!(track.getWay().getId().equals("SHTM") ||
                //                      track.getWay().getId().equals("jawt"))) continue;
                WayDisplayState wayDisplayState = getWayDisplayState(track.getWay());
                FieldImpl field = makeField(track, true);
                wayDisplayState.addTrack(track,  field);


            }

            for(WayDisplayState wayDisplayState: wayDisplayStates) {
                if (wayDisplayState.getWay().isObservation()) {
                    continue;
                }

                List fields = wayDisplayState.getFields();
                //                if (!(wayDisplayState.getWay().getId().equals("SHTM"))) continue;
                //                System.err.println (wayDisplayState.getWay() +" fields=" +fields.size());
                if(fields.size() == 0) continue;

                TrackDisplayable trackDisplay = new TrackDisplayable("track ");
                trackDisplay.setColorPalette(getColor(wayDisplayState.getColor()));
                trackDisplay.setUseTimesInAnimation(false);
                wayDisplayState.addDisplayable(trackDisplay);
                if(!wayDisplayState.getVisible()) {
                    trackDisplay.setVisible(false);
                }

                /*
                StationModelDisplayable dots  = new StationModelDisplayable("dots");
                wayDisplayState.addDisplayable(dots);
                StationModel model = new StationModel("TrackLocation");
                TextSymbol textSymbol = new TextSymbol("label","the label");
                textSymbol.setScale(1.5f);
                textSymbol.setRectPoint(Glyph.PT_UL);
                textSymbol.bounds = new java.awt.Rectangle(10,0,21,15);
                model.addSymbol(textSymbol);

                ShapeSymbol shapeSymbol = new ShapeSymbol(0, 0);
                shapeSymbol.setScale(0.5f);
                shapeSymbol.setShape(ucar.visad.ShapeUtility.CIRCLE);
                shapeSymbol.bounds = new java.awt.Rectangle(-15, -15, 30, 30);
                shapeSymbol.setRectPoint(Glyph.PT_MM);
                model.addSymbol(shapeSymbol);
                forecastHolder.addDisplayable(dots);
                dots.setScale(1.0f);
                dots.setStationModel(model);
                dots.setStationData(PointObFactory.makeTimeSequenceOfPointObs( wayDisplayState.getPointObs(),
                                                                               -1,-1));

                */
                forecastHolder.addDisplayable(trackDisplay);
                FieldImpl timeField = ucar.visad.Util.makeTimeField(fields,wayDisplayState.getTimes());
                trackDisplay.setTrack(timeField);
                List<StormTrack>  tracks = wayDisplayState.getTracks() ;
                for(StormTrack strack: tracks){
                      makeRingField(strack, wayDisplayState, forecastHolder,StormDataSource.ATTR_PROBABILITYRADIUS);
                }
            }
        }
        }

        if(wasActive)  displayMaster.setActive(true);
        long t2 = System.currentTimeMillis();
        System.err.println ("time:" + (t2-t1));


    }



        //        ucar.visad.Util.makeTimeField(List<Data> ranges, List times)


    private float[][]getColor(Color c) {
        if(c==null) c = Color.red;
        return ColorTableDefaults.allOneColor(c,true);
    }


    /** _more_          */
    private int cnt = 0;

    /**
     * _more_
     *
     * @param track _more_
     * @param fixedValue _more_
     *
     * @return _more_
     *
     * @throws Exception _more_
     */
    private FieldImpl makeField(StormTrack track, boolean fixedValue)
            throws Exception {


        List<DateTime>       times    = track.getTrackTimes();
        List<EarthLocation> locations     = track.getLocations();
        int        numPoints       = times.size();
        Unit                timeUnit = ((DateTime)times.get(0)).getUnit();

        RealType dfltRealType = RealType.getRealType("Default_" + (cnt++));
        Real                dfltReal = new Real(dfltRealType, 1);

        RealType timeType =
            RealType.getRealType(DataUtil.cleanName("track_time" + cnt + "_"
                + timeUnit), timeUnit);
        RealTupleType rangeType =
            new RealTupleType(RealType.getRealType("trackrange_" + cnt,
                dfltReal.getUnit()), timeType);
        double[][] newRangeVals = new double[2][numPoints];
        float[]    lats         = new float[numPoints];
        float[]    lons         = new float[numPoints];
        float[]    attrValue = null;
        //        if(!fixedValue) {
        //           attrValue = track.getTrackAttributeValues("MaxWindSpeed");
        attrValue = track.getTrackAttributeValues(StormDataSource.ATTR_WINDSPEED);
        //        }
        //            System.err.println("got category:" + (attrValue!=null));
        //        System.err.println("points:" + times + "\n" + locs);
        for (int i = 0; i < numPoints; i++) {
            DateTime      dateTime = (DateTime) times.get(i);
            Real          value    = (fixedValue
                                      ? dfltReal
                                      : new Real(dfltRealType, (attrValue!=null?attrValue[i]:0.0)));
            EarthLocation el       = locations.get(i);
            newRangeVals[0][i] = value.getValue();
            newRangeVals[1][i] = dateTime.getValue();
            lats[i]            = (float) el.getLatitude().getValue();
            lons[i]            = (float) el.getLongitude().getValue();
            //            if(Math.abs(lats[i])>90) System.err.println("bad lat:" + lats[i]);
        }
        GriddedSet llaSet = ucar.visad.Util.makeEarthDomainSet(lats, lons,
                                null);
        Set[] rangeSets = new Set[2];
        rangeSets[0] = new DoubleSet(new SetType(rangeType.getComponent(0)));
        rangeSets[1] = new DoubleSet(new SetType(rangeType.getComponent(1)));
        FunctionType newType =
            new FunctionType(((SetType) llaSet.getType()).getDomain(),
                             rangeType);
        FlatField timeTrack = new FlatField(newType, llaSet,
                                            (CoordinateSystem) null,
                                            rangeSets,
                                            new Unit[] { dfltReal.getUnit(),
                timeUnit });
        timeTrack.setSamples(newRangeVals, false);
        return timeTrack;
    }

      /** Type for Range */
    private final RealType rangeType = RealType.getRealType("Range",
                                           CommonUnit.meter);

    /** Type for Azimuth */
    private final RealType azimuthType = RealType.getRealType("Azimuth",
                                             CommonUnit.degree);
    /**
     * _more_
     *
     * @param track _more_

     *
     * @return _more_
     *
     * @throws Exception _more_
     */
    private RingSet [] makeRingField(StormTrack track, WayDisplayState wState, CompositeDisplayable holder,
                                        String attrName)
            throws Exception {
        List<EarthLocation> locations     = track.getLocations();
        int        numPoints       = locations.size();
        RingSet [] rings =   new RingSet[numPoints];
        double[][] newRangeVals = new double[2][numPoints];
        float[]    lats         = new float[numPoints];
        float[]    lons         = new float[numPoints];
        float[]    values = track.getTrackAttributeValues(attrName);
        for (int i = 0; i < numPoints; i++) {
            EarthLocation el       = locations.get(i);
            lats[i]            = (float) el.getLatitude().getValue();
            lons[i]            = (float) el.getLongitude().getValue();
            if(values[i]==values[i]) {
                rings[i] = makeRealTupleType( lats[i], lons[i], values[i]);
                wState.addRingsDisplayable(rings[i]);
                holder.addDisplayable(rings[i]);
            }
        }
        return rings;
    }

    private RingSet makeRealTupleType(double lat, double lon, double r)
            throws VisADException, RemoteException {
        Radar2DCoordinateSystem r2Dcs =
            new Radar2DCoordinateSystem((float) lat, (float) lon);
        RealTupleType rtt =  new RealTupleType(rangeType, azimuthType, r2Dcs, null);
        Color ringColor = Color.gray;

        RingSet  rss = new RingSet("range rings", rtt, ringColor);
        // set initial spacing etc.
        rss.setRingValues(
            new Real(rangeType, r, CommonUnit.meter.scale(1000)),
            new Real(rangeType, r, CommonUnit.meter.scale(1000)));
        rss.setVisible(true);
        /** width for range rings */
        float radialWidth = 1.f;

        rss.setLineWidth(radialWidth);

        return rss;

    }


    /**
     * _more_
     */
    public void doit() {
        trackModel = new AbstractTableModel() {
            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return false;
            }

            public int getRowCount() {
                if (trackCollection == null) {
                    return 0;
                }
                return trackCollection.getTracks().size();
            }

            public int getColumnCount() {
                return 2;
            }

            public void setValueAt(Object aValue, int rowIndex,
                                   int columnIndex) {}

            public Object getValueAt(int row, int column) {

                if ((trackCollection == null) || (row >= trackCollection.getTracks().size())) {
                    return "";
                }
                StormTrack track = trackCollection.getTracks().get(row);
                if (column == 0) {
                    return track.getWay();
                }
                return track.getTrackStartTime();
            }

            public String getColumnName(int column) {
                if (column == 0) {
                    return "Way";
                }
                return "Date";
            }
        };


        trackTable = new JTable(trackModel);

        int width  = 300;
        int height = 400;
        JScrollPane scroller = GuiUtils.makeScrollPane(trackTable, width,
                                   height);
        scroller.setBorder(BorderFactory.createLoweredBevelBorder());
        scroller.setPreferredSize(new Dimension(width, height));
        scroller.setMinimumSize(new Dimension(width, height));

    }


    /**
     *  Set the StormInfo property.
     *
     *  @param value The new value for StormInfo
     */
    public void setStormInfo(StormInfo value) {
        stormInfo = value;
    }

    /**
     *  Get the StormInfo property.
     *
     *  @return The StormInfo
     */
    public StormInfo getStormInfo() {
        return stormInfo;
    }




    /**
     *  Set the ForecastVisible property.
     *
     *  @param value The new value for ForecastVisible
     */
    public void setForecastVisible(boolean value) {
        forecastVisible = value;
    }


    public void setForecastRingsVisible(boolean value) {
        forecastRingsVisible = value;
    }
    /**
     *  Get the ObsVisible property.
     *
     *  @return The ForecastVisible
     */
    public boolean getForecastVisible() {
        return forecastVisible;
    }

    public boolean getForecastRingsVisible() {
        return forecastRingsVisible;
    }

    /**
     *  Set the Changed property.
     *
     *  @param value The new value for Changed
     */
    public void setChanged(boolean value) {
        changed = value;
    }

    /**
     *  Get the Changed property.
     *
     *  @return The Changed
     */
    public boolean getChanged() {
        return changed;
    }

    /**
     * Set the Active property.
     *
     * @param value The new value for Active
     */
    public void setActive(boolean value) {
        active = value;
    }

    /**
     * Get the Active property.
     *
     * @return The Active
     */
    public boolean getActive() {
        return active;
    }


    /**
       Set the WayDisplayStateMap property.

       @param value The new value for WayDisplayStateMap
    **/
    public void setWayDisplayStateMap (Hashtable<Way,WayDisplayState> value) {
	wayDisplayStateMap = value;
    }

    /**
       Get the WayDisplayStateMap property.

       @return The WayDisplayStateMap
    **/
    public Hashtable<Way,WayDisplayState> getWayDisplayStateMap () {
	return wayDisplayStateMap;
    }




}

