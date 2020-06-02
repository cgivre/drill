package org.apache.drill.exec.store.splunk;

import com.splunk.EntityCollection;
import com.splunk.Index;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.Table;
import org.apache.drill.exec.planner.logical.DynamicDrillTable;
import org.apache.drill.exec.store.AbstractSchema;
import org.apache.drill.exec.store.AbstractSchemaFactory;
import org.apache.drill.exec.store.SchemaConfig;
import org.apache.drill.shaded.guava.com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SplunkSchemaFactory extends AbstractSchemaFactory {

  private static final Logger logger = LoggerFactory.getLogger(SplunkSchemaFactory.class);
  private final SplunkStoragePlugin plugin;
  private final EntityCollection<Index> indexes;

  public SplunkSchemaFactory(SplunkStoragePlugin plugin) {
    super(plugin.getName());
    this.plugin = plugin;
    SplunkPluginConfig config = plugin.getConfig();
    SplunkConnection connection = new SplunkConnection(config);

    // Get Splunk Indexes
    connection.connect();
    indexes = connection.getIndexes();
  }

  @Override

  public void registerSchemas(SchemaConfig schemaConfig, SchemaPlus parent) {
    SplunkSchema schema = new SplunkSchema(plugin);
    SchemaPlus plusOfThis = parent.add(schema.getName(), schema);
  }

  class SplunkSchema extends AbstractSchema {

    private final Map<String, DynamicDrillTable> activeTables = new HashMap<>();
    private final SplunkStoragePlugin plugin;

    public SplunkSchema(SplunkStoragePlugin plugin) {
      super(Collections.emptyList(), plugin.getName());
      this.plugin = plugin;
      registerIndexes();

    }

    @Override
    public Table getTable(String name) {
      DynamicDrillTable table = activeTables.get(name);
      if (table != null) {
        // If the table was found, return it.
        return table;
      } else {
        // Register the table
        return registerTable(name, new DynamicDrillTable(plugin, plugin.getName(),
          new SplunkScanSpec(plugin.getName(), name, plugin.getConfig())));
      }
    }

    @Override
    public boolean showInInformationSchema() {
      return true;
    }

    @Override
    public Set<String> getTableNames() {
      return Sets.newHashSet(activeTables.keySet());
    }

    private DynamicDrillTable registerTable(String name, DynamicDrillTable table) {
      activeTables.put(name, table);
      return table;
    }

    @Override
    public String getTypeName() {
      return SplunkPluginConfig.NAME;
    }

    private void registerIndexes() {
      for (String indexName : indexes.keySet()) {
        logger.debug("Registering {}", indexName);
        registerTable(indexName, new DynamicDrillTable(plugin, plugin.getName(),
          new SplunkScanSpec(plugin.getName(), indexName, plugin.getConfig())));
      }
    }
  }
}
