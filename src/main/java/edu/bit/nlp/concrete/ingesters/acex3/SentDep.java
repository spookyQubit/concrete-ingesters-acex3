package edu.bit.nlp.concrete.ingesters.acex3;

public class SentDep {
    public int gov;
    public int dep;
    public String type;

    public SentDep(int gov, int dep, String type) {
        this.gov = gov;
        this.dep = dep;
        this.type = type;
    }

    public int getGov() {
        return gov;
    }

    public int getDep() {
        return dep;
    }

    public String getType() {
        return type;
    }

    public void setGov(int gov) {
        this.gov = gov;
    }

    public void setDep(int dep) {
        this.dep = dep;
    }

    public void setType(String type) {
        this.type = type;
    }

}
