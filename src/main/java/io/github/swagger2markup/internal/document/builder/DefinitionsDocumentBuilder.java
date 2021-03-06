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

import com.google.common.base.Optional;
import io.github.swagger2markup.Swagger2MarkupConverter;
import io.github.swagger2markup.Swagger2MarkupExtensionRegistry;
import io.github.swagger2markup.internal.document.MarkupDocument;
import io.github.swagger2markup.internal.type.ObjectType;
import io.github.swagger2markup.internal.type.RefType;
import io.github.swagger2markup.internal.type.Type;
import io.github.swagger2markup.internal.utils.ModelUtils;
import io.github.swagger2markup.markup.builder.MarkupDocBuilder;
import io.github.swagger2markup.spi.DefinitionsDocumentExtension;
import io.swagger.models.Model;
import io.swagger.models.properties.Property;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Validate;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

import static io.github.swagger2markup.internal.utils.MapUtils.toKeySet;
import static io.github.swagger2markup.spi.DefinitionsDocumentExtension.Context;
import static io.github.swagger2markup.spi.DefinitionsDocumentExtension.Position;
import static io.github.swagger2markup.utils.IOUtils.normalizeName;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * @author Robert Winkler
 */
public class DefinitionsDocumentBuilder extends MarkupDocumentBuilder {

    private static final String DEFINITIONS_ANCHOR = "definitions";
    private final String DEFINITIONS;
    private final String TYPE_COLUMN;
    private static final List<String> IGNORED_DEFINITIONS = Collections.singletonList("Void");
    private static final String DESCRIPTION_FILE_NAME = "description";

    public DefinitionsDocumentBuilder(Swagger2MarkupConverter.Context context, Swagger2MarkupExtensionRegistry extensionRegistry, Path outputPath) {
        super(context, extensionRegistry, outputPath);

        ResourceBundle labels = ResourceBundle.getBundle("io/github/swagger2markup/lang/labels", config.getOutputLanguage().toLocale());
        DEFINITIONS = labels.getString("definitions");
        TYPE_COLUMN = labels.getString("type_column");

        if (config.isDefinitionDescriptionsEnabled()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Include hand-written definition descriptions is enabled.");
            }
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("Include hand-written definition descriptions is disabled.");
            }
        }
        if (config.isSeparatedDefinitionsEnabled()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Create separated definition files is enabled.");
            }
            Validate.notNull(outputPath, "Output directory is required for separated definition files!");
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("Create separated definition files is disabled.");
            }
        }
    }

    /**
     * Builds the definitions MarkupDocument.
     *
     * @return the definitions MarkupDocument
     */
    @Override
    public MarkupDocument build() {
        if (MapUtils.isNotEmpty(globalContext.getSwagger().getDefinitions())) {
            applyDefinitionsDocumentExtension(new Context(Position.DOCUMENT_BEFORE, this.markupDocBuilder));
            buildDefinitionsTitle(DEFINITIONS);
            applyDefinitionsDocumentExtension(new Context(Position.DOCUMENT_BEGIN, this.markupDocBuilder));
            buildDefinitionsSection();
            applyDefinitionsDocumentExtension(new Context(Position.DOCUMENT_END, this.markupDocBuilder));
        }
        return new MarkupDocument(markupDocBuilder);
    }

    private void buildDefinitionsSection() {
        Set<String> definitionNames = toKeySet(globalContext.getSwagger().getDefinitions(), config.getDefinitionOrdering());
        for (String definitionName : definitionNames) {
            Model model = globalContext.getSwagger().getDefinitions().get(definitionName);
            if (isNotBlank(definitionName)) {
                if (checkThatDefinitionIsNotInIgnoreList(definitionName)) {
                    buildDefinition(definitionName, model);
                    if (logger.isInfoEnabled()) {
                        logger.info("Definition processed: {}", definitionName);
                    }
                } else {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Definition was ignored: {}", definitionName);
                    }
                }
            }
        }
    }

    private void buildDefinitionsTitle(String title) {
        this.markupDocBuilder.sectionTitleWithAnchorLevel1(title, DEFINITIONS_ANCHOR);
    }

    /**
     * Apply extension context to all DefinitionsContentExtension
     *
     * @param context context
     */
    private void applyDefinitionsDocumentExtension(Context context) {
        for (DefinitionsDocumentExtension extension : extensionRegistry.getDefinitionsDocumentExtensions()) {
            extension.apply(context);
        }
    }

    /**
     * Create the definition filename depending on the generation mode
     *
     * @param definitionName definition name
     * @return definition filename
     */
    private String resolveDefinitionDocument(String definitionName) {
        if (config.isSeparatedDefinitionsEnabled())
            return new File(config.getSeparatedDefinitionsFolder(), markupDocBuilder.addFileExtension(normalizeName(definitionName))).getPath();
        else
            return markupDocBuilder.addFileExtension(config.getDefinitionsDocument());
    }

    /**
     * Generate definition files depending on the generation mode
     *
     * @param definitionName definition name to process
     * @param model          definition model to process
     */
    private void buildDefinition(String definitionName, Model model) {

        if (config.isSeparatedDefinitionsEnabled()) {
            MarkupDocBuilder defDocBuilder = this.markupDocBuilder.copy(false);
            buildDefinition(definitionName, model, defDocBuilder);
            Path definitionFile = outputPath.resolve(resolveDefinitionDocument(definitionName));
            defDocBuilder.writeToFileWithoutExtension(definitionFile, StandardCharsets.UTF_8);
            if (logger.isInfoEnabled()) {
                logger.info("Separate definition file produced: {}", definitionFile);
            }

            definitionRef(definitionName, this.markupDocBuilder);

        } else {
            buildDefinition(definitionName, model, this.markupDocBuilder);
        }
    }

    /**
     * Checks that the definition is not in the list of ignored definitions.
     *
     * @param definitionName the name of the definition
     * @return true if the definition can be processed
     */
    private boolean checkThatDefinitionIsNotInIgnoreList(String definitionName) {
        return !IGNORED_DEFINITIONS.contains(definitionName);
    }

    /**
     * Builds a concrete definition
     *
     * @param definitionName the name of the definition
     * @param model          the Swagger Model of the definition
     * @param docBuilder     the docbuilder do use for output
     */
    private void buildDefinition(String definitionName, Model model, MarkupDocBuilder docBuilder) {
        buildDefinitionTitle(definitionName, definitionName, docBuilder);
        applyDefinitionsDocumentExtension(new Context(Position.DEFINITION_BEGIN, docBuilder, definitionName, model));
        buildDescriptionParagraph(definitionName, model, docBuilder);
        inlineDefinitions(typeSection(definitionName, model, docBuilder), definitionName, config.getInlineSchemaDepthLevel(), docBuilder);
        applyDefinitionsDocumentExtension(new Context(Position.DEFINITION_END, docBuilder, definitionName, model));
    }

    /**
     * Builds a cross-reference to a separated definition file.
     *
     * @param definitionName definition name to target
     * @param docBuilder     the docbuilder do use for output
     */
    private void definitionRef(String definitionName, MarkupDocBuilder docBuilder) {
        buildDefinitionTitle(docBuilder.copy(false).crossReference(new DefinitionDocumentResolverDefault().apply(definitionName), definitionName, definitionName).toString(), "ref-" + definitionName, docBuilder);
    }

    /**
     * Builds definition title
     *
     * @param title      definition title
     * @param anchor     optional anchor (null => auto-generate from title)
     * @param docBuilder the docbuilder do use for output
     */
    private void buildDefinitionTitle(String title, String anchor, MarkupDocBuilder docBuilder) {
        docBuilder.sectionTitleWithAnchorLevel2(title, anchor);
    }

    /**
     * Override Property description functor for definitions.
     * This implementation handles optional handwritten descriptions.
     */
    private class DefinitionPropertyDescriptor extends PropertyDescriptor {

        public DefinitionPropertyDescriptor(Type type) {
            super(type);
        }

        @Override
        public String getDescription(Property property, String propertyName) {
            if (config.isDefinitionDescriptionsEnabled()) {
                Optional<String> description = handWrittenDefinitionDescription(new File(normalizeName(type.getName()), normalizeName(propertyName)).toString(), DESCRIPTION_FILE_NAME);
                if (description.isPresent()) {
                    return description.get();
                } else {
                    return defaultString(property.getDescription());
                }
            } else {
                return defaultString(property.getDescription());
            }
        }
    }

    /**
     * Builds the type informations of a definition
     *
     * @param definitionName name of the definition to display
     * @param model          model of the definition to display
     * @param docBuilder     the docbuilder do use for output
     * @return a list of inlined types.
     */
    private List<ObjectType> typeSection(String definitionName, Model model, MarkupDocBuilder docBuilder) {
        List<ObjectType> localDefinitions = new ArrayList<>();
        Type modelType = ModelUtils.getType(model, globalContext.getSwagger().getDefinitions(), new DefinitionDocumentResolverFromDefinition());

        if (modelType instanceof ObjectType || modelType instanceof RefType) {
            localDefinitions.addAll(buildPropertiesTable(ModelUtils.getTypeProperties(modelType), definitionName, 1, new PropertyDescriptor(modelType), new DefinitionDocumentResolverFromDefinition(), docBuilder));
        } else if (modelType != null) {
            MarkupDocBuilder typeInfos = docBuilder.copy(false);
            typeInfos.italicText(TYPE_COLUMN).textLine(": " + modelType.displaySchema(docBuilder));

            docBuilder.paragraph(typeInfos.toString());
        }

        return localDefinitions;
    }
    
    private void buildDescriptionParagraph(String definitionName, Model model, MarkupDocBuilder docBuilder) {
        if (config.isDefinitionDescriptionsEnabled()) {
            Optional<String> description = handWrittenDefinitionDescription(normalizeName(definitionName), DESCRIPTION_FILE_NAME);
            if (description.isPresent()) {
                buildDescriptionParagraph(description.get(), docBuilder);
            } else {
                modelDescription(model, docBuilder);
            }
        } else {
            modelDescription(model, docBuilder);
        }
    }

    private void modelDescription(Model model, MarkupDocBuilder docBuilder) {
        String description = model.getDescription();
        if (isNotBlank(description)) {
            buildDescriptionParagraph(description, docBuilder);
        }
    }

    /**
     * Reads a hand-written description
     *
     * @param descriptionFolder   the name of the folder where the description file resides
     * @param descriptionFileName the name of the description file
     * @return the content of the file
     */
    private Optional<String> handWrittenDefinitionDescription(String descriptionFolder, String descriptionFileName) {
        for (String fileNameExtension : config.getMarkupLanguage().getFileNameExtensions()) {
            URI contentUri = config.getDefinitionDescriptionsUri().resolve(descriptionFolder).resolve(descriptionFileName + fileNameExtension);

            try (Reader reader = io.github.swagger2markup.utils.IOUtils.uriReader(contentUri)) {
                if (logger.isInfoEnabled()) {
                    logger.info("Definition description content processed {}", contentUri);
                }

                return Optional.of(IOUtils.toString(reader).trim());
            } catch (IOException e) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Failed to read Operation description content {} > {}", contentUri, e.getMessage());
                }
            }
        }

        return Optional.absent();
    }

    /**
     * Builds the title of an inline schema.
     * Inline definitions should never been referenced in TOC because they have no real existence, so they are just text.
     *
     * @param title      inline schema title
     * @param anchor     inline schema anchor
     * @param docBuilder the docbuilder do use for output
     */
    private void addInlineDefinitionTitle(String title, String anchor, MarkupDocBuilder docBuilder) {
        docBuilder.anchor(anchor, null);
        docBuilder.newLine();
        docBuilder.boldTextLine(title);
    }

    /**
     * Builds inline schema definitions
     *
     * @param definitions  all inline definitions to display
     * @param uniquePrefix unique prefix to prepend to inline object names to enforce unicity
     * @param depth        current inline schema depth
     * @param docBuilder   the docbuilder do use for output
     */
    private void inlineDefinitions(List<ObjectType> definitions, String uniquePrefix, int depth, MarkupDocBuilder docBuilder) {
        if (CollectionUtils.isNotEmpty(definitions)) {
            for (ObjectType definition : definitions) {
                addInlineDefinitionTitle(definition.getName(), definition.getUniqueName(), docBuilder);
                List<ObjectType> localDefinitions = buildPropertiesTable(definition.getProperties(), uniquePrefix, depth, new DefinitionPropertyDescriptor(definition), new DefinitionDocumentResolverFromDefinition(), docBuilder);
                for (ObjectType localDefinition : localDefinitions)
                    inlineDefinitions(Collections.singletonList(localDefinition), uniquePrefix, depth - 1, docBuilder);
            }
        }
    }

    /**
     * Overrides definition document resolver functor for inter-document cross-references from definitions files.
     * This implementation simplify the path between two definitions because all definitions are in the same path.
     */
    class DefinitionDocumentResolverFromDefinition extends DefinitionDocumentResolverDefault {

        public DefinitionDocumentResolverFromDefinition() {
        }

        public String apply(String definitionName) {
            String defaultResolver = super.apply(definitionName);

            if (defaultResolver != null && config.isSeparatedDefinitionsEnabled())
                return defaultString(config.getInterDocumentCrossReferencesPrefix()) + markupDocBuilder.addFileExtension(normalizeName(definitionName));
            else
                return defaultResolver;
        }
    }
}
