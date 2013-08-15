/*
 * Copyright 1997-2013 Unidata Program Center/University Corporation for
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

package ucar.unidata.ui;


import com.toedter.calendar.*;

import ucar.unidata.util.GuiUtils;

import java.awt.*;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import javax.swing.*;



/**
 * Widget for selecting dates and times
 *
 *
 * @author IDV Development Team
 * @version $Revision: 1.7 $
 */
public class DateTimePicker extends JPanel {

    /** the default time zone */
    private static TimeZone defaultTimeZone;

    /** the time zone */
    private TimeZone myTimeZone;

    /** the date chooser */
    private JDateChooser dateChooser;

    /** the SpinnerDateModel */
    SpinnerDateModel timeModel;

    /** the JCalendar */
    JCalendar jc;

    /**
     * Default  ctor
     */
    public DateTimePicker() {
        this(null, true);
    }

    /**
     * Create a DateTimePicker with the initial date.
     * @param date initial date
     */
    public DateTimePicker(Date date) {
        this(date, true);
    }

    /**
     * Create a DateTimePicker with the initial date.
     * @param date initial date
     * @param includeHours   true to have an hour picker
     */
    public DateTimePicker(Date date, boolean includeHours) {
        myTimeZone = getDefaultTimeZone();
        jc         = new JCalendar();
        Calendar calendar = getCalendar(null);
        jc.getDayChooser().setCalendar(calendar);
        jc.setCalendar(calendar);

        dateChooser = new JDateChooser(jc, new Date(), null, null);
        //dateChooser = new JDateChooser(jc);
        setLayout(new BorderLayout());


        // Create a date spinner that controls the hours
        timeModel = new SpinnerDateModel(calendar.getTime(), null, null,
                                         Calendar.HOUR_OF_DAY);
        javax.swing.JSpinner spinner = new javax.swing.JSpinner(timeModel);
        javax.swing.JSpinner.DateEditor editor =
            new javax.swing.JSpinner.DateEditor(spinner, "HH:mm");
        editor.getFormat().setTimeZone(calendar.getTimeZone());
        spinner.setEditor(editor);
        JComponent timeComp;
        if (includeHours) {
            timeComp = GuiUtils.hbox(spinner,
                                     new JLabel(" " + myTimeZone.getID()), 5);
        } else {
            timeComp = new JLabel(" " + myTimeZone.getID());
        }
        add(BorderLayout.CENTER, GuiUtils.hbox(dateChooser, timeComp));
        if (date != null) {
            setDate(date);
        }
    }

    /**
     * Set the default time zone for all instances
     *
     * @param timeZone  the time zone
     */
    public static void setDefaultTimeZone(TimeZone timeZone) {
        defaultTimeZone = timeZone;
    }

    /**
     * Get the default time zone
     *
     * @return  the default time zone
     */
    public static TimeZone getDefaultTimeZone() {
        if (defaultTimeZone == null) {
            defaultTimeZone = TimeZone.getTimeZone("GMT");
        }
        return defaultTimeZone;
    }


    /**
     * Get the Date that has been set
     *
     * @return  the date
     */
    public Date getDate() {
        Date     d   = dateChooser.getDate();
        Calendar cal = dateChooser.getCalendar();
        Calendar c   = getCalendarN(cal);
        // if(d.getHours() != 0 || d.getMinutes() != 0) {
        c.add(Calendar.HOUR_OF_DAY, -c.get(Calendar.HOUR_OF_DAY));
        c.add(Calendar.MINUTE, -c.get(Calendar.MINUTE));
        c.add(Calendar.SECOND, -c.get(Calendar.SECOND));
        c.add(Calendar.MILLISECOND, -c.get(Calendar.MILLISECOND));
        //  }
        if (timeModel != null) {
            Date     time    = timeModel.getDate();
            Calendar timeCal = new GregorianCalendar(cal.getTimeZone());
            timeCal.setTime(time);
            c.add(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY));
            c.add(Calendar.MINUTE, timeCal.get(Calendar.MINUTE));
        }
        return c.getTime();
    }

    /**
     * Get the calendar for this object
     *
     * @param d  the date
     *
     * @return  the associated calendar
     */
    private Calendar getCalendar(Date d) {
        Calendar calendar = new GregorianCalendar(myTimeZone);
        if (d != null) {
            calendar.setTime(d);
        }
        return calendar;
    }

    /**
     * _more_
     *
     * @param cal _more_
     *
     * @return _more_
     */
    private Calendar getCalendarN(Calendar cal) {
        Calendar calendar = new GregorianCalendar(myTimeZone);

        for (int i = 0; i < Calendar.FIELD_COUNT; i++) {
            calendar.set(i, cal.get(i));
        }
        return calendar;
    }


    /**
     * Set the Date.
     *
     * @param d  the new Date
     */
    public void setDate(Date d) {
        // Calendar c = getCalendar(d);
        dateChooser.setDate(d);
        timeModel.setValue(d);
    }

}
