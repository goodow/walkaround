/**
 * Copyright 2010 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 * 
 */

package com.google.walkaround.wave.server.robot.operations;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.walkaround.wave.server.index.WaveIndexer;
import com.google.walkaround.wave.server.index.WaveIndexer.UserIndexEntry;
import com.google.walkaround.wave.shared.IdHack;
import com.google.wave.api.ApiIdSerializer;
import com.google.wave.api.InvalidRequestException;
import com.google.wave.api.JsonRpcConstant.ParamsProperty;
import com.google.wave.api.OperationRequest;
import com.google.wave.api.SearchResult;
import com.google.wave.api.SearchResult.Digest;

import org.waveprotocol.box.server.robots.OperationContext;
import org.waveprotocol.box.server.robots.operations.OperationService;
import org.waveprotocol.box.server.robots.util.OperationUtil;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link OperationService} for the "search" operation.
 * 
 * @author i@goodow.com (Larry Tin)
 */
public class SearchService implements OperationService {
  private static final Logger log = Logger.getLogger(SearchService.class.getName());
  /**
   * The number of search results to return if not defined in the request. Defined in the spec.
   */
  private static final int DEFAULT_NUMBER_SEARCH_RESULTS = 10;

  private final WaveIndexer waveIndexer;

  @Inject
  public SearchService(WaveIndexer waveIndexer) {
    this.waveIndexer = waveIndexer;
  }

  @Override
  public void execute(OperationRequest operation, OperationContext context,
      ParticipantId participant) throws InvalidRequestException {
    String query = OperationUtil.getRequiredParameter(operation, ParamsProperty.QUERY);
    int index = OperationUtil.getOptionalParameter(operation, ParamsProperty.INDEX, 0);
    int numResults =
        OperationUtil.getOptionalParameter(operation, ParamsProperty.NUM_RESULTS,
            DEFAULT_NUMBER_SEARCH_RESULTS);

    SearchResult result = search(participant, query, index, numResults);

    Map<ParamsProperty, Object> data =
        ImmutableMap.<ParamsProperty, Object> of(ParamsProperty.SEARCH_RESULTS, result);
    context.constructResponse(operation, data);
  }

  private SearchResult search(ParticipantId participant, String query, int startAt, int numResults) {
    List<UserIndexEntry> waves = null;
    try {
      waves = waveIndexer.findWaves(participant, query, startAt, numResults);
    } catch (IOException e) {
      log.log(Level.SEVERE, "IOException searching waves", e);
    }
    SearchResult toRtn = new SearchResult(query);
    if (waves == null) {
      return toRtn;
    }
    for (UserIndexEntry index : waves) {
      Digest digest =
          new Digest(index.getTitle(), index.getSnippetHtml(), ApiIdSerializer.instance()
              .serialiseWaveId(IdHack.waveIdFromConvObjectId(index.getObjectId())), Arrays
              .asList(index.getCreator().getAddress()), index.getLastModifiedMillis(), index
              .getLastModifiedMillis(), index.getUnreadCount(), index.getBlipCount());
      toRtn.addDigest(digest);
    }
    return toRtn;
  }
}
