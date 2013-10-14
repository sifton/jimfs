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

import static com.google.jimfs.attribute.UserLookupService.createGroupPrincipal;
import static com.google.jimfs.attribute.UserLookupService.createUserPrincipal;
import static org.junit.Assert.assertNotNull;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.jimfs.attribute.AttributeProvider;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * Tests for {@link PosixAttributeProvider}.
 *
 * @author Colin Decker
 */
public class PosixAttributeProviderTest extends AttributeProviderTest<PosixAttributeProvider> {

  @Override
  protected PosixAttributeProvider createProvider() {
    return new PosixAttributeProvider();
  }

  @Override
  protected Set<? extends AttributeProvider<?>> createInheritedProviders() {
    return ImmutableSet.of(new BasicAttributeProvider(), new OwnerAttributeProvider());
  }

  @Test
  public void testInitialAttributes() {
    assertContainsAll(metadata,
        ImmutableMap.of(
            "group", createGroupPrincipal("group"),
            "permissions", PosixFilePermissions.fromString("rw-r--r--")));
  }

  @Test
  public void testSet() {
    assertSetAndGetSucceeds("group", createGroupPrincipal("foo"));
    assertSetAndGetSucceeds("permissions", PosixFilePermissions.fromString("rwxrwxrwx"));

    // invalid types
    assertSetFails("permissions", ImmutableList.of(PosixFilePermission.GROUP_EXECUTE));
    assertSetFails("permissions", ImmutableSet.of("foo"));
  }

  @Test
  public void testSetOnCreate() {
    assertSetAndGetSucceedsOnCreate("permissions", PosixFilePermissions.fromString("rwxrwxrwx"));
    assertSetFailsOnCreate("group", createGroupPrincipal("foo"));
  }

  @Test
  public void testView() throws IOException {
    metadata.setAttribute("owner:owner", createUserPrincipal("user"));

    PosixFileAttributeView view = provider.view(metadataSupplier(),
        ImmutableMap.<String, FileAttributeView>of(
            "basic", new BasicAttributeProvider().view(metadataSupplier(), NO_INHERITED_VIEWS),
            "owner", new OwnerAttributeProvider().view(metadataSupplier(), NO_INHERITED_VIEWS)));
    assertNotNull(view);

    ASSERT.that(view.name()).is("posix");
    ASSERT.that(view.getOwner()).is(createUserPrincipal("user"));

    PosixFileAttributes attrs = view.readAttributes();
    ASSERT.that(attrs.fileKey()).is(0L);
    ASSERT.that(attrs.owner()).is(createUserPrincipal("user"));
    ASSERT.that(attrs.group()).is(createGroupPrincipal("group"));
    ASSERT.that(attrs.permissions()).is(PosixFilePermissions.fromString("rw-r--r--"));

    view.setOwner(createUserPrincipal("root"));
    ASSERT.that(view.getOwner()).is(createUserPrincipal("root"));
    ASSERT.that(metadata.getAttribute("owner:owner")).is(createUserPrincipal("root"));

    view.setGroup(createGroupPrincipal("root"));
    ASSERT.that(view.readAttributes().group()).is(createGroupPrincipal("root"));
    ASSERT.that(metadata.getAttribute("posix:group")).is(createGroupPrincipal("root"));

    view.setPermissions(PosixFilePermissions.fromString("rwx------"));
    ASSERT.that(view.readAttributes().permissions())
        .is(PosixFilePermissions.fromString("rwx------"));
    ASSERT.that(metadata.getAttribute("posix:permissions"))
        .is(PosixFilePermissions.fromString("rwx------"));
  }

  @Test
  public void testAttributes() {
    PosixFileAttributes attrs = provider.readAttributes(metadata);
    ASSERT.that(attrs.permissions()).is(PosixFilePermissions.fromString("rw-r--r--"));
    ASSERT.that(attrs.group()).is(createGroupPrincipal("group"));
    ASSERT.that(attrs.fileKey()).is(0L);
  }
}
