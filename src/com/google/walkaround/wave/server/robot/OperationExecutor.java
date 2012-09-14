/*
 * Copyright 2012 Goodow.com
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
 */
package com.google.walkaround.wave.server.robot;

import com.google.inject.Inject;
import com.google.walkaround.proto.ObjectSessionProto;
import com.google.walkaround.proto.ServerMutateRequest;
import com.google.walkaround.proto.gson.ObjectSessionProtoGsonImpl;
import com.google.walkaround.proto.gson.ServerMutateRequestGsonImpl;
import com.google.walkaround.slob.server.AccessDeniedException;
import com.google.walkaround.slob.server.SlobFacilities;
import com.google.walkaround.slob.server.SlobNotFoundException;
import com.google.walkaround.slob.server.SlobStoreSelector;
import com.google.walkaround.util.shared.RandomBase64Generator;
import com.google.walkaround.wave.server.auth.UserContext;
import com.google.walkaround.wave.server.conv.ConvStore;
import com.google.walkaround.wave.server.model.ClientIdGenerator;
import com.google.walkaround.wave.server.model.ServerMessageSerializer;
import com.google.walkaround.wave.shared.IdHack;
import com.google.walkaround.wave.shared.WaveSerializer;
import com.google.wave.api.OperationRequest;
import com.google.wave.api.ProtocolVersion;
import com.google.wave.api.data.converter.EventDataConverterManager;

import org.waveprotocol.box.server.robots.OperationResults;
import org.waveprotocol.box.server.robots.OperationServiceRegistry;
import org.waveprotocol.box.server.robots.RobotWaveletData;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.box.server.robots.util.OperationUtil;
import org.waveprotocol.wave.model.id.IdConstants;
import org.waveprotocol.wave.model.id.SimplePrefixEscaper;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.WaveletDelta;

import java.io.IOException;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OperationExecutor {
  private final SlobFacilities convFacilities;
  private final RandomBase64Generator random64;
  private final EventDataConverterManager converterManager;
  private final SlobStoreSelector storeSelector;
  private final ClientIdGenerator clientIdGenerator;
  private final UserContext userCtx;
  private static final WaveSerializer SERIALIZER =
      new WaveSerializer(new ServerMessageSerializer());
  private static final Logger log = Logger.getLogger(OperationExecutor.class.getName());

  @Inject
  OperationExecutor(@ConvStore SlobFacilities convFacilities,
      EventDataConverterManager converterManager, SlobStoreSelector storeSelector,
      RandomBase64Generator random64, ClientIdGenerator clientIdGenerator, UserContext userCtx) {
    this.converterManager = converterManager;
    this.storeSelector = storeSelector;
    this.userCtx = userCtx;
    this.convFacilities = convFacilities;
    this.random64 = random64;
    this.clientIdGenerator = clientIdGenerator;
  }

  public OperationResults execute(List<OperationRequest> operations,
      OperationServiceRegistry operationRegistry, RobotWaveletData boundWavelet) throws IOException {

    ProtocolVersion protocolVersion = OperationUtil.getProtocolVersion(operations);

    ConversationUtil conversationUtil = new ConversationUtil(new IdHack.StubIdGenerator() {
      @Override
      public String newBlipId() {
        return SimplePrefixEscaper.DEFAULT_ESCAPER.join(IdConstants.TOKEN_SEPARATOR,
            IdConstants.BLIP_PREFIX, random64.next(6));
      }
    });
    OperationContextImpl context =
        new OperationContextImpl(convFacilities, converterManager
            .getEventDataConverter(protocolVersion), conversationUtil, boundWavelet);

    for (OperationRequest operation : operations) {
      // Get the operation of the author taking into account the "proxying for"
      // field.
      OperationUtil.executeOperation(operation, operationRegistry, context, userCtx
          .getParticipantId());
    }
    for (Entry<WaveletName, RobotWaveletData> waveletEntry : context.getOpenWavelets().entrySet()) {
      WaveletName waveletName = waveletEntry.getKey();
      log.info("waveletName=" + waveletName);
      RobotWaveletData w = waveletEntry.getValue();
      for (WaveletDelta delta : w.getDeltas()) {
        List<String> payloads = SERIALIZER.serializeDeltas(delta);
        ObjectSessionProto session = new ObjectSessionProtoGsonImpl();
        String slobId = IdHack.objectIdFromWaveletId(waveletName.waveletId).getId();
        session.setObjectId(slobId);
        session.setStoreType(convFacilities.getRootEntityKind());
        session.setClientId(clientIdGenerator.getIdForRobot(userCtx.getParticipantId()).getId());
        ServerMutateRequest mutateRequest = new ServerMutateRequestGsonImpl();
        mutateRequest.setSession(session);
        mutateRequest.setVersion(delta.getTargetVersion().getVersion());
        mutateRequest.addAllPayload(payloads);

        try {
          storeSelector.get(session.getStoreType()).getSlobStore().mutateObject(mutateRequest);
        } catch (SlobNotFoundException e) {
          throw new RuntimeException("Slob not found processing robot response: " + slobId, e);
        } catch (AccessDeniedException e) {
          // TODO: Make INFO once we confirm that it really only occurs if the
          // robot was removed
          log.log(Level.SEVERE, "Robot removed while processing response?! " + slobId, e);
        }
      }
    }
    return context;
  }

}
