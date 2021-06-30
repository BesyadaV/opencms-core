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

package org.opencms.xml.xml2json;

import org.opencms.file.types.CmsResourceTypeXmlContainerPage;
import org.opencms.file.types.CmsResourceTypeXmlContent;
import org.opencms.i18n.CmsLocaleManager;
import org.opencms.json.JSONArray;
import org.opencms.json.JSONException;
import org.opencms.json.JSONObject;
import org.opencms.main.CmsLog;
import org.opencms.main.OpenCms;
import org.opencms.util.CmsStringUtil;
import org.opencms.xml.content.CmsXmlContent;
import org.opencms.xml.content.I_CmsXmlContentHandler;
import org.opencms.xml.content.I_CmsXmlContentHandler.JsonRendererSettings;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;

/**
 * Sub-handler for converting XML contents to JSON, either as a whole or just specific locales or paths.
 */
public class CmsXmlContentJsonHandler implements I_CmsJsonHandler {

    /**
     * Exception thrown when path lookup fails.
     */
    public static class PathNotFoundException extends Exception {

        /** Serial version id. */
        private static final long serialVersionUID = 1L;

        public PathNotFoundException(String string) {

            super(string);
        }
    }

    /** Request parameter name. */
    public static final String PARAM_LOCALE = "locale";

    /** Request parameter name. */
    public static final String PARAM_PATH = "path";

    /** The logger instance for this class. */
    private static final Log LOG = CmsLog.getLog(CmsXmlContentJsonHandler.class);

    /**
     * Creates an empty JSON object.
     *
     * @return the empty JSON object
     */
    public static JSONObject empty() {

        try {
            return new JSONObject("{}");
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Looks up sub-object in the given JSON object.
     *
     * @param current the initial object
     * @param path the path to look up
     * @return the sub-object
     *
     * @throws JSONException if something goes wrong
     * @throws PathNotFoundException if the path can not be found in the JSON object
     */
    public static Object lookupPath(Object current, String path) throws JSONException, PathNotFoundException {

        String[] tokens = path.split("[/\\[\\]]");
        for (String token : tokens) {
            if (CmsStringUtil.isEmptyOrWhitespaceOnly(token)) {
                continue;
            }
            if (StringUtils.isNumeric(token) && (current instanceof JSONArray)) {
                current = ((JSONArray)current).get(Integer.parseInt(token));
            } else if (current instanceof JSONObject) {
                current = ((JSONObject)current).get(token);
            } else {
                throw new PathNotFoundException("Path not found");
            }
        }
        return current;
    }

    /**
     * @see org.opencms.xml.xml2json.I_CmsJsonHandler#getOrder()
     */
    public double getOrder() {

        return 100.0;

    }

    /**
     * @see org.opencms.xml.xml2json.I_CmsJsonHandler#matches(org.opencms.xml.xml2json.CmsJsonHandlerContext)
     */
    public boolean matches(CmsJsonHandlerContext context) {

        return CmsResourceTypeXmlContent.isXmlContent(context.getResource())
            && !CmsResourceTypeXmlContainerPage.isContainerPage(context.getResource());
    }

    /**
     * @see org.opencms.xml.xml2json.I_CmsJsonHandler#renderJson(org.opencms.xml.xml2json.CmsJsonHandlerContext)
     */
    public CmsJsonResult renderJson(CmsJsonHandlerContext context) {

        try {
            CmsXmlContent content = context.getContent();
            I_CmsXmlContentJsonRenderer renderer = createContentRenderer(context);

            Object json = null;
            String localeParam = context.getParameters().get(PARAM_LOCALE);
            String pathParam = context.getParameters().get(PARAM_PATH);
            if ((localeParam == null) && (pathParam == null)) {
                JSONObject json1 = CmsDefaultXmlContentJsonRenderer.renderAllLocales(content, renderer);

                CmsResourceDataJsonHelper helper = new CmsResourceDataJsonHelper(
                    context.getCms(),
                    context.getResource(),
                    context.getAccessPolicy()::checkPropertyAccess);
                helper.addProperties(json1);
                json1.put("attributes", helper.attributes());
                JSONArray locales = new JSONArray();
                for (Locale locale : context.getContent().getLocales()) {
                    locales.put(locale.toString());
                }
                json1.put("locales", locales);
                helper.addPathAndLink(json1);
                json = json1;
            } else if (localeParam != null) {
                Locale locale = CmsLocaleManager.getLocale(localeParam);
                Locale selectedLocale = OpenCms.getLocaleManager().getBestMatchingLocale(
                    locale,
                    Collections.emptyList(),
                    context.getContent().getLocales());
                if ((selectedLocale == null) || !context.getContent().hasLocale(selectedLocale)) {
                    throw new PathNotFoundException("Locale not found");
                }
                json = renderer.render(context.getContent(), selectedLocale);
                if (pathParam != null) {
                    Object result = lookupPath(json, pathParam);
                    json = result;
                }
            } else {
                return new CmsJsonResult(
                    "Can not use path parameter without locale parameter.",
                    HttpServletResponse.SC_BAD_REQUEST);
            }
            CmsJsonResult res = new CmsJsonResult(json, HttpServletResponse.SC_OK);
            return res;

        } catch (JSONException | PathNotFoundException e) {
            LOG.info(e.getLocalizedMessage(), e);
            return new CmsJsonResult(e.getLocalizedMessage(), HttpServletResponse.SC_NOT_FOUND);
        } catch (Exception e) {
            LOG.error(e.getLocalizedMessage(), e);
            return new CmsJsonResult(e.getLocalizedMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Creates the content renderer instance.
     *
     * @param context the JSON handler context
     *
     * @return the content renderer instance
     *
     * @throws Exception if something goes wrong
     */
    protected I_CmsXmlContentJsonRenderer createContentRenderer(CmsJsonHandlerContext context) throws Exception {

        I_CmsXmlContentHandler handler = context.getContent().getContentDefinition().getContentHandler();
        JsonRendererSettings settings = handler.getJsonRendererSettings();
        I_CmsXmlContentJsonRenderer renderer = null;
        if (settings == null) {
            renderer = new CmsDefaultXmlContentJsonRenderer();
        } else {
            renderer = (I_CmsXmlContentJsonRenderer)Class.forName(settings.getClassName()).newInstance();
            for (Map.Entry<String, String> entry : settings.getParameters().entrySet()) {
                renderer.addConfigurationParameter(entry.getKey(), entry.getValue());
            }
            renderer.initConfiguration();
        }
        renderer.initialize(context);
        return renderer;
    }

}
