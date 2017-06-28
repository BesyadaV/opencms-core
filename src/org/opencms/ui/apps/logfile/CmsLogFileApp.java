/*
 * This library is part of OpenCms -
 * the Open Source Content Management System
 *
 * Copyright (c) Alkacon Software GmbH & Co. KG (http://www.alkacon.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For further information about Alkacon Software, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.opencms.ui.apps.logfile;

import org.opencms.main.OpenCms;
import org.opencms.ui.A_CmsUI;
import org.opencms.ui.CmsVaadinUtils;
import org.opencms.ui.FontOpenCms;
import org.opencms.ui.apps.A_CmsWorkplaceApp;
import org.opencms.ui.apps.Messages;
import org.opencms.ui.components.CmsBasicDialog;
import org.opencms.ui.components.CmsToolBar;
import org.opencms.util.CmsStringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.Component;
import com.vaadin.ui.TextField;
import com.vaadin.ui.Window;

/**
 * Main class of Log managment app.<p>
 */
public class CmsLogFileApp extends A_CmsWorkplaceApp {

    /**Log folder path.*/
    protected static final String LOG_FOLDER = OpenCms.getSystemInfo().getLogFileRfsPath().substring(
        0,
        OpenCms.getSystemInfo().getLogFileRfsPath().lastIndexOf("/") + 1);

    /** The file table filter input. */
    private TextField m_tableFilter;

    /**
     * Simple function to get the prefix of an logchannel name.<p>
     *
     * @param logname the full name of the logging channel
     *
     * @return a string array with different package prefixes
     */
    protected static String[] buildsufix(String logname) {

        // help String array to store all combination
        String[] prefix_temp = new String[logname.length()];
        int count = 0;
        while (logname.indexOf(".") > 1) {
            // separate the name of the logger into pieces of name and separator e.g.: "org."
            String subprefix = logname.substring(0, logname.indexOf(".") + 1);
            logname = logname.replace(subprefix, "");
            if (logname.indexOf(".") > 1) {
                if (count > 0) {
                    // build different suffixes based on the pieces separated above
                    prefix_temp[count] = prefix_temp[count - 1] + subprefix;
                } else {
                    // if it's the first piece of the name only it will be set
                    prefix_temp[count] = subprefix;
                }
            }
            count++;
        }
        // if the logger name has more then one piece
        if (count >= 1) {
            // create result string array
            String[] prefix = new String[count - 1];
            // copy all different prefixes to one array with right size
            for (int i = 0; i < (count - 1); i++) {
                prefix[i] = prefix_temp[i].substring(0, prefix_temp[i].length() - 1);
            }
            // return all different prefixes
            return prefix;
        }
        // if the logger name has only one or less piece
        else {
            // return the full logger name
            String[] nullreturn = new String[1];
            nullreturn[0] = logname;
            return nullreturn;
        }
    }

    /**
     * Help function to get all loggers from LogManager.<p>
     *
     * @return List of Logger
     */
    protected static List<Logger> getLoggers() {

        // list of all loggers
        List<Logger> definedLoggers = new ArrayList<Logger>();
        // list of all parent loggers
        List<Logger> packageLoggers = new ArrayList<Logger>();
        @SuppressWarnings("unchecked")
        List<Logger> curentloggerlist = Collections.list(LogManager.getCurrentLoggers());
        Iterator<Logger> it_curentlogger = curentloggerlist.iterator();
        // get all current loggers
        while (it_curentlogger.hasNext()) {
            // get the logger
            Logger log = it_curentlogger.next();
            String logname = log.getName();
            String[] prefix = buildsufix(logname);
            // create all possible package logger from given logger name
            for (int i = 0; i < prefix.length; i++) {
                // get the name of the logger without the prefix
                String temp = log.getName().replace(prefix[i], "");
                // if the name has suffix
                if (temp.length() > 1) {
                    temp = temp.substring(1);
                }
                if (temp.lastIndexOf(".") > 1) {
                    // generate new logger with "org.opencms" prefix and the next element
                    // between the points e.g.: "org.opencms.search"
                    Logger temp_logger = Logger.getLogger(prefix[i] + "." + temp.substring(0, temp.indexOf(".")));
                    // activate the heredity so the logger get the appender from parent logger
                    temp_logger.setAdditivity(true);
                    // add the logger to the packageLoggers list if it is not part of it
                    if (!packageLoggers.contains(temp_logger)) {
                        packageLoggers.add(temp_logger);
                    }
                }
            }
            definedLoggers.add(log);
        }

        Iterator<Logger> it_logger = packageLoggers.iterator();
        // iterate about all packageLoggers
        while (it_logger.hasNext()) {
            Logger temp = it_logger.next();
            // check if the logger is part of the logger list
            if (!definedLoggers.contains(temp)) {
                // add the logger to the logger list
                definedLoggers.add(temp);
            }
        }

        // sort all loggers by name
        Collections.sort(definedLoggers, new Comparator<Object>() {

            public int compare(Logger o1, Logger o2) {

                return String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName());
            }

            public int compare(Object obj, Object obj1) {

                return compare((Logger)obj, (Logger)obj1);
            }
        });
        // return all loggers
        return definedLoggers;
    }

    /**
     * @see org.opencms.ui.apps.A_CmsWorkplaceApp#getBreadCrumbForState(java.lang.String)
     */
    @Override
    protected LinkedHashMap<String, String> getBreadCrumbForState(String state) {

        LinkedHashMap<String, String> crumbs = new LinkedHashMap<String, String>();

        //Check if state is empty -> start
        if (CmsStringUtil.isEmptyOrWhitespaceOnly(state)) {
            crumbs.put("", CmsVaadinUtils.getMessageText(Messages.GUI_LOGFILE_VIEW_TOOL_NAME_0));
            return crumbs;
        }
        return new LinkedHashMap<String, String>();
    }

    /**
     * @see org.opencms.ui.apps.A_CmsWorkplaceApp#getComponentForState(java.lang.String)
     */
    @Override
    protected Component getComponentForState(String state) {

        if (m_tableFilter != null) {
            m_infoLayout.removeComponent(m_tableFilter);
            m_tableFilter = null;
        }
        addDownloadButton();
        if (state.isEmpty()) {
            m_rootLayout.setMainHeightFull(false);
            return new CmsLogFileView(this);

        }

        return null;
    }

    /**
     * @see org.opencms.ui.apps.A_CmsWorkplaceApp#getSubNavEntries(java.lang.String)
     */
    @Override
    protected List<NavEntry> getSubNavEntries(String state) {

        return null;
    }

    /**
     * Adds the download button.
     */
    private void addDownloadButton() {

        Button button = CmsToolBar.createButton(
            FontOpenCms.DOWNLOAD,
            CmsVaadinUtils.getMessageText(Messages.GUI_MESSAGES_BROADCAST_TO_ALL_0));
        button.addClickListener(new ClickListener() {

            private static final long serialVersionUID = 1L;

            public void buttonClick(ClickEvent event) {

                Window window = CmsBasicDialog.prepareWindow(CmsBasicDialog.DialogWidth.wide);
                window.setCaption(CmsVaadinUtils.getMessageText(Messages.GUI_LOGFILE_DOWNLOAD_0));
                window.setContent(new CmsLogDownloadDialog(window));
                A_CmsUI.get().addWindow(window);
            }
        });
        m_uiContext.addToolbarButton(button);
    }
}
