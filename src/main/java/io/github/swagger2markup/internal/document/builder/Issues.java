package io.github.swagger2markup.internal.document.builder;

public class Issues
{
    private String expand;
    private String id;
    private String self;
    private String key;
    private Fields fields;
    
    public String getExpand()
    {
        return expand;
    }
    public void setExpand(String expand)
    {
        this.expand = expand;
    }
    public String getId()
    {
        return id;
    }
    public void setId(String id)
    {
        this.id = id;
    }
    public String getSelf()
    {
        return self;
    }
    public void setSelf(String self)
    {
        this.self = self;
    }
    public String getKey()
    {
        return key;
    }
    public void setKey(String key)
    {
        this.key = key;
    }
    public Fields getFields()
    {
        return fields;
    }
    public void setFields(Fields fields)
    {
        this.fields = fields;
    }
}
