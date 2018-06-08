package org.eclipse.ppp4e.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

import org.eclipse.ppp4e.ProvisioningPlugin;
import org.eclipse.ppp4j.messages.Initialize;
import org.eclipse.ppp4j.messages.InitializeResult;
import org.eclipse.ppp4j.messages.ProvisionResult;
import org.eclipse.ppp4j.messages.ProvisioningParameters;
import org.eclipse.ppp4j.messages.RpcRequest;
import org.eclipse.ppp4j.messages.RpcResponse;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class Server {
	private String baseMethod = "projectProvisioning/";
	StreamConnectionProvider streamConnectionProvider;
	Map<Integer, Semaphore> messageResponseSemaphores = new HashMap<>();
	Map<Integer, Object> messageResponses = new HashMap<>();
	private Integer nextMessageId = 0;
	private Gson gson = new Gson();
	private PrintWriter writer;
	private BufferedReader reader;

	public Server(StreamConnectionProvider streamConnectionProvider) {
		this.streamConnectionProvider = streamConnectionProvider;
		try {
			streamConnectionProvider.start();
			writer = new PrintWriter(streamConnectionProvider.getOutputStream());
			listenForMessages();
		} catch (Exception e) {
			ProvisioningPlugin.logError(e);
		}
	}

	public void closeConnection() {
		if (writer != null) {
			writer.close();
		}
		if (reader != null) {
			try {
				reader.close();
			} catch (IOException e) {
				ProvisioningPlugin.logError(e);
			}
		}
		streamConnectionProvider.stop();
	}

	public CompletableFuture<InitializeResult> Initalize() {
		return sendMessage("initalize", new Initialize(true, true)).thenApply(object -> {
			return gson.fromJson(gson.toJson(object), InitializeResult.class);
		});
	}

	public CompletableFuture<ProvisionResult> Provision(ProvisioningParameters parameters) {
		return sendMessage("provision", parameters).thenApply(object -> {
			return gson.fromJson(gson.toJson(object), ProvisionResult.class);
		});
	}

	private CompletableFuture<Object> sendMessage(String method, Object params) {
		CompletableFuture<Object> completableFuture = CompletableFuture.supplyAsync(() -> {
			final int id = nextMessageId;
			nextMessageId++;
			RpcRequest request = new RpcRequest(String.valueOf(id), baseMethod + method, params);
			writer.println(gson.toJson(request));
			writer.flush();

			Semaphore responseSemaphore = new Semaphore(0);
			messageResponseSemaphores.put(id, responseSemaphore);
			try {
				responseSemaphore.acquire();
				Object result = messageResponses.get(id);
				messageResponses.remove(id);
				return result;
			} catch (InterruptedException e) {
				ProvisioningPlugin.logError(e);
				return null;
			}
		});
		return completableFuture;
	}

	private void listenForMessages() {
		CompletableFuture.runAsync(() -> {
			try {
				reader = new BufferedReader(new InputStreamReader(streamConnectionProvider.getInputStream()));
				while (true) {
					String input = reader.readLine();
					if (input == null) {
						break;
					}
					RpcResponse response;
					try {
						response = gson.fromJson(input, RpcResponse.class);
					} catch (JsonSyntaxException e) {
						System.out.println("Unknown message format: " + input);
						continue;
					}
					Integer id = Integer.parseInt(response.id);
					messageResponses.put(id, response.result);
					messageResponseSemaphores.get(id).release();
				}
			} catch (Exception e) {
				System.out.println(e);
			} finally {
				if (reader != null) {
					try {
						reader.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		});
	}
}
