package edu.bit.nlp.concrete.ingesters.acex3;

public class WordTokenPOSDep{
    protected int mStart;
    protected int mEnd;
    protected int mNewLineCount;
    protected String mWord;
    protected String pos;
    protected int gov;
    protected String type;

    public WordTokenPOSDep(String w,  String pos, int gov, String type, int s, int e) {
        this.mWord = w;
        this.pos = pos;
        this.gov = gov;
        this.type = type;
        this.mStart = s;
        this.mEnd = e;
        this.mNewLineCount = 0;
    }

    public WordTokenPOSDep(String w,  String pos, int gov, String type, int s, int e, int nl) {
        this.mWord = w;
        this.pos = pos;
        this.gov = gov;
        this.type = type;
        this.mStart = s;
        this.mEnd = e;
        this.mNewLineCount = nl;
    }

    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("[");
        buffer.append(this.mWord);
        buffer.append(", ");
        buffer.append(this.pos);
        buffer.append(", ");
        buffer.append(this.mStart);
        buffer.append(", ");
        buffer.append(this.mEnd);
        buffer.append("]");
        return buffer.toString();
    }

    public int getStart() {
        return this.mStart;
    }

    public void setStart(int i) {
        this.mStart = i;
    }

    public int getEnd() {
        return this.mEnd;
    }

    public void setEnd(int i) {
        this.mEnd = i;
    }

    public int getNewLineCount() {
        return this.mNewLineCount;
    }

    public void setNewLineCount(int i) {
        this.mNewLineCount = i;
    }

    public String getWord() {
        return this.mWord;
    }

    public void setWord(String w) {
        this.mWord = w;
    }

    public String getPOS() {
        return this.pos;
    }

    public void setPOS(String pos) {
        this.pos = pos;
    }

    public void setGov(int gov) {
        this.gov = gov;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getGov() {
        return gov;
    }

    public String getType() {
        return type;
    }
}

