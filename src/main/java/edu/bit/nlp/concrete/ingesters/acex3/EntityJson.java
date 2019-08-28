package edu.bit.nlp.concrete.ingesters.acex3;

public class EntityJson {
    private int startIndex;
    private int endIndex;
    private String entityType;
    private String entityText;

    public void setStartIndex(int startIndex) {
        this.startIndex = startIndex;
    }

    public void setEndIndex(int endIndex) {
        this.endIndex = endIndex;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public void setEntityText(String entityText) {
        this.entityText = entityText;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public int getEndIndex() {
        return endIndex;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityText() {
        return entityText;
    }

    public EntityJson(int startIndex, int endIndex, String entityType, String entityText){
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.entityType = entityType;
        this.entityText = entityText;
    }


}
