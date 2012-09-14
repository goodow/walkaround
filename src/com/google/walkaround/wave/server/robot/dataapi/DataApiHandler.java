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
package com.google.walkaround.wave.server.robot.dataapi;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.walkaround.util.server.servlet.AbstractHandler;
import com.google.walkaround.wave.server.robot.OperationExecutor;
import com.google.wave.api.InvalidRequestException;
import com.google.wave.api.JsonRpcResponse;
import com.google.wave.api.OperationRequest;
import com.google.wave.api.ProtocolVersion;
import com.google.wave.api.RobotSerializer;
import com.google.wave.api.impl.GsonFactory;

import org.waveprotocol.box.server.robots.OperationResults;
import org.waveprotocol.box.server.robots.OperationServiceRegistry;
import org.waveprotocol.box.server.robots.util.OperationUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class DataApiHandler extends AbstractHandler {
  private static final Logger log = Logger.getLogger(DataApiHandler.class.getName());
  private static final String JSON_CONTENT_TYPE = "application/json";

  private final OperationServiceRegistry serviceRegistry;
  private final OperationExecutor operationExecutor;
  private final RobotSerializer serializer;

  /** Holds incoming operation requests. */
  private List<OperationRequest> operations;

  /**
   * Reads the given HTTP request's input stream into a string.
   * 
   * @param req the HTTP request to be read.
   * @return a string representation of the given HTTP request's body.
   * 
   * @throws IOException if there is a problem reading the body.
   */
  private static String readRequestBody(HttpServletRequest req) throws IOException {
    StringBuilder json = new StringBuilder();
    BufferedReader reader = req.getReader();
    String line;
    while ((line = reader.readLine()) != null) {
      json.append(line);
    }
    return json.toString();
  }

  @Inject
  DataApiHandler(DataApiOperationServiceRegistry serviceRegistry,
      OperationExecutor operationExecutor, RobotSerializer serializer) {
    this.serviceRegistry = serviceRegistry;
    this.operationExecutor = operationExecutor;
    this.serializer = serializer;
  }

  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException,
      ServletException {
    String apiRequest = readRequestBody(req);
    log.info("Received the following Json: " + apiRequest);
    try {
      operations = serializer.deserializeOperations(apiRequest);
    } catch (InvalidRequestException e) {
      log.info("Unable to parse Json to list of OperationRequests: " + apiRequest);
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST,
          "Unable to parse Json to list of OperationRequests: " + apiRequest);
      return;
    }
    log.info(operations.size() + " operation requests returned");
    for (OperationRequest request : operations) {
      log.info("" + request);
    }
    OperationResults operationResults =
        operationExecutor.execute(operations, serviceRegistry, null);
    handleResults(operationResults, resp, OperationUtil.getProtocolVersion(operations));
  }

  /**
   * Handles an {@link OperationResults} by submitting the deltas that are generated and writing a
   * response to the robot.
   * 
   * @param results the results of the operations performed.
   * @param resp the servlet to write the response in.
   * @param version the version of the protocol to use for writing a response.
   * @throws IOException if the response can not be written.
   */
  private void handleResults(OperationResults results, HttpServletResponse resp,
      ProtocolVersion version) throws IOException {
    // Ensure that responses are returned in the same order as corresponding
    // requests.
    LinkedList<JsonRpcResponse> responses = Lists.newLinkedList();
    for (OperationRequest operation : operations) {
      String opId = operation.getId();
      JsonRpcResponse response = results.getResponses().get(opId);
      responses.addLast(response);
    }

    String jsonResponse =
        serializer.serialize(responses, GsonFactory.JSON_RPC_RESPONSE_LIST_TYPE, version);
    log.info("Returning the following Json: " + jsonResponse);

    // Write the response back through the HttpServlet
    try {
      resp.setContentType(JSON_CONTENT_TYPE);
      PrintWriter writer = resp.getWriter();
      writer.append(jsonResponse);
      writer.flush();
      resp.setStatus(HttpServletResponse.SC_OK);
    } catch (IOException e) {
      log.log(Level.SEVERE, "IOException during writing of a response", e);
      throw e;
    }
  }

}
