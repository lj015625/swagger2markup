package io.github.swagger2markup.internal.document.builder;

import io.github.swagger2markup.Swagger2MarkupConverter;
import io.github.swagger2markup.Swagger2MarkupExtensionRegistry;
import io.github.swagger2markup.internal.document.MarkupDocument;
import io.github.swagger2markup.markup.builder.MarkupLanguage;
import io.github.swagger2markup.markup.builder.MarkupTableColumn;
import io.github.swagger2markup.spi.OverviewDocumentExtension;
import io.github.swagger2markup.spi.OverviewDocumentExtension.Context;
import io.github.swagger2markup.spi.OverviewDocumentExtension.Position;

import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
/**
 * @author Leonardo Ji
 */
public class JiraDocumentBuilder extends MarkupDocumentBuilder 
{
    private final String TITLE;
    private final String LINK_COLUMN;
    private final String CR_COLUMN;
    private final String SUMMARY_COLUMN;
    private final String TOTAL;
    private static final String OVERVIEW_ANCHOR = "Jira";
    private static final String COLON = " : ";
    private final URL jiraJQLURL;
    
    
    public JiraDocumentBuilder(Swagger2MarkupConverter.Context context, Swagger2MarkupExtensionRegistry extensionRegistry, Path outputPath){
        super(context, extensionRegistry, outputPath);
        jiraJQLURL = context.getJiraJQLURL();
        ResourceBundle labels = ResourceBundle.getBundle("io/github/swagger2markup/lang/labels", config.getOutputLanguage().toLocale());
        TITLE = labels.getString("jira");
        LINK_COLUMN = labels.getString("link_column");
        CR_COLUMN = labels.getString("cr_column");
        SUMMARY_COLUMN = labels.getString("summary_column");
        TOTAL = labels.getString("total");
    }

    @Override
    public MarkupDocument build()
    {
        applyJiraDocumentExtension(new Context(Position.DOCUMENT_BEFORE, this.markupDocBuilder));
        buildTitle(TITLE);
        applyJiraDocumentExtension(new Context(Position.DOCUMENT_BEGIN, this.markupDocBuilder));
        buildDescriptionParagraph("List of JIRA items are auto retrieved from JIRA.", this.markupDocBuilder);

        ClientConfig config = new DefaultClientConfig();
        Client client = Client.create(config);
        WebResource webResource = client.resource(jiraJQLURL.toString());
        ClientResponse clientResponse = webResource.get(ClientResponse.class);
      
        String json = null;
        if (clientResponse.getStatus() == 200)
        {
            json = clientResponse.getEntity(String.class);
        }
        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = gsonBuilder.create();
        JiraSummary jiraSummary = null;
        if(json != null)
        {
            jiraSummary = gson.fromJson(json, JiraSummary.class);
        }
        buildJiraInfoSection(jiraSummary.getTotal());
        List<List<String>> cells = new ArrayList<>();
        List<MarkupTableColumn> cols = Arrays.asList(
                new MarkupTableColumn(NAME_COLUMN).withWidthRatio(1).withHeaderColumn(true).withMarkupSpecifiers(MarkupLanguage.ASCIIDOC, ".^1h"),
                new MarkupTableColumn(LINK_COLUMN).withWidthRatio(1).withHeaderColumn(true).withMarkupSpecifiers(MarkupLanguage.ASCIIDOC, ".^3h"),
                new MarkupTableColumn(CR_COLUMN).withWidthRatio(1).withHeaderColumn(true).withMarkupSpecifiers(MarkupLanguage.ASCIIDOC, ".^1h"),
                new MarkupTableColumn(SUMMARY_COLUMN).withWidthRatio(6).withMarkupSpecifiers(MarkupLanguage.ASCIIDOC, ".^5"));
        for (Issues issue : jiraSummary.getIssues())
        {
            String name = issue.getKey();
            String link = issue.getSelf();
            String cr = issue.getFields().getCustomfield_10292();
            String summary = issue.getFields().getSummary();
            
            cells.add(Arrays.asList(name, link, cr, summary));
            
        }
        markupDocBuilder.tableWithColumnSpecs(cols, cells);

        applyJiraDocumentExtension(new Context(Position.DOCUMENT_END, this.markupDocBuilder));
        return new MarkupDocument(markupDocBuilder);
    }

    private void buildTitle(String title) {
        this.markupDocBuilder.sectionTitleWithAnchorLevel1(title, OVERVIEW_ANCHOR);
    }

    private void buildJiraInfoSection(int totalCount)
    {
        this.markupDocBuilder.sectionTitleLevel2(TITLE);
        this.markupDocBuilder.textLine(TOTAL + COLON + totalCount);
    }
    
    /**
     * Apply extension context to all OverviewContentExtension
     *
     * @param context context
     */
    private void applyJiraDocumentExtension(Context context) {
        for (OverviewDocumentExtension extension : extensionRegistry.getOverviewDocumentExtensions()) {
            extension.apply(context);
        }
    }

}
