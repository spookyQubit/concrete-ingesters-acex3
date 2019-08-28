package edu.bit.nlp.concrete.ingesters.acex3;

public class TriggerJson {
    private int startIndex;
    private int endIndex;
    private String text;

    public void setEndIndex(int endIndex) {
        this.endIndex = endIndex;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setStartIndex(int startIndex) {
        this.startIndex = startIndex;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public int getEndIndex() {
        return endIndex;
    }

    public String getText() {
        return text;
    }

    public TriggerJson(int startIndex, int endIndex, String text) {
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.text = text;
    }
}
