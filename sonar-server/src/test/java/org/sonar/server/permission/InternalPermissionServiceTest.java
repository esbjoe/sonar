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

package org.sonar.server.permission;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang.ObjectUtils;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.security.DefaultGroups;
import org.sonar.core.user.GroupDto;
import org.sonar.core.user.GroupRoleDto;
import org.sonar.core.user.Permission;
import org.sonar.core.user.RoleDao;
import org.sonar.core.user.UserDao;
import org.sonar.core.user.UserDto;
import org.sonar.core.user.UserRoleDto;
import org.sonar.server.exceptions.BadRequestException;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.UnauthorizedException;
import org.sonar.server.user.MockUserSession;

import java.util.Map;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonar.server.permission.InternalPermissionServiceTest.MatchesGroupRole.matchesRole;
import static org.sonar.server.permission.InternalPermissionServiceTest.MatchesUserRole.matchesRole;

public class InternalPermissionServiceTest {

  @Rule
  public ExpectedException throwable = ExpectedException.none();

  private Map<String, Object> params;
  private InternalPermissionService service;
  private RoleDao roleDao;
  private UserDao userDao;

  @Before
  public void setUpCommonStubbing() {
    MockUserSession.set().setLogin("admin").setPermissions(Permission.SYSTEM_ADMIN);

    UserDto targetedUser = new UserDto().setId(2L).setLogin("user").setActive(true);
    GroupDto targetedGroup = new GroupDto().setId(2L).setName("group");

    roleDao = mock(RoleDao.class);

    userDao = mock(UserDao.class);
    when(userDao.selectActiveUserByLogin("user")).thenReturn(targetedUser);
    when(userDao.selectGroupByName("group")).thenReturn(targetedGroup);

    service = new InternalPermissionService(roleDao, userDao);
  }

  @Test
  public void should_add_user_permission() throws Exception {
    params = buildParams("user", null, Permission.DASHBOARD_SHARING);
    setUpUserPermissions("user", Permission.QUALITY_PROFILE_ADMIN.key());
    UserRoleDto roleToInsert = new UserRoleDto().setUserId(2L).setRole(Permission.DASHBOARD_SHARING.key());

    service.addPermission(params);

    verify(roleDao).insertUserRole(argThat(matchesRole(roleToInsert)));
  }

  @Test
  public void should_remove_user_permission() throws Exception {
    params = buildParams("user", null, Permission.QUALITY_PROFILE_ADMIN);
    setUpUserPermissions("user", Permission.QUALITY_PROFILE_ADMIN.key());
    UserRoleDto roleToRemove = new UserRoleDto().setUserId(2L).setRole(Permission.QUALITY_PROFILE_ADMIN.key());

    service.removePermission(params);

    verify(roleDao).deleteUserRole(argThat(matchesRole(roleToRemove)));
  }

  @Test
  public void should_add_group_permission() throws Exception {
    params = buildParams(null, "group", Permission.DASHBOARD_SHARING);
    setUpGroupPermissions("group", Permission.QUALITY_PROFILE_ADMIN.key());
    GroupRoleDto roleToInsert = new GroupRoleDto().setGroupId(2L).setRole(Permission.DASHBOARD_SHARING.key());

    service.addPermission(params);

    verify(roleDao).insertGroupRole(argThat(matchesRole(roleToInsert)));
  }

  @Test
  public void should_remove_group_permission() throws Exception {
    params = buildParams(null, "group", Permission.QUALITY_PROFILE_ADMIN);
    setUpGroupPermissions("group", Permission.QUALITY_PROFILE_ADMIN.key());
    GroupRoleDto roleToRemove = new GroupRoleDto().setGroupId(2L).setRole(Permission.QUALITY_PROFILE_ADMIN.key());

    service.removePermission(params);

    verify(roleDao).deleteGroupRole(argThat(matchesRole(roleToRemove)));
  }

  @Test
  public void should_skip_redundant_permission_change() throws Exception {
    params = buildParams("user", null, Permission.QUALITY_PROFILE_ADMIN);
    setUpUserPermissions("user", Permission.QUALITY_PROFILE_ADMIN.key());

    service.addPermission(params);

    verify(roleDao, never()).insertUserRole(any(UserRoleDto.class));
  }

  @Test
  public void should_fail_on_invalid_request() throws Exception {
    throwable.expect(BadRequestException.class);
    params = buildParams("user", "group", Permission.QUALITY_PROFILE_ADMIN);

    service.addPermission(params);
  }

  @Test
  public void should_fail_on_insufficient_rights() throws Exception {
    throwable.expect(ForbiddenException.class);
    params = buildParams("user", null, Permission.QUALITY_PROFILE_ADMIN);

    MockUserSession.set().setLogin("unauthorized").setPermissions(Permission.QUALITY_PROFILE_ADMIN);

    service.addPermission(params);
  }

  @Test
  public void should_fail_on_anonymous_access() throws Exception {
    throwable.expect(UnauthorizedException.class);
    params = buildParams("user", null, Permission.QUALITY_PROFILE_ADMIN);

    MockUserSession.set();

    service.addPermission(params);
  }

  @Test
  public void should_add_permission_to_anyone_group() throws Exception {
    params = buildParams(null, DefaultGroups.ANYONE, Permission.QUALITY_PROFILE_ADMIN);
    GroupRoleDto roleToInsert = new GroupRoleDto().setRole(Permission.QUALITY_PROFILE_ADMIN.key());

    service.addPermission(params);

    verify(roleDao).insertGroupRole(argThat(matchesRole(roleToInsert)));
  }

  @Test
  public void should_remove_permission_from_anyone_group() throws Exception {
    params = buildParams(null, DefaultGroups.ANYONE, Permission.QUALITY_PROFILE_ADMIN);
    setUpGroupPermissions(DefaultGroups.ANYONE, Permission.QUALITY_PROFILE_ADMIN.key());
    GroupRoleDto roleToDelete = new GroupRoleDto().setRole(Permission.QUALITY_PROFILE_ADMIN.key());

    service.removePermission(params);

    verify(roleDao).deleteGroupRole(argThat(matchesRole(roleToDelete)));
  }

  protected static class MatchesUserRole extends BaseMatcher<UserRoleDto> {

    private final UserRoleDto referenceDto;

    private MatchesUserRole(UserRoleDto referenceDto) {
      this.referenceDto = referenceDto;
    }

    public static MatchesUserRole matchesRole(UserRoleDto referenceDto) {
      return new MatchesUserRole(referenceDto);
    }

    @Override
    public boolean matches(Object o) {
      if (o != null && o instanceof UserRoleDto) {
        UserRoleDto otherDto = (UserRoleDto) o;
        return ObjectUtils.equals(referenceDto.getResourceId(), otherDto.getResourceId()) &&
          ObjectUtils.equals(referenceDto.getRole(), otherDto.getRole()) &&
          ObjectUtils.equals(referenceDto.getUserId(), otherDto.getUserId());
      }
      return false;
    }

    @Override
    public void describeTo(Description description) {
    }
  }

  protected static class MatchesGroupRole extends BaseMatcher<GroupRoleDto> {

    private final GroupRoleDto referenceDto;

    private MatchesGroupRole(GroupRoleDto referenceDto) {
      this.referenceDto = referenceDto;
    }

    public static MatchesGroupRole matchesRole(GroupRoleDto referenceDto) {
      return new MatchesGroupRole(referenceDto);
    }

    @Override
    public boolean matches(Object o) {
      if (o != null && o instanceof GroupRoleDto) {
        GroupRoleDto otherDto = (GroupRoleDto) o;
        return ObjectUtils.equals(referenceDto.getResourceId(), otherDto.getResourceId()) &&
          ObjectUtils.equals(referenceDto.getRole(), otherDto.getRole()) &&
          ObjectUtils.equals(referenceDto.getGroupId(), otherDto.getGroupId());
      }
      return false;
    }

    @Override
    public void describeTo(Description description) {
    }
  }

  private Map<String, Object> buildParams(String login, String group, Permission perm) {
    Map<String, Object> params = Maps.newHashMap();
    params.put("user", login);
    params.put("group", group);
    params.put("permission", perm.key());
    return params;
  }

  private void setUpUserPermissions(String login, String... permissions) {
    when(roleDao.selectUserPermissions(login)).thenReturn(Lists.newArrayList(permissions));
  }

  private void setUpGroupPermissions(String groupName, String... permissions) {
    when(roleDao.selectGroupPermissions(groupName)).thenReturn(Lists.newArrayList(permissions));
  }
}
