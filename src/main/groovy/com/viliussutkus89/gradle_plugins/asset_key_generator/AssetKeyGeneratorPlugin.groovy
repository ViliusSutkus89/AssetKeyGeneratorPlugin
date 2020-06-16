/*
 * AssetKeyGeneratorPlugin.groovy
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

import org.gradle.api.Plugin
import org.gradle.api.Project

class AssetKeyGeneratorPlugin implements Plugin<Project> {
  @Override
  void apply(Project project) {
    def wrapperTask = project.tasks.create('generateAssetKeys')

    def id = 0
    project.android.sourceSets.each { srcSet ->
      srcSet.getAssets().srcDirs.each { File srcDir ->
        if (srcDir.exists()) {
          wrapperTask.dependsOn(project.tasks.create("generateAssetKeys-${++id}", AssetKeyGeneratorTask) {
            inputDir.set(srcDir)
            outputDir.set(project.buildDir)
            outputFile.set(new File(srcDir.getAbsolutePath(), "assetExtract.json"))
          })
        }
      }
    }
  }
}
