package li.power.app.mifareuidchanger.model;

import java.util.ArrayList;

public class Settings {
    private String keyA;
    private String keyB;

    private ArrayList<UidItem> list;

    public Settings(String keyA, String keyB, ArrayList<UidItem> list) {
        this.keyA = keyA;
        this.keyB = keyB;
        this.list = list;
    }

    public Settings() {
        this.list = new ArrayList<>();
    }

    public String getKeyA() {
        return keyA;
    }

    public String getKeyB() {
        return keyB;
    }

    public ArrayList<UidItem> getList(){
        return list;
    }

    public void setKeyA(String keyA) {
        this.keyA = keyA;
    }

    public void setKeyB(String keyB) {
        this.keyB = keyB;
    }
}