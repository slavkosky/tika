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
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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

    @Serial
    private static final long serialVersionUID = -6656102320836767910L;

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MediaType.application("jsonl"));

    public static final String JSONL_MIME_TYPE = "application/jsonl";

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    private void processJsonNode(JsonNode node, XHTMLContentHandler xhtml, String fieldName, boolean isFirstField)
            throws SAXException {
        if (node.isObject()) {
            // Start a new nested dl for this object
            xhtml.startElement("dl");

            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String entryKey = entry.getKey();
                JsonNode valueNode = entry.getValue();

                // Skip empty values or "[]" values
                String valueText = valueNode.asText();
                if (valueText.isEmpty() || "[]".equals(valueText)) {
                    continue;
                }

                // Create dt element with microdata attribute
                xhtml.startElement("dt", "itemprop", entryKey);

                // Process the first field differently using <dfn> tag
                if (isFirstField) {
                    xhtml.startElement("dfn");
                    xhtml.characters(valueText);
                    xhtml.endElement("dfn");
                    isFirstField = false; // Only apply to the very first field
                } else {
                    processJsonNode(valueNode, xhtml, entryKey, false);
                }

                xhtml.endElement("dt");

                // Skip dd element for the first field since it's already processed in dt
                if (!isFirstField) {
                    // Create dd element with microdata attribute
                    xhtml.startElement("dd", "itemprop", entryKey);
                    processJsonNode(valueNode, xhtml, entryKey, false);
                    xhtml.endElement("dd");
                }
            }

            xhtml.endElement("dl");
        } else if (node.isArray()) {
            // Handle json values with child objects
            xhtml.startElement("ul");
            for (JsonNode element : node) {
                xhtml.startElement("li");
                processJsonNode(element, xhtml, null, false);
                xhtml.endElement("li");
            }
            xhtml.endElement("ul");
        } else {
            // Base case: simple value
            String value = node.asText();
            // Skip empty values or "[]" values
            if (!value.isEmpty() && !"[]".equals(value)) {
                xhtml.characters(value);
            }
        }
    }


    @Override
    public void parse(InputStream stream,
                      ContentHandler handler,
                      Metadata metadata,
                      ParseContext context)
            throws IOException, SAXException, TikaException {

        // Basic metadata from older logic
        metadata.set(Metadata.CONTENT_TYPE, JSONL_MIME_TYPE);

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        // Collect unique header filenames here
        Set<String> uniqueHeaders = new LinkedHashSet<>();
        String moduleValue = null;
        String headerFieldName = null;
        String moduleFieldName = null;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
            ObjectMapper mapper = new ObjectMapper();
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;  // skip blank lines
                }

                JsonNode rootNode;
                try {
                    rootNode = mapper.readTree(line);
                } catch (JsonProcessingException e) {
                    // log invalid lines in the output as-is:
                    xhtml.element("p", e.getMessage());
                    continue;
                }

                // List to store all fields for use throughout the method
                List<Map.Entry<String, JsonNode>> fieldsList = new ArrayList<>();
                Iterator<Map.Entry<String, JsonNode>> fields = rootNode.fields();

                // Collect all fields first
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    JsonNode valueNode = entry.getValue();

                    // Skip null nodes
                    if (valueNode == null) {
                        continue;
                    }

                    fieldsList.add(entry);
                }

                // Process header and module metadata if we have enough fields
                if (fieldsList.size() >= 2) {
                    Map.Entry<String, JsonNode> secondField = fieldsList.get(1);
                    headerFieldName = secondField.getKey(); // Store the field name for later use
                    String headerPath = secondField.getValue().asText();
                    String headerFileName = headerPath.replaceAll("^.*[\\\\/]", "");
                    if (!headerFileName.isEmpty()) {
                        uniqueHeaders.add(headerFileName);
                    }
                }

                if (fieldsList.size() >= 4 && moduleValue == null) {
                    Map.Entry<String, JsonNode> fourthField = fieldsList.get(3);
                    moduleFieldName = fourthField.getKey(); // Store the field name for later use
                    if (fourthField.getKey().equals("module")) {
                        String mod = fourthField.getValue().asText().trim();
                        if (!mod.isEmpty()) {
                            moduleValue = mod;
                        }
                    }
                }

                // Output fields to XHTML with new format
                xhtml.startElement("div");
                xhtml.startElement("dl");

                // Process the first field specially (using dfn tag)
                if (!fieldsList.isEmpty()) {
                    Map.Entry<String, JsonNode> firstField = fieldsList.get(0);
                    String firstFieldKey = firstField.getKey();
                    JsonNode firstFieldValue = firstField.getValue();

                    xhtml.startElement("dt", "itemprop", firstFieldKey);
                    xhtml.startElement("dfn");
                    xhtml.characters(firstFieldValue.asText());
                    xhtml.endElement("dfn");
                    xhtml.endElement("dt");

                    // Skip the first field in the main loop since we've already processed it
                    processNodeFields(xhtml, rootNode, fieldsList, 0);
                }

                xhtml.endElement("dl");
                xhtml.endElement("div");
            }
        }

        // After reading the entire file, store metadata using the field names from JSON
        if (moduleValue != null && moduleFieldName != null) {
            metadata.set(moduleFieldName, moduleValue);

            if (headerFieldName != null) {
                for (String header : uniqueHeaders) {
                    metadata.add(headerFieldName, header);
                }
            }
        }

        xhtml.endDocument();
    }

    private void processNodeFields(XHTMLContentHandler xhtml, JsonNode rootNode,
                                  List<Map.Entry<String, JsonNode>> fieldsList, int skipFirstN)
            throws SAXException {
        for (int i = skipFirstN + 1; i < fieldsList.size(); i++) {
            Map.Entry<String, JsonNode> entry = fieldsList.get(i);
            String fieldKey = entry.getKey();
            JsonNode valueNode = entry.getValue();

            // Skip empty values or "[]" values
            String valueText = valueNode.asText();
            if (valueText.isEmpty() || "[]".equals(valueText)) {
                continue;
            }

            // Add dd element with microdata attribute
            xhtml.startElement("dd", "itemprop", fieldKey);

            if (valueNode.isArray() || valueNode.isObject()) {
                processJsonNode(valueNode, xhtml, fieldKey, false);
            } else {
                xhtml.characters(valueText);
            }

            xhtml.endElement("dd");
        }
    }
}
