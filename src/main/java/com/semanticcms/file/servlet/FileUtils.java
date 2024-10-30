/*
 * semanticcms-file-servlet - Files nested within SemanticCMS pages and elements in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017, 2019, 2020, 2021, 2022, 2024  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of semanticcms-file-servlet.
 *
 * semanticcms-file-servlet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * semanticcms-file-servlet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with semanticcms-file-servlet.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.semanticcms.file.servlet;

import com.semanticcms.core.model.Element;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.servlet.CaptureLevel;
import com.semanticcms.core.servlet.CapturePage;
import com.semanticcms.file.model.File;
import com.semanticcms.openfile.servlet.OpenFile;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Static utilities for working with {@link File}.
 */
public final class FileUtils {

  /** Make no instances. */
  private FileUtils() {
    throw new AssertionError();
  }

  private static final Logger logger = Logger.getLogger(FileUtils.class.getName());

  private static class IsOpenFileAllowedLock {
    private IsOpenFileAllowedLock() {
      // Empty lock class to help heap profile
    }
  }

  private static final IsOpenFileAllowedLock isOpenFileAllowedLock = new IsOpenFileAllowedLock();
  private static boolean openFileNotFound;

  /**
   * Determines if local file opening is allowed.
   *
   * <p>Uses reflection to avoid hard dependency on semanticcms-openfile-servlet.</p>
   *
   * @see  OpenFile#isAllowed(javax.servlet.ServletContext, javax.servlet.ServletRequest)
   */
  public static boolean isOpenFileAllowed(ServletContext servletContext, ServletRequest request) throws ServletException {
    synchronized (isOpenFileAllowedLock) {
      // If failed once, fail quickly the second time
      if (openFileNotFound) {
        return false;
      }
      try {
        Class<?> openFileClass = Class.forName("com.semanticcms.openfile.servlet.OpenFile");
        Method isAllowedMethod = openFileClass.getMethod("isAllowed", ServletContext.class, ServletRequest.class);
        return (Boolean) isAllowedMethod.invoke(null, servletContext, request);
      } catch (ClassNotFoundException e) {
        logger.warning("Unable to open local files, if desktop integration is desired, add the semanticcms-openfile-servlet package.");
        openFileNotFound = true;
        return false;
      } catch (ReflectiveOperationException e) {
        throw new ServletException(e);
      }
    }
  }

  /**
   * Determines if the given page has any {@link File} that is not {@linkplain File#isHidden() hidden}.
   */
  public static boolean hasFile(
      ServletContext servletContext,
      HttpServletRequest request,
      HttpServletResponse response,
      Page page,
      final boolean recursive
  ) throws ServletException, IOException {
    return CapturePage.traversePagesAnyOrder(
        servletContext,
        request,
        response,
        page,
        CaptureLevel.META,
        p -> {
          for (Element e : p.getElements()) {
            if ((e instanceof File) && !((File) e).isHidden()) {
              return true;
            }
          }
          return null;
        },
        p -> recursive ? p.getChildRefs() : null,
        // Child not in missing book
        childPage -> childPage.getBook() != null
    ) != null;
  }
}
