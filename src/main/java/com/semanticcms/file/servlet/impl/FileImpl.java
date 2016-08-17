/*
 * semanticcms-file-servlet - Files nested within SemanticCMS pages and elements in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016  AO Industries, Inc.
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
 * along with semanticcms-file-servlet.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.semanticcms.file.servlet.impl;

import static com.aoindustries.encoding.JavaScriptInXhtmlAttributeEncoder.encodeJavaScriptInXhtmlAttribute;
import com.aoindustries.encoding.NewEncodingUtils;
import static com.aoindustries.encoding.TextInXhtmlAttributeEncoder.encodeTextInXhtmlAttribute;
import static com.aoindustries.encoding.TextInXhtmlEncoder.encodeTextInXhtml;
import com.aoindustries.io.buffer.BufferResult;
import com.aoindustries.net.UrlUtils;
import com.aoindustries.servlet.http.LastModifiedServlet;
import com.aoindustries.util.StringUtility;
import com.semanticcms.core.model.NodeBodyWriter;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.servlet.Headers;
import com.semanticcms.core.servlet.OpenFile;
import com.semanticcms.core.servlet.PageRefResolver;
import com.semanticcms.core.servlet.ServletElementContext;
import com.semanticcms.core.servlet.impl.LinkImpl;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;

final public class FileImpl {

	public static interface FileImplBody<E extends Throwable> {
		void doBody(boolean discard) throws E, IOException, SkipPageException;
	}

	/**
	 * @param out Optional, when null meta data is verified but no output is generated
	 */
	public static void writeFileImpl(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Writer out,
		com.semanticcms.file.model.File element
	) throws ServletException, IOException, SkipPageException {
		// Resolve file now to catch problems earlier even in meta mode
		PageRef file = PageRefResolver.getPageRef(
			servletContext,
			request,
			element.getBook(),
			element.getPath()
		);
		// Find the local file, assuming relative to CVSWORK directory
		File resourceFile = file.getResourceFile(false, true);
		// Check if is directory and filename matches required pattern for directory
		boolean isDirectory;
		if(resourceFile == null) {
			// In other book and not available, assume directory when ends in path separator
			isDirectory = file.getPath().endsWith(com.semanticcms.file.model.File.SEPARATOR_STRING);
		} else {
			// In accessible book, use attributes
			isDirectory = resourceFile.isDirectory();
			// When is a directory, must end in slash
			if(!file.getPath().endsWith(com.semanticcms.file.model.File.SEPARATOR_STRING)) {
				throw new IllegalArgumentException(
					"References to directories must end in slash ("
					+ com.semanticcms.file.model.File.SEPARATOR_CHAR
					+ "): "
					+ file
				);
			}
		}
		if(out != null) {
			BufferResult body = element.getBody();
			boolean hasBody = body.getLength() != 0;
			// Determine if local file opening is allowed
			final boolean isAllowed = OpenFile.isAllowed(servletContext, request);
			final boolean isExporting = Headers.isExporting(request);

			out.write("<a");
			if(!hasBody) {
				// TODO: Class like p:link, where providing empty class disables automatic class selection here
				out.write(" class=\"");
				out.write(isDirectory ? "semanticcms-file-directory-link" : "semanticcms-file-file-link");
				out.write('"');
			}
			out.write(" href=\"");
			if(
				isAllowed
				&& resourceFile != null
				&& !isExporting
			) {
				encodeTextInXhtmlAttribute(resourceFile.toURI().toString(), out);
			} else {
				final String urlPath;
				if(
					resourceFile != null
					&& !isDirectory
					// Check for header disabling auto last modified
					&& !"false".equalsIgnoreCase(request.getHeader(LastModifiedServlet.LAST_MODIFIED_HEADER_NAME))
				) {
					// Include last modified on file
					urlPath = request.getContextPath()
						+ file.getBookPrefix()
						+ file.getPath()
						+ "?" + LastModifiedServlet.LAST_MODIFIED_PARAMETER_NAME
						+ "=" + LastModifiedServlet.encodeLastModified(resourceFile.lastModified())
					;
				} else {
					urlPath = request.getContextPath()
						+ file.getBookPrefix()
						+ file.getPath()
					;
				}
				encodeTextInXhtmlAttribute(
					response.encodeURL(
						UrlUtils.encodeUrlPath(
							urlPath,
							response.getCharacterEncoding()
						)
					),
					out
				);
			}
			out.write('"');
			if(
				isAllowed
				&& resourceFile != null
				&& !isExporting
			) {
				out.write(" onclick=\"");
				encodeJavaScriptInXhtmlAttribute("semanticcms_core_servlet.openFile(\"", out);
				NewEncodingUtils.encodeTextInJavaScriptInXhtmlAttribute(file.getBook().getName(), out);
				encodeJavaScriptInXhtmlAttribute("\", \"", out);
				NewEncodingUtils.encodeTextInJavaScriptInXhtmlAttribute(file.getPath(), out);
				encodeJavaScriptInXhtmlAttribute("\"); return false;", out);
				out.write('"');
			}
			out.write('>');
			if(!hasBody) {
				if(resourceFile == null) {
					LinkImpl.writeBrokenPathInXhtml(file, out);
				} else {
					encodeTextInXhtml(resourceFile.getName(), out);
					if(isDirectory) encodeTextInXhtml(com.semanticcms.file.model.File.SEPARATOR_CHAR, out);
				}
			} else {
				body.writeTo(new NodeBodyWriter(element, out, new ServletElementContext(servletContext, request, response)));
			}
			out.write("</a>");
			if(!hasBody && resourceFile != null && !isDirectory) {
				out.write(" (");
				encodeTextInXhtml(StringUtility.getApproximateSize(resourceFile.length()), out);
				out.write(')');
			}
		}
	}

	/**
	 * Make no instances.
	 */
	private FileImpl() {
	}
}
