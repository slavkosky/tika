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
package org.apache.tika.extractor;

import static org.apache.tika.sax.XHTMLContentHandler.XHTML;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.exception.CorruptedFileException;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.DelegatingParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ParseRecord;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.EmbeddedContentHandler;

/**
 * Helper class for parsers of package archives or other compound document
 * formats that support embedded or attached component documents.
 *
 * @since Apache Tika 0.8
 */
public class ParsingEmbeddedDocumentExtractor implements EmbeddedDocumentExtractor {

    private static final File ABSTRACT_PATH = new File("");

    private static final Parser DELEGATING_PARSER = new DelegatingParser();

    private boolean writeFileNameToContent = true;

    protected final ParseContext context;

    public ParsingEmbeddedDocumentExtractor(ParseContext context) {
        this.context = context;
    }

    @Override
    public boolean shouldParseEmbedded(Metadata metadata) {
        DocumentSelector selector = context.get(DocumentSelector.class);
        if (selector != null) {
            return selector.select(metadata);
        }

        FilenameFilter filter = context.get(FilenameFilter.class);
        if (filter != null) {
            String name = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
            if (name != null) {
                return filter.accept(ABSTRACT_PATH, name);
            }
        }

        return true;
    }

    @Override
    public void parseEmbedded(
            TikaInputStream tis, ContentHandler handler, Metadata metadata, boolean outputHtml)
            throws SAXException, IOException {
        if (outputHtml) {
            AttributesImpl attributes = new AttributesImpl();
            attributes.addAttribute("", "class", "class", "CDATA", "package-entry");
            handler.startElement(XHTML, "div", "div", attributes);
        }

        String name = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        if (writeFileNameToContent && name != null && name.length() > 0 && outputHtml) {
            handler.startElement(XHTML, "h1", "h1", new AttributesImpl());
            char[] chars = name.toCharArray();
            handler.characters(chars, 0, chars.length);
            handler.endElement(XHTML, "h1", "h1");
        }

        // Use the delegate parser to parse this entry
        try {
            tis.setCloseShield();
            DELEGATING_PARSER.parse(tis, new EmbeddedContentHandler(new BodyContentHandler(handler)),
                    metadata, context);
        } catch (EncryptedDocumentException ede) {
            recordException(ede, context);
        } catch (CorruptedFileException e) {
            //necessary to stop the parse to avoid infinite loops
            //on corrupt sqlite3 files
            throw new IOException(e);
        } catch (TikaException e) {
            recordException(e, context);
        } finally {
            tis.removeCloseShield();
        }

        if (outputHtml) {
            handler.endElement(XHTML, "div", "div");
        }
    }

    void recordException(Exception e, ParseContext context) {
        ParseRecord record = context.get(ParseRecord.class);
        if (record == null) {
            return;
        }
        record.addException(e);
    }

    public Parser getDelegatingParser() {
        return DELEGATING_PARSER;
    }

    public void setWriteFileNameToContent(boolean writeFileNameToContent) {
        this.writeFileNameToContent = writeFileNameToContent;
    }

    public boolean isWriteFileNameToContent() {
        return writeFileNameToContent;
    }
}
