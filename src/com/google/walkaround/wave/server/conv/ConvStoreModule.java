/*
 * Copyright 2011 Google Inc. All Rights Reserved.
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

package com.google.walkaround.wave.server.conv;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.inject.PrivateModule;
import com.google.inject.multibindings.Multibinder;
import com.google.walkaround.slob.server.AccessChecker;
import com.google.walkaround.slob.server.PostCommitAction;
import com.google.walkaround.slob.server.PostCommitActionQueue;
import com.google.walkaround.slob.server.PreCommitAction;
import com.google.walkaround.slob.server.StoreModuleHelper;
import com.google.walkaround.slob.shared.SlobModel;
import com.google.walkaround.wave.server.conv.PermissionCache.PermissionSource;
import com.google.walkaround.wave.server.index.IndexTask;
import com.google.walkaround.wave.server.model.WaveObjectStoreModel;
import com.google.walkaround.wave.server.robot.NotifyAllRobotsPreCommitAction;

import java.util.logging.Logger;

/**
 * Guice module that configures an object store for conversation wavelets.
 *
 * @author ohler@google.com (Christian Ohler)
 */
public class ConvStoreModule extends PrivateModule {

  @SuppressWarnings("unused")
  private static final Logger log = Logger.getLogger(ConvStoreModule.class.getName());

  // Perhaps a better name would be "Conv", but we can't change it, for
  // compatibility with existing data.
  public static String ROOT_ENTITY_KIND = "Wavelet";

  @Override protected void configure() {
    StoreModuleHelper.makeBasicBindingsAndExposures(binder(), ConvStore.class);
    StoreModuleHelper.bindEntityKinds(binder(), ROOT_ENTITY_KIND);

    bind(SlobModel.class).to(WaveObjectStoreModel.class);
    bind(AccessChecker.class).to(ConvAccessChecker.class);
    bind(PermissionSource.class).to(ConvPermissionSource.class);

    Multibinder<PreCommitAction> preCommitActions =
        Multibinder.newSetBinder(binder(), PreCommitAction.class);
    preCommitActions.addBinding().to(IndexTask.ConvPreCommit.class);
    preCommitActions.addBinding().toInstance(new NotifyAllRobotsPreCommitAction());

    Multibinder<PostCommitAction> postCommitActions =
        Multibinder.newSetBinder(binder(), PostCommitAction.class);
    postCommitActions.addBinding().to(IndexTask.Conv.class);

    bind(Queue.class).annotatedWith(PostCommitActionQueue.class).toInstance(
        QueueFactory.getQueue("post-commit-conv"));
  }

}
