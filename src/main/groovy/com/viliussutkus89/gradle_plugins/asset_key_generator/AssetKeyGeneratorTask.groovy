/*
 * AssetKeyGeneratorTask.groovy
 *
 * Copyright (C) 2020 Vilius Sutkus'89 <ViliusSutkus89@gmail.com>
 *
 * AssetExtractor-Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as published by
 * the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viliussutkus89.gradle_plugins.asset_key_generator

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileType
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.work.ChangeType
import org.gradle.work.FileChange
import org.gradle.work.Incremental
import org.gradle.work.InputChanges

import java.security.MessageDigest

abstract class AssetKeyGeneratorTask extends DefaultTask {
  @Incremental
  @PathSensitive(PathSensitivity.RELATIVE)
  @InputDirectory
  abstract DirectoryProperty getInputDir()

  @OutputDirectory
  abstract DirectoryProperty getOutputDir()

  @OutputFile
  abstract Property<File> getOutputFile()

  private Map<String, Map<String, Object>> m_directoriesToRehash;
  private Map<String, Object> m_assetKeys;

  @TaskAction
  void execute(InputChanges inputChanges) {
    println(inputChanges.incremental
            ? 'Executing incrementally'
            : 'Executing non-incrementally'
    )

    loadFile(inputChanges.incremental)

    m_directoriesToRehash = new TreeMap<>({ a, b -> b <=> a })
    m_directoriesToRehash.put(File.separator, m_assetKeys)

    for (FileChange change : inputChanges.getFileChanges(inputDir)) {
      if (change.fileType == FileType.DIRECTORY) {
        continue
      }

      if (change.normalizedPath == outputFile.get().name) {
        continue
      }

      List<String> fileNameWithPath = change.normalizedPath.tokenize(File.separator)
      if (change.changeType == ChangeType.REMOVED) {
        removeEntry(m_assetKeys, fileNameWithPath, new StringBuilder())
      } else {
        Map<String, Object> directory = getEntryDirectory(m_assetKeys, fileNameWithPath, new StringBuilder())
        Map<String, Object> entriesInDirectory = directory.get('entries') as Map<String, Object>
        updateFileKey(change.file, entriesInDirectory)
      }
    }

    for (Map.Entry<String, Map<String, Object>> directory : m_directoriesToRehash) {
      updateDirectoryKey(directory)
    }

    saveFile()
  }

  //@TODO: file became directory
  //@TODO: directory became file
  private Map<String, Object> getEntryDirectory(Map<String, Object> assetKeys, List<String> path, StringBuilder pathTraversed) {
    Map<String, Object> entries = (Map<String, Object>) assetKeys.get("entries", [:])
    String entryName = path.pop()

    if (path.isEmpty()) {
      entries.get(entryName, null)
      return assetKeys;
    }

    Map<String, Object> emptyDirectory = [key: null, entries: [:]]
    Map<String, Object> currentDirectory = (Map<String, Object>) entries.get(entryName, emptyDirectory)

    pathTraversed.append(File.separator)
    pathTraversed.append(entryName)

    m_directoriesToRehash.put(pathTraversed.toString(), currentDirectory)

    return getEntryDirectory(currentDirectory, path, pathTraversed)
  }

  private void removeEntry(Map<String, Object> assetKeys, List<String> path, StringBuilder pathTraversed) {
    Map<String, Object> entries = (Map<String, Object>) assetKeys.get("entries")
    if (null == entries) {
      return
    }

    String entryName = path.pop()

    Object entry = entries.get(entryName)
    if (path.isEmpty() || entry instanceof String) {
      if (path.isEmpty() && entry instanceof String) {
        entries.remove(entryName)
      }
      return;
    }

    if (!entry instanceof Map<String, Object>) {
      return
    }
    removeEntry((Map<String, Object>) entry, path, pathTraversed)

    //@TODO:
//    pathTraversed.append(File.separator)
//    pathTraversed.append(entryName)
//    m_directoriesToRehash.put(pathTraversed.toString(), currentDirectory)
//    return getEntryDirectory(currentDirectory, path, pathTraversed)
  }

  private static void updateFileKey(File inputFile, Map<String, Object> entriesInDirectory) {
    MessageDigest messageDigest = MessageDigest.getInstance("SHA-256")
    messageDigest.update(inputFile.bytes)
    String fileHash = messageDigest.digest().encodeHex()
    entriesInDirectory.put(inputFile.name, fileHash)
  }

  private static void updateDirectoryKey(Map.Entry<String, Map<String, Object>> directory) {
    String directoryName = directory.key
    Map<String, Object> directoryEntry = directory.value

    StringBuilder calculatedKey = new StringBuilder(directoryName)

    Map<String, Object> entries = directoryEntry.get('entries') as Map<String, Object>
    for (Map.Entry<String, Object> subEntry in entries) {
      if (subEntry.value instanceof String) {
        calculatedKey.append("f:")
        calculatedKey.append(subEntry)
      } else {
        calculatedKey.append("d:")
        calculatedKey.append(subEntry.value.get('key'))
      }
      calculatedKey.append("-")
    }

    MessageDigest messageDigest = MessageDigest.getInstance("SHA-256")
    messageDigest.update(calculatedKey.toString().bytes)
    String directoryHash = messageDigest.digest().encodeHex()
    directoryEntry.put('key', directoryHash)
  }

  private void loadFile(boolean loadFromFile) {
    if (loadFromFile) {
      logger.info "Loading json from ${outputFile.get()}"
      m_assetKeys = new JsonSlurper().parse(outputFile.get()) as Map<String, Object>
    } else {
      logger.info "Creating new database for ${outputFile.get()}"
      m_assetKeys = [version: 1, key: null, entries: [:]]
    }
  }

  private void saveFile() {
    logger.info "Saving json to ${outputFile.get()}"
    outputFile.get().text = JsonOutput.prettyPrint(JsonOutput.toJson(m_assetKeys))
  }

}
