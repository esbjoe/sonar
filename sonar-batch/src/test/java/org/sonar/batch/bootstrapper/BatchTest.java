/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2013 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.batch.bootstrapper;

import org.junit.Test;
import org.sonar.api.batch.bootstrap.ProjectReactor;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class BatchTest {
  @Test
  public void testBuilder() {
    Batch batch = newBatch();
    assertNotNull(batch);

  }

  private Batch newBatch() {
    return Batch.builder()
      .setEnvironment(new EnvironmentInformation("Gradle", "1.0"))
      .setProjectReactor(new ProjectReactor(org.sonar.api.batch.bootstrap.ProjectDefinition.create()))
      .addComponent("fake")
      .build();
  }

  @Test(expected = IllegalStateException.class)
  public void shouldFailIfNullComponents() {
    Batch.builder()
      .setProjectReactor(new ProjectReactor(org.sonar.api.batch.bootstrap.ProjectDefinition.create()))
      .setEnvironment(new EnvironmentInformation("Gradle", "1.0"))
      .setComponents(null)
      .build();
  }

  @Test
  public void shouldDisableLoggingConfiguration() {
    Batch batch = Batch.builder()
      .setEnvironment(new EnvironmentInformation("Gradle", "1.0"))
      .setProjectReactor(new ProjectReactor(org.sonar.api.batch.bootstrap.ProjectDefinition.create()))
      .addComponent("fake")
      .setEnableLoggingConfiguration(false)
      .build();
    assertNull(batch.getLoggingConfiguration());
  }

  @Test
  public void loggingConfigurationShouldBeEnabledByDefault() {
    assertNotNull(newBatch().getLoggingConfiguration());
  }
}
