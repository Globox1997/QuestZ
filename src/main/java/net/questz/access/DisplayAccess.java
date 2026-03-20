package net.questz.access;

import java.util.Map;

public interface DisplayAccess {

    void setManualPosition(int x, int y);

    int getManualX();

    int getManualY();

    int getTabOrder();

    void setTabOrder(int order);

    void setCriterionOverrides(Map<String, String> overrides);

    Map<String, String> getCriterionOverrides();
}
