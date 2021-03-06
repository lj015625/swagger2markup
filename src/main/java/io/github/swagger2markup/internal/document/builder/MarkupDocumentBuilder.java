/*
 * Copyright 2016 Robert Winkler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.swagger2markup.internal.document.builder;

import io.github.swagger2markup.Swagger2MarkupConfig;
import io.github.swagger2markup.Swagger2MarkupConverter;
import io.github.swagger2markup.Swagger2MarkupExtensionRegistry;
import io.github.swagger2markup.internal.document.MarkupDocument;
import io.github.swagger2markup.internal.type.DefinitionDocumentResolver;
import io.github.swagger2markup.internal.type.ObjectType;
import io.github.swagger2markup.internal.type.RefType;
import io.github.swagger2markup.internal.type.Type;
import io.github.swagger2markup.internal.utils.PropertyUtils;
import io.github.swagger2markup.markup.builder.MarkupDocBuilder;
import io.github.swagger2markup.markup.builder.MarkupDocBuilders;
import io.github.swagger2markup.markup.builder.MarkupLanguage;
import io.github.swagger2markup.markup.builder.MarkupTableColumn;
import io.github.swagger2markup.utils.IOUtils;
import io.swagger.models.properties.Property;
import io.swagger.util.Json;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.*;

import static io.github.swagger2markup.internal.utils.MapUtils.toKeySet;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * @author Robert Winkler
 */
public abstract class MarkupDocumentBuilder {

    protected final String DEFAULT_COLUMN;
    protected final String EXAMPLE_COLUMN;
    protected final String REQUIRED_COLUMN;
    protected final String SCHEMA_COLUMN;
    protected final String NAME_COLUMN;
    protected final String DESCRIPTION_COLUMN;
    protected final String SCOPES_COLUMN;
    protected final String DESCRIPTION;
    protected final String PRODUCES;
    protected final String CONSUMES;
    protected final String TAGS;
    protected final String NO_CONTENT;

    protected Logger logger = LoggerFactory.getLogger(getClass());

    protected Swagger2MarkupConverter.Context globalContext;
    protected Swagger2MarkupExtensionRegistry extensionRegistry;
    protected Swagger2MarkupConfig config;
    protected MarkupDocBuilder markupDocBuilder;
    protected Path outputPath;

    MarkupDocumentBuilder(Swagger2MarkupConverter.Context globalContext, Swagger2MarkupExtensionRegistry extensionRegistry, Path outputPath) {
        this.globalContext = globalContext;
        this.extensionRegistry = extensionRegistry;
        this.config = globalContext.getConfig();
        this.outputPath = outputPath;

        this.markupDocBuilder = MarkupDocBuilders.documentBuilder(config.getMarkupLanguage(), config.getLineSeparator()).withAnchorPrefix(config.getAnchorPrefix());

        ResourceBundle labels = ResourceBundle.getBundle("io/github/swagger2markup/lang/labels", config.getOutputLanguage().toLocale());
        DEFAULT_COLUMN = labels.getString("default_column");
        EXAMPLE_COLUMN = labels.getString("example_column");
        REQUIRED_COLUMN = labels.getString("required_column");
        SCHEMA_COLUMN = labels.getString("schema_column");
        NAME_COLUMN = labels.getString("name_column");
        DESCRIPTION_COLUMN = labels.getString("description_column");
        SCOPES_COLUMN = labels.getString("scopes_column");
        DESCRIPTION = DESCRIPTION_COLUMN;
        PRODUCES = labels.getString("produces");
        CONSUMES = labels.getString("consumes");
        TAGS = labels.getString("tags");
        NO_CONTENT = labels.getString("no_content");
    }

    /**
     * Builds the MarkupDocument.
     *
     * @return the built MarkupDocument
     * @throws IOException if the files to include are not readable
     */
    public abstract MarkupDocument build() throws IOException;

    /**
     * Build a generic property table
     *
     * @param properties                 properties to display
     * @param uniquePrefix               unique prefix to prepend to inline object names to enforce unicity
     * @param depth                      current inline schema object depth
     * @param propertyDescriptor         property descriptor to apply to properties
     * @param definitionDocumentResolver definition document resolver to apply to property type cross-reference
     * @param docBuilder                 the docbuilder do use for output
     * @return a list of inline schemas referenced by some properties, for later display
     */
    protected List<ObjectType> buildPropertiesTable(Map<String, Property> properties, String uniquePrefix, int depth, PropertyDescriptor propertyDescriptor, DefinitionDocumentResolver definitionDocumentResolver, MarkupDocBuilder docBuilder) {
        List<ObjectType> localDefinitions = new ArrayList<>();
        List<List<String>> cells = new ArrayList<>();
        List<MarkupTableColumn> cols = Arrays.asList(
                new MarkupTableColumn(NAME_COLUMN).withWidthRatio(1).withHeaderColumn(true).withMarkupSpecifiers(MarkupLanguage.ASCIIDOC, ".^1h"),
                new MarkupTableColumn(DESCRIPTION_COLUMN).withWidthRatio(6).withMarkupSpecifiers(MarkupLanguage.ASCIIDOC, ".^6"),
                new MarkupTableColumn(REQUIRED_COLUMN).withWidthRatio(1).withMarkupSpecifiers(MarkupLanguage.ASCIIDOC, ".^1"),
                new MarkupTableColumn(SCHEMA_COLUMN).withWidthRatio(1).withMarkupSpecifiers(MarkupLanguage.ASCIIDOC, ".^1"),
                new MarkupTableColumn(DEFAULT_COLUMN).withWidthRatio(1).withMarkupSpecifiers(MarkupLanguage.ASCIIDOC, ".^1"),
                new MarkupTableColumn(EXAMPLE_COLUMN).withWidthRatio(1).withMarkupSpecifiers(MarkupLanguage.ASCIIDOC, ".^1"));
        if (MapUtils.isNotEmpty(properties)) {
            Set<String> propertyNames = toKeySet(properties, config.getPropertyOrdering());
            for (String propertyName : propertyNames) {
                Property property = properties.get(propertyName);
                Type propertyType = PropertyUtils.getType(property, definitionDocumentResolver);
                if (depth > 0 && propertyType instanceof ObjectType) {
                    if (MapUtils.isNotEmpty(((ObjectType) propertyType).getProperties())) {
                        propertyType.setName(propertyName);
                        propertyType.setUniqueName(uniquePrefix + " " + propertyName);
                        localDefinitions.add((ObjectType) propertyType);

                        propertyType = new RefType((ObjectType) propertyType);
                    }
                }

                Object example = PropertyUtils.getExample(config.isGeneratedExamplesEnabled(), property, markupDocBuilder);

                List<String> content = Arrays.asList(
                        propertyName,
                        swaggerMarkupDescription(propertyDescriptor.getDescription(property, propertyName)),
                        Boolean.toString(property.getRequired()),
                        propertyType.displaySchema(docBuilder),
                        PropertyUtils.getDefaultValue(property),
                        example != null ? Json.pretty(example) : ""
                );
                cells.add(content);
            }
            docBuilder.tableWithColumnSpecs(cols, cells);
        } else {
            docBuilder.textLine(NO_CONTENT);
        }

        return localDefinitions;
    }

    /**
     * Returns converted markup text from Swagger.
     *
     * @param markupText text to convert
     * @return converted markup text
     */
    protected String swaggerMarkupDescription(String markupText) {
        return markupDocBuilder.copy(false).importMarkup(new StringReader(markupText), globalContext.getConfig().getSwaggerMarkupLanguage()).toString().trim();
    }

    protected void buildDescriptionParagraph(String description, MarkupDocBuilder docBuilder) {
        if (isNotBlank(description)) {
            docBuilder.paragraph(swaggerMarkupDescription(description));
        }
    }

    /**
     * A functor to return descriptions for a given property
     */
    class PropertyDescriptor {
        protected Type type;

        public PropertyDescriptor(Type type) {
            this.type = type;
        }

        public String getDescription(Property property, String propertyName) {
            return defaultString(property.getDescription());
        }
    }

    /**
     * Default {@code DefinitionDocumentResolver} functor
     */
    class DefinitionDocumentResolverDefault implements DefinitionDocumentResolver {

        public DefinitionDocumentResolverDefault() {
        }

        public String apply(String definitionName) {
            if (!config.isInterDocumentCrossReferencesEnabled() || outputPath == null)
                return null;
            else if (config.isSeparatedDefinitionsEnabled())
                return defaultString(config.getInterDocumentCrossReferencesPrefix()) + new File(config.getSeparatedDefinitionsFolder(), markupDocBuilder.addFileExtension(IOUtils.normalizeName(definitionName))).getPath();
            else
                return defaultString(config.getInterDocumentCrossReferencesPrefix()) + markupDocBuilder.addFileExtension(config.getDefinitionsDocument());
        }
    }
}
