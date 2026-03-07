package net.questz.access;

public interface DisplayAccess {

    void setManualPosition(int x, int y);

    int getManualX();

    int getManualY();

    int getTabOrder();

    void setTabOrder(int order);
}
