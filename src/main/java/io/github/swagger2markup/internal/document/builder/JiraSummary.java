package io.github.swagger2markup.internal.document.builder;

import java.util.List;

public class JiraSummary
{
    private String expand;
    private int maxResults;
    private int total;
    private String startAt;
    private List<Issues> issues;

    public int getMaxResults()
    {
        return maxResults;
    }

    public void setMaxResults(int maxResults)
    {
        this.maxResults = maxResults;
    }

    public int getTotal()
    {
        return total;
    }

    public void setTotal(int total)
    {
        this.total = total;
    }

    public List<Issues> getIssues()
    {
        return issues;
    }

    public void setIssues(List<Issues> issues)
    {
        this.issues = issues;
    }

    public String getExpand()
    {
        return expand;
    }

    public void setExpand(String expand)
    {
        this.expand = expand;
    }

    public String getStartAt()
    {
        return startAt;
    }

    public void setStartAt(String startAt)
    {
        this.startAt = startAt;
    }
}