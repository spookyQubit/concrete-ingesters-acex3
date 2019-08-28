package edu.bit.nlp.concrete.ingesters.acex3;

public class ArgumentJson {
    private int startIndex;
    private int endIndex;
    private String role;
    private String text;

    public void setStartIndex(int startIndex) {
        this.startIndex = startIndex;
    }

    public void setEndIndex(int endIndex) {
        this.endIndex = endIndex;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public int getEndIndex() {
        return endIndex;
    }

    public String getRole() {
        return role;
    }

    public String getText() {
        return text;
    }

    public ArgumentJson(int startIndex, int endIndex, String role, String text) {
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.role = role;
        this.text = text;
    }
}
