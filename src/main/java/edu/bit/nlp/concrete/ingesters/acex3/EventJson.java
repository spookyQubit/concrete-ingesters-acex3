package edu.bit.nlp.concrete.ingesters.acex3;
import java.util.*;

public class EventJson {
    private TriggerJson trigger;
    private List<ArgumentJson> arguments;
    private String eventType;


    public TriggerJson getTrigger() {
        return trigger;
    }

    public List<ArgumentJson> getArguments() {
        return arguments;
    }

    public void setTrigger(TriggerJson trigger) {
        this.trigger = trigger;
    }

    public void setArguments(List<ArgumentJson> arguments) {
        this.arguments = arguments;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEventType() {
        return eventType;
    }

    public EventJson(TriggerJson trigger, List<ArgumentJson> arguments, String eventType) {
        this.trigger = trigger;
        this.arguments = new ArrayList<ArgumentJson>();
        this.arguments.addAll(arguments);
        this.eventType = eventType;
    }
    public boolean equals(Object obj) {
        if (!(obj instanceof EventJson))
            return false;
        if (obj == this)
            return true;
        return this.eventType.equals(((EventJson) obj).eventType) && this.getTrigger().getText().equals(((EventJson) obj).getTrigger().getText()) ;
    }

}
