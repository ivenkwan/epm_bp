package org.kie.kogito.toolchain;

import org.drools.ruleunits.api.DataSource;
import org.drools.ruleunits.api.DataStore;
import org.drools.ruleunits.api.RuleUnitData;

public class ToolchainUnit implements RuleUnitData {
  private final DataStore<String> strings = DataSource.createStore();

  public DataStore<String> getStrings() {
    return strings;
  }
}
