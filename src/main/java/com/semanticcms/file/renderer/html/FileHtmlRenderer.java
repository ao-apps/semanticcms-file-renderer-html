/*
 * semanticcms-file-renderer-html - Files referenced in HTML in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017, 2019, 2020, 2021, 2022, 2024  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of semanticcms-file-renderer-html.
 *
 * semanticcms-file-renderer-html is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * semanticcms-file-renderer-html is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with semanticcms-file-renderer-html.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.semanticcms.file.renderer.html;

import com.aoapps.hodgepodge.util.Tuple2;
import com.aoapps.html.any.AnyA;
import com.aoapps.html.any.AnyUnion_Palpable_Phrasing;
import com.aoapps.io.buffer.BufferResult;
import com.aoapps.lang.Strings;
import com.aoapps.net.Path;
import com.aoapps.net.URIEncoder;
import com.aoapps.servlet.lastmodified.LastModifiedServlet;
import com.semanticcms.core.model.BookRef;
import com.semanticcms.core.model.NodeBodyWriter;
import com.semanticcms.core.model.ResourceRef;
import com.semanticcms.core.renderer.html.Headers;
import com.semanticcms.core.renderer.html.HtmlRenderer;
import com.semanticcms.core.renderer.html.PageIndex;
import com.semanticcms.core.resources.Resource;
import com.semanticcms.core.resources.ResourceConnection;
import com.semanticcms.core.resources.ResourceStore;
import com.semanticcms.core.servlet.ServletElementContext;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Writer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;

/**
 * Writes the file element HTML.
 */
public final class FileHtmlRenderer {

  /** Make no instances. */
  private FileHtmlRenderer() {
    throw new AssertionError();
  }

  /**
   * The body content inside the file element HTML.
   *
   * @param  <Ex>  An arbitrary exception type that may be thrown
   */
  @FunctionalInterface
  public static interface FileImplBody<Ex extends Throwable> {
    /**
     * Does the body content.
     */
    void doBody(boolean discard) throws Ex, IOException, SkipPageException;
  }

  /**
   * Writes the file element HTML.
   *
   * @param content Optional, when null meta data is verified but no output is generated
   */
  public static void writeFileImpl(
      ServletContext servletContext,
      HttpServletRequest request,
      HttpServletResponse response,
      AnyUnion_Palpable_Phrasing<?, ?> content,
      com.semanticcms.file.model.File element
  ) throws ServletException, IOException {
    ResourceStore resourceStore;
    ResourceRef resourceRef;
      {
        Tuple2<ResourceStore, ResourceRef> resource = element.getResource();
        if (resource == null) {
          throw new IllegalArgumentException("Resource not set on file: " + element);
        }
        resourceStore = resource.getElement1();
        resourceRef = resource.getElement2();
      }
    BookRef bookRef = resourceRef.getBookRef();
    // Find the resource, if available
    Resource resource = resourceStore == null ? null : resourceStore.getResource(resourceRef.getPath());
    // Connect to resource
    ResourceConnection conn = resource == null ? null : resource.open();
    try {
      // Find the local file, if available
      File resourceFile;
        {
          File resourceFileTmp;
          if (conn == null || !conn.exists()) {
            resourceFileTmp = null;
          } else {
            assert resource != null;
            try {
              resourceFileTmp = resource.getFile();
            } catch (FileNotFoundException e) {
              // Resource removed between exists() and getFile()
              resourceFileTmp = null;
            }
          }
          resourceFile = resourceFileTmp;
        }
      // Check if is directory and filename matches required pattern for directory
      boolean isDirectory;
      if (resourceFile == null) {
        // In other book and not available, assume directory when ends in path separator
        isDirectory = resourceRef.getPath().toString().endsWith(Path.SEPARATOR_STRING);
      } else {
        // In accessible book, use attributes
        isDirectory = resourceFile.isDirectory();
        // When is a directory, must end in slash
        if (
            isDirectory
                && !resourceRef.getPath().toString().endsWith(Path.SEPARATOR_STRING)
        ) {
          throw new IllegalArgumentException(
              "References to directories must end in slash ("
                  + Path.SEPARATOR_CHAR
                  + "): "
                  + resourceRef
          );
        }
      }
      if (content != null) {
        BufferResult body = element.getBody();
        boolean hasBody = body.getLength() != 0;
        // Determine if local file opening is allowed
        final boolean isOpenFileAllowed = FileUtils.isOpenFileAllowed(servletContext, request);
        final boolean isExporting = Headers.isExporting(request);

        String elemId = element.getId();
        AnyA<?, ? extends AnyUnion_Palpable_Phrasing<?, ?>, ?, ?> a = content.a();
        if (elemId != null) {
          // TODO: To appendIdInPage, review other uses, too
          a.id(PageIndex.getRefIdInPage(request, element.getPage(), elemId));
        }
        if (!hasBody) {
          // TODO: Class like core:link, where providing empty class disables automatic class selection here
          a.clazz(HtmlRenderer.getInstance(servletContext).getLinkCssClass(element));
        }
        if (
            isOpenFileAllowed
                && resourceFile != null
                && !isExporting
        ) {
          a.href(response.encodeURL(resourceFile.toURI().toASCIIString()));
        } else {
          final String urlPath;
          long lastModified;
          if (
              conn != null
                  && !isDirectory
                  // Check for header disabling auto last modified
                  && !"false".equalsIgnoreCase(request.getHeader(LastModifiedServlet.LAST_MODIFIED_HEADER_NAME))
                  && conn.exists()
                  && (lastModified = conn.getLastModified()) != 0
          ) {
            // Include last modified on file
            urlPath =
                request.getContextPath()
                    + bookRef.getPrefix()
                    + resourceRef.getPath()
                    + "?" + LastModifiedServlet.LAST_MODIFIED_PARAMETER_NAME
                    + "=" + LastModifiedServlet.encodeLastModified(lastModified);
          } else {
            urlPath =
                request.getContextPath()
                    + bookRef.getPrefix()
                    + resourceRef.getPath();
          }
          a.href(response.encodeURL(URIEncoder.encodeURI(urlPath)));
        }
        if (
            isOpenFileAllowed
                && resourceFile != null
                && !isExporting
        ) {
          a.onclick(onclick -> onclick
              .append("semanticcms_openfile_servlet.openFile(").text(bookRef.getDomain()).append(", ").text(bookRef.getPath()).append(", ").text(resourceRef.getPath()).append("); return false;")
          );
        }
        a.__(a__ -> {
          if (!hasBody) {
            if (resourceFile == null) {
              String path = resourceRef.getPath().toString();
              int slashBefore;
              if (path.endsWith(Path.SEPARATOR_STRING)) {
                slashBefore = path.lastIndexOf(Path.SEPARATOR_STRING, path.length() - 2);
              } else {
                slashBefore = path.lastIndexOf(Path.SEPARATOR_STRING);
              }
              String filename = path.substring(slashBefore + 1);
              if (filename.isEmpty()) {
                throw new IllegalArgumentException("Invalid filename for file: " + path);
              }
              a__.text(filename);
            } else {
              a__.text(resourceFile.getName());
              if (isDirectory) {
                a__.text(Path.SEPARATOR_CHAR);
              }
            }
          } else {
            @SuppressWarnings("deprecation")
            Writer unsafe = a__.getRawUnsafe();
            body.writeTo(new NodeBodyWriter(element, unsafe, new ServletElementContext(servletContext, request, response)));
          }
        });
        long length;
        if (
            !hasBody
                && conn != null
                && !isDirectory
                && conn.exists()
                && (length = conn.getLength()) != -1
        ) {
          content.text(" (").text(Strings.getApproximateSize(length)).text(')');
        }
      }
    } finally {
      // TODO: Close earlier?
      if (conn != null) {
        conn.close();
      }
    }
  }
}
