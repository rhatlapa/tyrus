/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.tyrus.test.e2e;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfiguration;
import javax.websocket.ClientEndpointConfigurationBuilder;
import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;
import javax.websocket.MessageHandler;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class MaxMessageSizeTest {

    private CountDownLatch messageLatch;
    private String receivedMessage;

    @ServerEndpoint(value = "/endpoint1")
    public static class Endpoint1 {

        @OnMessage(maxMessageSize = 5)
        public String doThat(String message) {
            return message;
        }
    }

    @ServerEndpoint(value = "/endpoint2")
    public static class Endpoint2 {

        @OnMessage(maxMessageSize = 5)
        public String doThat(Session s, String message, boolean last) throws IOException {
            return message;
        }
    }

    @Test
    public void runTestBasic() {
        Server server = new Server(Endpoint1.class);

        try {
            server.start();

            messageLatch = new CountDownLatch(1);

            final ClientEndpointConfiguration clientConfiguration = ClientEndpointConfigurationBuilder.create().build();
            ClientManager client = ClientManager.createClient();

            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfiguration endpointConfiguration) {
                    try {
                        session.addMessageHandler(new MessageHandler.Basic<String>() {
                            @Override
                            public void onMessage(String message) {
                                receivedMessage = message;
                                messageLatch.countDown();
                            }
                        });

                        session.getBasicRemote().sendText("TEST1");
                    } catch (IOException e) {
                        // do nothing.
                    }
                }
            }, clientConfiguration, new URI("ws://localhost:8025/websockets/tests/endpoint1"));

            messageLatch.await(1, TimeUnit.SECONDS);
            Assert.assertEquals(0, messageLatch.getCount());
            Assert.assertEquals("TEST1", receivedMessage);

            messageLatch = new CountDownLatch(1);

            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfiguration endpointConfiguration) {
                    try {
                        session.getBasicRemote().sendText("LONG--");
                    } catch (IOException e) {
                        // do nothing.
                    }
                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    if(closeReason.getCloseCode().equals(CloseReason.CloseCodes.TOO_BIG)) {
                        messageLatch.countDown();
                    }
                }
            }, clientConfiguration, new URI("ws://localhost:8025/websockets/tests/endpoint1"));

            messageLatch.await(1, TimeUnit.SECONDS);
            Assert.assertEquals(0, messageLatch.getCount());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @Test
    public void runTestAsync() {
        Server server = new Server(Endpoint2.class);

        try {
            server.start();

            messageLatch = new CountDownLatch(1);

            final ClientEndpointConfiguration clientConfiguration = ClientEndpointConfigurationBuilder.create().build();
            ClientManager client = ClientManager.createClient();

            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfiguration endpointConfiguration) {
                    try {
                        session.addMessageHandler(new MessageHandler.Basic<String>() {
                            @Override
                            public void onMessage(String message) {
                                receivedMessage = message;
                                messageLatch.countDown();
                            }
                        });

                        session.getBasicRemote().sendText("TEST1", false);
                    } catch (IOException e) {
                        // do nothing.
                    }
                }
            }, clientConfiguration, new URI("ws://localhost:8025/websockets/tests/endpoint2"));

            messageLatch.await(1, TimeUnit.SECONDS);
            Assert.assertEquals(0, messageLatch.getCount());
            Assert.assertEquals("TEST1", receivedMessage);

            messageLatch = new CountDownLatch(1);

            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfiguration endpointConfiguration) {
                    try {
                        session.getBasicRemote().sendText("LONG--", false);
                    } catch (IOException e) {
                        // do nothing.
                    }
                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    if(closeReason.getCloseCode().equals(CloseReason.CloseCodes.TOO_BIG)) {
                        messageLatch.countDown();
                    }
                }
            }, clientConfiguration, new URI("ws://localhost:8025/websockets/tests/endpoint2"));

            messageLatch.await(1, TimeUnit.SECONDS);
            Assert.assertEquals(0, messageLatch.getCount());

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }
}