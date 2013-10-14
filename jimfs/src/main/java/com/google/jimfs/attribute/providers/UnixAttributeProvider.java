/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.jimfs.attribute.providers;

import com.google.common.collect.ImmutableSet;
import com.google.jimfs.attribute.AttributeProvider;
import com.google.jimfs.attribute.FileMetadata;

import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Attribute provider that provides the "unix" attribute view.
 *
 * @author Colin Decker
 */
final class UnixAttributeProvider extends AttributeProvider<UnixFileAttributeView> {

  private static final ImmutableSet<String> ATTRIBUTES = ImmutableSet.of(
      "uid",
      "ino",
      "dev",
      "nlink",
      "rdev",
      "ctime",
      "mode",
      "gid");

  private static final ImmutableSet<String> INHERITED_VIEWS =
      ImmutableSet.of("basic", "owner", "posix");

  private final AtomicInteger uidGenerator = new AtomicInteger();
  private final ConcurrentMap<Object, Integer> idCache = new ConcurrentHashMap<>();

  @Override
  public String name() {
    return "unix";
  }

  @Override
  public ImmutableSet<String> inherits() {
    return INHERITED_VIEWS;
  }

  @Override
  public ImmutableSet<String> fixedAttributes() {
    return ATTRIBUTES;
  }

  @Override
  public Class<UnixFileAttributeView> viewType() {
    return UnixFileAttributeView.class;
  }

  @Override
  public UnixFileAttributeView view(FileMetadata.Lookup lookup,
      Map<String, FileAttributeView> inheritedViews) {
    throw new UnsupportedOperationException(); // should not be called
  }

  /**
   * Returns an ID that is guaranteed to be the same for any invocation with equal objects.
   */
  private Integer getUniqueId(Object object) {
    Integer id = idCache.get(object);
    if (id == null) {
      id = uidGenerator.incrementAndGet();
      Integer existing = idCache.putIfAbsent(object, id);
      if (existing != null) {
        return existing;
      }
    }
    return id;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Object get(FileMetadata metadata, String attribute) {
    switch (attribute) {
      case "uid":
        UserPrincipal user = (UserPrincipal) metadata.getAttribute("owner:owner");
        return getUniqueId(user);
      case "gid":
        GroupPrincipal group = (GroupPrincipal) metadata.getAttribute("posix:group");
        return getUniqueId(group);
      case "mode":
        Set<PosixFilePermission> permissions =
            (Set<PosixFilePermission>) metadata.getAttribute("posix:permissions");
        return toMode(permissions);
      case "ctime":
        return FileTime.fromMillis(metadata.getCreationTime());
      case "rdev":
        return 0L;
      case "dev":
        return 1L;
      case "ino":
        return getUniqueId(metadata);
      case "nlink":
        return metadata.links();
    }

    return null;
  }

  @Override
  public void set(
      FileMetadata metadata, String view, String attribute, Object value, boolean create) {
    throw unsettable(view, attribute);
  }

  @SuppressWarnings("OctalInteger")
  private static int toMode(Set<PosixFilePermission> permissions) {
    int result = 0;
    for (PosixFilePermission permission : permissions) {
      switch (permission) {
        case OWNER_READ:
          result |= 0400;
          break;
        case OWNER_WRITE:
          result |= 0200;
          break;
        case OWNER_EXECUTE:
          result |= 0100;
          break;
        case GROUP_READ:
          result |= 0040;
          break;
        case GROUP_WRITE:
          result |= 0020;
          break;
        case GROUP_EXECUTE:
          result |= 0010;
          break;
        case OTHERS_READ:
          result |= 0004;
          break;
        case OTHERS_WRITE:
          result |= 0002;
          break;
        case OTHERS_EXECUTE:
          result |= 0001;
          break;
      }
    }
    return result;
  }
}
