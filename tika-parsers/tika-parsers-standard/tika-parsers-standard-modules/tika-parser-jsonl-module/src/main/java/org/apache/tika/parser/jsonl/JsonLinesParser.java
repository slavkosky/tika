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
import org.xml.sax.helpers.AttributesImpl;

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

    private static final AttributesImpl EMPTY_ATTRIBUTES = new AttributesImpl();

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    private void processJsonNode(JsonNode node, XHTMLContentHandler xhtml, String fieldName, String parentId, boolean isFirstField)
            throws SAXException {
        if (node.isObject()) {
            AttributesImpl dlAttributes = new AttributesImpl();
            String dlId = parentId != null ? parentId : "";

            if (fieldName != null && !fieldName.isEmpty()) {
                dlAttributes.addAttribute("", "id", "id", "CDATA", dlId);
                dlAttributes.addAttribute("", "class", "class", "CDATA", fieldName);
            } else {
                dlAttributes.addAttribute("", "class", "class", "CDATA", "json-object");
            }
            xhtml.startElement("dl", dlAttributes);

            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String keyNode = entry.getKey();
                JsonNode valueNode = entry.getValue();

                if (valueNode.isArray() && valueNode.isEmpty()) {
                    continue; // Skip empty arrays
                } else if (valueNode.isObject() && valueNode.isEmpty()) {
                    continue; // Skip empty objects
                } else if (!valueNode.isArray() && !valueNode.isObject() &&
                        (valueNode.asText().isEmpty() || "[]".equals(valueNode.asText()))) {
                    continue; // Skip empty text values
                }

                String valueStr = valueNode.isValueNode() ? valueNode.asText() : "";
                String elementId = parentId != null ? parentId + "-" + keyNode : keyNode;
                AttributesImpl dtAttributes = new AttributesImpl();
                dtAttributes.addAttribute("", "id", "id", "CDATA", elementId);
                dtAttributes.addAttribute("", "itemprop", "itemprop", "CDATA", keyNode);

                xhtml.startElement("dt", dtAttributes);
                if (isFirstField) {
                    xhtml.startElement("dfn", EMPTY_ATTRIBUTES);
                    xhtml.characters(valueNode.asText());
                    xhtml.endElement("dfn");
                    isFirstField = false;
                } else {
                    xhtml.characters(valueStr);
                }
                xhtml.endElement("dt");

                // For non-primitive values, we'll render them in the dd element
                if (valueNode.isArray() || valueNode.isObject()) {
                    AttributesImpl ddAttributes = new AttributesImpl();
                    ddAttributes.addAttribute("", "id", "id", "CDATA", elementId);
                    ddAttributes.addAttribute("", "itemprop", "itemprop", "CDATA", keyNode);
                    xhtml.startElement("dd", ddAttributes);
                    processJsonNode(valueNode, xhtml, keyNode, elementId, false);
                    xhtml.endElement("dd");
                }
            }
            xhtml.endElement("dl");
        } else if (node.isArray()) {
            AttributesImpl ulAttributes = new AttributesImpl();
            xhtml.startElement("ul", ulAttributes);

            int itemIndex = 0;
            for (JsonNode element : node) {
                // For array items, create a meaningful ID based on the value if it's a simple value
                String itemValue = element.isValueNode() ? element.asText() : String.valueOf(itemIndex);
                String itemId = fieldName + "-" + sanitizeForId(itemValue);

                AttributesImpl liAttributes = new AttributesImpl();
                liAttributes.addAttribute("", "id", "id", "CDATA", itemId);

                xhtml.startElement("li", liAttributes);

                // For object type array items, use a more structured approach
                if (element.isObject()) {
                    AttributesImpl dlAttributes = new AttributesImpl();
                    dlAttributes.addAttribute("", "class", "class", "CDATA", fieldName + "-list");
                    xhtml.startElement("dl", dlAttributes);

                    // Process object fields, combining relevant properties
                    Iterator<Map.Entry<String, JsonNode>> fields = element.fields();
                    String typeValue = null;
                    String nameValue = null;
                    String descValue = null;

                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> entry = fields.next();
                        String key = entry.getKey();
                        JsonNode value = entry.getValue();

                        if ("type".equals(key)) typeValue = value.asText();
                        else if ("name".equals(key)) nameValue = value.asText();
                        else if ("description".equals(key)) descValue = value.asText();
                    }

                    // Create combined type+name element
                    if (typeValue != null && nameValue != null) {
                        AttributesImpl dtAttributes = new AttributesImpl();
                        dtAttributes.addAttribute("", "id", "id", "CDATA", typeValue + "_" + nameValue);
                        dtAttributes.addAttribute("", "itemprop", "itemprop", "CDATA", "type+name");

                        xhtml.startElement("dt", dtAttributes);
                        xhtml.characters(typeValue + " " + nameValue);
                        xhtml.endElement("dt");
                    }

                    // Create description element
                    if (descValue != null) {
                        AttributesImpl ddAttributes = new AttributesImpl();
                        ddAttributes.addAttribute("", "id", "id", "CDATA", "description-" + nameValue);
                        ddAttributes.addAttribute("", "itemprop", "itemprop", "CDATA", "description");

                        xhtml.startElement("dd", ddAttributes);
                        xhtml.characters(descValue);
                        xhtml.endElement("dd");
                    }

                    xhtml.endElement("dl");
                } else {
                    // For simple value array items
                    xhtml.characters(element.asText());
                }

                xhtml.endElement("li");
                itemIndex++;
            }
            xhtml.endElement("ul");
        } else {
            xhtml.characters(node.asText());
        }
    }

    private String sanitizeForId(String input) {
        if (input == null || input.isEmpty()) {
            return "empty";
        }
        String limited = input.length() > 30 ? input.substring(0, 30) : input;
        return limited.replaceAll("[^A-Za-z0-9_-]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("(^-|-$)", "");
    }

    @Override
    public void parse(InputStream stream,
                      ContentHandler handler,
                      Metadata metadata,
                      ParseContext context)
            throws IOException, SAXException, TikaException {

        metadata.set(Metadata.CONTENT_TYPE, JSONL_MIME_TYPE);

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        Set<String> uniqueHeaders = new LinkedHashSet<>();
        String moduleValue = null, headerFieldName = null, moduleFieldName = null;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
            ObjectMapper mapper = new ObjectMapper();
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                JsonNode rootNode;
                try {
                    rootNode = mapper.readTree(line);
                } catch (JsonProcessingException ex) {
                    xhtml.element("p", ex.getMessage());
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
                xhtml.startElement("div", EMPTY_ATTRIBUTES);
                xhtml.startElement("dl", EMPTY_ATTRIBUTES);

                // Process the first field specially (using dfn tag)
                if (!fieldsList.isEmpty()) {
                    Map.Entry<String, JsonNode> firstField = fieldsList.get(0);
                    String firstFieldKey = firstField.getKey();
                    JsonNode firstFieldValue = firstField.getValue();
                    String firstFieldValueStr = firstFieldValue.asText();
                    AttributesImpl dtAttributes = new AttributesImpl();
                    dtAttributes.addAttribute("", "id", "id", "CDATA", firstFieldValueStr + "-" + firstFieldKey);
                    dtAttributes.addAttribute("", "itemprop", "itemprop", "CDATA", firstFieldKey);
                    xhtml.startElement("dt", dtAttributes);
                    xhtml.startElement("dfn", EMPTY_ATTRIBUTES);
                    xhtml.characters(firstFieldValueStr);
                    xhtml.endElement("dfn");
                    xhtml.endElement("dt");

                    // Process remaining fields using the first field value as parent ID
                    for (int i = 1; i < fieldsList.size(); i++) {
                        Map.Entry<String, JsonNode> entry = fieldsList.get(i);
                        String fieldKey = entry.getKey();
                        JsonNode valueNode = entry.getValue();

                        // Skip empty values
                        if ((valueNode.isArray() && valueNode.isEmpty()) ||
                                (valueNode.isObject() && valueNode.isEmpty()) ||
                                (!valueNode.isArray() && !valueNode.isObject() &&
                                        (valueNode.asText().isEmpty() || "[]".equals(valueNode.asText())))) {
                            continue;
                        }

                        AttributesImpl ddAttributes = new AttributesImpl();
                        ddAttributes.addAttribute("", "id", "id", "CDATA", firstFieldValueStr + "-" + fieldKey);
                        ddAttributes.addAttribute("", "itemprop", "itemprop", "CDATA", fieldKey);

                        xhtml.startElement("dd", ddAttributes);

                        if (valueNode.isArray() || valueNode.isObject()) {
                            processJsonNode(valueNode, xhtml, fieldKey, firstFieldValueStr, false);
                        } else {
                            xhtml.characters(valueNode.asText());
                        }

                        xhtml.endElement("dd");
                    }
                }

                xhtml.endElement("dl");
                xhtml.endElement("div");
            }
        }

        // After reading the entire file, store file-level metadata using field names from JSON
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
}
