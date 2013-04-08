/*
 * Sonar, open source software quality management tool.
 * Copyright (C) 2008-2012 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * Sonar is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Sonar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Sonar; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.api.scan.source;

import com.google.common.collect.ImmutableList;

public final class HighlightableTextType {

  public static final String ANNOTATION = "a";
  public static final String LITERAL = "s";
  public static final String LINE_COMMENT = "cd";
  public static final String BLOCK_COMMENT = "cppd";
  public static final String CONSTANT = "c";
  public static final String KEYWORD = "k";

  private static final ImmutableList<String> SUPPORTED_TEXT_TYPES = ImmutableList.of(
          ANNOTATION, LITERAL, LINE_COMMENT, BLOCK_COMMENT, CONSTANT, KEYWORD);

  public static boolean supports(String textType) {
    return SUPPORTED_TEXT_TYPES.contains(textType);
  }
}