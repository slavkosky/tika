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

package org.apache.tika.parser.jsonl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;


public class JsonLinesParser implements Parser {

    private static final long serialVersionUID = -6656102320836767910L;

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MediaType.application("jsonl"));

    public static final String JSONL_MIME_TYPE = "application/jsonl";

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler,
                      Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        metadata.set(Metadata.CONTENT_TYPE, JSONL_MIME_TYPE);
        metadata.set("JsonLinesParser-Version", "1.0");

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        ObjectMapper mapper = new ObjectMapper();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                try {
                    // Parse a single JSON object from the line as a generic Map.
                    Map<?, ?> jsonObject = mapper.readValue(line, Map.class);

                    // Iterate over every key/value pair
                    for (Map.Entry<?, ?> entry : jsonObject.entrySet()) {
                        // Skip keys with null values.
                        if (entry.getValue() == null) {
                            continue;
                        }
                        String key = entry.getKey().toString();
                        String value = entry.getValue().toString().trim();

                        // If the value is empty (or whitespace only), disregard it.
                        if (value.isEmpty() || value.matches("^\\s*$|^\\[]$")) {
                            continue;
                        }
                        if (key.matches("className")) {
                            // Add each key/value pair to the XHTML output.
                            xhtml.element("p", key + " = " + value);

                            // Add each entry to the Metadata using a namespaced key.
                            metadata.add("jsonl:" + key, value);
                        }
                        else {
                            // Add each key/value pair to the XHTML output.
                            xhtml.element("p", key + " = " + value);

                            // Add each entry to the Metadata using a namespaced key.
                            metadata.add("jsonl:" + key, value);
                        }
                    }
                } catch (JsonProcessingException e) {
                    // If a line isn’t valid JSON, log a message in the XHTML output.
                    xhtml.element("p", "Line " + lineNumber + ":" + e.getMessage());
                }
            }
        }
        xhtml.endDocument();
    }
}
