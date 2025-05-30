/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.microsoft.chm;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.html.JSoupParser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

public class ChmParser implements Parser {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = 5938777307516469802L;

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(MediaType.application("vnd.ms-htmlhelp"),
                    MediaType.application("chm"), MediaType.application("x-chm"))));

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        ChmExtractor chmExtractor = new ChmExtractor(stream);

        // metadata
        metadata.set(Metadata.CONTENT_TYPE, "application/vnd.ms-htmlhelp");

        // content
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        Parser htmlParser =
                EmbeddedDocumentUtil.tryToFindExistingLeafParser(JSoupParser.class, context);
        if (htmlParser == null) {
            htmlParser = new JSoupParser();
        }

        for (DirectoryListingEntry entry : chmExtractor.getChmDirList()
                .getDirectoryListingEntryList()) {
            final String entryName = entry.getName();
            if (entryName.endsWith(".html") || entryName.endsWith(".htm")) {
//                AttributesImpl attrs = new AttributesImpl();
//                attrs.addAttribute("", "name", "name", "String", entryName);
//                xhtml.startElement("", "document", "document", attrs);

                byte[] data = chmExtractor.extractChmEntry(entry);

                parsePage(data, htmlParser, xhtml, context);

//                xhtml.endElement("", "", "document");
            }
        }

        xhtml.endDocument();
    }


    private void parsePage(byte[] byteObject, Parser htmlParser, ContentHandler xhtml,
                           ParseContext context) throws TikaException, IOException, SAXException { // throws IOException
        Metadata metadata = new Metadata();
        ContentHandler handler = new EmbeddedContentHandler(new BodyContentHandler(xhtml));// -1
        try (TikaInputStream tis = TikaInputStream.get(byteObject)) {
            htmlParser.parse(tis, handler, metadata, context);
        }
    }

}
