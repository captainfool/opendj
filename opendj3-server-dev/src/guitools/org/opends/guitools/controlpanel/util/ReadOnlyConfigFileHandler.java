/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.guitools.controlpanel.util;

import static org.opends.messages.ConfigMessages.*;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.admin.Configuration;
import org.opends.server.api.ConfigHandler;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.AttributeType;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryEnvironmentConfig;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.IndexType;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.RestoreConfig;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.StaticUtils;

/**
 * A class used to read the configuration from a file.  This config file
 * handler does not allow to modify the configuration, only to read it.
 */
public class ReadOnlyConfigFileHandler extends ConfigHandler
{
//The mapping that holds all of the configuration entries that have been read
  // from the LDIF file.
  private HashMap<DN,ConfigEntry> configEntries = new HashMap<DN,ConfigEntry>();

//The reference to the configuration root entry.
  private ConfigEntry configRootEntry;

  // The server root
  private String serverRoot;

  // The instance root
  private String instanceRoot;

  private final Set<String> emptyStringSet = new HashSet<String>();

  private DN[] baseDNs;

  /**
   * {@inheritDoc}
   */
  @Override
  public void finalizeConfigHandler()
  {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ConfigEntry getConfigEntry(DN entryDN) throws ConfigException
  {
    return configEntries.get(entryDN);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ConfigEntry getConfigRootEntry() throws ConfigException
  {
    return configRootEntry;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getServerRoot()
  {
    return serverRoot;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getInstanceRoot()
  {
    return instanceRoot;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public synchronized void initializeConfigHandler(String configFile,
      boolean checkSchema)
  throws InitializationException
  {
    File f = new File(configFile);
    // We will use the LDIF reader to read the configuration file.  Create an
    // LDIF import configuration to do this and then get the reader.
    LDIFReader reader = null;
    try
    {
      try
      {
        LDIFImportConfig importConfig =
          new LDIFImportConfig(f.getAbsolutePath());

        reader = new LDIFReader(importConfig);
      }
      catch (Throwable t)
      {
        throw new InitializationException(
            ERR_CONFIG_FILE_CANNOT_OPEN_FOR_READ.get(f.getAbsolutePath(), t), t);
      }

      if (! f.exists())
      {
        LocalizableMessage message =
          ERR_CONFIG_FILE_DOES_NOT_EXIST.get(f.getAbsolutePath());
        throw new InitializationException(message);
      }

      configEntries.clear();

      // Read the first entry from the configuration file.
      Entry entry;
      try
      {
        entry = reader.readEntry(checkSchema);
        if (entry == null)
        {
          LocalizableMessage message = ERR_CONFIG_FILE_EMPTY.get(f.getAbsolutePath());
          throw new InitializationException(message);
        }
        configRootEntry = new ConfigEntry(entry, null);

        baseDNs = new DN[] { configRootEntry.getDN() };

        configEntries.put(entry.getName(), configRootEntry);
        // Iterate through the rest of the configuration file and process the
        // remaining entries.
        while (entry != null)
        {
          // Read the next entry from the configuration.
          entry = reader.readEntry(checkSchema);
          if (entry != null)
          {
            DN entryDN = entry.getName();
            DN parentDN = entryDN.parent();
            ConfigEntry parentEntry = null;
            if (parentDN != null)
            {
              parentEntry = configEntries.get(parentDN);
            }
            if (parentEntry == null)
            {
              if (parentDN == null)
              {
                LocalizableMessage message = ERR_CONFIG_FILE_UNKNOWN_PARENT.get(
                    entryDN, reader.getLastEntryLineNumber(), f.getAbsolutePath());
                throw new InitializationException(message);
              }
              else
              {
                LocalizableMessage message = ERR_CONFIG_FILE_NO_PARENT.get(entryDN,
                    reader.getLastEntryLineNumber(), f.getAbsolutePath(), parentDN);
                throw new InitializationException(message);
              }
            }
            else
            {
              ConfigEntry configEntry = new ConfigEntry(entry, parentEntry);
              parentEntry.addChild(configEntry);
              configEntries.put(entryDN, configEntry);
            }
          }
        }
      }
      catch (InitializationException ie)
      {
        throw ie;
      }
      catch (LDIFException le)
      {
        throw new InitializationException(
            ERR_CONFIG_FILE_INVALID_LDIF_ENTRY.get(le.getLineNumber(), f.getAbsolutePath(), le), le);
      }
      catch (Throwable t)
      {
        throw new InitializationException(
            ERR_CONFIG_FILE_READ_ERROR.get(f.getAbsolutePath(), t), t);
      }


      // Determine the appropriate server root.
      File rootFile = DirectoryServer.getEnvironmentConfig().getServerRoot();
      serverRoot = rootFile.getAbsolutePath();

      File instanceRootFile =
        DirectoryEnvironmentConfig.getInstanceRootFromServerRoot(rootFile);
      instanceRoot = instanceRootFile.getAbsolutePath();
    }
    catch (InitializationException ie)
    {
      throw ie;
    }
    catch (Throwable t)
    {
    }
    finally
    {
      StaticUtils.close(reader);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void writeSuccessfulStartupConfig()
  {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void writeUpdatedConfig() throws DirectoryException
  {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void addEntry(Entry arg0, AddOperation arg1)
  throws DirectoryException, CanceledOperationException
  {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void configureBackend(Configuration arg0) throws ConfigException
  {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void createBackup(BackupConfig arg0) throws DirectoryException
  {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteEntry(DN arg0, DeleteOperation arg1)
  throws DirectoryException, CanceledOperationException
  {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void exportLDIF(LDIFExportConfig arg0) throws DirectoryException
  {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void finalizeBackend()
  {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DN[] getBaseDNs()
  {
    return baseDNs;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Entry getEntry(DN entryDN)
  throws DirectoryException
  {
    ConfigEntry configEntry = configEntries.get(entryDN);
    if (configEntry == null)
    {
      return null;
    }
    else
    {
      return configEntry.getEntry();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long getEntryCount()
  {
    return configEntries.size();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Set<String> getSupportedControls()
  {
    return emptyStringSet;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Set<String> getSupportedFeatures()
  {
    return emptyStringSet;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ConditionResult hasSubordinates(DN entryDN) throws DirectoryException
  {
    ConfigEntry baseEntry = configEntries.get(entryDN);
    if(baseEntry == null)
    {
      return ConditionResult.UNDEFINED;
    }
    else if(baseEntry.hasChildren())
    {
      return ConditionResult.TRUE;
    }
    else
    {
      return ConditionResult.FALSE;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public LDIFImportResult importLDIF(LDIFImportConfig arg0)
  throws DirectoryException
  {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void initializeBackend()
  throws ConfigException, InitializationException
  {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isIndexed(AttributeType arg0, IndexType arg1)
  {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isLocal()
  {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long numSubordinates(DN entryDN, boolean subtree)
  throws DirectoryException
  {
    ConfigEntry baseEntry = configEntries.get(entryDN);
    if (baseEntry == null)
    {
      return -1;
    }

    if(!subtree)
    {
      return baseEntry.getChildren().size();
    }
    else
    {
      long count = 0;
      for(ConfigEntry child : baseEntry.getChildren().values())
      {
        count += numSubordinates(child.getDN(), true);
        count ++;
      }
      return count;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void preloadEntryCache() throws UnsupportedOperationException
  {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void removeBackup(BackupDirectory arg0, String arg1)
  throws DirectoryException
  {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void renameEntry(DN arg0, Entry arg1, ModifyDNOperation arg2)
  throws DirectoryException, CanceledOperationException
  {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void replaceEntry(Entry arg0, Entry arg1, ModifyOperation arg2)
  throws DirectoryException, CanceledOperationException
  {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void restoreBackup(RestoreConfig arg0) throws DirectoryException
  {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void search(SearchOperation arg0)
  throws DirectoryException, CanceledOperationException
  {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean supportsBackup()
  {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean supportsBackup(BackupConfig arg0, StringBuilder arg1)
  {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean supportsLDIFExport()
  {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean supportsLDIFImport()
  {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean supportsRestore()
  {
    return false;
  }
}
