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
package org.sonar.core.measure;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import org.apache.commons.dbutils.DbUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.sonar.core.persistence.Database;
import org.sonar.core.resource.SnapshotDto;

import javax.annotation.Nullable;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Comparator;
import java.util.List;

class MeasureFilterSql {

  private static final int FETCH_SIZE = 1000;
  private final Database database;
  private final MeasureFilter filter;
  private final MeasureFilterContext context;
  private final String sql;
  private final List<Date> dateParameters = Lists.newArrayList();

  MeasureFilterSql(Database database, MeasureFilter filter, MeasureFilterContext context) {
    this.database = database;
    this.filter = filter;
    this.context = context;
    this.sql = generateSql();
  }

  List<MeasureFilterRow> execute(Connection connection) throws SQLException {
    PreparedStatement statement = connection.prepareStatement(sql);
    statement.setFetchSize(FETCH_SIZE);
    ResultSet rs = null;
    try {
      for (int index = 0; index < dateParameters.size(); index++) {
        statement.setDate(index + 1, dateParameters.get(index));
      }
      rs = statement.executeQuery();
      return process(rs);

    } finally {
      DbUtils.closeQuietly(rs);
      DbUtils.closeQuietly(statement);
    }
  }

  String sql() {
    return sql;
  }

  private String generateSql() {
    StringBuilder sb = new StringBuilder(1000);
    sb.append("SELECT s.id, s.project_id, s.root_project_id, ");
    sb.append(filter.sort().column());
    sb.append(" FROM snapshots s INNER JOIN projects p ON s.project_id=p.id ");

    for (int index = 0; index < filter.getMeasureConditions().size(); index++) {
      MeasureFilterCondition condition = filter.getMeasureConditions().get(index);
      sb.append(" INNER JOIN project_measures pmcond").append(index);
      sb.append(" ON s.id=pmcond").append(index).append(".snapshot_id AND ");
      condition.appendSqlCondition(sb, index);
    }

    if (filter.isOnFavourites()) {
      sb.append(" INNER JOIN properties props ON props.resource_id=s.project_id ");
    }

    if (filter.sort().isOnMeasure()) {
      sb.append(" LEFT OUTER JOIN project_measures pmsort ON s.id=pmsort.snapshot_id AND pmsort.metric_id=");
      sb.append(filter.sort().metric().getId());
      sb.append(" AND pmsort.rule_id IS NULL AND pmsort.rule_priority IS NULL AND pmsort.characteristic_id IS NULL AND pmsort.person_id IS NULL ");
    }

    sb.append(" WHERE ");
    appendResourceConditions(sb);

    for (int index = 0; index < filter.getMeasureConditions().size(); index++) {
      MeasureFilterCondition condition = filter.getMeasureConditions().get(index);
      sb.append(" AND ");
      condition.appendSqlCondition(sb, index);
    }

    return sb.toString();
  }

  private void appendResourceConditions(StringBuilder sb) {
    sb.append(" s.status='P' AND s.islast=").append(database.getDialect().getTrueSqlValue());
    if (context.getBaseSnapshot() == null) {
      sb.append(" AND p.copy_resource_id IS NULL ");
    }
    if (!filter.getResourceQualifiers().isEmpty()) {
      sb.append(" AND s.qualifier IN ");
      appendInStatement(filter.getResourceQualifiers(), sb);
    }
    if (!filter.getResourceScopes().isEmpty()) {
      sb.append(" AND s.scope IN ");
      appendInStatement(filter.getResourceScopes(), sb);
    }
    if (!filter.getResourceLanguages().isEmpty()) {
      sb.append(" AND p.language IN ");
      appendInStatement(filter.getResourceLanguages(), sb);
    }
    appendDateConditions(sb);
    appendFavouritesCondition(sb);
    appendResourceNameCondition(sb);
    appendResourceKeyCondition(sb);
    appendResourceBaseCondition(sb);
  }

  private void appendDateConditions(StringBuilder sb) {
    if (filter.getFromDate() != null) {
      sb.append(" AND s.created_at >= ? ");
      dateParameters.add(new Date(filter.getFromDate().getTime()));
    }
    if (filter.getToDate() != null) {
      sb.append(" AND s.created_at <= ? ");
      dateParameters.add(new Date(filter.getToDate().getTime()));
    }
  }

  private void appendFavouritesCondition(StringBuilder sb) {
    if (filter.isOnFavourites()) {
      sb.append(" AND props.prop_key='favourite' AND props.resource_id IS NOT NULL AND props.user_id=");
      sb.append(context.getUserId());
      sb.append(" ");
    }
  }

  private void appendResourceBaseCondition(StringBuilder sb) {
    SnapshotDto baseSnapshot = context.getBaseSnapshot();
    if (baseSnapshot != null) {
      if (filter.isOnBaseResourceChildren()) {
        sb.append(" AND s.parent_snapshot_id=").append(baseSnapshot.getId());
      } else {
        Long rootSnapshotId = (baseSnapshot.getRootId() != null ? baseSnapshot.getRootId() : baseSnapshot.getId());
        sb.append(" AND s.root_snapshot_id=").append(rootSnapshotId);
        sb.append(" AND s.path LIKE '").append(StringUtils.defaultString(baseSnapshot.getPath())).append(baseSnapshot.getId()).append(".%'");
      }
    }
  }

  private void appendResourceKeyCondition(StringBuilder sb) {
    if (StringUtils.isNotBlank(filter.getResourceKeyRegexp())) {
      sb.append(" AND UPPER(p.kee) LIKE '");
      // limitation : special characters _ and % are not escaped
      String regexp = StringEscapeUtils.escapeSql(filter.getResourceKeyRegexp());
      regexp = StringUtils.replaceChars(regexp, '*', '%');
      regexp = StringUtils.replaceChars(regexp, '?', '_');
      sb.append(StringUtils.upperCase(regexp)).append("'");
    }
  }

  private void appendResourceNameCondition(StringBuilder sb) {
    if (StringUtils.isNotBlank(filter.getResourceName())) {
      sb.append(" AND s.project_id IN (SELECT rindex.resource_id FROM resource_index rindex WHERE rindex.kee like '");
      sb.append(StringEscapeUtils.escapeSql(StringUtils.lowerCase(filter.getResourceName())));
      sb.append("%'");
      if (!filter.getResourceQualifiers().isEmpty()) {
        sb.append(" AND rindex.qualifier IN ");
        appendInStatement(filter.getResourceQualifiers(), sb);
      }
      sb.append(") ");
    }
  }

  List<MeasureFilterRow> process(ResultSet rs) throws SQLException {
    List<MeasureFilterRow> rows = Lists.newArrayList();
    RowProcessor rowProcessor;
    if (filter.sort().isOnNumericMeasure()) {
      rowProcessor = new NumericSortRowProcessor();
    } else if (filter.sort().isOnDate()) {
      rowProcessor = new DateSortRowProcessor();
    } else {
      rowProcessor = new TextSortRowProcessor();
    }

    while (rs.next()) {
      rows.add(rowProcessor.fetch(rs));
    }

    return rowProcessor.sort(rows, filter.sort().isAsc());
  }

  private static void appendInStatement(List<String> values, StringBuilder to) {
    to.append(" (");
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        to.append(",");
      }
      to.append("'");
      to.append(StringEscapeUtils.escapeSql(values.get(i)));
      to.append("'");
    }
    to.append(") ");
  }

  abstract static class RowProcessor {
    abstract Function sortFieldFunction();

    abstract Ordering sortFieldOrdering(boolean ascending);

    abstract MeasureFilterRow fetch(ResultSet rs) throws SQLException;

    final List<MeasureFilterRow> sort(List<MeasureFilterRow> rows, boolean ascending) {
      Ordering<MeasureFilterRow> ordering = sortFieldOrdering(ascending).onResultOf(sortFieldFunction());
      return ordering.immutableSortedCopy(rows);
    }
  }

  static class TextSortRowProcessor extends RowProcessor {
    MeasureFilterRow fetch(ResultSet rs) throws SQLException {
      MeasureFilterRow row = new MeasureFilterRow(rs.getLong(1), rs.getLong(2), rs.getLong(3));
      row.setSortText(rs.getString(4));
      return row;
    }

    Function sortFieldFunction() {
      return new Function<MeasureFilterRow, String>() {
        public String apply(MeasureFilterRow row) {
          return row.getSortText();
        }
      };
    }

    Ordering sortFieldOrdering(boolean ascending) {
      Ordering<String> ordering = Ordering.from(String.CASE_INSENSITIVE_ORDER);
      if (!ascending) {
        ordering = ordering.reverse();
      }
      return ordering;
    }

  }

  static class NumericSortRowProcessor extends RowProcessor {
    MeasureFilterRow fetch(ResultSet rs) throws SQLException {
      MeasureFilterRow row = new MeasureFilterRow(rs.getLong(1), rs.getLong(2), rs.getLong(3));
      double value = rs.getDouble(4);
      if (!rs.wasNull()) {
        row.setSortDouble(value);
      }
      return row;
    }

    Function sortFieldFunction() {
      return new Function<MeasureFilterRow, Double>() {
        public Double apply(MeasureFilterRow row) {
          return row.getSortDouble();
        }
      };
    }

    Ordering sortFieldOrdering(boolean ascending) {
      return newObjectOrdering(ascending);
    }
  }

  static class DateSortRowProcessor extends RowProcessor {
    MeasureFilterRow fetch(ResultSet rs) throws SQLException {
      MeasureFilterRow row = new MeasureFilterRow(rs.getLong(1), rs.getLong(2), rs.getLong(3));
      row.setSortDate(rs.getTimestamp(4));
      return row;
    }

    Function sortFieldFunction() {
      return new Function<MeasureFilterRow, Timestamp>() {
        public Timestamp apply(MeasureFilterRow row) {
          return row.getSortDate();
        }
      };
    }

    Ordering sortFieldOrdering(boolean ascending) {
      return newObjectOrdering(ascending);
    }
  }

  private static Ordering newObjectOrdering(boolean ascending) {
    if (ascending) {
      return Ordering.from(new Comparator<Comparable>() {
        public int compare(@Nullable Comparable left, @Nullable Comparable right) {
          if (left == null) {
            return 1;
          }
          if (right == null) {
            return -1;
          }

          return left.compareTo(right);
        }
      });
    }
    return Ordering.from(new Comparator<Comparable>() {
      public int compare(@Nullable Comparable left, @Nullable Comparable right) {
        if (left == null) {
          return 1;
        }
        if (right == null) {
          return -1;
        }

        return -left.compareTo(right);
      }
    });
  }
}
