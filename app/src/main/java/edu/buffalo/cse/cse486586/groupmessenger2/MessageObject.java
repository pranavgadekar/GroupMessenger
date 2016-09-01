package edu.buffalo.cse.cse486586.groupmessenger2;

import java.io.Serializable;
import java.util.UUID;

/**
 * Created by pranav on 3/18/16.
 * Class to store details about each message being sent and received
 * messageID is a random number unique to each message
 * suggestedSeqNumber is the suggested seq number by each server running in each process
 * status of message is false indicating that it is not deliverable
 */
public class MessageObject implements Serializable,Comparable<MessageObject>{

    public String message;
    public int messageID;
    public int suggestedSeqNumber;
    public boolean status;

    public MessageObject(String message,int messageID, int suggestedSeqNumber) {
        this.message = message;
        this.messageID = messageID;
        this.suggestedSeqNumber = suggestedSeqNumber;
        this.status = false;
    }

    public boolean isStatus() {
        return status;
    }

    public void setStatus() {
        this.status = true;
    }

    public int getSuggestedSeqNumber() {
        return suggestedSeqNumber;
    }

    public void setSuggestedSeqNumber(int suggestedSeqNumber) {
        this.suggestedSeqNumber = suggestedSeqNumber;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public int compareTo(MessageObject another) {
        if(this.suggestedSeqNumber > another.suggestedSeqNumber){
            return 1;
        }
        else if(this.suggestedSeqNumber < another.suggestedSeqNumber){
            return -1;
        }
        else{
            return 0;
        }
    }
}
